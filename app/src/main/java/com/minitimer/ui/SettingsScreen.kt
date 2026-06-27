package com.minitimer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minitimer.TimerViewModel
import com.minitimer.i18n.I18n
import com.minitimer.model.ACCENT_COLORS
import com.minitimer.model.AUTO_DISMISS_OPTIONS
import com.minitimer.model.HEADSET_ONLY
import com.minitimer.model.SPEAKER_AND_HEADSET
import com.minitimer.model.VIBRATION_PATTERNS
import com.minitimer.ui.theme.DONE_RED
import com.minitimer.ui.theme.JetBrainsMono
import com.minitimer.ui.theme.ON_ACCENT
import com.minitimer.ui.theme.SURFACE
import com.minitimer.ui.theme.TEXT_DIM
import com.minitimer.ui.theme.TEXT_FADED
import com.minitimer.ui.theme.TRACK
import com.minitimer.util.formatRemaining
import kotlin.math.roundToInt

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
    var showSoundDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 48.dp),
    ) {
        // ===== Apariencia =====
        SettingsGroup(t.groupAppearance, accent) {
            ItemLabel(t.language)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("Español", s.language == "es", accent) { vm.setLanguage("es") }
                Chip("English", s.language == "en", accent) { vm.setLanguage("en") }
            }
            GroupDivider()
            ItemLabel(t.color)
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
        }

        // ===== Temporizador =====
        SettingsGroup(t.groupTimer, accent) {
            ItemLabel(t.presets)
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
                            containerColor = TRACK,
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
                    placeholder = { Text(t.presetPlaceholder, color = TEXT_FADED) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = TRACK,
                        unfocusedContainerColor = TRACK,
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
            GroupDivider()
            ItemLabel(t.autoDismiss)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AUTO_DISMISS_OPTIONS.forEach { v ->
                    Chip(if (v == 0) t.off else "${v}s", s.autoDismiss == v, accent) {
                        vm.setAutoDismiss(v)
                    }
                }
            }
        }

        // ===== Alarma y sonido =====
        SettingsGroup(t.groupAlarm, accent) {
            ItemLabel(t.alarmSound)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(TRACK)
                    .clickable { showSoundDialog = true }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    s.alarmSoundName ?: t.defaultSound,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = TEXT_DIM,
                )
            }
            GroupDivider()
            val volPct = (s.alarmVolume * 100).roundToInt()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    t.alarmVolume,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(
                    onClick = { vm.setAlarmVolume(((volPct - 5).coerceAtLeast(0)) / 100f) },
                    enabled = volPct > 0,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = TRACK,
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease volume")
                }
                Text(
                    "$volPct%",
                    color = accent,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(64.dp),
                )
                FilledTonalIconButton(
                    onClick = { vm.setAlarmVolume(((volPct + 5).coerceAtMost(100)) / 100f) },
                    enabled = volPct < 100,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = accent,
                        contentColor = ON_ACCENT,
                    ),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Increase volume")
                }
            }
            GroupDivider()
            ItemLabel(t.headsetTitle)
            RadioRow(
                label = t.headsetBoth,
                selected = s.headsetMode == SPEAKER_AND_HEADSET,
                accent = accent,
            ) { vm.setHeadsetMode(SPEAKER_AND_HEADSET) }
            RadioRow(
                label = t.headsetOnly,
                selected = s.headsetMode == HEADSET_ONLY,
                accent = accent,
            ) { vm.setHeadsetMode(HEADSET_ONLY) }
            GroupDivider()
            SwitchRow(
                label = t.ignoreSilent,
                desc = t.ignoreSilentDesc,
                checked = s.ignoreSilent,
                accent = accent,
                onCheckedChange = { vm.setIgnoreSilent(it) },
            )
            GroupDivider()
            SwitchRow(
                label = t.vibration,
                desc = null,
                checked = s.vibrationEnabled,
                accent = accent,
                onCheckedChange = { vm.setVibrationEnabled(it) },
            )
            if (s.vibrationEnabled) {
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VIBRATION_PATTERNS.forEachIndexed { index, pattern ->
                        Chip(
                            pattern.name,
                            s.vibrationPattern == index,
                            accent,
                        ) {
                            vm.setVibrationPattern(index)
                            vm.previewVibration(index)
                        }
                    }
                }
            }
        }

        // ===== Overlay =====
        SettingsGroup(t.groupOverlay, accent) {
            SwitchRow(
                label = t.showNowBar,
                desc = t.showNowBarDesc,
                checked = s.showNowBar,
                accent = accent,
                onCheckedChange = { vm.setShowNowBar(it) },
            )
            GroupDivider()
            SwitchRow(
                label = t.showOverlay,
                desc = t.showOverlayDesc,
                checked = s.showOverlay,
                accent = accent,
                onCheckedChange = { vm.setShowOverlay(it) },
            )
            GroupDivider()
            SwitchRow(
                label = t.showRing,
                desc = t.showRingDesc,
                checked = s.showRing,
                accent = accent,
                onCheckedChange = { vm.setShowRing(it) },
            )
            // Posición del anillo: solo relevante si el anillo está activo.
            if (s.showRing) {
                GroupDivider()
                ItemLabel(t.ringPosition)
                Text(
                    t.ringPositionDesc,
                    color = TEXT_DIM,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                OffsetStepperRow(
                    axis = "X",
                    value = vm.ringOffsetX,
                    accent = accent,
                    onMinus = { vm.nudgeRingX(-1) },
                    onPlus = { vm.nudgeRingX(1) },
                )
                Spacer(Modifier.height(8.dp))
                OffsetStepperRow(
                    axis = "Y",
                    value = vm.ringOffsetY,
                    accent = accent,
                    onMinus = { vm.nudgeRingY(-1) },
                    onPlus = { vm.nudgeRingY(1) },
                )
                if (vm.ringOffsetX != 0 || vm.ringOffsetY != 0) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = { vm.resetRingOffset() },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = accent),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(t.reset, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        TextButton(
            onClick = { vm.resetSettings() },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = DONE_RED),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(t.reset, fontWeight = FontWeight.SemiBold)
        }
    }

    if (showSoundDialog) {
        AlarmSoundPickerDialog(
            vm = vm,
            accent = accent,
            currentUri = s.alarmSoundUri,
            title = t.alarmSound,
            selectLabel = t.select,
            cancelLabel = t.cancel,
            defaultLabel = t.defaultSound,
            onDismiss = { showSoundDialog = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmSoundPickerDialog(
    vm: TimerViewModel,
    accent: Color,
    currentUri: String?,
    title: String,
    selectLabel: String,
    cancelLabel: String,
    defaultLabel: String,
    onDismiss: () -> Unit,
) {
    val sounds = remember { vm.loadAlarmSounds() }
    var selectedUri by remember { mutableStateOf(currentUri) }

    fun stopAndDismiss() {
        vm.stopPreview()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = { stopAndDismiss() },
        containerColor = SURFACE,
        title = { Text(title, color = Color.White, fontWeight = FontWeight.SemiBold) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                items(sounds) { sound ->
                    val selected = sound.uri == selectedUri
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                selectedUri = sound.uri
                                vm.previewSound(sound.uri)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = {
                                selectedUri = sound.uri
                                vm.previewSound(sound.uri)
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = accent,
                                unselectedColor = Color(0xFF9AA0A4),
                            ),
                        )
                        Text(
                            sound.name,
                            color = Color.White,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { vm.previewSound(sound.uri) }) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "Play",
                                tint = accent,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val name = sounds.firstOrNull { it.uri == selectedUri }?.name
                        ?: defaultLabel
                    vm.setAlarmSound(selectedUri, name)
                    stopAndDismiss()
                },
            ) {
                Text(selectLabel, color = accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = { stopAndDismiss() }) {
                Text(cancelLabel, color = Color(0xFF9AA0A4))
            }
        },
    )
}

@Composable
private fun RadioRow(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = accent,
                unselectedColor = Color(0xFF9AA0A4),
            ),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            color = Color.White,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun OffsetStepperRow(
    axis: String,
    value: Int,
    accent: Color,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            axis,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        FilledTonalIconButton(
            onClick = onMinus,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = TRACK,
                contentColor = Color.White,
            ),
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease $axis")
        }
        Text(
            "$value",
            color = accent,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            fontFamily = JetBrainsMono,
            modifier = Modifier.width(64.dp),
        )
        FilledTonalIconButton(
            onClick = onPlus,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = accent,
                contentColor = ON_ACCENT,
            ),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Increase $axis")
        }
    }
}

