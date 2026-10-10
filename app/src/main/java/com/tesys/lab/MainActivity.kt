package com.tesys.lab

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
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

enum class Tab(val label: String, val glyph: String) {
    Modbus("Modbus", "\u2699"),
    Motor("Motor", "\u23FB"),
    Electrical("Electrical", "\u26A1"),
    Protection("Protection", "\uD83D\uDEE1"),
    Registers("Registers", "\u2261"),
    Frames("Frames", ">_")
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
    var tab by rememberSaveable { mutableStateOf(Tab.Modbus) }
    val state by vm.state.collectAsState()
    val s by vm.settings.collectAsState()

    Scaffold(
        containerColor = C.Bg,
        topBar = {
            Row(
                Modifier.fillMaxWidth().background(C.Card).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("TeSys Tera Lab", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        if (s.mode == "TCP") "Modbus TCP \u00B7 ${s.host}:${s.port}" else "Modbus RTU \u00B7 USB-RS485",
                        fontSize = 11.sp, color = C.Dim, fontFamily = Mono
                    )
                }
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
                        onClick = { tab = t },
                        icon = { Text(t.glyph, fontSize = 16.sp) },
                        label = { Text(t.label, fontSize = 10.sp, maxLines = 1) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                Tab.Modbus -> ConnectionScreen(vm)
                Tab.Motor -> GroupScreen(vm, "motor")
                Tab.Electrical -> GroupScreen(vm, "electrical")
                Tab.Protection -> ProtectionScreen(vm)
                Tab.Registers -> RegistersScreen(vm)
                Tab.Frames -> FramesScreen(vm)
            }
        }
    }
}
