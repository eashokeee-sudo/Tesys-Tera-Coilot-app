package com.tesys.lab.modbus

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One logged transaction. Raw bytes are kept so the Logs screen can explain every field. */
class FrameEntry(
    val time: String,
    val txBytes: ByteArray,
    val rxBytes: ByteArray,
    val ms: Long,
    val result: String,
    val ok: Boolean,
    val tcp: Boolean,
    val write: Boolean,
    val id: Long
) {
    val tx: String get() = Modbus.hex(txBytes)
    val rx: String get() = Modbus.hex(rxBytes)
}

data class CommStats(
    val total: Long = 0, val ok: Long = 0, val timeouts: Long = 0,
    val crc: Long = 0, val exceptions: Long = 0, val other: Long = 0, val avgMs: Long = 0
) {
    val successPct: Int get() = if (total == 0L) 0 else (ok * 100 / total).toInt()
}

/** Rolling log of the last transactions + counters (shown on the Logs tab). */
class FrameLog(private val max: Int = 300) {
    private val _entries = MutableStateFlow<List<FrameEntry>>(emptyList())
    val entries: StateFlow<List<FrameEntry>> = _entries
    private val _stats = MutableStateFlow(CommStats())
    val stats: StateFlow<CommStats> = _stats
    @Volatile var paused = false
    @Volatile var tcp = false          // true: frames carry a Modbus TCP (MBAP) header, false: RTU with CRC
    private var sumMs = 0L
    private var seq = 0L
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun add(tx: ByteArray, rx: ByteArray, ms: Long, result: String, kind: ErrKind?, write: Boolean = false) {
        val s = _stats.value
        sumMs += ms
        val total = s.total + 1
        val ns = when (kind) {
            null -> s.copy(ok = s.ok + 1)
            ErrKind.TIMEOUT -> s.copy(timeouts = s.timeouts + 1)
            ErrKind.CRC -> s.copy(crc = s.crc + 1)
            ErrKind.EXCEPTION -> s.copy(exceptions = s.exceptions + 1)
            else -> s.copy(other = s.other + 1)
        }.copy(total = total, avgMs = sumMs / total)
        _stats.value = ns
        if (paused) return
        val e = FrameEntry(fmt.format(Date()), tx, rx, ms, result, kind == null, tcp, write, ++seq)
        _entries.value = (listOf(e) + _entries.value).take(max)
    }

    @Synchronized
    fun clear() { _entries.value = emptyList(); _stats.value = CommStats(); sumMs = 0 }
}

/**
 * Modbus client. Reads are always allowed. The single write function (FC06) refuses to run unless
 * [writeAllowed] returns true, which the app only does while the user has unlocked write access.
 */
class ModbusClient(
    private val transport: Transport,
    private val unit: Int,
    private val timeoutMs: Long,
    private val log: FrameLog
) {
    private val mutex = Mutex()
    @Volatile var writeAllowed: () -> Boolean = { false }

    /** FC03 / FC04. */
    suspend fun readWords(fc: Int, addr: Int, qty: Int): IntArray = mutex.withLock {
        val f = exchange(Modbus.readPdu(fc, addr, qty))
        Modbus.parseWords(f.pdu, qty)
    }

    /** FC01 / FC02. Returns one 0/1 per bit. */
    suspend fun readBits(fc: Int, addr: Int, qty: Int): IntArray = mutex.withLock {
        val f = exchange(Modbus.readPdu(fc, addr, qty))
        Modbus.parseBits(f.pdu, qty)
    }

    /** FC06 write single register; verifies the echo. Throws when write access is locked. */
    suspend fun writeRegister(addr: Int, value: Int) {
        mutex.withLock {
            if (!writeAllowed()) throw CommError("Write access is locked. Unlock it first.", ErrKind.PROTOCOL)
            val req = Modbus.writePdu(addr, value and 0xFFFF)
            val f = exchange(req)
            if (!f.pdu.contentEquals(req)) throw CommError("Unexpected write response", ErrKind.PROTOCOL, f.tx, f.rx)
        }
    }

    private suspend fun exchange(pdu: ByteArray): Frame {
        val isWrite = pdu.isNotEmpty() && pdu[0].toInt() == 6
        val t0 = System.nanoTime()
        try {
            val f = transport.transact(unit, pdu, timeoutMs)
            val ms = (System.nanoTime() - t0) / 1_000_000
            if (f.pdu.isNotEmpty() && (f.pdu[0].toInt() and 0x80) != 0) {
                val code = if (f.pdu.size > 1) f.pdu[1].toInt() and 0xFF else 0
                log.add(f.tx, f.rx, ms, "EXC $code", ErrKind.EXCEPTION, isWrite)
                throw ModbusException(code, f.tx, f.rx)
            }
            log.add(f.tx, f.rx, ms, "OK", null, isWrite)
            return f
        } catch (e: ModbusException) {
            throw e
        } catch (e: CommError) {
            val ms = (System.nanoTime() - t0) / 1_000_000
            log.add(e.tx, e.rx, ms, e.message ?: e.kind.name, e.kind, isWrite)
            throw e
        }
    }
}
