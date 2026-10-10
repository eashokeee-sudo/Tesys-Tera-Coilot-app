package com.tesys.lab.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.tesys.lab.model.ConnState
import com.tesys.lab.model.TrendPoint

private fun loadColor(pct: Double) = when {
    pct >= 95 -> C.Red
    pct >= 80 -> C.Amber
    else -> C.Green
}

@Composable
private fun Bar(fraction: Float, color: Color) {
    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(C.Deep)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(color))
    }
}

@Composable
private fun Metric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = C.Dim)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 20.sp, fontFamily = Mono, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun ProtectionScreen(vm: MainViewModel) {
    val params by vm.params.collectAsState()
    val readings by vm.readings.collectAsState()
    val state by vm.state.collectAsState()
    val err by vm.lastError.collectAsState()
    val trend by vm.trend.collectAsState()
    var windowSec by rememberSaveable { mutableStateOf(60) }

    val iavgP = params.firstOrNull { it.role == "IAVG" }
    val flcP = params.firstOrNull { it.role == "FLC" }
    val thP = params.firstOrNull { it.role == "THERMAL" }
    val live = state == ConnState.CONNECTED
    val iavg = if (state == ConnState.DISCONNECTED) null else iavgP?.let { readings[it.id]?.value }
    val flc = if (state == ConnState.DISCONNECTED) null else flcP?.let { readings[it.id]?.value }
    val th = if (state == ConnState.DISCONNECTED) null else thP?.let { readings[it.id]?.value }
    val loadPct = if (iavg != null && flc != null && flc > 0) iavg / flc * 100.0 else null

    val shown = params.filter {
        it.enabled && it.screen == "protection" && it.role !in setOf("FLC", "IAVG", "THERMAL")
    }

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ConnBanner(state, err) }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Protection & load analysis", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Pill("READ-ONLY", C.Blue)
            }
        }

        item {
            Panel(title = "Load vs configured FLC") {
                Row(Modifier.fillMaxWidth()) {
                    Metric("FLC", flc?.let { "%.2f A".format(it) } ?: "---", C.Red, Modifier.weight(1f))
                    Metric("IAVG", iavg?.let { "%.2f A".format(it) } ?: "---", if (live) C.Blue else C.Dim, Modifier.weight(1f))
                    Metric("LOAD", loadPct?.let { "%.0f %%".format(it) } ?: "---", loadPct?.let { if (live) loadColor(it) else C.Dim } ?: C.Dim, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Bar(((loadPct ?: 0.0) / 100.0).toFloat(), loadPct?.let { loadColor(it) } ?: C.Dim)
                Spacer(Modifier.height(6.dp))
                if (iavg != null && flc != null) {
                    val margin = flc - iavg
                    Text(
                        if (margin >= 0) "Margin: %.2f A below FLC".format(margin) else "Over FLC by %.2f A".format(-margin),
                        fontSize = 12.sp, color = C.Dim
                    )
                }
                if (iavgP == null || flcP == null)
                    Text("Give one parameter the role IAVG and one the role FLC (Registers tab) to enable this card.", fontSize = 12.sp, color = C.Amber)
                else if (!flcP.verified)
                    Text("FLC register / data type / scale not verified yet. Compare with the DTM and edit it under Registers.", fontSize = 12.sp, color = C.Amber)
            }
        }

        item {
            Panel(title = "Motor thermal capacity") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (thP == null) "No parameter with role THERMAL" else thP.name, Modifier.weight(1f), fontSize = 13.sp, color = C.Dim)
                    Text(th?.let { "%.0f %%".format(it) } ?: "---", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        color = th?.let { if (live) loadColor(it) else C.Dim } ?: C.Dim)
                }
                Spacer(Modifier.height(8.dp))
                Bar(((th ?: 0.0) / 100.0).toFloat(), th?.let { loadColor(it) } ?: C.Dim)
                Spacer(Modifier.height(4.dp))
                Text("Amber from 80 %, red from 95 %", fontSize = 10.sp, color = C.Dim)
            }
        }

        item {
            Panel(title = "FLC vs IAVG trend") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(60 to "1 min", 900 to "15 min", 3600 to "1 h").forEach { (sec, label) ->
                        FilterChip(selected = windowSec == sec, onClick = { windowSec = sec }, label = { Text(label, fontSize = 12.sp) })
                    }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { vm.clearTrend() }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                        Text("Clear", fontSize = 12.sp)
                    }
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

        shown.groupBy { it.group }.forEach { (g, items) ->
            item(key = "pg_$g") { GroupPanel(g, items, readings, state) }
        }
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
