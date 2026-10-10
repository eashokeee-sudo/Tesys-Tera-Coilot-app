package com.tesys.lab.engine

import com.tesys.lab.model.DType
import com.tesys.lab.model.ParamDef
import com.tesys.lab.model.Reading
import java.util.Locale

object Decoder {

    /**
     * Decode the registers (or the single bit for BOOL) belonging to [p].
     * [w] must hold exactly p.span entries (16-bit values 0..65535, or 0/1 for bits).
     */
    fun decode(p: ParamDef, w: IntArray, swapWords: Boolean, swapBytes: Boolean, now: Long = System.currentTimeMillis()): Reading {
        var words = w.copyOf()
        if (swapBytes && !p.type.isBit) words = IntArray(words.size) { ((words[it] and 0xFF) shl 8) or ((words[it] ushr 8) and 0xFF) }
        if (swapWords && words.size == 2) words = intArrayOf(words[1], words[0])

        val raw: Long = when (p.type) {
            DType.UINT16, DType.BITMAP16 -> words[0].toLong() and 0xFFFF
            DType.INT16 -> words[0].toShort().toLong()
            DType.UINT32, DType.BITMAP32 -> ((words[0].toLong() and 0xFFFF) shl 16) or (words[1].toLong() and 0xFFFF)
            DType.INT32 -> (((words[0].toLong() and 0xFFFF) shl 16) or (words[1].toLong() and 0xFFFF)).toInt().toLong()
            DType.BOOL -> (words[0] and 1).toLong()
        }
        val value = if (p.type.isBitmap || p.type.isBit) null else raw * p.scale
        return Reading(raw = raw, value = value, text = display(p, raw, value), timeMs = now)
    }

    fun display(p: ParamDef, raw: Long, value: Double?): String = when {
        p.type == DType.BOOL -> if (raw != 0L) "ON" else "OFF"
        p.type.isBitmap -> rawHex(p, raw)
        p.enumMap.isNotEmpty() -> p.enumMap[raw] ?: "$raw"
        value != null -> String.format(Locale.US, "%.${p.decimalPlaces()}f", value)
        else -> "$raw"
    }

    fun rawHex(p: ParamDef, raw: Long): String =
        if (p.type.words == 2) String.format(Locale.US, "0x%08X", raw and 0xFFFFFFFFL)
        else String.format(Locale.US, "0x%04X", raw and 0xFFFFL)

    /** Raw words as hex for the Registers tab ("0001 55BD"). */
    fun rawWordsHex(p: ParamDef, raw: Long): String =
        if (p.type.isBit) "$raw"
        else if (p.type.words == 2) String.format(Locale.US, "%04X %04X", (raw ushr 16) and 0xFFFF, raw and 0xFFFF)
        else String.format(Locale.US, "%04X", raw and 0xFFFF)
}