/** Encabezado de grupo + contenedor (card) estilo Material 3. */
@Composable
private fun SettingsGroup(
    title: String,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Text(
        title,
        color = accent,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SURFACE)
            .padding(16.dp),
        content = content,
    )
}

/** Etiqueta de un ítem dentro de un grupo. */
@Composable
private fun ItemLabel(text: String) {
    Text(
        text,
        color = Color.White,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

/** Divisor entre ítems de un mismo grupo. */
@Composable
private fun GroupDivider() {
    HorizontalDivider(
        color = TRACK,
        thickness = 1.dp,
        modifier = Modifier.padding(vertical = 16.dp),
    )
}

/** Fila con etiqueta (y descripción opcional) + Switch. */
@Composable
private fun SwitchRow(
    label: String,
    desc: String?,
    checked: Boolean,
    accent: Color,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (desc != null) {
                Spacer(Modifier.height(4.dp))
                Text(desc, color = TEXT_DIM, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ON_ACCENT,
                checkedTrackColor = accent,
                uncheckedThumbColor = Color(0xFFCFD3D6),
                uncheckedTrackColor = TRACK,
            ),
        )
    }
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
            containerColor = TRACK,
            labelColor = Color(0xFFCFD3D6),
            selectedContainerColor = accent,
            selectedLabelColor = ON_ACCENT,
        ),
        border = null,
    )
}
