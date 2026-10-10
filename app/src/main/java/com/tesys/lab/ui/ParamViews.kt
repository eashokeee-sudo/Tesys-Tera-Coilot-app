package com.tesys.lab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tesys.lab.MainViewModel
import com.tesys.lab.model.BitStyle
import com.tesys.lab.model.ConnState
import com.tesys.lab.model.DType
import com.tesys.lab.model.ParamDef
import com.tesys.lab.model.Reading

/** "---" until a real value exists. Never shows a fake zero. */
fun valueText(p: ParamDef, r: Reading?, state: ConnState): String {
    if (r == null || state == ConnState.DISCONNECTED) return "---"
    return r.text
}

fun valueColor(p: ParamDef, r: Reading?, state: ConnState): Color = when {
    r == null || state == ConnState.DISCONNECTED -> C.Dim
    r.error != null -> C.Red
    state != ConnState.CONNECTED -> C.Dim      // stale
    p.unit == "V" -> C.Blue
    else -> C.Green
}

@Composable
fun ConnBanner(state: ConnState, error: String?) {
    when (state) {
        ConnState.DISCONNECTED -> Panel(borderColor = C.Red.copy(alpha = 0.5f)) {
            Text("No TeSys Tera connected", color = C.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text("Values stay at --- until a device answers. Open the Modbus tab to connect.", color = C.Dim, fontSize = 12.sp)
        }
        ConnState.COMM_LOST -> Panel(borderColor = C.Amber.copy(alpha = 0.6f)) {
            Text("Communication lost", color = C.Amber, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("Values below are stale (last good reading).${if (error != null) "\n$error" else ""}", color = C.Dim, fontSize = 12.sp)
        }
        ConnState.CONNECTING -> Panel { Text("Connecting...", color = C.Amber, fontSize = 13.sp) }
        ConnState.CONNECTED -> {}
    }
}

@Composable
fun ParamRow(p: ParamDef, r: Reading?, state: ConnState) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(p.name, fontSize = 13.sp, color = C.Text)
            Text("Reg ${p.register}  0x%04X".format(p.address), fontSize = 10.sp, color = C.Dim, fontFamily = Mono)
        }
        val v = valueText(p, r, state)
        val unit = if (r != null && r.error == null && v != "---" && p.unit.isNotBlank() && p.enumMap.isEmpty() && !p.type.isBit) " ${p.unit}" else ""
        Text(v + unit, fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor(p, r, state))
    }
}

@Composable
fun Tile(p: ParamDef, r: Reading?, state: ConnState, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(C.Deep)
            .border(1.dp, C.Border, RoundedCornerShape(10.dp)).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(p.name, fontSize = 11.sp, color = C.Dim)
        Spacer(Modifier.height(6.dp))
        Text(valueText(p, r, state), fontFamily = Mono, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = valueColor(p, r, state))
        Text(p.unit, fontSize = 11.sp, color = C.Dim)
    }
}

@Composable
private fun BitChip(text: String, color: Color) {
    Text(
        text, fontSize = 11.sp, color = color,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BitView(p: ParamDef, r: Reading?, state: ConnState) {
    val live = r != null && state != ConnState.DISCONNECTED
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.name, Modifier.weight(1f), fontSize = 13.sp, color = C.Text)
            Text(
                if (live) com.tesys.lab.engine.Decoder.rawHex(p, r!!.raw) else "---",
                fontFamily = Mono, fontSize = 11.sp, color = C.Dim
            )
        }
        Spacer(Modifier.height(6.dp))
        if (!live) return@Column
        val raw = r!!.raw
        val nBits = if (p.type == DType.BITMAP32) 32 else 16
        val on = when (p.bitStyle) {
            BitStyle.ENABLE -> C.Green; BitStyle.ALARM -> C.Amber; BitStyle.TRIP -> C.Red; BitStyle.STATE -> C.Blue
        }
        val named = p.bits.sortedBy { it.bit }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            named.forEach { b ->
                val set = (raw shr b.bit) and 1L == 1L
                val label = when {
                    p.bitStyle == BitStyle.ENABLE && set -> "\u2713 ${b.name}"
                    set -> "\u25CF ${b.name}"
                    else -> "\u25CB ${b.name}"
                }
                BitChip(label, if (set) on else C.Dim)
            }
            val namedSet = named.map { it.bit }.toSet()
            for (i in 0 until nBits) {
                if (i !in namedSet && (raw shr i) and 1L == 1L) BitChip("\u25CF bit $i", on)
            }
        }
        if (named.isEmpty() && raw == 0L) Text("No bits set", fontSize = 11.sp, color = C.Dim)
    }
}

@Composable
fun GroupPanel(title: String, items: List<ParamDef>, readings: Map<String, Reading>, state: ConnState) {
    Panel(title = title) {
        val tiles = items.filter { it.tile }
        tiles.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { Tile(it, readings[it.id], state, Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(8.dp))
        }
        items.filterNot { it.tile }.forEach { p ->
            if (p.type.isBitmap) BitView(p, readings[p.id], state) else ParamRow(p, readings[p.id], state)
        }
    }
}

@Composable
fun StateCard(p: ParamDef, r: Reading?, state: ConnState) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("STATE", Modifier.weight(1f), color = C.Dim, fontSize = 13.sp, fontFamily = Mono)
            val v = if (r == null || state == ConnState.DISCONNECTED) "OFFLINE" else r.text
            val col = when {
                state != ConnState.CONNECTED -> C.Dim
                v.equals("RUN", true) -> C.Green
                v.equals("START", true) -> C.Amber
                v.equals("STOP", true) -> C.Dim
                else -> C.Blue
            }
            Pill(v, col)
        }
    }
}

/** Generic screen: everything in the catalog whose `screen` equals [screen], grouped by `group`. */
@Composable
fun GroupScreen(vm: MainViewModel, screen: String) {
    val params by vm.params.collectAsState()
    val readings by vm.readings.collectAsState()
    val state by vm.state.collectAsState()
    val err by vm.lastError.collectAsState()
    val list = params.filter { it.enabled && it.screen == screen }
    val stateParam = list.firstOrNull { it.role == "MOTOR_STATE" }

    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ConnBanner(state, err) }
        if (stateParam != null) item { StateCard(stateParam, readings[stateParam.id], state) }
        if (list.isEmpty()) item {
            Panel { Text("Nothing assigned to this screen yet. Go to Registers, add or edit a parameter and set its Screen to \"$screen\".", color = C.Dim, fontSize = 13.sp) }
        }
        list.filter { it.role != "MOTOR_STATE" }.groupBy { it.group }.forEach { (g, items) ->
            item(key = "g_$g") { GroupPanel(g, items, readings, state) }
        }
    }
}
