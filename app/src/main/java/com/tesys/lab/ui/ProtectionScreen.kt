package com.tesys.lab.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tesys.lab.MainViewModel
import com.tesys.lab.model.Codes
import com.tesys.lab.model.ConnState
import com.tesys.lab.model.ParamDef
import com.tesys.lab.model.TrendPoint
import kotlinx.coroutines.launch

/**
 * Protection: configured FLC, FLC vs IAVG graph, enable / disable of the protection functions,
 * and the last 5 trips and events read from the device logs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProtectionScreen(vm: MainViewModel) {
    val params by vm.params.collectAsState()
    val readings by vm.readings.collectAsState()
    val state by vm.state.collectAsState()
    val err by vm.lastError.collectAsState()
    val trend by vm.trend.collectAsState()
    val unlocked by vm.unlocked.collectAsState()
    val trips by vm.tripLog.collectAsState()
    val events by vm.eventLog.collectAsState()
    val logStatus by vm.logStatus.collectAsState()
    val scope = rememberCoroutineScope()

    var windowSec by rememberSaveable { mutableStateOf(60) }
    var msg by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Pair<ParamDef, Int>?>(null) }
    var picking by remember { mutableStateOf<ParamDef?>(null) }

    val live = state == ConnState.CONNECTED
    val iavgP = params.firstOrNull { it.role == "IAVG" }
    val flcP = params.firstOrNull { it.role == "FLC" }
    val iavg = if (state == ConnState.DISCONNECTED) null else iavgP?.let { readings[it.id]?.value }
    val flc = if (state == ConnState.DISCONNECTED) null else flcP?.let { readings[it.id]?.value }
    val loadPct = if (iavg != null && flc != null && flc > 0) iavg / flc * 100.0 else null

    val funcs = params.filter { it.enabled && it.role == "PROT_FUNC" && it.screen == "protection" }
    val counters = params.filter { it.enabled && it.group == "Trip counters" }

    LaunchedEffect(readings) {
        if (live) funcs.forEach { p -> readings[p.id]?.let { r -> if (r.error == null) vm.rememberMode(p.id, r.raw.toInt()) } }
    }
    LaunchedEffect(live) { if (live) vm.refreshLogs() }

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ConnBanner(state, err) }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Protection", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Pill(if (unlocked) "WRITE UNLOCKED" else "READ-ONLY", if (unlocked) C.Red else C.Blue)
            }
        }

        item {
            Panel(title = "Load vs configured FLC") {
                Row(Modifier.fillMaxWidth()) {
                    Metric("FLC (configured)", flc?.let { "%.1f A".format(it) } ?: "---", C.Red, Modifier.weight(1f))
                    Metric("IAVG", iavg?.let { "%.2f A".format(it) } ?: "---", if (live) C.Blue else C.Dim, Modifier.weight(1f))
                    Metric("LOAD", loadPct?.let { "%.0f %%".format(it) } ?: "---", loadPct?.let { if (live) loadColor(it) else C.Dim } ?: C.Dim, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Bar(((loadPct ?: 0.0) / 100.0).toFloat(), loadPct?.let { loadColor(it) } ?: C.Dim)
                Spacer(Modifier.height(6.dp))
                if (iavg != null && flc != null) {
                    val margin = flc - iavg
                    Text(if (margin >= 0) "Margin: %.2f A below FLC".format(margin) else "Over FLC by %.2f A".format(-margin), fontSize = 12.sp, color = C.Dim)
                }
                if (flcP != null) Text("FLC1 is read from register ${flcP.register} and is shown read-only. Change it in the DTM.", fontSize = 11.sp, color = C.Dim)
                if (iavgP == null || flcP == null)
                    Text("Give one parameter the role IAVG and one the role FLC (Device > Parameter manager).", fontSize = 12.sp, color = C.Amber)
            }
        }

        item {
            Panel(title = "FLC vs IAVG trend") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(60 to "1 min", 900 to "15 min", 3600 to "1 h").forEach { (sec, label) ->
                        FilterChip(selected = windowSec == sec, onClick = { windowSec = sec }, label = { Text(label, fontSize = 12.sp) })
                    }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { vm.clearTrend() }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) { Text("Clear", fontSize = 12.sp) }
                }
                Spacer(Modifier.height(8.dp))
                LoadChart(trend, windowSec * 1000L, Modifier.fillMaxWidth().height(200.dp))
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("\u25A0 IAVG", color = C.Blue, fontSize = 11.sp)
                    Text("\u25A0 FLC", color = C.Red, fontSize = 11.sp)
                }
                val tMax = trend.lastOrNull()?.t ?: 0L
                val vis = trend.filter { it.t >= tMax - windowSec * 1000L }
                val loads = vis.mapNotNull { pt -> pt.flc?.takeIf { it > 0 }?.let { pt.iavg / it * 100.0 } }
                if (loads.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Metric("MAX LOAD", "%.0f %%".format(loads.max()), C.Text, Modifier.weight(1f))
                        Metric("AVG LOAD", "%.0f %%".format(loads.average()), C.Text, Modifier.weight(1f))
                        Metric("MAX IAVG", "%.1f A".format(vis.maxOf { it.iavg }), C.Text, Modifier.weight(1f))
                    }
                }
            }
        }

        if (funcs.isNotEmpty()) item {
            Panel(title = "Protection functions") {
                Text(
                    if (unlocked) "Switch a function off or on. Off writes Disable; on restores the last mode the device reported."
                    else "Read-only. Unlock write access on the Control tab to change a function.",
                    fontSize = 11.sp, color = C.Dim
                )
                Spacer(Modifier.height(6.dp))
                funcs.forEach { p ->
                    val r = readings[p.id]
                    val ok = r != null && live && r.error == null
                    val mode = if (ok) r!!.raw.toInt() else -1
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontSize = 13.sp)
                            Text(
                                if (!ok) (r?.text ?: "---") else (p.enumMap[mode.toLong()] ?: "$mode"),
                                fontSize = 11.sp, fontFamily = Mono,
                                color = if (!ok) C.Dim else if (mode > 0) C.Green else C.Dim
                            )
                        }
                        if (!p.verified) Pill("CHECK", C.Amber)
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = mode > 0, enabled = unlocked && ok,
                            onCheckedChange = { want ->
                                if (!want) pending = p to 0
                                else {
                                    val last = vm.lastMode(p.id)
                                    if (last > 0) pending = p to last else picking = p
                                }
                            }
                        )
                    }
                }
                if (msg.isNotBlank()) Text(msg, fontFamily = Mono, fontSize = 11.sp, color = C.Amber, modifier = Modifier.padding(top = 6.dp))
            }
        }

        item {
            Panel(title = "Last 5 trips") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(logStatus ?: "", Modifier.weight(1f), fontSize = 11.sp, color = C.Dim)
                    OutlinedButton(onClick = { scope.launch { vm.refreshLogs() } }, enabled = live, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                        Text("Read now", fontSize = 12.sp)
                    }
                }
                if (trips.isEmpty()) Text(if (live) "No trip recorded." else "Connect to read the trip log.", fontSize = 12.sp, color = C.Dim, modifier = Modifier.padding(top = 6.dp))
                trips.forEach { t ->
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Text("#${t.index}", fontFamily = Mono, fontSize = 12.sp, color = C.Dim, modifier = Modifier.width(26.dp))
                        Column(Modifier.weight(1f)) {
                            Text(Codes.trip(t.code), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = C.Red)
                            Text(t.time, fontFamily = Mono, fontSize = 11.sp, color = C.Dim)
                            Text("Thermal %d %%   L1 %.1f  L2 %.1f  L3 %.1f A".format(t.thermal, t.l1, t.l2, t.l3), fontFamily = Mono, fontSize = 11.sp)
                            Text("%.0f / %.0f / %.0f V   %.2f Hz   imb %.1f %%".format(t.v12, t.v23, t.v31, t.freq, t.imbalance), fontFamily = Mono, fontSize = 11.sp, color = C.Dim)
                        }
                    }
                }
                Text("Record 1 is the most recent. Times are decoded as BCD; a value shown as 'raw' means the format differs.", fontSize = 10.sp, color = C.Dim, modifier = Modifier.padding(top = 8.dp))
            }
        }

        item {
            Panel(title = "Last 5 events") {
                if (events.isEmpty()) Text(if (live) "No event recorded." else "Connect to read the event log.", fontSize = 12.sp, color = C.Dim)
                events.forEach { e ->
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                        Text("#${e.index}", fontFamily = Mono, fontSize = 12.sp, color = C.Dim, modifier = Modifier.width(26.dp))
                        Column(Modifier.weight(1f)) {
                            Text(Codes.event(e.code), fontSize = 13.sp)
                            Text("${e.time}   code ${e.code}", fontFamily = Mono, fontSize = 11.sp, color = C.Dim)
                        }
                    }
                }
            }
        }

        item {
            Panel(title = "Trip counters") {
                val hit = counters.filter { state != ConnState.DISCONNECTED && (readings[it.id]?.raw ?: 0L) > 0L }
                if (hit.isEmpty()) Text(if (live) "No trips counted." else "---", fontSize = 12.sp, color = C.Dim)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    hit.forEach { p -> Pill(p.name.removeSuffix(" trip counter") + " x" + (readings[p.id]?.raw ?: 0L), C.Red) }
                }
            }
        }
    }

    pending?.let { (p, newMode) ->
        val modeName = p.enumMap[newMode.toLong()] ?: "$newMode"
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(if (newMode == 0) "Disable ${p.name}?" else "Enable ${p.name}?") },
            text = {
                Text(
                    if (newMode == 0) "This switches the protection OFF in the device. The motor is no longer protected by this function. Continue only if you are authorised and the plant is safe."
                    else "Sets the function to \"$modeName\" (the last mode the device reported)."
                )
            },
            confirmButton = {
                TextButton(onClick = { val pp = p; val mm = newMode; pending = null; scope.launch { msg = vm.setProtectionMode(pp, mm) } }) {
                    Text(if (newMode == 0) "Disable" else "Enable", color = if (newMode == 0) C.Red else C.Green)
                }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } }
        )
    }
    picking?.let { p ->
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text("Enable ${p.name} as") },
            text = {
                Column {
                    p.enumMap.filterKeys { it > 0L }.forEach { (k, name) ->
                        TextButton(onClick = { pending = p to k.toInt(); picking = null }) { Text(name) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun LoadChart(points: List<TrendPoint>, windowMs: Long, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val left = 40.dp.toPx(); val right = 8.dp.toPx(); val top = 8.dp.toPx(); val bottom = 18.dp.toPx()
        val w = size.width - left - right
        val h = size.height - top - bottom
        val tMax = points.lastOrNull()?.t ?: System.currentTimeMillis()
        val tMin = tMax - windowMs
        val vis = points.filter { it.t >= tMin }
        var yMax = 1.0
        vis.forEach { yMax = maxOf(yMax, it.iavg, it.flc ?: 0.0) }
        yMax *= 1.15

        fun x(t: Long) = left + (t - tMin).toFloat() / windowMs.toFloat() * w
        fun y(v: Double) = top + h - (v / yMax).toFloat() * h

        val paint = android.graphics.Paint().apply {
            color = C.Dim.toArgb(); textSize = 10.sp.toPx(); isAntiAlias = true
        }
        for (i in 0..4) {
            val v = yMax * i / 4
            val yy = y(v)
            drawLine(C.Border, Offset(left, yy), Offset(left + w, yy), 1f)
            drawContext.canvas.nativeCanvas.drawText("%.1f".format(v), 0f, yy + 4f, paint)
        }
        val secs = windowMs / 1000
        val startLabel = if (secs >= 3600) "-1 h" else if (secs >= 60) "-${secs / 60} min" else "-${secs}s"
        drawContext.canvas.nativeCanvas.drawText(startLabel, left, size.height - 2f, paint)
        drawContext.canvas.nativeCanvas.drawText("now", left + w - 20.dp.toPx(), size.height - 2f, paint)

        fun line(sel: (TrendPoint) -> Double?, color: Color, width: Float) {
            val path = Path()
            var started = false
            vis.forEach { pt ->
                val v = sel(pt) ?: return@forEach
                if (!started) { path.moveTo(x(pt.t), y(v)); started = true } else path.lineTo(x(pt.t), y(v))
            }
            if (started) drawPath(path, color, style = Stroke(width))
        }
        line({ it.flc }, C.Red, 2.5.dp.toPx())
        line({ it.iavg }, C.Blue, 2.dp.toPx())
        vis.lastOrNull()?.let { drawCircle(C.Blue, 3.dp.toPx(), Offset(x(it.t), y(it.iavg))) }
    }
}
