package com.tesys.lab.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tesys.lab.MainViewModel
import com.tesys.lab.model.Cmd
import com.tesys.lab.model.Commands
import com.tesys.lab.model.ConnState
import kotlinx.coroutines.launch

/** Start / Stop / Trip reset. Locked until the user unlocks write access; every action is logged on the Logs tab. */
@Composable
fun ControlScreen(vm: MainViewModel) {
    val params by vm.params.collectAsState()
    val readings by vm.readings.collectAsState()
    val state by vm.state.collectAsState()
    val err by vm.lastError.collectAsState()
    val unlocked by vm.unlocked.collectAsState()
    val left by vm.unlockLeftSec.collectAsState()
    val channelId by vm.cmdChannelId.collectAsState()
    val starter by vm.starterType.collectAsState()
    val scope = rememberCoroutineScope()

    var confirm by remember { mutableStateOf<Cmd?>(null) }
    var showUnlock by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }

    val ch = Commands.channel(channelId)
    val byId = params.filter { it.enabled }.associateBy { it.id }
    fun bit(id: String): Boolean? = byId[id]?.let { boolOf(readings[it.id], state) }
    val live = state == ConnState.CONNECTED
    val dol = starter == "DOL"
    val canWrite = unlocked && live
    val trip = bit("st_trip")

    fun sendCmd(c: Cmd) { scope.launch { result = vm.sendCommand(c) } }

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ConnBanner(state, err) }

        item {
            Panel(title = "Motor") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StateChip("STOP", bit("ms_stop"), C.Dim, Modifier.weight(1f))
                    StateChip("RUN", bit("ms_run"), C.Green, Modifier.weight(1f))
                    StateChip("INHIBIT", bit("ms_inhibit"), C.Amber, Modifier.weight(1f))
                    StateChip("TRIP", trip, C.Red, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                StateChip("REMOTE MODE", bit("ms_remote"), C.Blue, Modifier.fillMaxWidth())
            }
        }

        item {
            Panel(title = "Write access", borderColor = if (unlocked) C.Red.copy(alpha = 0.6f) else C.Border) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Pill(if (unlocked) "UNLOCKED" else "LOCKED", if (unlocked) C.Red else C.Green)
                    Spacer(Modifier.width(10.dp))
                    Text(if (unlocked) "%d:%02d left".format(left / 60, left % 60) else "Commands and protection changes are blocked.",
                        fontSize = 12.sp, color = C.Dim, modifier = Modifier.weight(1f))
                    if (unlocked) OutlinedButton(onClick = { vm.lock() }) { Text("Lock now") }
                    else Button(onClick = { showUnlock = true }, colors = ButtonDefaults.buttonColors(containerColor = C.Red)) { Text("Unlock") }
                }
                Text("Unlocks for 5 minutes and locks again on disconnect.", fontSize = 11.sp, color = C.Dim, modifier = Modifier.padding(top = 6.dp))
            }
        }

        item {
            Panel(title = "Commands") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { confirm = Commands.START }, enabled = canWrite && dol,
                        modifier = Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = C.Green)
                    ) { Text("START", fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = { sendCmd(Commands.STOP) }, enabled = canWrite && dol,
                        modifier = Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = C.Red)
                    ) { Text("STOP", fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { confirm = Commands.TRIP_RESET }, enabled = canWrite && trip != false,
                    modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = C.Amber)
                ) { Text("TRIP RESET", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        !live -> "Connect first."
                        !unlocked -> "Unlock write access to enable the buttons."
                        !dol && trip == false -> "Overload starter: only Trip reset exists, and it is enabled while a trip is active."
                        !dol -> "Overload starter: Start and Stop are not supported."
                        trip == false -> "Trip reset is enabled while a trip is active."
                        else -> "Stop takes precedence if Run and Stop are ever set together."
                    }, fontSize = 11.sp, color = C.Dim
                )
                if (result.isNotBlank()) Text(result, fontFamily = Mono, fontSize = 11.sp, color = C.Amber, modifier = Modifier.padding(top = 8.dp))
            }
        }

        item {
            Panel(title = "Starter and command channel") {
                Text("Starter type", fontSize = 12.sp, color = C.Dim)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !dol, onClick = { vm.setStarter("OL") }, label = { Text("Overload (reset only)", fontSize = 12.sp) })
                    FilterChip(selected = dol, onClick = { vm.setStarter("DOL") }, label = { Text("DOL (start / stop)", fontSize = 12.sp) })
                }
                Spacer(Modifier.height(8.dp))
                Text("Command register", fontSize = 12.sp, color = C.Dim)
                Commands.CHANNELS.forEach { c ->
                    FilterChip(
                        selected = channelId == c.id, onClick = { vm.setChannel(c.id) },
                        label = { Text("%s - register %d (0x%04X)".format(if (c.id == "hmi") "HMI" else "COMM", c.register, c.address), fontSize = 12.sp) }
                    )
                }
                Text(ch.note, fontSize = 11.sp, color = C.Dim, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { scope.launch { result = vm.verifyCommandRegister() } }, enabled = live, modifier = Modifier.fillMaxWidth()) {
                    Text("Verify command register (read only)", fontSize = 13.sp)
                }
            }
        }

        item {
            Panel(title = "Command register data (DIVA requirement)") {
                KeyValue("Attribute", "Commands Register 1")
                KeyValue("Size", "2 bytes / 1 word")
                KeyValue("Range", "0 .. 65535, step 1, x1")
                KeyValue("Access", "RW")
                KeyValue("Register address", "49876  =  40001 + 9875")
                KeyValue("Wire address", "9875 (0x2693)")
                KeyValue("Function codes", "0x03, 0x06, 0x10")
                KeyValue("Default", "0")
                Spacer(Modifier.height(6.dp))
                Text("Bit assignment", fontSize = 11.sp, color = C.Dim)
                KeyValue("Bit 0  (0x0001)", "Motor run forward")
                KeyValue("Bit 3  (0x0008)", "Trip reset")
                KeyValue("Bit 12 (0x1000)", "Motor stop")
                KeyValue("All other bits", "Reserved")
                Text(
                    "The app writes the whole register: Start = 0x0001, Stop = 0x1000, Trip reset = 0x0008 and then 0x0000. " +
                        "The DIVA requirement says Start and Stop need the Control Station set to HMI. " +
                        "The DTM guide lists bit 12 of the HMI register as reserved, so check Stop on the device.",
                    fontSize = 11.sp, color = C.Dim, modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }

    if (showUnlock) {
        AlertDialog(
            onDismissRequest = { showUnlock = false },
            title = { Text("Unlock write access?") },
            text = { Text("For 5 minutes this app can send Start, Stop and Trip reset commands and change protection settings on the connected device. Make sure the machine is safe and that you are authorised.") },
            confirmButton = { TextButton(onClick = { vm.unlock(); showUnlock = false }) { Text("Unlock", color = C.Red) } },
            dismissButton = { TextButton(onClick = { showUnlock = false }) { Text("Cancel") } }
        )
    }
    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (c.id == "start") "Start the motor?" else "Reset the trip?") },
            text = {
                Text(
                    if (c.id == "start") "Sends Motor run forward (0x0001) to register ${ch.register}. The motor will start if the device allows it. Make sure people and machinery are clear."
                    else "Sends Trip reset (0x0008) to register ${ch.register}, then releases it. Find and fix the cause of the trip first."
                )
            },
            confirmButton = { TextButton(onClick = { val cc = c; confirm = null; sendCmd(cc) }) { Text(if (c.id == "start") "Start" else "Reset", color = C.Green) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } }
        )
    }
}
