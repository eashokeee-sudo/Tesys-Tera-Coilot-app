package com.tesys.lab.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tesys.lab.MainViewModel
import com.tesys.lab.model.ConnState

/** Device identity and firmware versions read from the product information registers. */
@Composable
fun DeviceScreen(vm: MainViewModel, onOpenManager: () -> Unit) {
    val params by vm.params.collectAsState()
    val readings by vm.readings.collectAsState()
    val state by vm.state.collectAsState()
    val err by vm.lastError.collectAsState()
    val s by vm.settings.collectAsState()

    val byId = params.associateBy { it.id }
    fun txt(id: String): String {
        val p = byId[id] ?: return "not in catalog"
        val r = readings[id]
        if (!p.enabled) return "disabled"
        if (r == null || state == ConnState.DISCONNECTED) return "---"
        if (r.error != null) return r.error
        return if (p.fmt == "FWVER" && r.raw != 0L) r.text + "  (0x%08X)".format(r.raw) else r.text
    }
    fun raw(id: String): Long? = readings[id]?.takeIf { it.error == null && state != ConnState.DISCONNECTED }?.raw

    val mfg = listOf("mfg_day", "mfg_month", "mfg_year", "mfg_hour", "mfg_min", "mfg_sec").map { raw(it) }
    val mfgText = if (mfg.all { it != null } && mfg.any { it != 0L })
        "%02d-%02d-%d %02d:%02d:%02d".format(mfg[0], mfg[1], mfg[2], mfg[3], mfg[4], mfg[5]) else "---"

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ConnBanner(state, err) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Device and firmware", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                OutlinedButton(onClick = { vm.refreshNow() }, enabled = state != ConnState.DISCONNECTED) { Text("Refresh", fontSize = 12.sp) }
            }
        }

        item {
            Panel(title = "Main unit (LTMT)") {
                KeyValue("Commercial reference", txt("dev_ref"))
                KeyValue("Serial number", txt("dev_serial"))
                KeyValue("Product ID", txt("dev_pid"))
                KeyValue("Hardware version", txt("hw_main"))
                KeyValue("Firmware version", txt("fw_main"))
                KeyValue("Boot firmware", txt("boot_main"))
                KeyValue("Manufactured", mfgText)
            }
        }

        item {
            Panel(title = "Sensor module (LTMTCT / LTMTCTV)") {
                KeyValue("Hardware version", txt("hw_ct"))
                KeyValue("Firmware version", txt("fw_ct"))
                KeyValue("Boot firmware", txt("boot_ct"))
                KeyValue("Module type code", txt("mod_ct"))
            }
        }

        item {
            Panel(title = "Control operator unit (LTMTCUF)") {
                KeyValue("Hardware version", txt("hw_cu"))
                KeyValue("Firmware version", txt("fw_cu"))
                KeyValue("Boot firmware", txt("boot_cu"))
            }
        }

        item {
            Panel(title = "Expansion modules") {
                var any = false
                for (n in 1..5) {
                    val fw = raw("fw_ex$n") ?: 0L
                    val ty = raw("mod_ex$n") ?: 0L
                    if (fw != 0L || ty != 0L) {
                        any = true
                        KeyValue("Expansion $n", "fw " + txt("fw_ex$n").substringBefore("  (") + "   type " + txt("mod_ex$n"))
                    }
                }
                if (!any) Text(if (state == ConnState.DISCONNECTED) "---" else "No expansion module reported.", fontSize = 12.sp, color = C.Dim)
            }
        }

        item {
            Panel(title = "Connection") {
                KeyValue("Interface", if (s.mode == "TCP") "Modbus TCP" else "Modbus RTU (USB-RS485)")
                if (s.mode == "TCP") {
                    KeyValue("Address", "${s.host}:${s.port}")
                    KeyValue("Unit ID", "${s.unitId}")
                } else {
                    KeyValue("Slave ID", "${s.slaveId}")
                    KeyValue("Serial", "${s.baud} bps, ${s.parity.lowercase()} parity, ${s.stopBits} stop bit")
                }
                Text("Version numbers are shown as aaa.bbb.ccc with the raw register value in brackets so you can compare with the DTM.", fontSize = 10.sp, color = C.Dim, modifier = Modifier.padding(top = 6.dp))
            }
        }

        item {
            Button(onClick = onOpenManager, modifier = Modifier.fillMaxWidth()) { Text("Parameter manager (add / edit / delete parameters)") }
        }
    }
}
