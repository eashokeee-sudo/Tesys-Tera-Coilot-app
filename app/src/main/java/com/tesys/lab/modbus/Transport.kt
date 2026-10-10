package com.tesys.lab.modbus

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/** Moves one request PDU to the device and returns the response. Framing (MBAP / CRC) lives here. */
interface Transport {
    suspend fun open()
    fun close()
    suspend fun transact(unit: Int, pdu: ByteArray, timeoutMs: Long): Frame
}

/* ------------------------------------------------------------------ Modbus TCP */

class TcpTransport(private val host: String, private val port: Int) : Transport {
    private var socket: Socket? = null
    private var txId = 0

    override suspend fun open() = withContext(Dispatchers.IO) {
        val s = Socket()
        s.tcpNoDelay = true
        try {
            s.connect(InetSocketAddress(host, port), 3000)
        } catch (e: IOException) {
            runCatching { s.close() }
            throw CommError("Cannot connect to $host:$port (${e.message})", ErrKind.IO)
        }
        socket = s
    }

    override fun close() {
        runCatching { socket?.close() }
        socket = null
    }

    override suspend fun transact(unit: Int, pdu: ByteArray, timeoutMs: Long): Frame =
        withContext(Dispatchers.IO) {
            val s = socket ?: throw CommError("Not connected", ErrKind.IO)
            txId = (txId + 1) and 0xFFFF
            val len = pdu.size + 1
            val adu = ByteArray(7 + pdu.size)
            adu[0] = (txId shr 8).toByte(); adu[1] = txId.toByte()
            adu[4] = (len shr 8).toByte(); adu[5] = len.toByte()
            adu[6] = unit.toByte()
            System.arraycopy(pdu, 0, adu, 7, pdu.size)
            try {
                s.soTimeout = timeoutMs.toInt().coerceAtLeast(50)
                s.getOutputStream().apply { write(adu); flush() }
                val inp = s.getInputStream()
                // Skip stale responses (wrong transaction id) - at most a few.
                repeat(5) {
                    val hdr = readFully(inp, 7, adu)
                    val rid = ((hdr[0].toInt() and 0xFF) shl 8) or (hdr[1].toInt() and 0xFF)
                    val rlen = ((hdr[4].toInt() and 0xFF) shl 8) or (hdr[5].toInt() and 0xFF)
                    if (rlen < 2 || rlen > 260) throw CommError("Bad MBAP length $rlen", ErrKind.PROTOCOL, adu, hdr)
                    val body = readFully(inp, rlen - 1, adu, hdr)
                    if (rid == txId) {
                        return@withContext Frame(adu, hdr + body, body)
                    }
                }
                throw CommError("No matching transaction id", ErrKind.PROTOCOL, adu)
            } catch (e: SocketTimeoutException) {
                throw CommError("Timeout", ErrKind.TIMEOUT, adu)
            } catch (e: IOException) {
                throw CommError("I/O error: ${e.message}", ErrKind.IO, adu)
            }
        }

    private fun readFully(inp: InputStream, n: Int, tx: ByteArray, already: ByteArray = ByteArray(0)): ByteArray {
        val buf = ByteArray(n)
        var got = 0
        while (got < n) {
            val r = inp.read(buf, got, n - got)
            if (r < 0) throw CommError("Connection closed by device", ErrKind.IO, tx, already + buf.copyOf(got))
            got += r
        }
        return buf
    }
}

/* ------------------------------------------------------------------ Modbus RTU over USB-RS485 */

class UsbRtuTransport(
    private val usb: UsbManager,
    private val device: UsbDevice,
    private val baud: Int,
    private val parity: String,
    private val stopBits: Int
) : Transport {
    private var port: UsbSerialPort? = null

    override suspend fun open() = withContext(Dispatchers.IO) {
        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
            ?: throw CommError("No serial driver for this USB device", ErrKind.IO)
        val conn = usb.openDevice(device)
            ?: throw CommError("Cannot open USB device (permission denied?)", ErrKind.IO)
        val p = driver.ports[0]
        try {
            p.open(conn)
            val par = when (parity) {
                "ODD" -> UsbSerialPort.PARITY_ODD
                "EVEN" -> UsbSerialPort.PARITY_EVEN
                else -> UsbSerialPort.PARITY_NONE
            }
            val sb = if (stopBits == 2) UsbSerialPort.STOPBITS_2 else UsbSerialPort.STOPBITS_1
            p.setParameters(baud, UsbSerialPort.DATABITS_8, sb, par)
        } catch (e: Exception) {
            runCatching { p.close() }
            throw CommError("Serial open failed: ${e.message}", ErrKind.IO)
        }
        port = p
    }

    override fun close() {
        runCatching { port?.close() }
        port = null
    }

    override suspend fun transact(unit: Int, pdu: ByteArray, timeoutMs: Long): Frame =
        withContext(Dispatchers.IO) {
            val p = port ?: throw CommError("Port closed", ErrKind.IO)
            val n = pdu.size + 1
            val adu = ByteArray(n + 2)
            adu[0] = unit.toByte()
            System.arraycopy(pdu, 0, adu, 1, pdu.size)
            val crc = Modbus.crc16(adu, n)
            adu[n] = (crc and 0xFF).toByte()
            adu[n + 1] = (crc shr 8).toByte()

            try {
                runCatching { p.purgeHwBuffers(true, true) }
                p.write(adu, 1000)
                val buf = ByteArray(300)
                val tmp = ByteArray(256)
                var got = 0
                var expected = -1
                val deadline = System.nanoTime() + timeoutMs * 1_000_000
                while (true) {
                    val remainingMs = (deadline - System.nanoTime()) / 1_000_000
                    if (remainingMs <= 0) throw CommError("Timeout", ErrKind.TIMEOUT, adu, buf.copyOf(got))
                    val r = p.read(tmp, minOf(remainingMs, 100L).toInt())
                    if (r > 0) {
                        val c = minOf(r, buf.size - got)
                        System.arraycopy(tmp, 0, buf, got, c)
                        got += c
                    }
                    if (expected < 0 && got >= 3) expected = expectedLength(buf)
                    if (expected in 1..got) break
                }
                val rx = buf.copyOf(expected)
                val calc = Modbus.crc16(rx, expected - 2)
                val rxCrc = (rx[expected - 2].toInt() and 0xFF) or ((rx[expected - 1].toInt() and 0xFF) shl 8)
                if (calc != rxCrc) throw CommError("CRC error", ErrKind.CRC, adu, rx)
                if ((rx[0].toInt() and 0xFF) != unit) throw CommError("Reply from unit ${rx[0].toInt() and 0xFF}, expected $unit", ErrKind.PROTOCOL, adu, rx)
                Frame(adu, rx, rx.copyOfRange(1, expected - 2))
            } catch (e: CommError) {
                throw e
            } catch (e: Exception) {
                throw CommError("USB I/O error: ${e.message}", ErrKind.IO, adu)
            }
        }

    private fun expectedLength(b: ByteArray): Int {
        val fc = b[1].toInt() and 0xFF
        return when {
            fc and 0x80 != 0 -> 5                         // exception: addr fc code crc crc
            fc in 1..4 -> 5 + (b[2].toInt() and 0xFF)     // addr fc bytecount data crc crc
            fc == 5 || fc == 6 || fc == 15 || fc == 16 -> 8
            else -> 5
        }
    }
}
