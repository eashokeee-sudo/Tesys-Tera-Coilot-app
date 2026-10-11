package com.tesys.lab.model

import java.math.BigDecimal

/** Data types a parameter can have. [words] = number of 16-bit registers it occupies. */
enum class DType(val words: Int, val isBitmap: Boolean = false, val isBit: Boolean = false) {
    UINT16(1), INT16(1), UINT32(2), INT32(2), UINT64(4),
    BITMAP16(1, isBitmap = true), BITMAP32(2, isBitmap = true),
    /** Single bit read with FC01/FC02 (coils / discrete inputs). */
    BOOL(1, isBit = true)
}

/** How a bitmap is displayed. */
enum class BitStyle { STATE, ENABLE, ALARM, TRIP }

data class BitDef(val bit: Int, val name: String)

/**
 * One monitored parameter. The screens are driven by a list of these (catalog.json),
 * so adding / deleting / editing a parameter never needs a code change.
 *
 * [register] uses the numbering printed in the Schneider guides (Register = Address + 1).
 * The address sent on the wire is [address] = register - 1.
 *
 * [fmt] = optional special display format: ASCII (text, [length] registers), FWVER (aaa.bbb.ccc),
 * HWVER (letter) or HMIN (minutes shown as h:mm).
 * role PROT_FUNC marks a protection "Function setting" register (0 Disable / 1 Alarm / 2 Trip / 3 Alarm+Trip)
 * that the Protection screen may enable / disable when write access is unlocked.
 */
data class ParamDef(
    val id: String,
    val name: String,
    val register: Int,
    val type: DType = DType.UINT16,
    val function: Int = 3,              // 1, 2 (bits) or 3, 4 (words)
    val scale: Double = 1.0,            // engineering value = raw * scale
    val unit: String = "",
    val decimals: Int = -1,             // -1 = derive from scale
    val screen: String = "registers",   // motor | electrical | protection | registers
    val group: String = "",             // card title on the screen
    val role: String = "",              // IAVG | FLC | THERMAL | MOTOR_STATE | PROT_FUNC | ""
    val tile: Boolean = false,          // draw as big tile (3 per row)
    val bitStyle: BitStyle = BitStyle.STATE,
    val enumMap: Map<Long, String> = emptyMap(),
    val bits: List<BitDef> = emptyList(),
    val everyN: Int = 1,                // poll every N-th cycle (1 = every cycle)
    val enabled: Boolean = true,
    val verified: Boolean = false,      // tick once confirmed against DTM / HMI / guide
    val source: String = "",
    val notes: String = "",
    val fmt: String = "",
    val length: Int = 0
) {
    val address: Int get() = register - 1
    val span: Int get() = if (type.isBit) 1 else if (fmt == "ASCII" && length > 0) length else type.words
    val isBitFunction: Boolean get() = function == 1 || function == 2

    fun decimalPlaces(): Int =
        if (decimals >= 0) decimals
        else BigDecimal.valueOf(scale).stripTrailingZeros().scale().coerceIn(0, 6)

    companion object {
        val SCREENS = listOf("motor", "electrical", "protection", "registers")
        val ROLES = listOf("", "IAVG", "FLC", "THERMAL", "MOTOR_STATE", "PROT_FUNC")
        val FUNCTIONS = listOf(3, 4, 2, 1)
    }
}

/** Latest decoded value of a parameter. */
data class Reading(
    val raw: Long = 0,
    val value: Double? = null,
    val text: String = "---",
    val timeMs: Long = 0,
    val error: String? = null
)

enum class ConnState { DISCONNECTED, CONNECTING, CONNECTED, COMM_LOST }

data class ConnSettings(
    val mode: String = "USB",           // USB | TCP
    val slaveId: Int = 1,
    val baud: Int = 19200,
    val parity: String = "EVEN",        // NONE | ODD | EVEN
    val stopBits: Int = 1,
    val pollMs: Long = 1000,
    val timeoutMs: Long = 1000,
    val host: String = "192.168.1.10",
    val port: Int = 502,
    val unitId: Int = 1,
    val swapWords: Boolean = false,     // swap the two words of 32-bit values
    val swapBytes: Boolean = false      // swap the two bytes of each word
)

data class TrendPoint(val t: Long, val iavg: Double, val flc: Double?, val thermal: Double?)
