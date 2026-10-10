package com.tesys.lab.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@Composable
fun FramesScreen(vm: MainViewModel) {
    val entries by vm.frameLog.entries.collectAsState()
    val st by vm.frameLog.stats.collectAsState()
    var paused by remember { mutableStateOf(vm.frameLog.paused) }

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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.frameLog.paused = !paused; paused = !paused }, modifier = Modifier.weight(1f)) {
                    Text(if (paused) "Resume log" else "Pause log")
                }
                OutlinedButton(onClick = { vm.frameLog.clear() }, modifier = Modifier.weight(1f)) { Text("Clear") }
            }
        }
        items(entries) { e ->
            Panel(borderColor = if (e.ok) C.Border else C.Red.copy(alpha = 0.5f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.time, Modifier.weight(1f), fontFamily = Mono, fontSize = 11.sp, color = C.Dim)
                    Text("${e.ms} ms", fontFamily = Mono, fontSize = 11.sp, color = C.Dim)
                    Spacer(Modifier.width(8.dp))
                    Pill(e.result, if (e.ok) C.Green else C.Red)
                }
                Spacer(Modifier.height(6.dp))
                Text("TX  ${e.tx}", fontFamily = Mono, fontSize = 10.sp, color = C.Blue)
                Text("RX  ${e.rx.ifBlank { "-" }}", fontFamily = Mono, fontSize = 10.sp, color = C.Green, maxLines = 4)
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
