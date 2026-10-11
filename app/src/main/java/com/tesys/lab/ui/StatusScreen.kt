package com.tesys.lab.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tesys.lab.MainViewModel
import com.tesys.lab.model.ConnState
import com.tesys.lab.model.DType

private val KNOWN_GROUPS = setOf("Motor state", "Inhibit status", "Main unit I/O", "Thermal memory", "Statistics")

/** Motor status, inhibit list, I/O, thermal memory and statistics on one scrolling screen. Driven by the catalog. */
@Composable
fun StatusScreen(vm: MainViewModel) {
    val params by vm.params.collectAsState()
    val readings by vm.readings.collectAsState()
    val state by vm.state.collectAsState()
    val err by vm.lastError.collectAsState()

    val onScreen = params.filter { it.enabled && it.screen == "motor" }
    val byId = onScreen.associateBy { it.id }
    fun bit(id: String): Boolean? = byId[id]?.let { boolOf(readings[it.id], state) }

    // Motor state: the discrete status bits, with the enum register as fallback.
    val enumText = byId["motor_status"]?.let { p -> readings[p.id]?.takeIf { state != ConnState.DISCONNECTED }?.text }
    var stop = bit("ms_stop")
    var start = bit("ms_start")
    var run = bit("ms_run")
    val inhibit = bit("ms_inhibit")
    if (stop == null && start == null && run == null && enumText != null) {
        stop = enumText == "Stop"; start = enumText == "Start"; run = enumText == "Run"
    }
    val live = state == ConnState.CONNECTED

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ConnBanner(state, err) }

        item {
            Panel(title = "Motor status") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StateChip("STOP", stop, C.Dim, Modifier.weight(1f))
                    StateChip("START", start, C.Amber, Modifier.weight(1f))
                    StateChip("RUN", run, C.Green, Modifier.weight(1f))
                    StateChip("INHIBIT", inhibit, C.Amber, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StateChip("REMOTE", bit("ms_remote"), C.Blue, Modifier.weight(1f))
                    StateChip("TRIP", bit("st_trip"), C.Red, Modifier.weight(1f))
                    StateChip("ALARM", bit("st_alarm"), C.Amber, Modifier.weight(1f))
                    StateChip("PICKUP", bit("st_pickup"), C.Amber, Modifier.weight(1f))
                }
                val details = onScreen.filter { it.group == "Motor state" && it.type != DType.BOOL && it.id != "motor_status" }
                if (details.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    details.forEach { p -> ParamRow(p, readings[p.id], state) }
                }
                if (!live && state != ConnState.DISCONNECTED) Text("Waiting for data...", fontSize = 11.sp, color = C.Dim)
            }
        }

        val inhibitItems = onScreen.filter { it.group == "Inhibit status" && it.type == DType.BOOL }
        if (inhibitItems.isNotEmpty()) item {
            val active = inhibitItems.count { boolOf(readings[it.id], state) == true }
            Panel(title = "Inhibit status") {
                DotGrid(inhibitItems, readings, state, C.Amber)
                Spacer(Modifier.height(4.dp))
                Text(if (live) (if (active == 0) "No inhibit active" else "$active inhibit(s) active") else "---", fontSize = 11.sp, color = C.Dim)
            }
        }

        val io = onScreen.filter { it.group == "Main unit I/O" }
        if (io.isNotEmpty()) item {
            val di = io.filter { it.id.startsWith("di_") }
            val dout = io.filter { it.id.startsWith("do_") }
            val co = io.filter { it.id.startsWith("co_") }
            Panel(title = "Main unit I/O") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.weight(1f)) {
                        di.forEach { Dot(it.name, boolOf(readings[it.id], state), C.Green) }
                    }
                    Column(Modifier.weight(1f)) {
                        dout.forEach { Dot(it.name, boolOf(readings[it.id], state), C.Green) }
                    }
                }
                if (co.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    co.forEach { Dot(it.name, boolOf(readings[it.id], state), C.Green) }
                }
            }
        }

        val thermal = onScreen.filter { it.group == "Thermal memory" }
        if (thermal.isNotEmpty()) item {
            val main = thermal.firstOrNull { it.role == "THERMAL" }
            val pct = if (state == ConnState.DISCONNECTED) null else main?.let { readings[it.id]?.value }
            Panel(title = "Thermal memory") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Thermal memory", Modifier.weight(1f), fontSize = 13.sp)
                    Text(pct?.let { "%.0f %%".format(it) } ?: "---", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        color = pct?.let { if (live) loadColor(it) else C.Dim } ?: C.Dim)
                }
                Spacer(Modifier.height(8.dp))
                Bar(((pct ?: 0.0) / 100.0).toFloat(), pct?.let { loadColor(it) } ?: C.Dim)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    thermal.filter { it.role != "THERMAL" }.forEach { StatCell(it, readings[it.id], state, Modifier.weight(1f)) }
                }
            }
        }

        val stats = onScreen.filter { it.group == "Statistics" }
        if (stats.isNotEmpty()) item {
            Panel(title = "Statistics") {
                stats.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        pair.forEach { StatCell(it, readings[it.id], state, Modifier.weight(1f)) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        // anything you added yourself to this screen
        onScreen.filter { it.group !in KNOWN_GROUPS }.groupBy { it.group }.forEach { (g, items) ->
            item(key = "sg_$g") { GroupPanel(g, items, readings, state) }
        }
    }
}
