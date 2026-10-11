package com.tesys.lab.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tesys.lab.MainViewModel
import com.tesys.lab.engine.ExplainRow
import com.tesys.lab.engine.FrameExplain
import com.tesys.lab.modbus.FrameEntry

@Composable
private fun ExplainTable(title: String, rows: List<ExplainRow>, color: androidx.compose.ui.graphics.Color) {
    Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
    rows.forEach { r ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
            Text(r.hex.ifBlank { "" }, Modifier.width(92.dp), fontFamily = Mono, fontSize = 10.sp, color = color)
            Column(Modifier.weight(1f)) {
                Text(r.field, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(r.meaning, fontSize = 11.sp, color = C.Dim)
            }
        }
    }
}

/** Modbus frame log: every request and reply, with a field-by-field explanation on tap. */
@Composable
fun LogsScreen(vm: MainViewModel) {
    val entries by vm.frameLog.entries.collectAsState()
    val st by vm.frameLog.stats.collectAsState()
    val params by vm.params.collectAsState()
    var paused by remember { mutableStateOf(vm.frameLog.paused) }
    var filter by remember { mutableStateOf("ALL") }
    var help by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(setOf<Long>()) }

    val shown: List<FrameEntry> = when (filter) {
        "ERR" -> entries.filter { !it.ok }
        "WRITE" -> entries.filter { it.write }
        else -> entries
    }

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Panel(title = "Communication health") {
                Row(Modifier.fillMaxWidth()) {
                    Stat("Success", "${st.successPct} %", Modifier.weight(1f))
                    Stat("Timeouts", "${st.timeouts}", Modifier.weight(1f))
                    Stat("CRC err", "${st.crc}", Modifier.weight(1f))
                    Stat("Exceptions", "${st.exceptions}", Modifier.weight(1f))
                    Stat("Avg", "${st.avgMs} ms", Modifier.weight(1f))
                }
                Text("${st.total} requests", fontSize = 11.sp, color = C.Dim, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = filter == "ALL", onClick = { filter = "ALL" }, label = { Text("All", fontSize = 12.sp) })
                FilterChip(selected = filter == "ERR", onClick = { filter = "ERR" }, label = { Text("Errors", fontSize = 12.sp) })
                FilterChip(selected = filter == "WRITE", onClick = { filter = "WRITE" }, label = { Text("Writes", fontSize = 12.sp) })
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { help = !help }) { Text(if (help) "Hide guide" else "Frame guide", fontSize = 12.sp) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.frameLog.paused = !paused; paused = !paused }, modifier = Modifier.weight(1f)) { Text(if (paused) "Resume log" else "Pause log") }
                OutlinedButton(onClick = { vm.frameLog.clear(); expanded = emptySet() }, modifier = Modifier.weight(1f)) { Text("Clear") }
            }
        }
        if (help) item {
            Panel(title = "How to read a frame") {
                Text("Modbus RTU (USB-RS485)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Address (1) | Function (1) | Data (n) | CRC-16 (2, low byte first)", fontSize = 11.sp, fontFamily = Mono, color = C.Blue)
                Spacer(Modifier.height(6.dp))
                Text("Modbus TCP (Ethernet)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Transaction ID (2) | Protocol 0 (2) | Length (2) | Unit ID (1) | Function (1) | Data (n)   - no CRC", fontSize = 11.sp, fontFamily = Mono, color = C.Blue)
                Spacer(Modifier.height(6.dp))
                Text("Functions: 01/02 read bits, 03/04 read registers, 06 write one register. A reply with the function code + 0x80 is an exception: 01 illegal function, 02 illegal address, 03 illegal value, 04 device failure, 06 busy.", fontSize = 11.sp, color = C.Dim)
                Spacer(Modifier.height(4.dp))
                Text("Register number = wire address + 1 (the guides print both). 32-bit values use two registers, high word first.", fontSize = 11.sp, color = C.Dim)
            }
        }
        if (shown.isEmpty()) item { Text("No frames yet. Connect and they appear here.", fontSize = 12.sp, color = C.Dim) }
        items(shown, key = { it.id }) { e ->
            val id = e.id
            val isOpen = id in expanded
            Panel(
                Modifier.clickable { expanded = if (isOpen) expanded - id else expanded + id },
                borderColor = if (!e.ok) C.Red.copy(alpha = 0.5f) else if (e.write) C.Amber.copy(alpha = 0.6f) else C.Border
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.time, Modifier.weight(1f), fontFamily = Mono, fontSize = 11.sp, color = C.Dim)
                    Text("${e.ms} ms", fontFamily = Mono, fontSize = 11.sp, color = C.Dim)
                    Spacer(Modifier.width(8.dp))
                    Pill(e.result, if (e.ok) C.Green else C.Red)
                }
                Spacer(Modifier.height(4.dp))
                Text(FrameExplain.summary(e, params), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("TX  ${e.tx}", fontFamily = Mono, fontSize = 10.sp, color = C.Blue)
                Text("RX  ${e.rx.ifBlank { "-" }}", fontFamily = Mono, fontSize = 10.sp, color = C.Green, maxLines = if (isOpen) 12 else 2)
                if (isOpen) {
                    ExplainTable("REQUEST", FrameExplain.rows(e.txBytes, true, e.tcp, e.txBytes, params), C.Blue)
                    ExplainTable("RESPONSE", FrameExplain.rows(e.rxBytes, false, e.tcp, e.txBytes, params), C.Green)
                } else {
                    Text("Tap to explain", fontSize = 10.sp, color = C.Dim, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = C.Dim)
        Text(value, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
