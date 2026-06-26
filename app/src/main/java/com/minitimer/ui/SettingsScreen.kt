package com.minitimer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minitimer.TimerViewModel
import com.minitimer.i18n.I18n
import com.minitimer.model.ACCENT_COLORS
import com.minitimer.model.AUTO_DISMISS_OPTIONS
import com.minitimer.ui.theme.JetBrainsMono
import com.minitimer.ui.theme.ON_ACCENT
import com.minitimer.ui.theme.SURFACE
import com.minitimer.util.formatRemaining

@OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    ExperimentalMaterial3Api::class,
)
@Composable
fun SettingsScreen(vm: TimerViewModel) {
    val s = vm.settings
    val t = I18n.get(s.language)
    val accent = Color(s.accent)
    var presetInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 48.dp),
    ) {
            // Idioma
            SectionLabel(t.language)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("Español", s.language == "es", accent) { vm.setLanguage("es") }
                Chip("English", s.language == "en", accent) { vm.setLanguage("en") }
            }

            // Color
            SectionLabel(t.color)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ACCENT_COLORS.forEach { c ->
                    val selected = s.accent == c
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .then(
                                if (selected) Modifier.border(2.dp, Color.White, CircleShape)
                                else Modifier
                            )
                            .clickable { vm.setAccent(c) }
                    )
                }
            }

            // Presets
            SectionLabel(t.presets)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                s.presets.forEach { sec ->
                    InputChip(
                        selected = false,
                        onClick = { vm.removePreset(sec) },
                        label = {
                            Text(
                                formatRemaining(sec * 1000L),
                                color = accent,
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                            )
                        },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Remove",
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = InputChipDefaults.inputChipColors(
                            containerColor = SURFACE,
                            labelColor = accent,
                            trailingIconColor = Color.White,
                        ),
                        border = null,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = presetInput,
                    onValueChange = { presetInput = it },
                    placeholder = { Text(t.presetPlaceholder, color = Color(0xFF5A5D5F)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SURFACE,
                        unfocusedContainerColor = SURFACE,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = accent,
                    ),
                )
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = { if (vm.addPreset(presetInput)) presetInput = "" },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = ON_ACCENT,
                    ),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
                ) {
                    Text(t.add, fontWeight = FontWeight.Bold)
                }
            }

            // Auto descartar
            SectionLabel(t.autoDismiss)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AUTO_DISMISS_OPTIONS.forEach { v ->
                    Chip(if (v == 0) t.off else "${v}s", s.autoDismiss == v, accent) {
                        vm.setAutoDismiss(v)
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            TextButton(
                onClick = { vm.resetSettings() },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF7A7A)),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(t.reset, fontWeight = FontWeight.SemiBold)
            }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(24.dp))
    Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(12.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Chip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        },
        shape = RoundedCornerShape(20.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = SURFACE,
            labelColor = Color(0xFFCFD3D6),
            selectedContainerColor = accent,
            selectedLabelColor = ON_ACCENT,
        ),
        border = null,
    )
}
