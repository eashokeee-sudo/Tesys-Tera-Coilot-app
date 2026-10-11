package com.tesys.lab.engine

import com.tesys.lab.model.EventRecord
import com.tesys.lab.model.TripRecord
import java.util.Locale

/** Decodes the raw registers of one trip log / event log record. */
object LogParser {
    private fun u32(w: IntArray, i: Int): Long = ((w[i].toLong() and 0xFFFF) shl 16) or (w[i + 1].toLong() and 0xFFFF)

    private fun bcd(x: Int): Int = ((x ushr 4) and 0xF) * 10 + (x and 0xF)

    /**
     * 4-register date and time. Assumed packed BCD, as in the TeSys Tera RTC registers:
     * [SS 00] [HH mm] [MM DD] [YY YY]. Returns "raw ...." when the words do not look like BCD, so a
     * wrong assumption is visible instead of a wrong date.
     */
    fun dateTime(w: IntArray): String {
        if (w.size < 4) return "-"
        val four = w.copyOfRange(0, 4)
        val raw = four.joinToString(" ") { String.format(Locale.US, "%04X", it and 0xFFFF) }
        if (four.all { it == 0 }) return "-"
        val notBcd = four.any { v -> (0..3).any { n -> ((v ushr (4 * n)) and 0xF) > 9 } }
        if (notBcd) return "raw $raw"
        val ss = bcd(four[0] ushr 8)
        val hh = bcd(four[1] ushr 8)
        val mi = bcd(four[1] and 0xFF)
        val mo = bcd(four[2] ushr 8)
        val dd = bcd(four[2] and 0xFF)
        val yy = bcd(four[3] ushr 8) * 100 + bcd(four[3] and 0xFF)
        if (mo !in 1..12 || dd !in 1..31 || hh > 23 || mi > 59 || ss > 59) return "raw $raw"
        return String.format(Locale.US, "%04d-%02d-%02d %02d:%02d:%02d", yy, mo, dd, hh, mi, ss)
    }

    /** Trip log record = 32 registers. Offsets follow the guide: 0x1770 + n. Returns null for an empty slot. */
    fun trip(index: Int, w: IntArray): TripRecord? {
        if (w.size < 24 || w.all { it == 0 }) return null
        return TripRecord(
            index = index, time = dateTime(w), code = w[4], thermal = w[5],
            l1 = u32(w, 6) * 0.001, l2 = u32(w, 8) * 0.001, l3 = u32(w, 10) * 0.001,
            v12 = w[18] * 0.1, v23 = w[19] * 0.1, v31 = w[20] * 0.1,
            freq = w[23] * 0.01, imbalance = w[16] * 0.01
        )
    }

    /** Event log record = 8 registers: date and time (4), event code at +4. */
    fun event(index: Int, w: IntArray): EventRecord? {
        if (w.size < 5 || w.all { it == 0 }) return null
        return EventRecord(index, dateTime(w), w[4])
    }
}
