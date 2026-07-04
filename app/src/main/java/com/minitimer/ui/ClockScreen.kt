package com.minitimer.ui

import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minitimer.TimerViewModel
import com.minitimer.notify.ClockAlignAccessibilityService
import com.minitimer.i18n.I18n
import com.minitimer.model.OSD_TEXT_COLORS
import com.minitimer.model.OSD_TEXT_SIZE_MAX
import com.minitimer.model.OSD_TEXT_SIZE_MIN
import com.minitimer.model.OSD_TEXT_SIZE_STEP
import com.minitimer.model.OsdPanel
import com.minitimer.ui.athlete.ColorDot
import com.minitimer.ui.theme.AppTheme
import kotlin.math.roundToInt

private const val MEMO_MAX = 40

/**
 * Pestaña Reloj: panel de control del OSD flotante. Dos paneles independientes
 * (selector Panel 1 / Panel 2); cada uno con su on/off, contenido, apariencia y
 * posición. "Lo que ves aquí es lo que se pinta encima de otras apps."
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockScreen(vm: TimerViewModel) {
    val c = vm.config
    val t = I18n.get(c.general.language)
    val accent = AppTheme.colors.accent
    val context = LocalContext.current

    var panelIndex by remember { mutableIntStateOf(0) }
    val panel = c.clock.panel(panelIndex)
    fun upd(p: OsdPanel) = vm.setClockPanel(panelIndex, p)

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // Al volver de Ajustes, re-sincroniza para que el servicio tome el permiso.
        vm.setClockPanel(panelIndex, vm.config.clock.panel(panelIndex))
    }
    fun requestOverlay() {
        runCatching {
            overlayLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        }
    }
    val hasOverlay = Settings.canDrawOverlays(context)

    // Exclusión de optimización de batería (para que el sistema no mate el OSD).
    var battTick by remember { mutableIntStateOf(0) }
    val battLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { battTick++ }
    val powerManager = remember { context.getSystemService(PowerManager::class.java) }
    val ignoringBattery = battTick.let {
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    }
    fun requestBatteryExclusion() {
        runCatching {
            battLauncher.launch(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        }
    }

    // Accesibilidad: necesaria para alinear el panel con el reloj del sistema.
    var accTick by remember { mutableIntStateOf(0) }
    val accLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { accTick++ }
    val accessibilityOn = accTick.let { isClockAlignAccessibilityEnabled(context) }
    fun openAccessibilitySettings() {
        runCatching { accLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 48.dp),
    ) {
        // Aviso de permiso (solo si falta).
        if (!hasOverlay) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.16f))
                    .clickable { requestOverlay() }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    t.clockPermNeeded,
                    color = AppTheme.colors.textPrimary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(t.grant, color = accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(4.dp))
        }

        // Selector de panel (Panel 1 / Panel 2).
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for (i in 0..1) {
                val selected = panelIndex == i
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) accent else AppTheme.colors.surface)
                        .clickable { panelIndex = i }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val on = c.clock.panel(i).enabled
                    Text(
                        "${t.clockPanel} ${i + 1}" + if (on) " ●" else "",
                        color = if (selected) AppTheme.colors.onAccent else AppTheme.colors.textDim,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                }
            }
        }

        // Toggle maestro del panel seleccionado.
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(AppTheme.colors.surface)
                .padding(16.dp),
        ) {
            SwitchRow(
                label = t.clockShow,
                desc = t.clockShowDesc,
                checked = panel.enabled,
                accent = accent,
                onCheckedChange = { on ->
                    if (on && !hasOverlay) requestOverlay()
                    upd(panel.copy(enabled = on))
                },
            )
        }

        // Posición: offsets finos por orientación (sin arrastre). Cada orientación
        // (vertical/horizontal) guarda su propio ajuste; +X derecha, +Y abajo.
        SettingsGroup(t.clockPosition, accent) {
            // Alinear con el reloj del sistema (solo para paneles con hora).
            if (panel.showTime) {
                SwitchRow(
                    label = t.clockAlign,
                    desc = t.clockAlignDesc,
                    checked = panel.alignToSystemClock,
                    accent = accent,
                    onCheckedChange = { on ->
                        if (on && !accessibilityOn) openAccessibilitySettings()
                        upd(panel.copy(alignToSystemClock = on))
                    },
                )
                if (panel.alignToSystemClock && !accessibilityOn) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            t.clockAlignAccNeeded,
                            color = AppTheme.colors.textDim,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(accent)
                                .clickable { openAccessibilitySettings() }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                t.clockAlignEnable,
                                color = AppTheme.colors.onAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                        }
                    }
                } else if (panel.alignToSystemClock) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        t.clockAlignActive,
                        color = accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                GroupDivider()
            }
            val portrait = LocalConfiguration.current.orientation !=
                Configuration.ORIENTATION_LANDSCAPE
            val (offX, offY) = vm.clockOffset(panelIndex, portrait)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t.clockPosOrientation,
                    color = AppTheme.colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (portrait) t.clockPosPortrait else t.clockPosLandscape,
                    color = accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(t.clockPosHint, color = AppTheme.colors.textDim, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("X", color = AppTheme.colors.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                AppStepButton("−", accent, enabled = panel.enabled) {
                    vm.nudgeClockPanel(panelIndex, portrait, -1, 0)
                }
                Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) {
                    Text("$offX", color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                AppStepButton("+", accent, enabled = panel.enabled) {
                    vm.nudgeClockPanel(panelIndex, portrait, 1, 0)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Y", color = AppTheme.colors.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                AppStepButton("−", accent, enabled = panel.enabled) {
                    vm.nudgeClockPanel(panelIndex, portrait, 0, -1)
                }
                Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) {
                    Text("$offY", color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                AppStepButton("+", accent, enabled = panel.enabled) {
                    vm.nudgeClockPanel(panelIndex, portrait, 0, 1)
                }
            }
            if (panel.enabled && (offX != 0 || offY != 0)) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent)
                        .clickable { vm.resetClockPanel(panelIndex, portrait) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        t.clockResetPos,
                        color = AppTheme.colors.onAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        // Mantener activo: excluir de optimización de batería.
        // Una vez concedido el permiso, el grupo se desvanece (fade + encoger +
        // escalar), como el "no me quiero ir, señor Stark".
        AnimatedVisibility(
            visible = !ignoringBattery,
            exit = fadeOut(tween(800)) +
                scaleOut(targetScale = 0.8f, animationSpec = tween(800)) +
                shrinkVertically(tween(800)),
        ) {
            SettingsGroup(t.clockKeepAlive, accent) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            t.clockBattOpt,
                            color = AppTheme.colors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(t.clockBattOptDesc, color = AppTheme.colors.textDim, fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(accent)
                            .clickable { requestBatteryExclusion() }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(t.clockBattExclude, color = AppTheme.colors.onAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }

        // Contenido.
        SettingsGroup(t.clockContent, accent) {
            SwitchRow(t.clockTime, null, panel.showTime, accent) { upd(panel.copy(showTime = it)) }
            GroupDivider()
            SwitchRow(t.clockSecondsDesc, null, panel.showSeconds, accent) { upd(panel.copy(showSeconds = it)) }
            GroupDivider()
            SwitchRow(t.clockVolume, t.clockVolumeDesc, panel.showVolume, accent) { upd(panel.copy(showVolume = it)) }
            GroupDivider()
            SwitchRow(t.clockBattery, t.clockBatteryDesc, panel.showBattery, accent) { upd(panel.copy(showBattery = it)) }
            GroupDivider()
            SwitchRow(t.clockCharging, t.clockChargingDesc, panel.showCharging, accent) { upd(panel.copy(showCharging = it)) }
            GroupDivider()
            SwitchRow(t.clock24h, null, panel.use24h, accent) { upd(panel.copy(use24h = it)) }
            GroupDivider()
            SwitchRow(t.clockDate, null, panel.showDate, accent) { upd(panel.copy(showDate = it)) }
            GroupDivider()
            SwitchRow(t.clockMemo, null, panel.showMemo, accent) { upd(panel.copy(showMemo = it)) }
            if (panel.showMemo) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = panel.memo,
                    onValueChange = { if (it.length <= MEMO_MAX) upd(panel.copy(memo = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(t.clockMemo) },
                    supportingText = { Text("${panel.memo.length}/$MEMO_MAX") },
                    keyboardOptions = KeyboardOptions.Default,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = AppTheme.colors.track,
                        focusedLabelColor = accent,
                        cursorColor = accent,
                        focusedTextColor = AppTheme.colors.textPrimary,
                        unfocusedTextColor = AppTheme.colors.textPrimary,
                    ),
                )
            }
        }

        // Apariencia.
        SettingsGroup(t.groupAppearance, accent) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    t.clockSize,
                    color = AppTheme.colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                AppStepButton("−", accent, enabled = panel.textSizeSp > OSD_TEXT_SIZE_MIN) {
                    upd(panel.copy(textSizeSp = (panel.textSizeSp - OSD_TEXT_SIZE_STEP).coerceAtLeast(OSD_TEXT_SIZE_MIN)))
                }
                Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) {
                    Text("${panel.textSizeSp}pt", color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                AppStepButton("+", accent, enabled = panel.textSizeSp < OSD_TEXT_SIZE_MAX) {
                    upd(panel.copy(textSizeSp = (panel.textSizeSp + OSD_TEXT_SIZE_STEP).coerceAtMost(OSD_TEXT_SIZE_MAX)))
                }
            }
            GroupDivider()
            SwitchRow(t.clockDarkMode, t.clockDarkModeDesc, panel.autoDarkColor, accent) {
                upd(panel.copy(autoDarkColor = it))
            }
            if (!panel.autoDarkColor) {
                GroupDivider()
                var showColors by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        t.clockTextColor,
                        color = AppTheme.colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    ColorDot(
                        color = panel.textColor,
                        size = 26,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { showColors = true },
                    )
                }
                if (showColors) {
                    ModalBottomSheet(
                        onDismissRequest = { showColors = false },
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                        containerColor = AppTheme.colors.bg,
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(5),
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(OSD_TEXT_COLORS) { col ->
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(col))
                                        .clickable { upd(panel.copy(textColor = col)); showColors = false },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (col == panel.textColor) {
                                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
            GroupDivider()
            val transpPct = ((1f - panel.bgAlpha) * 100).roundToInt()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    t.clockTransparency,
                    color = AppTheme.colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                AppStepButton("−", accent, enabled = transpPct > 0) {
                    val v = (transpPct - 10).coerceAtLeast(0)
                    upd(panel.copy(bgAlpha = 1f - v / 100f))
                }
                Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) {
                    Text("$transpPct%", color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                AppStepButton("+", accent, enabled = transpPct < 100) {
                    val v = (transpPct + 10).coerceAtMost(100)
                    upd(panel.copy(bgAlpha = 1f - v / 100f))
                }
            }
        }
    }
}

/** ¿Está activo el servicio de accesibilidad de alineación de Mini Timer? */
private fun isClockAlignAccessibilityEnabled(context: android.content.Context): Boolean {
    val expected = ComponentName(context, ClockAlignAccessibilityService::class.java)
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    return enabled.split(':').any {
        ComponentName.unflattenFromString(it) == expected
    }
}
