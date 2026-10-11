package com.tesys.lab.engine

import com.tesys.lab.model.ParamDef
import com.tesys.lab.modbus.FrameEntry
import com.tesys.lab.modbus.Modbus

/** One line of the "what does this frame mean" table: hex bytes, field name, plain-language meaning. */
class ExplainRow(val hex: String, val field: String, val meaning: String)

/** Turns raw Modbus RTU / TCP frames into plain-language explanations (Logs screen). */
object FrameExplain {
    private val FC_NAMES = mapOf(
        1 to "Read coils", 2 to "Read discrete inputs", 3 to "Read holding registers",
        4 to "Read input registers", 6 to "Write single register"
    )

    private fun h(b: ByteArray, from: Int, len: Int): String =
        Modbus.hex(b.copyOfRange(from, minOf(b.size, from + len)))

    private fun u16(b: ByteArray, i: Int): Int = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    /** Range of the PDU (function code + data) inside a frame, or null if the frame is too short. */
    private fun pduRange(b: ByteArray, tcp: Boolean): IntRange? =
        if (tcp) { if (b.size >= 8) 7 until b.size else null }
        else { if (b.size >= 4) 1 until b.size - 2 else null }

    private fun names(fc: Int, addr: Int, qty: Int, params: List<ParamDef>): String {
        val hit = params.filter { it.function == fc && it.address < addr + qty && it.address + it.span > addr }
        if (hit.isEmpty()) return "(no catalog parameter)"
        val n = hit.take(3).joinToString(", ") { it.name }
        return if (hit.size > 3) "$n +${hit.size - 3} more" else n
    }

    /** One-line summary of the request, shown on the collapsed log card. */
    fun summary(e: FrameEntry, params: List<ParamDef>): String {
        val r = pduRange(e.txBytes, e.tcp) ?: return "Incomplete request"
        val fc = e.txBytes[r.first].toInt() and 0xFF
        val len = r.last - r.first + 1
        if (len < 5) return FC_NAMES[fc] ?: "Function $fc"
        val a = u16(e.txBytes, r.first + 1)
        val q = u16(e.txBytes, r.first + 3)
        return when (fc) {
            1, 2, 3, 4 -> "${FC_NAMES[fc]} ${a + 1}..${a + q}: ${names(fc, a, q, params)}"
            6 -> "WRITE register ${a + 1} = 0x%04X: ${names(3, a, 1, params)}".format(q)
            else -> "Function $fc"
        }
    }

