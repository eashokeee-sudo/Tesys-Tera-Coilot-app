package com.tesys.lab.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tesys.lab.MainViewModel
import com.tesys.lab.engine.Decoder
import com.tesys.lab.model.ParamDef
import kotlinx.coroutines.launch

@Composable
fun RegistersScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val params by vm.params.collectAsState()
    val readings by vm.readings.collectAsState()
    val state by vm.state.collectAsState()
    val msg by vm.message.collectAsState()
    val scope = rememberCoroutineScope()

    var editing by remember { mutableStateOf<ParamDef?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }

    var qReg by remember { mutableStateOf("5511") }
    var qCnt by remember { mutableStateOf("2") }
    var qFc by remember { mutableIntStateOf(3) }
    var qOut by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { ctx.contentResolver.openOutputStream(it)?.use { s -> s.write(vm.exportText().toByteArray()) } }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { pendingImport = ctx.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() } }
    }

    val shown = params.filter {
        filter.isBlank() || it.name.contains(filter, true) || it.register.toString().contains(filter) || it.group.contains(filter, true)
    }

    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Parameter catalog (${params.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Add, edit or delete anything here. The screens rebuild themselves from this list.", fontSize = 12.sp, color = C.Dim)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val pad = PaddingValues(horizontal = 6.dp)
                Button(onClick = { creating = true }, Modifier.weight(1f), contentPadding = pad) { Text("+ Add", fontSize = 12.sp) }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }, Modifier.weight(1f), contentPadding = pad) { Text("Import", fontSize = 12.sp) }
                OutlinedButton(onClick = { exportLauncher.launch("tesys_tera_catalog.json") }, Modifier.weight(1f), contentPadding = pad) { Text("Export", fontSize = 12.sp) }
                OutlinedButton(onClick = { confirmReset = true }, Modifier.weight(1f), contentPadding = pad) { Text("Reset", fontSize = 12.sp) }
            }
            msg?.let { Text(it, fontSize = 12.sp, color = C.Amber, modifier = Modifier.padding(top = 4.dp)) }
        }

        item {
            Panel(title = "Quick read (any register, one shot)") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(qReg, { qReg = it.filter(Char::isDigit) }, label = { Text("Register") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(qCnt, { qCnt = it.filter(Char::isDigit) }, label = { Text("Count") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(0.7f))
                    Dropdown("FC", qFc, ParamDef.FUNCTIONS, Modifier.weight(0.8f).align(Alignment.CenterVertically), text = { "%02d".format(it) }) { qFc = it }
                }
                Spacer(Modifier.height(6.dp))
                Button(onClick = {
                    val r = qReg.toIntOrNull(); val c = qCnt.toIntOrNull()
                    if (r == null || r < 1 || c == null || c < 1 || c > 125) qOut = "Enter register 1..65536 and count 1..125."
                    else scope.launch { qOut = vm.quickRead(qFc, r, c) }
                }, modifier = Modifier.fillMaxWidth()) { Text("Read") }
                if (qOut.isNotBlank()) Text(qOut, fontFamily = Mono, fontSize = 11.sp, color = C.Green, modifier = Modifier.padding(top = 6.dp))
            }
        }

        item {
            OutlinedTextField(filter, { filter = it }, label = { Text("Search name, register or card") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }

        items(shown, key = { it.id }) { p ->
            val r = readings[p.id]
            Panel(Modifier.clickable { editing = p }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        val scaleTxt = if (p.scale != 1.0 && !p.type.isBitmap) " \u00D7" + p.scale.toBigDecimal().stripTrailingZeros().toPlainString() else ""
                        val unitTxt = if (p.unit.isNotBlank()) " " + p.unit else ""
                        Text(
                            "Reg " + p.register + " \u00B7 " + "0x%04X".format(p.address) + " \u00B7 " + "FC%02d".format(p.function) +
                                " \u00B7 " + p.type + scaleTxt + unitTxt,
                            fontSize = 10.sp, color = C.Dim, fontFamily = Mono
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                            Pill(p.screen, C.Blue)
                            if (p.role.isNotBlank()) Pill(p.role, C.Green)
                            if (!p.verified) Pill("UNVERIFIED", C.Amber)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(valueText(p, r, state), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = valueColor(p, r, state))
                        Text(if (r != null && r.error == null && state != com.tesys.lab.model.ConnState.DISCONNECTED) Decoder.rawWordsHex(p, r.raw) else "", fontFamily = Mono, fontSize = 10.sp, color = C.Dim)
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(p.enabled, { vm.setParamEnabled(p.id, it) })
                }
            }
        }
    }

    if (creating) ParamEditor(null, vm) { creating = false }
    editing?.let { e -> ParamEditor(e, vm) { editing = null } }

    pendingImport?.let { text ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Import catalog") },
            text = { Text("Merge adds new parameters and overwrites matching IDs. Replace swaps the whole catalog.") },
            confirmButton = { TextButton(onClick = { vm.importText(text, true); pendingImport = null }) { Text("Merge") } },
            dismissButton = { TextButton(onClick = { vm.importText(text, false); pendingImport = null }) { Text("Replace") } }
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset to defaults?") },
            text = { Text("All your changes to the parameter catalog will be lost. Export first if you want a backup.") },
            confirmButton = { TextButton(onClick = { vm.resetCatalog(); confirmReset = false }) { Text("Reset", color = C.Red) } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } }
        )
    }
}
