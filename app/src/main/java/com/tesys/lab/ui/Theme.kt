package com.tesys.lab.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object C {
    val Bg = Color(0xFF0B1220)
    val Card = Color(0xFF141C2E)
    val Deep = Color(0xFF0A0F1C)
    val Border = Color(0xFF243049)
    val Green = Color(0xFF10B981)
    val Blue = Color(0xFF60A5FA)
    val Red = Color(0xFFEF4444)
    val Amber = Color(0xFFF59E0B)
    val Text = Color(0xFFE5E7EB)
    val Dim = Color(0xFF94A3B8)
}

val Mono = FontFamily.Monospace

@Composable
fun LabTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Green, onPrimary = Color.White,
            background = C.Bg, onBackground = C.Text,
            surface = C.Card, onSurface = C.Text,
            surfaceVariant = C.Deep, onSurfaceVariant = C.Dim,
            outline = C.Border, secondaryContainer = Color(0xFF0F3D2E), onSecondaryContainer = C.Green
        ),
        content = content
    )
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    title: String? = null,
    borderColor: Color = C.Border,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(C.Card)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        if (title != null && title.isNotBlank()) {
            Text(title.uppercase(), color = C.Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
        }
        content()
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text, color = color, fontSize = 11.sp, fontFamily = Mono, fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
fun <T> Dropdown(
    label: String,
    value: T,
    options: List<T>,
    modifier: Modifier = Modifier,
    text: (T) -> String = { it.toString() },
    onSelect: (T) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${text(value)}", maxLines = 1, fontSize = 13.sp)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(text(o)) }, onClick = { onSelect(o); menuOpen = false })
            }
        }
    }
}

/** Integer text field that only reports valid numbers. */
@Composable
fun NumField(label: String, value: Long, modifier: Modifier = Modifier, onChange: (Long) -> Unit) {
    var t by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = t,
        onValueChange = { s -> t = s; s.toLongOrNull()?.let(onChange) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
        modifier = modifier
    )
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp)
        Switch(checked, onChange)
    }
}
