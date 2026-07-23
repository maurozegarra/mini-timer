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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.minitimer.AlarmScope
import com.minitimer.SettingsRoot
import com.minitimer.SettingsSection
import com.minitimer.TimerViewModel
import com.minitimer.data.BackupManager
import com.minitimer.i18n.I18n
import com.minitimer.i18n.Strings
import com.minitimer.model.AlarmConfig
import com.minitimer.model.AppConfig
import java.text.DateFormat
import java.util.Date
import com.minitimer.model.ACCENT_COLORS
import com.minitimer.model.ADD_INCREMENT_OPTIONS
import com.minitimer.model.AUTO_DISMISS_OPTIONS
import com.minitimer.model.HEADSET_ONLY
import com.minitimer.model.SPEAKER_AND_HEADSET
import com.minitimer.model.THEME_AUTO
import com.minitimer.model.THEME_DARK
import com.minitimer.model.THEME_LIGHT
import com.minitimer.model.VIBRATION_PATTERNS
import com.minitimer.ui.theme.Dims
import com.minitimer.ui.theme.AppTheme
import com.minitimer.ui.theme.DONE_RED
import com.minitimer.ui.theme.JetBrainsMono
import com.minitimer.util.formatRemaining
import com.minitimer.util.incLabel
import kotlin.math.roundToInt

@OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    ExperimentalMaterial3Api::class,
)
@Composable
fun SettingsScreen(vm: TimerViewModel) {
    val c = vm.config
    val t = I18n.get(c.general.language)
    val accent = AppTheme.colors.accent
    var presetH by remember { mutableStateOf(0) }
    var presetM by remember { mutableStateOf(5) }
    var presetS by remember { mutableStateOf(0) }
    var soundDialogScope by remember { mutableStateOf<AlarmScope?>(null) }

    val context = LocalContext.current
    var folderName by remember { mutableStateOf(BackupManager.folderName(context)) }
    var lastBackupAt by remember { mutableStateOf<Long?>(null) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            BackupManager.setFolder(context, uri)
            folderName = BackupManager.folderName(context)
            val existing = BackupManager.backupExportedAt(context)
            if (existing != null) {
                lastBackupAt = existing
                showRestoreDialog = true
            } else {
                BackupManager.writeBackup(context)
                lastBackupAt = BackupManager.backupExportedAt(context)
            }
        }
    }

    LaunchedEffect(folderName) {
        if (folderName != null) lastBackupAt = BackupManager.backupExportedAt(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.bg)
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 48.dp),
    ) {
        when (vm.settingsSection) {
        null -> SettingsCategoryList(vm = vm, c = c, t = t, accent = accent, root = vm.settingsRoot, folderName = folderName)

        SettingsSection.APPEARANCE -> SettingsGroup(t.groupAppearance, accent) {
            ItemLabel(t.language)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("Español", c.general.language == "es", accent) { vm.setLanguage("es") }
                Chip("English", c.general.language == "en", accent) { vm.setLanguage("en") }
            }
            GroupDivider()
            ItemLabel(t.color)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ACCENT_COLORS.forEach { col ->
                    val selected = c.general.accent == col
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(col))
                            .then(
                                if (selected) Modifier.border(2.dp, AppTheme.colors.textPrimary, CircleShape)
                                else Modifier
                            )
                            .clickable { vm.setAccent(col) }
                    )
                }
            }
            GroupDivider()
            ItemLabel(t.theme)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip(t.themeAuto, c.general.themeMode == THEME_AUTO, accent) { vm.setThemeMode(THEME_AUTO) }
                Chip(t.themeLight, c.general.themeMode == THEME_LIGHT, accent) { vm.setThemeMode(THEME_LIGHT) }
                Chip(t.themeDark, c.general.themeMode == THEME_DARK, accent) { vm.setThemeMode(THEME_DARK) }
            }
        }

        SettingsSection.TIMER -> {
            SettingsGroup(t.groupTimer, accent) {
                ItemLabel(t.presets)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    c.timer.presets.forEach { sec ->
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
                                containerColor = AppTheme.colors.track,
                                labelColor = accent,
                                trailingIconColor = AppTheme.colors.textPrimary,
                            ),
                            border = null,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                WheelTimePicker(
                    h = presetH,
                    m = presetM,
                    s = presetS,
                    accent = accent,
                    t = t,
                    onChange = { h, m, sec -> presetH = h; presetM = m; presetS = sec },
                )
                Spacer(Modifier.height(12.dp))
                val presetSec = presetH * 3600 + presetM * 60 + presetS
                Button(
                    onClick = { vm.addPresetSeconds(presetSec) },
                    enabled = presetSec > 0,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = AppTheme.colors.onAccent,
                        disabledContainerColor = AppTheme.colors.track,
                        disabledContentColor = AppTheme.colors.textDim,
                    ),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) {
                    Text(t.add, fontWeight = FontWeight.Bold)
                }
                GroupDivider()
                ItemLabel(t.autoDismiss)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AUTO_DISMISS_OPTIONS.forEach { v ->
                        Chip(if (v == 0) t.off else "${v}s", c.timer.autoDismiss == v, accent) {
                            vm.setAutoDismiss(v)
                        }
                    }
                }
                GroupDivider()
                ItemLabel(t.addTimeTitle)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ADD_INCREMENT_OPTIONS.forEach { v ->
                        Chip(incLabel(v), c.timer.addIncrementSec == v, accent) {
                            vm.setAddIncrement(v)
                        }
                    }
                }
            }
            AlarmSection(vm, AlarmScope.TIMER, t, accent) { soundDialogScope = AlarmScope.TIMER }
        }

        SettingsSection.OVERLAY -> SettingsGroup(t.groupOverlay, accent) {
            SwitchRow(
                label = t.showNowBar,
                desc = t.showNowBarDesc,
                checked = c.timer.showNowBar,
                accent = accent,
                onCheckedChange = { vm.setShowNowBar(it) },
            )
            GroupDivider()
            SwitchRow(
                label = t.showOverlay,
                desc = t.showOverlayDesc,
                checked = c.timer.showOverlay,
                accent = accent,
                onCheckedChange = { vm.setShowOverlay(it) },
            )
            GroupDivider()
            SwitchRow(
                label = t.showRing,
                desc = t.showRingDesc,
                checked = c.timer.showRing,
                accent = accent,
                onCheckedChange = { vm.setShowRing(it) },
            )
            // Posición del anillo: solo relevante si el anillo está activo.
            if (c.timer.showRing) {
                GroupDivider()
                ItemLabel(t.ringPosition)
                Text(
                    t.ringPositionDesc,
                    color = AppTheme.colors.textDim,
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

        SettingsSection.BACKUP -> SettingsGroup(t.groupBackup, accent) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppTheme.colors.track)
                    .clickable { folderPicker.launch(null) }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        t.backupFolder,
                        color = AppTheme.colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        folderName ?: t.backupNotSet,
                        color = AppTheme.colors.textDim,
                        fontSize = 13.sp,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = AppTheme.colors.textDim,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(t.backupAutoDesc, color = AppTheme.colors.textDim, fontSize = 13.sp)
            GroupDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        t.lastBackup,
                        color = AppTheme.colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        lastBackupAt?.let {
                            DateFormat.getDateTimeInstance(
                                DateFormat.MEDIUM, DateFormat.SHORT, t.locale,
                            ).format(Date(it))
                        } ?: t.never,
                        color = AppTheme.colors.textDim,
                        fontSize = 13.sp,
                    )
                }
                TextButton(
                    onClick = { showRestoreDialog = true },
                    enabled = folderName != null && lastBackupAt != null,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = accent),
                ) {
                    Text(t.restore, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        SettingsSection.DEVELOPER -> SettingsGroup(t.groupDeveloper, accent) {
            SwitchRow(
                label = t.developerMode,
                desc = t.developerModeDesc,
                checked = c.general.developerMode,
                accent = accent,
                onCheckedChange = { vm.setDeveloperMode(it) },
            )
        }
        }
    }

    val dialogScope = soundDialogScope
    if (dialogScope != null) {
        AlarmSoundPickerDialog(
            vm = vm,
            scope = dialogScope,
            accent = accent,
            title = t.alarmSound,
            selectLabel = t.select,
            cancelLabel = t.cancel,
            defaultLabel = t.defaultSound,
            onDismiss = { soundDialogScope = null },
        )
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            containerColor = AppTheme.colors.surface,
            title = { Text(t.restoreTitle, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold) },
            text = { Text(t.restoreMessage, color = AppTheme.colors.textDim) },
            confirmButton = {
                TextButton(onClick = {
                    val json = BackupManager.readBackup(context)
                    if (json != null && BackupManager.restoreFromJson(context, json)) {
                        vm.reload()
                    }
                    lastBackupAt = BackupManager.backupExportedAt(context)
                    showRestoreDialog = false
                }) {
                    Text(t.restore, color = accent, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text(t.cancel, color = AppTheme.colors.textDim)
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmSoundPickerDialog(
    vm: TimerViewModel,
    scope: AlarmScope,
    accent: Color,
    title: String,
    selectLabel: String,
    cancelLabel: String,
    defaultLabel: String,
    onDismiss: () -> Unit,
) {
    val sounds = remember { vm.loadAlarmSounds() }
    val previewVol = vm.alarmFor(scope).volume
    var selectedUri by remember { mutableStateOf(vm.alarmFor(scope).soundUri) }

    fun stopAndDismiss() {
        vm.stopPreview()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = { stopAndDismiss() },
        containerColor = AppTheme.colors.surface,
        title = { Text(title, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold) },
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
                                vm.previewTone(sound.uri, previewVol)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = {
                                selectedUri = sound.uri
                                vm.previewTone(sound.uri, previewVol)
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = accent,
                                unselectedColor = Color(0xFF9AA0A4),
                            ),
                        )
                        Text(
                            sound.name,
                            color = AppTheme.colors.textPrimary,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { vm.previewTone(sound.uri, previewVol) }) {
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
                    vm.setAlarm(scope, vm.alarmFor(scope).copy(soundUri = selectedUri, soundName = name))
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

/**
 * Bloque de alarma reutilizable e INDEPENDIENTE por pestaña. Lee y escribe la
 * [AlarmConfig] de [scope] vía [vm]; el preview suena EXACTAMENTE como la alarma
 * real de esa pestaña ("lo que pruebas es lo que suena").
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AlarmSection(
    vm: TimerViewModel,
    scope: AlarmScope,
    t: Strings,
    accent: Color,
    onPickSound: () -> Unit,
) {
    val alarm = vm.alarmFor(scope)
    SettingsGroup(t.groupAlarm, accent) {
        ItemLabel(t.alarmSound)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AppTheme.colors.track)
                .clickable { onPickSound() }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                alarm.soundName ?: t.defaultSound,
                color = AppTheme.colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = AppTheme.colors.textDim,
            )
        }
        GroupDivider()
        val volPct = (alarm.volume * 100).roundToInt()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                t.alarmVolume,
                color = AppTheme.colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            AppStepButton("−", accent, enabled = volPct > 0) {
                vm.setAlarm(scope, alarm.copy(volume = ((volPct - 5).coerceAtLeast(0)) / 100f))
                vm.previewVolume(vm.alarmFor(scope))
            }
            Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) {
                Text("$volPct%", color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
            AppStepButton("+", accent, enabled = volPct < 100) {
                vm.setAlarm(scope, alarm.copy(volume = ((volPct + 5).coerceAtMost(100)) / 100f))
                vm.previewVolume(vm.alarmFor(scope))
            }
        }
        GroupDivider()
        ItemLabel(t.headsetTitle)
        RadioRow(
            label = t.headsetBoth,
            selected = alarm.headsetMode == SPEAKER_AND_HEADSET,
            accent = accent,
        ) { vm.setAlarm(scope, alarm.copy(headsetMode = SPEAKER_AND_HEADSET)) }
        RadioRow(
            label = t.headsetOnly,
            selected = alarm.headsetMode == HEADSET_ONLY,
            accent = accent,
        ) { vm.setAlarm(scope, alarm.copy(headsetMode = HEADSET_ONLY)) }
        GroupDivider()
        SwitchRow(
            label = t.ignoreSilent,
            desc = t.ignoreSilentDesc,
            checked = alarm.ignoreSilent,
            accent = accent,
            onCheckedChange = { vm.setAlarm(scope, alarm.copy(ignoreSilent = it)) },
        )
        GroupDivider()
        SwitchRow(
            label = t.vibration,
            desc = null,
            checked = alarm.vibrationEnabled,
            accent = accent,
            onCheckedChange = { vm.setAlarm(scope, alarm.copy(vibrationEnabled = it)) },
        )
        if (alarm.vibrationEnabled) {
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VIBRATION_PATTERNS.forEachIndexed { index, pattern ->
                    Chip(
                        pattern.name,
                        alarm.vibrationPattern == index,
                        accent,
                    ) {
                        vm.setAlarm(scope, alarm.copy(vibrationPattern = index))
                        vm.previewVibration(index)
                    }
                }
            }
        }
    }
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
            color = AppTheme.colors.textPrimary,
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
            color = AppTheme.colors.textPrimary,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        AppStepButton("−", accent) { onMinus() }
        Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) {
            Text("$value", color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        AppStepButton("+", accent) { onPlus() }
    }
}

/** Lista principal de categorías, agrupada por mini-app (lista + subpantalla). */
@Composable
private fun SettingsCategoryList(
    vm: TimerViewModel,
    c: AppConfig,
    t: Strings,
    accent: Color,
    root: SettingsRoot,
    folderName: String?,
) {
    when (root) {
        SettingsRoot.GENERAL -> {
            val langName = if (c.general.language == "es") "Español" else "English"
            val themeName = when (c.general.themeMode) {
                THEME_LIGHT -> t.themeLight
                THEME_DARK -> t.themeDark
                else -> t.themeAuto
            }
            CategoryHeader(t.groupGeneral, accent)
            CategoryCard {
                SettingsCategoryRow(
                    icon = Icons.Filled.Palette,
                    title = t.groupAppearance,
                    subtitle = "$langName · $themeName",
                    accent = accent,
                    first = true,
                ) { vm.settingsSection = SettingsSection.APPEARANCE }
                SettingsCategoryRow(
                    icon = Icons.Filled.Backup,
                    title = t.groupBackup,
                    subtitle = folderName ?: t.backupNotSet,
                    accent = accent,
                ) { vm.settingsSection = SettingsSection.BACKUP }
                SettingsCategoryRow(
                    icon = Icons.Filled.Code,
                    title = t.groupDeveloper,
                    subtitle = if (c.general.developerMode) t.on else t.off,
                    accent = accent,
                ) { vm.settingsSection = SettingsSection.DEVELOPER }
            }

            Spacer(Modifier.height(28.dp))
            TextButton(
                onClick = { vm.resetSettings() },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = DONE_RED),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally),
            ) {
                Text(t.reset, fontWeight = FontWeight.SemiBold)
            }
        }

        SettingsRoot.TIMER -> {
            val autoDismissLabel = if (c.timer.autoDismiss == 0) t.off else "${c.timer.autoDismiss}s"
            val overlaySummary = listOfNotNull(
                if (c.timer.showNowBar) t.showNowBar else null,
                if (c.timer.showOverlay) t.showOverlay else null,
                if (c.timer.showRing) t.showRing else null,
            ).joinToString(" · ").ifBlank { t.off }
            CategoryHeader(t.groupTimer, accent)
            CategoryCard {
                SettingsCategoryRow(
                    icon = Icons.Filled.Timer,
                    title = t.groupTimer,
                    subtitle = "${t.autoDismiss}: $autoDismissLabel · ${incLabel(c.timer.addIncrementSec)}",
                    accent = accent,
                    first = true,
                ) { vm.settingsSection = SettingsSection.TIMER }
                SettingsCategoryRow(
                    icon = Icons.Filled.Layers,
                    title = t.groupOverlay,
                    subtitle = overlaySummary,
                    accent = accent,
                ) { vm.settingsSection = SettingsSection.OVERLAY }
            }
        }

    }
}

/** Encabezado de un grupo de categorías en la lista de Ajustes. */
@Composable
private fun CategoryHeader(text: String, accent: Color) {
    Text(
        text,
        color = accent,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** Tarjeta contenedora de filas de categoría (Material 3). */
@Composable
private fun CategoryCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dims.card))
            .background(AppTheme.colors.surface),
        content = content,
    )
}

/** Fila de categoría en la lista principal de Ajustes. */
@Composable
private fun SettingsCategoryRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    first: Boolean = false,
    onClick: () -> Unit,
) {
    if (!first) {
        HorizontalDivider(
            color = AppTheme.colors.track,
            thickness = 1.dp,
            modifier = Modifier.padding(start = 70.dp),
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = AppTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                color = AppTheme.colors.textDim,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = AppTheme.colors.textDim,
        )
    }
}

/** Encabezado de grupo + contenedor (card) estilo Material 3. */
@Composable
internal fun SettingsGroup(
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
            .clip(RoundedCornerShape(Dims.card))
            .background(AppTheme.colors.surface)
            .padding(16.dp),
        content = content,
    )
}

/** Etiqueta de un ítem dentro de un grupo. */
@Composable
internal fun ItemLabel(text: String) {
    Text(
        text,
        color = AppTheme.colors.textPrimary,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

/** Divisor entre ítems de un mismo grupo. */
@Composable
internal fun GroupDivider() {
    HorizontalDivider(
        color = AppTheme.colors.track,
        thickness = 1.dp,
        modifier = Modifier.padding(vertical = 16.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Chip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        },
        shape = RoundedCornerShape(20.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = AppTheme.colors.track,
            labelColor = AppTheme.colors.textDim,
            selectedContainerColor = accent,
            selectedLabelColor = AppTheme.colors.onAccent,
        ),
        border = null,
    )
}
