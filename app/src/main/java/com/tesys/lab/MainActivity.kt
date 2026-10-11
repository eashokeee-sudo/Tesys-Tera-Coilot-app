package com.tesys.lab

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tesys.lab.model.ConnState
import com.tesys.lab.ui.*

/** [screen] = which catalog screen key is polled while this tab is visible. */
enum class Tab(val label: String, val glyph: String, val screen: String) {
    Connect("Connect", "\u2699", "motor"),
    Status("Status", "\u25CF", "motor"),
    Metering("Meter", "\u26A1", "electrical"),
    Control("Control", "\u25B6", "motor"),
    Protection("Protect", "\uD83D\uDEE1", "protection"),
    Device("Device", "\u25C6", "registers"),
    Logs("Logs", ">_", "motor")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)   // lab tool: don't sleep while monitoring
        setContent { LabTheme { AppRoot() } }
    }
}

@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(Tab.Connect) }
    var showManager by remember { mutableStateOf(false) }
    val state by vm.state.collectAsState()
    val s by vm.settings.collectAsState()
    val unlocked by vm.unlocked.collectAsState()

    LaunchedEffect(tab, showManager) { vm.setActive(if (showManager) "registers" else tab.screen) }
    BackHandler(enabled = showManager) { showManager = false }

    Scaffold(
        containerColor = C.Bg,
        topBar = {
            Row(
                Modifier.fillMaxWidth().background(C.Card).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("TeSys Tera Connect", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        if (s.mode == "TCP") "Modbus TCP \u00B7 ${s.host}:${s.port}" else "Modbus RTU \u00B7 USB-RS485",
                        fontSize = 11.sp, color = C.Dim, fontFamily = Mono
                    )
                }
                if (unlocked) { Pill("WRITE", C.Red); Spacer(Modifier.width(6.dp)) }
                when (state) {
                    ConnState.CONNECTED -> Pill("CONNECTED", C.Green)
                    ConnState.CONNECTING -> Pill("CONNECTING", C.Amber)
                    ConnState.COMM_LOST -> Pill("COMM LOST", C.Amber)
                    ConnState.DISCONNECTED -> Pill("DISCONNECTED", C.Red)
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = C.Card) {
                Tab.values().forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t; showManager = false },
                        icon = { Text(t.glyph, fontSize = 15.sp) },
                        label = { Text(t.label, fontSize = 9.sp, maxLines = 1) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (showManager) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showManager = false }) { Text("\u2190 Back") }
                        Text("Parameter manager", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    RegistersScreen(vm)
                }
            } else when (tab) {
                Tab.Connect -> ConnectionScreen(vm)
                Tab.Status -> StatusScreen(vm)
                Tab.Metering -> GroupScreen(vm, "electrical")
                Tab.Control -> ControlScreen(vm)
                Tab.Protection -> ProtectionScreen(vm)
                Tab.Device -> DeviceScreen(vm) { showManager = true }
                Tab.Logs -> LogsScreen(vm)
            }
        }
    }
}
