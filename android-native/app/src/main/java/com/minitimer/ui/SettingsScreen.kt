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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: TimerViewModel, requestOverlayPermission: () -> Unit) {
    val s = vm.settings
    val t = I18n.get(s.language)
    val accent = Color(s.accent)
    var presetInput by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(top = 56.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).clickable { vm.showSettings = false },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(t.settings, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(40.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ACCENT_COLORS.forEach { c ->
                    val selected = s.accent == c
                    Box(
                        modifier = Modifier
                            .size(40.dp)
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(SURFACE)
                            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    ) {
                        Text(
                            formatRemaining(sec * 1000L),
                            color = accent,
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF33363A))
                                .clickable { vm.removePreset(sec) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("\u00D7", color = Color.White, fontSize = 16.sp)
                        }
                    }
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
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent)
                        .clickable {
                            if (vm.addPreset(presetInput)) presetInput = ""
                        }
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                ) {
                    Text(t.add, color = ON_ACCENT, fontWeight = FontWeight.Bold)
                }
            }

            // Ventana flotante
            SectionLabel(t.floatingWindow)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip(t.on, s.floatingWindow, accent) {
                    vm.setFloatingWindow(true)
                    requestOverlayPermission()
                }
                Chip(t.off, !s.floatingWindow, accent) { vm.setFloatingWindow(false) }
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
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(SURFACE)
                    .clickable { vm.resetSettings() }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(t.reset, color = Color(0xFFFF7A7A), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(24.dp))
    Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun Chip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) accent else SURFACE)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            color = if (selected) ON_ACCENT else Color(0xFFCFD3D6),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
