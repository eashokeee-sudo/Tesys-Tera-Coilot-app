package com.tesys.lab.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tesys.lab.MainViewModel
import com.tesys.lab.UsbHelper
import com.tesys.lab.model.ConnState
import kotlinx.coroutines.launch

@Composable
fun ConnectionScreen(vm: MainViewModel) {
    val s by vm.settings.collectAsState()
    val state by vm.state.collectAsState()
    val usb by vm.usbDevices.collectAsState()
    val sel by vm.selectedUsb.collectAsState()
    val msg by vm.message.collectAsState()
    val scope = rememberCoroutineScope()
    var pingText by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Modbus configuration", fontWeight = FontWeight.Bold, fontSize = 16.sp)

        Panel(title = "Physical interface") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = s.mode == "USB", onClick = { vm.update { it.copy(mode = "USB") } }, label = { Text("USB-OTG (RTU)") })
                FilterChip(selected = s.mode == "TCP", onClick = { vm.update { it.copy(mode = "TCP") } }, label = { Text("TCP / IP") })
            }
            if (s.mode == "USB") {
                Spacer(Modifier.height(10.dp))
                if (usb.isEmpty()) Text("No USB device detected. Plug the USB-RS485 cable through an OTG adapter, then Auto-detect.", fontSize = 12.sp, color = C.Dim)
                usb.forEach { d ->
                    FilterChip(
                        selected = sel == d.deviceName,
                        onClick = { vm.selectedUsb.value = d.deviceName },
                        label = { Text(UsbHelper.label(d), fontSize = 12.sp, maxLines = 1) }
                    )
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { vm.autoDetectUsb() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Auto-detect Schneider USB cable (VID 0x16DE)", fontSize = 13.sp)
                }
            }
        }

        if (s.mode == "USB") {
            Panel(title = "Modbus serial settings") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Slave ID (1-247)", s.slaveId.toLong(), Modifier.weight(1f)) { v -> vm.update { it.copy(slaveId = v.toInt().coerceIn(1, 247)) } }
                    Dropdown("Baud", s.baud, listOf(2400, 4800, 9600, 19200, 38400, 57600, 115200), Modifier.weight(1f)) { b -> vm.update { it.copy(baud = b) } }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dropdown("Parity", s.parity, listOf("NONE", "ODD", "EVEN"), Modifier.weight(1f)) { p -> vm.update { it.copy(parity = p) } }
                    Dropdown("Stop bits", s.stopBits, listOf(1, 2), Modifier.weight(1f)) { b -> vm.update { it.copy(stopBits = b) } }
                }
                Text("Factory default: node 1, 19200 bps, Even parity.", fontSize = 11.sp, color = C.Dim, modifier = Modifier.padding(top = 6.dp))
            }
        } else {
            Panel(title = "Modbus TCP settings") {
                OutlinedTextField(
                    value = s.host, onValueChange = { v -> vm.update { it.copy(host = v) } },
                    label = { Text("IP address") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Port", s.port.toLong(), Modifier.weight(1f)) { v -> vm.update { it.copy(port = v.toInt().coerceIn(1, 65535)) } }
                    NumField("Unit ID", s.unitId.toLong(), Modifier.weight(1f)) { v -> vm.update { it.copy(unitId = v.toInt().coerceIn(0, 255)) } }
                }
                Text("Default port 502. Max 8 simultaneous Modbus TCP connections per device. If every read returns an exception, try another Unit ID (e.g. 255).", fontSize = 11.sp, color = C.Dim, modifier = Modifier.padding(top = 6.dp))
            }
        }

        Panel(title = "Polling") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumField("Poll cycle (ms)", s.pollMs, Modifier.weight(1f)) { v -> vm.update { it.copy(pollMs = v.coerceIn(100, 60000)) } }
                NumField("Timeout (ms)", s.timeoutMs, Modifier.weight(1f)) { v -> vm.update { it.copy(timeoutMs = v.coerceIn(100, 10000)) } }
            }
        }

        Panel(title = "Data order (only change if values look wrong)") {
            SwitchRow("Swap the two words of 32-bit values", s.swapWords) { v -> vm.update { it.copy(swapWords = v) } }
            SwitchRow("Swap bytes inside each word", s.swapBytes) { v -> vm.update { it.copy(swapBytes = v) } }
        }

        Button(
            onClick = { if (state == ConnState.DISCONNECTED) vm.connect() else vm.disconnect() },
            enabled = state != ConnState.CONNECTING,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = if (state == ConnState.DISCONNECTED) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColors(containerColor = C.Red)
        ) {
            Text(
                when (state) {
                    ConnState.DISCONNECTED -> "Connect to TeSys Tera"
                    ConnState.CONNECTING -> "Connecting..."
                    else -> "Disconnect"
                }, fontWeight = FontWeight.Bold
            )
        }
        OutlinedButton(
            onClick = { scope.launch { pingText = vm.ping() } },
            enabled = state == ConnState.CONNECTED || state == ConnState.COMM_LOST,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Send Modbus ping (read first enabled parameter)") }

        (pingText ?: msg)?.let { Text(it, fontFamily = Mono, fontSize = 12.sp, color = C.Amber) }

        Text("This app is read-only: it cannot write registers, start/stop the motor or change protection settings.", fontSize = 11.sp, color = C.Dim)
    }
}
