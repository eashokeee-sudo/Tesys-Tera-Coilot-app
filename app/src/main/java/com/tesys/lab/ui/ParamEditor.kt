package com.tesys.lab.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tesys.lab.MainViewModel
import com.tesys.lab.model.BitDef
import com.tesys.lab.model.BitStyle
import com.tesys.lab.model.DType
import com.tesys.lab.model.ParamDef

/** "0=Name" lines (separated by newline or ;) -> list of (number, name). */
fun parseKv(t: String): List<Pair<Long, String>> =
    t.split('\n', ';').mapNotNull { line ->
        val i = line.indexOf('=')
        if (i <= 0) null else line.substring(0, i).trim().toLongOrNull()?.let { it to line.substring(i + 1).trim() }
    }

/**
 * Add / edit / duplicate / delete one parameter. Pass [initial] = null to create a new one.
 * Everything here is stored in catalog.json - no code change is ever needed.
 */
@Composable
fun ParamEditor(initial: ParamDef?, vm: MainViewModel, onDismiss: () -> Unit) {
    val isNew = initial == null
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var register by remember { mutableStateOf(initial?.register?.toString() ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: DType.UINT16) }
    var fc by remember { mutableIntStateOf(initial?.function ?: 3) }
    var scale by remember { mutableStateOf((initial?.scale ?: 1.0).toString()) }
    var unit by remember { mutableStateOf(initial?.unit ?: "") }
    var decimals by remember { mutableStateOf(if ((initial?.decimals ?: -1) >= 0) initial!!.decimals.toString() else "") }
    var screen by remember { mutableStateOf(initial?.screen ?: "registers") }
    var group by remember { mutableStateOf(initial?.group ?: "") }
    var role by remember { mutableStateOf(initial?.role ?: "") }
    var tile by remember { mutableStateOf(initial?.tile ?: false) }
    var bitStyle by remember { mutableStateOf(initial?.bitStyle ?: BitStyle.STATE) }
    var everyN by remember { mutableStateOf((initial?.everyN ?: 1).toString()) }
    var enabled by remember { mutableStateOf(initial?.enabled ?: true) }
    var verified by remember { mutableStateOf(initial?.verified ?: false) }
    var enumText by remember { mutableStateOf(initial?.enumMap?.entries?.joinToString("\n") { "${it.key}=${it.value}" } ?: "") }
    var bitsText by remember { mutableStateOf(initial?.bits?.joinToString("\n") { "${it.bit}=${it.name}" } ?: "") }
    var source by remember { mutableStateOf(initial?.source ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val regInt = register.toIntOrNull()

    fun build(newId: String): ParamDef? {
        if (name.isBlank()) { error = "Name is required."; return null }
        if (regInt == null || regInt !in 1..65536) { error = "Register must be 1..65536 (number as printed in the guide)."; return null }
        val sc = scale.toDoubleOrNull()
        if (sc == null || sc == 0.0) { error = "Scale must be a non-zero number (e.g. 1, 0.001, 0.1)."; return null }
        val en = everyN.toIntOrNull()
        if (en == null || en < 1) { error = "'Poll every N cycles' must be 1 or more."; return null }
        if (type == DType.BOOL && fc !in listOf(1, 2)) { error = "Type BOOL needs function 1 or 2."; return null }
        if (type != DType.BOOL && fc !in listOf(3, 4)) { error = "Function 1/2 can only be used with type BOOL."; return null }
        return ParamDef(
            id = newId, name = name.trim(), register = regInt, type = type, function = fc, scale = sc,
            unit = unit.trim(), decimals = decimals.toIntOrNull()?.coerceIn(0, 6) ?: -1,
            screen = screen, group = group.trim(), role = role, tile = tile, bitStyle = bitStyle,
            enumMap = parseKv(enumText).toMap(LinkedHashMap()),
            bits = parseKv(bitsText).map { BitDef(it.first.toInt(), it.second) },
            everyN = en, enabled = enabled, verified = verified, source = source.trim(), notes = notes.trim()
        )
    }

    fun newId() = "u" + java.lang.Long.toString(System.currentTimeMillis(), 36)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add parameter" else "Edit parameter") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    register, { register = it.filter(Char::isDigit) }, singleLine = true,
                    label = { Text("Register (guide number)") },
                    supportingText = { Text(if (regInt != null) "Wire address = ${regInt - 1} (0x%04X)".format(regInt - 1) else "Register = address + 1") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dropdown("Type", type, DType.values().toList(), Modifier.weight(1f)) { t ->
                        type = t
                        if (t == DType.BOOL && fc !in listOf(1, 2)) fc = 2
                        if (t != DType.BOOL && fc !in listOf(3, 4)) fc = 3
                    }
                    Dropdown("FC", fc, ParamDef.FUNCTIONS, Modifier.weight(1f), text = { "%02d".format(it) }) { fc = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(scale, { scale = it }, label = { Text("Scale") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(unit, { unit = it }, label = { Text("Unit") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(decimals, { decimals = it.filter(Char::isDigit) }, label = { Text("Decimals (blank=auto)") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(everyN, { everyN = it.filter(Char::isDigit) }, label = { Text("Poll every N") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Dropdown("Show on screen", screen, ParamDef.SCREENS, Modifier.fillMaxWidth()) { screen = it }
                OutlinedTextField(group, { group = it }, label = { Text("Card title (group)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Dropdown("Role", role, ParamDef.ROLES, Modifier.fillMaxWidth(), text = { it.ifBlank { "none" } }) { role = it }
                SwitchRow("Draw as big tile (3 per row)", tile) { tile = it }
                if (type.isBitmap) Dropdown("Bit style", bitStyle, BitStyle.values().toList(), Modifier.fillMaxWidth()) { bitStyle = it }
                OutlinedTextField(
                    enumText, { enumText = it }, label = { Text("Value labels  (1=STOP)") },
                    supportingText = { Text("One per line: raw value = text") }, modifier = Modifier.fillMaxWidth(), minLines = 2
                )
                if (type.isBitmap) OutlinedTextField(
                    bitsText, { bitsText = it }, label = { Text("Bit names  (0=Thermal overload)") },
                    supportingText = { Text("One per line: bit number = name. Set bits without a name show as 'bit N'.") },
                    modifier = Modifier.fillMaxWidth(), minLines = 3
                )
                OutlinedTextField(source, { source = it }, label = { Text("Source (document / page)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                SwitchRow("Enabled (polled)", enabled) { enabled = it }
                SwitchRow("Verified (confirmed in guide / on device)", verified) { verified = it }
                error?.let { Text(it, color = C.Red, fontSize = 12.sp) }
                if (!isNew) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            build(newId())?.let { vm.saveParam(it.copy(name = it.name + " (copy)"), null); onDismiss() }
                        }, modifier = Modifier.weight(1f)) { Text("Duplicate") }
                        OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = C.Red)) { Text("Delete") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                build(initial?.id ?: newId())?.let { vm.saveParam(it, initial?.id); onDismiss() }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (confirmDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete parameter?") },
            text = { Text("\"${initial.name}\" (register ${initial.register}) will be removed from the catalog. You can restore it by importing a backup or resetting to defaults.") },
            confirmButton = { TextButton(onClick = { vm.deleteParam(initial.id); confirmDelete = false; onDismiss() }) { Text("Delete", color = C.Red) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } }
        )
    }
}
