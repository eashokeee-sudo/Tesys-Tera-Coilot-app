package com.tesys.lab.modbus

enum class ErrKind { TIMEOUT, CRC, EXCEPTION, IO, PROTOCOL }

open class CommError(
    message: String,
    val kind: ErrKind,
    val tx: ByteArray = ByteArray(0),
    val rx: ByteArray = ByteArray(0)
) : Exception(message)

class ModbusException(val code: Int, tx: ByteArray, rx: ByteArray) :
    CommError("Modbus exception $code (${Modbus.exceptionText(code)})", ErrKind.EXCEPTION, tx, rx)

/** Request/response bytes of one transaction. [pdu] = function code + data (no address, no CRC / MBAP). */
class Frame(val tx: ByteArray, val rx: ByteArray, val pdu: ByteArray)

object Modbus {
    /** CRC-16/MODBUS over the first [len] bytes. Append low byte first. */
    fun crc16(b: ByteArray, len: Int = b.size): Int {
        var crc = 0xFFFF
        for (i in 0 until len) {
            crc = crc xor (b[i].toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1
            }
        }
        return crc and 0xFFFF
    }

    /** Read request PDU (FC 01/02/03/04). Only read functions exist in this app. */
    fun readPdu(fc: Int, addr: Int, qty: Int): ByteArray = byteArrayOf(
        fc.toByte(), (addr shr 8).toByte(), addr.toByte(), (qty shr 8).toByte(), qty.toByte()
    )

    /** Registers from an FC03/FC04 response PDU. */
    fun parseWords(pdu: ByteArray, qty: Int): IntArray {
        require(pdu.size >= 2) { "Short response" }
        val n = pdu[1].toInt() and 0xFF
        if (n < qty * 2 || pdu.size < 2 + qty * 2) throw CommError("Short response ($n bytes, expected ${qty * 2})", ErrKind.PROTOCOL)
        return IntArray(qty) { i ->
            ((pdu[2 + 2 * i].toInt() and 0xFF) shl 8) or (pdu[3 + 2 * i].toInt() and 0xFF)
        }
    }

    /** Bits (0/1) from an FC01/FC02 response PDU, LSB first. */
    fun parseBits(pdu: ByteArray, qty: Int): IntArray {
        require(pdu.size >= 2) { "Short response" }
        val n = pdu[1].toInt() and 0xFF
        if (n * 8 < qty || pdu.size < 2 + n) throw CommError("Short bit response", ErrKind.PROTOCOL)
        return IntArray(qty) { i -> (pdu[2 + i / 8].toInt() shr (i % 8)) and 1 }
    }

    fun hex(b: ByteArray): String = b.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    fun exceptionText(code: Int) = when (code) {
        1 -> "Illegal function"
        2 -> "Illegal data address"
        3 -> "Illegal data value"
        4 -> "Server device failure"
        6 -> "Server busy"
        else -> "Unknown"
    }
}