    fun rows(bytes: ByteArray, request: Boolean, tcp: Boolean, req: ByteArray, params: List<ParamDef>): List<ExplainRow> {
        val out = ArrayList<ExplainRow>()
        if (bytes.isEmpty()) {
            out.add(ExplainRow("", if (request) "Request" else "Response",
                if (request) "Nothing was sent." else "No response received (timeout, or cable / wiring problem)."))
            return out
        }
        val range = pduRange(bytes, tcp)
        if (range == null) {
            out.add(ExplainRow(Modbus.hex(bytes), "Incomplete frame", "Too short to decode (${bytes.size} bytes)."))
            return out
        }
        if (tcp) {
            out.add(ExplainRow(h(bytes, 0, 2), "Transaction ID", "0x%04X - the reply must carry the same number".format(u16(bytes, 0))))
            out.add(ExplainRow(h(bytes, 2, 2), "Protocol ID", if (u16(bytes, 2) == 0) "0 = Modbus" else "Unexpected value"))
            out.add(ExplainRow(h(bytes, 4, 2), "Length", "${u16(bytes, 4)} bytes follow (unit ID + function + data)"))
            out.add(ExplainRow(h(bytes, 6, 1), "Unit ID", "${bytes[6].toInt() and 0xFF} - device address"))
        } else {
            out.add(ExplainRow(h(bytes, 0, 1), "Slave address", "${bytes[0].toInt() and 0xFF} - Modbus device address"))
        }
        val p0 = range.first
        val len = range.last - p0 + 1
        val fcRaw = bytes[p0].toInt() and 0xFF
        val fc = fcRaw and 0x7F
        val fcName = FC_NAMES[fc] ?: "Function $fc"

        if (fcRaw and 0x80 != 0) {
            out.add(ExplainRow(h(bytes, p0, 1), "Function", "0x%02X = EXCEPTION reply to function %d (%s)".format(fcRaw, fc, fcName)))
            if (len >= 2) {
                val code = bytes[p0 + 1].toInt() and 0xFF
                out.add(ExplainRow(h(bytes, p0 + 1, 1), "Exception code", "$code - ${Modbus.exceptionText(code)}"))
            }
        } else {
            out.add(ExplainRow(h(bytes, p0, 1), "Function", "%d - %s".format(fc, fcName)))
            if (request) {
                if (fc in 1..4 && len >= 5) {
                    val a = u16(bytes, p0 + 1)
                    val q = u16(bytes, p0 + 3)
                    out.add(ExplainRow(h(bytes, p0 + 1, 2), "Start address", "0x%04X = register %d (register = address + 1)".format(a, a + 1)))
                    out.add(ExplainRow(h(bytes, p0 + 3, 2), "Quantity", "$q ${if (fc <= 2) "bits" else "registers"} to read"))
                    out.add(ExplainRow("", "Parameters", names(fc, a, q, params)))
                } else if (fc == 6 && len >= 5) {
                    val a = u16(bytes, p0 + 1)
                    val v = u16(bytes, p0 + 3)
                    out.add(ExplainRow(h(bytes, p0 + 1, 2), "Register address", "0x%04X = register %d".format(a, a + 1)))
                    out.add(ExplainRow(h(bytes, p0 + 3, 2), "Value written", "0x%04X = %d".format(v, v)))
                    out.add(ExplainRow("", "Parameters", names(3, a, 1, params)))
                }
            } else {
                val rr = pduRange(req, tcp)
                var a = -1
                var q = 0
                if (rr != null && rr.last - rr.first + 1 >= 5) {
                    a = u16(req, rr.first + 1)
                    q = u16(req, rr.first + 3)
                }
                if (fc in 1..4 && len >= 2) {
                    val n = bytes[p0 + 1].toInt() and 0xFF
                    out.add(ExplainRow(h(bytes, p0 + 1, 1), "Byte count", "$n data bytes follow"))
                    val have = minOf(n, range.last - (p0 + 1))
                    if (fc >= 3) {
                        val words = have / 2
                        for (i in 0 until minOf(words, 6)) {
                            val v = u16(bytes, p0 + 2 + 2 * i)
                            val regNo = if (a >= 0) "register ${a + 1 + i}" else "word ${i + 1}"
                            out.add(ExplainRow(h(bytes, p0 + 2 + 2 * i, 2), regNo, "0x%04X = %d".format(v, v)))
                        }
                        if (words > 6) out.add(ExplainRow("", "...", "${words - 6} more registers not listed"))
                        if (a >= 0) {
                            for (p in params.filter { it.function == fc && it.address >= a && it.address + it.span <= a + words }.take(6)) {
                                val off = p.address - a
                                val w = IntArray(p.span) { u16(bytes, p0 + 2 + 2 * (off + it)) }
                                val rd = Decoder.decode(p, w, false, false)
                                val unit = if (p.unit.isNotBlank() && p.enumMap.isEmpty() && p.fmt.isEmpty()) " ${p.unit}" else ""
                                out.add(ExplainRow("", p.name, rd.text + unit))
                            }
                        }
                    } else if (a >= 0) {
                        val hit = params.filter { it.function == fc && it.type.isBit && it.address >= a && it.address < a + q }
                        for (p in hit.take(8)) {
                            val bitIdx = p.address - a
                            val idx = p0 + 2 + bitIdx / 8
                            if (idx < bytes.size) out.add(ExplainRow("", p.name, if ((bytes[idx].toInt() shr (bitIdx % 8)) and 1 == 1) "ON (1)" else "OFF (0)"))
                        }
                        if (hit.size > 8) out.add(ExplainRow("", "...", "${hit.size - 8} more bits not listed"))
                    }
                } else if (fc == 6 && len >= 5) {
                    out.add(ExplainRow(h(bytes, p0 + 1, 2), "Register address (echo)", "register ${u16(bytes, p0 + 1) + 1}"))
                    out.add(ExplainRow(h(bytes, p0 + 3, 2), "Value (echo)", "0x%04X - the device repeats the request to confirm".format(u16(bytes, p0 + 3))))
                }
            }
        }
        if (!tcp) {
            val n = bytes.size
            val got = (bytes[n - 2].toInt() and 0xFF) or ((bytes[n - 1].toInt() and 0xFF) shl 8)
            val calc = Modbus.crc16(bytes, n - 2)
            out.add(ExplainRow(h(bytes, n - 2, 2), "CRC",
                if (got == calc) "OK - error check matches (sent low byte first)"
                else "MISMATCH - expected %02X %02X".format(calc and 0xFF, calc shr 8)))
        }
        return out
    }
}
