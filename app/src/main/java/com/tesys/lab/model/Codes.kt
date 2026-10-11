package com.tesys.lab.model

/** Where a command is written. [address] is the Modbus address sent on the wire (register = address + 1). */
data class CmdChannel(val id: String, val label: String, val address: Int, val note: String) {
    val register: Int get() = address + 1
}

/** A command = the full 16-bit value written to the command register. [releaseAfterMs] > 0 writes 0x0000 afterwards. */
data class Cmd(val id: String, val label: String, val value: Int, val releaseAfterMs: Long = 0)

/**
 * Command register 1.
 * Source of the HMI channel: DIVA requirement table "Commands Register 1", register address 49876
 * (= 40001 + 9875), FC 0x03 / 0x06 / 0x10, bit 0 Motor run forward, bit 3 Trip reset, bit 12 Motor stop.
 * 9875 (0x2693) = "HMI command register 1" in the TeSys Tera DTM guide (DOCA0275EN-02).
 * Source of the COMM channel: DOCA0258EN-02 "Command data 1", 0x02C0 (register 705).
 */
object Commands {
    const val BIT_RUN_FORWARD = 0
    const val BIT_TRIP_RESET = 3
    const val BIT_STOP = 12

    val HMI = CmdChannel("hmi", "HMI command register 1", 9875,
        "DIVA table address 49876 = 40001 + 9875 = 0x2693. Needs the Control Station set to HMI.")
    val COMM = CmdChannel("comm", "Communication command register 1", 704,
        "DOCA0258EN-02 Command data 1 at 0x02C0 (register 705).")
    val CHANNELS = listOf(HMI, COMM)

    val START = Cmd("start", "Start (run forward)", 1 shl BIT_RUN_FORWARD)
    val STOP = Cmd("stop", "Stop", 1 shl BIT_STOP)
    val TRIP_RESET = Cmd("trip_reset", "Trip reset", 1 shl BIT_TRIP_RESET, releaseAfterMs = 1000)

    fun channel(id: String): CmdChannel = CHANNELS.firstOrNull { it.id == id } ?: HMI
}

/** Register map of the data logs (DOCA0258EN-02 Data Logs). Addresses are the ones sent on the wire. */
object LogMap {
    const val TRIP_BASE = 6000       // 0x1770, trip log 1 (register 6001), 32 registers per record, last 20 trips
    const val TRIP_STRIDE = 32
    const val EVENT_BASE = 7000      // 0x1B58, event log 1 (register 7001), 8 registers per record, last 100 events
    const val EVENT_STRIDE = 8
}

data class TripRecord(
    val index: Int, val time: String, val code: Int, val thermal: Int,
    val l1: Double, val l2: Double, val l3: Double,
    val v12: Double, val v23: Double, val v31: Double,
    val freq: Double, val imbalance: Double
)

data class EventRecord(val index: Int, val time: String, val code: Int)

/** Trip and event code names from the TeSys Tera Ethernet guide (Trip Code / Event Code appendices). */
object Codes {
    private val tripNames: Map<Int, String> = buildMap {
        listOf(
            "Thermal overload", "Locked rotor", "Stalled rotor", "Definite time overcurrent", "Normal inverse overcurrent",
            "Short time overcurrent", "Calculated ground", "Measured ground", "Phase under current", "Current imbalance",
            "Current phase loss", "Current phase reversal", "Phase under voltage", "Phase over voltage", "Voltage phase loss",
            "Voltage imbalance", "Voltage phase reversal", "Under frequency", "Over frequency", "Excessive start time",
            "Communication loss", "Over temperature", "Under power", "Over power", "Under power factor"
        ).forEachIndexed { i, n -> put(i + 1, "$n trip") }
        put(27, "Device internal trip")
        put(28, "HMI communication loss trip")
        put(29, "Wiring error detection trip")
        for (n in 1..12) put(32 + n, "Interlock $n trip")
        for (n in 1..4) put(64 + n, "Analog input $n trip")
        put(95, "Stucked reset button")
        put(96, "Logic test interrupted trip")
        put(97, "Motor stop error detection trip")
    }

    private val protNames = listOf(
        "Thermal overload", "Locked rotor", "Stalled rotor", "Definite time overcurrent", "Normal inverse overcurrent",
        "Short time overcurrent", "Calculated ground", "Measured ground", "Phase under current", "Current imbalance",
        "Current phase loss", "Current phase reversal", "Phase under voltage", "Phase over voltage", "Voltage phase loss",
        "Voltage imbalance", "Voltage phase reversal", "Under frequency", "Over frequency"
    )

    private val inhibitNames = listOf(
        "No voltage", "Under voltage", "Trip", "Thermal", "Max starts",
        "Interlock 1", "Interlock 2", "Interlock 3", "Interlock 4", "Interlock 5", "Interlock 6",
        "Interlock 7", "Interlock 8", "Interlock 9", "Interlock 10", "Interlock 11", "Interlock 12",
        "Local DI stop", "Remote DI stop", "Comm stop", "Forced stop", "Anti backspin", "Device internal error",
        "Interlock time", "Speed change", "Custom stop", "Firmware update"
    )

    fun trip(code: Int): String = if (code == 0) "No trip" else tripNames[code] ?: "Trip code $code"

    /** Events come in pairs: odd = set / ON, even = reset / OFF (counted from [first]). */
    private fun pair(base: String, word: String, code: Int, first: Int, off: String = "reset"): String =
        if ((code - first) % 2 == 0) "$base $word" else "$base $word $off"

    fun event(code: Int): String = when {
        code in 1..38 -> pair(protNames[(code - 1) / 2], "alarm", code, 1)
        code in 41..50 -> pair(listOf("Communication loss", "Over temperature", "Under power", "Over power", "Under power factor")[(code - 41) / 2], "alarm", code, 41)
        code == 53 || code == 54 -> pair("Device internal", "alarm", code, 53)
        code == 55 || code == 56 -> pair("HMI communication loss", "alarm", code, 55)
        code in 65..88 -> pair("Interlock ${(code - 65) / 2 + 1}", "alarm", code, 65)
        code in 129..136 -> pair("AI${(code - 129) / 2 + 1}", "alarm", code, 129)
        code in 193..226 -> pair(protNames[(code - 193) / 2], "pickup", code, 193)
        code in 385..432 -> pair("DI ${(code - 385) / 2 + 1}", "ON", code, 385, "OFF").replace(" ON OFF", " OFF")
        code in 449..474 -> pair("DO ${(code - 449) / 2 + 1}", "ON", code, 449, "OFF").replace(" ON OFF", " OFF")
        code in 641..694 -> pair(inhibitNames[(code - 641) / 2], "inhibit", code, 641)
        code in 897..993 && tripNames.containsKey(code - 896) -> tripNames.getValue(code - 896) + " reset"
        else -> "Event $code"
    }
}
