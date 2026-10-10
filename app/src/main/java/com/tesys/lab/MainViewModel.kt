package com.tesys.lab

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tesys.lab.engine.PollEngine
import com.tesys.lab.model.CatalogStore
import com.tesys.lab.model.ConnSettings
import com.tesys.lab.model.ConnState
import com.tesys.lab.model.ParamDef
import com.tesys.lab.model.TrendPoint
import com.tesys.lab.modbus.CommError
import com.tesys.lab.modbus.ErrKind
import com.tesys.lab.modbus.FrameLog
import com.tesys.lab.modbus.ModbusClient
import com.tesys.lab.modbus.TcpTransport
import com.tesys.lab.modbus.Transport
import com.tesys.lab.modbus.UsbRtuTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context = app.applicationContext
    private val prefs = ctx.getSharedPreferences("lab_settings", Context.MODE_PRIVATE)

    val store = CatalogStore(ctx)
    val params: StateFlow<List<ParamDef>> = store.params
    val frameLog = FrameLog()
    val engine = PollEngine(viewModelScope)
    val readings = engine.readings
    val state = engine.state
    val lastError = engine.lastError

    val settings = MutableStateFlow(loadSettings())
    val usbDevices = MutableStateFlow<List<android.hardware.usb.UsbDevice>>(emptyList())
    val selectedUsb = MutableStateFlow<String?>(null)          // UsbDevice.deviceName
    val message = MutableStateFlow<String?>(null)
    val trend = MutableStateFlow<List<TrendPoint>>(emptyList())

    private var transport: Transport? = null
    private var client: ModbusClient? = null

    init { refreshUsb() }

    /* ----------------------------------------------------------- settings */

    private fun loadSettings(): ConnSettings {
        val d = ConnSettings()
        return ConnSettings(
            mode = prefs.getString("mode", d.mode)!!,
            slaveId = prefs.getInt("slaveId", d.slaveId),
            baud = prefs.getInt("baud", d.baud),
            parity = prefs.getString("parity", d.parity)!!,
            stopBits = prefs.getInt("stopBits", d.stopBits),
            pollMs = prefs.getLong("pollMs", d.pollMs),
            timeoutMs = prefs.getLong("timeoutMs", d.timeoutMs),
            host = prefs.getString("host", d.host)!!,
            port = prefs.getInt("port", d.port),
            unitId = prefs.getInt("unitId", d.unitId),
            swapWords = prefs.getBoolean("swapWords", d.swapWords),
            swapBytes = prefs.getBoolean("swapBytes", d.swapBytes)
        )
    }

    fun update(f: (ConnSettings) -> ConnSettings) {
        val s = f(settings.value)
        settings.value = s
        prefs.edit()
            .putString("mode", s.mode).putInt("slaveId", s.slaveId).putInt("baud", s.baud)
            .putString("parity", s.parity).putInt("stopBits", s.stopBits)
            .putLong("pollMs", s.pollMs).putLong("timeoutMs", s.timeoutMs)
            .putString("host", s.host).putInt("port", s.port).putInt("unitId", s.unitId)
            .putBoolean("swapWords", s.swapWords).putBoolean("swapBytes", s.swapBytes)
            .apply()
    }

    /* ----------------------------------------------------------- USB */

    fun refreshUsb() {
        val list = UsbHelper.devices(ctx)
        usbDevices.value = list
        if (list.none { it.deviceName == selectedUsb.value }) selectedUsb.value = null
    }

    /** Select the Schneider cable (VID 0x16DE) if present, otherwise the first USB device. */
    fun autoDetectUsb() {
        refreshUsb()
        val list = usbDevices.value
        val pick = list.firstOrNull { it.vendorId == UsbHelper.SCHNEIDER_VID } ?: list.firstOrNull()
        selectedUsb.value = pick?.deviceName
        message.value = if (pick == null) "No USB device found. Check the OTG adapter and cable." else "Selected ${UsbHelper.label(pick)}"
    }

    /* ----------------------------------------------------------- connection */

    fun connect() {
        if (state.value != ConnState.DISCONNECTED) return
        val s = settings.value
        message.value = null
        engine.setState(ConnState.CONNECTING)
        viewModelScope.launch {
            try {
                val t: Transport
                val unit: Int
                if (s.mode == "TCP") {
                    t = TcpTransport(s.host.trim(), s.port)
                    unit = s.unitId
                } else {
                    val dev = usbDevices.value.firstOrNull { it.deviceName == selectedUsb.value }
                        ?: throw CommError("Select a USB device first (Auto-detect).", ErrKind.IO)
                    if (!UsbHelper.requestPermission(ctx, dev)) throw CommError("USB permission denied.", ErrKind.IO)
                    t = UsbRtuTransport(UsbHelper.manager(ctx), dev, s.baud, s.parity, s.stopBits)
                    unit = s.slaveId
                }
                t.open()
                transport = t
                val c = ModbusClient(t, unit, s.timeoutMs, frameLog)
                client = c
                engine.start(c, { store.params.value }, s.pollMs, s.swapWords, s.swapBytes) { recordTrend() }
            } catch (e: Exception) {
                message.value = e.message ?: "Connection failed"
                engine.setState(ConnState.DISCONNECTED)
            }
        }
    }

    fun disconnect() {
        engine.stop()
        transport?.close()
        transport = null
        client = null
        engine.setState(ConnState.DISCONNECTED)
        engine.clearReadings()
    }

    override fun onCleared() { disconnect() }

    /* ----------------------------------------------------------- trend */

    private fun recordTrend() {
        if (engine.state.value != ConnState.CONNECTED) return
        val ps = store.params.value
        val r = engine.readings.value
        val iavg = ps.firstOrNull { it.role == "IAVG" }?.let { r[it.id]?.value } ?: return
        val flc = ps.firstOrNull { it.role == "FLC" }?.let { r[it.id]?.value }
        val th = ps.firstOrNull { it.role == "THERMAL" }?.let { r[it.id]?.value }
        val now = System.currentTimeMillis()
        trend.value = (trend.value + TrendPoint(now, iavg, flc, th)).takeLast(3600)
    }

    fun clearTrend() { trend.value = emptyList() }

    /* ----------------------------------------------------------- catalog editing */

    fun saveParam(p: ParamDef, originalId: String?) = store.upsert(p, originalId)
    fun deleteParam(id: String) = store.delete(id)
    fun setParamEnabled(id: String, on: Boolean) = store.setEnabled(id, on)
    fun exportText() = store.exportText()
    fun importText(text: String, merge: Boolean) { message.value = store.importText(text, merge) }
    fun resetCatalog() { store.resetToDefault(); message.value = "Catalog reset to defaults." }

    /* ----------------------------------------------------------- one-off reads */

    /** Reads raw registers once (Registers tab > Quick read). [register] uses the guide numbering. */
    suspend fun quickRead(fc: Int, register: Int, count: Int): String {
        val c = client ?: return "Not connected."
        return try {
            val addr = register - 1
            if (fc == 1 || fc == 2) {
                val bits = c.readBits(fc, addr, count)
                "Reg $register (0x%04X)  FC%02d\n".format(addr, fc) + bits.joinToString(" ")
            } else {
                val w = c.readWords(fc, addr, count)
                val sb = StringBuilder("Reg $register (0x%04X)  FC%02d\n".format(addr, fc))
                w.forEachIndexed { i, v ->
                    sb.append("%d: %04X  dec %d  s16 %d\n".format(register + i, v, v, v.toShort().toInt()))
                }
                if (w.size >= 2) {
                    val u32 = ((w[0].toLong() and 0xFFFF) shl 16) or (w[1].toLong() and 0xFFFF)
                    sb.append("U32 (w0,w1): $u32\n")
                    val sw = ((w[1].toLong() and 0xFFFF) shl 16) or (w[0].toLong() and 0xFFFF)
                    sb.append("U32 (swapped): $sw")
                }
                sb.toString().trimEnd()
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    /** Quick link test: reads the first enabled word parameter. */
    suspend fun ping(): String {
        val p = store.params.value.firstOrNull { it.enabled && !it.isBitFunction } ?: return "No enabled parameter to read."
        val t0 = System.nanoTime()
        val r = quickRead(p.function, p.register, p.span)
        val ms = (System.nanoTime() - t0) / 1_000_000
        return if (r.startsWith("Error") || r.startsWith("Not")) r else "Ping OK (${ms} ms) - ${p.name} (reg ${p.register})"
    }
}
