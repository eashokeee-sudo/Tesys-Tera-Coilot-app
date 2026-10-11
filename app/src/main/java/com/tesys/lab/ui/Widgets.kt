package com.tesys.lab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tesys.lab.model.ConnState
import com.tesys.lab.model.ParamDef
import com.tesys.lab.model.Reading

fun loadColor(pct: Double) = when {
    pct >= 95 -> C.Red
    pct >= 80 -> C.Amber
    else -> C.Green
}

/** null = unknown (not connected / not read), true / false = real bit state. Never a fake "off". */
fun boolOf(r: Reading?, state: ConnState): Boolean? =
    if (r == null || state == ConnState.DISCONNECTED || r.error != null) null else r.raw != 0L

@Composable
fun Bar(fraction: Float, color: Color) {
    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(C.Deep)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(color))
    }
}

@Composable
fun Metric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = C.Dim)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 20.sp, fontFamily = Mono, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun KeyValue(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 12.sp, color = C.Dim)
        Text(value, fontSize = 12.sp, fontFamily = Mono, fontWeight = FontWeight.Bold, color = C.Text)
    }
}

/** Lamp + label, like the DTM status pages. */
@Composable
fun Dot(label: String, on: Boolean?, onColor: Color, modifier: Modifier = Modifier) {
    val lamp = when (on) { true -> onColor; false -> Color(0xFF475569); null -> C.Border }
    Row(modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 12.sp, color = if (on == null) C.Dim else C.Text, maxLines = 2)
        Spacer(Modifier.width(6.dp))
        Box(Modifier.size(13.dp).clip(CircleShape).background(lamp).border(1.dp, C.Border, CircleShape))
    }
}

/** Two columns of lamps. */
@Composable
fun DotGrid(items: List<ParamDef>, readings: Map<String, Reading>, state: ConnState, onColor: Color) {
    items.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            pair.forEach { p -> Dot(p.name, boolOf(readings[p.id], state), onColor, Modifier.weight(1f)) }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
fun StateChip(label: String, on: Boolean?, color: Color, modifier: Modifier = Modifier) {
    val c = if (on == true) color else C.Dim
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (on == true) c.copy(alpha = 0.18f) else C.Deep)
            .border(1.dp, if (on == true) c else C.Border, RoundedCornerShape(10.dp))
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = c, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
    }
}

/** Compact label-over-value cell for statistics grids. */
@Composable
fun StatCell(p: ParamDef, r: Reading?, state: ConnState, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 3.dp)) {
        Text(p.name, fontSize = 11.sp, color = C.Dim, maxLines = 1)
        val v = valueText(p, r, state)
        val unit = if (r != null && r.error == null && v != "---" && p.unit.isNotBlank() && p.enumMap.isEmpty()) " ${p.unit}" else ""
        Text(v + unit, fontSize = 14.sp, fontFamily = Mono, fontWeight = FontWeight.Bold, color = valueColor(p, r, state))
    }
}
