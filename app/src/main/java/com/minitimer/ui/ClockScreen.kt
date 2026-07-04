package com.minitimer.ui

import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.minitimer.i18n.I18n
import com.minitimer.model.OSD_TEXT_COLORS
import com.minitimer.model.OSD_TEXT_SIZE_MAX
import com.minitimer.model.OSD_TEXT_SIZE_MIN
import com.minitimer.model.OSD_TEXT_SIZE_STEP
import com.minitimer.model.OsdPanel
import com.minitimer.ui.theme.AppTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

    // Reloj en vivo para la vista previa (se actualiza cada segundo).
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1_000)
        }
    }

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

        // Mantener activo: excluir de optimización de batería.
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
                if (ignoringBattery) {
                    Text(t.clockBattExcluded, color = accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                } else {
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

        // Vista previa en vivo.
        Text(
            "${t.clockPreview} · ${t.clockPanel} ${panelIndex + 1}",
            color = accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
        )
        OsdPreview(panel, nowMs, t.locale, context)

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
                ItemLabel(t.clockTextColor)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OSD_TEXT_COLORS.forEach { col ->
                        val selected = panel.textColor == col
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(col))
                                .then(
                                    if (selected) {
                                        Modifier.border(2.dp, AppTheme.colors.textPrimary, CircleShape)
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable { upd(panel.copy(textColor = col)) },
                        )
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

        // Posición: offsets finos por orientación (sin arrastre). Cada orientación
        // (vertical/horizontal) guarda su propio ajuste; +X derecha, +Y abajo.
        SettingsGroup(t.clockPosition, accent) {
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
    }
}

/** Vista previa fiel del OSD: mismo contenido/tamaño/color que se pintará. */
@Composable
private fun OsdPreview(
    panel: OsdPanel,
    nowMs: Long,
    locale: Locale,
    context: android.content.Context,
) {
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val battery = remember { context.getSystemService(BatteryManager::class.java) }
    val vol = audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
    val batt = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
    val charging = previewChargingLabel(context)
    val sizeSp = panel.textSizeSp.sp
    val textColor = if (panel.autoDarkColor) {
        if (isSystemInDarkTheme()) Color.White else Color.Black
    } else {
        Color(panel.textColor)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF14181B))
            .padding(vertical = 20.dp, horizontal = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = panel.bgAlpha.coerceIn(0f, 1f)))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                osdMainText(panel, nowMs, vol, batt, charging, locale),
                color = textColor,
                fontSize = sizeSp,
                fontWeight = FontWeight.SemiBold,
            )
            if (panel.showMemo && panel.memo.isNotBlank()) {
                Text(
                    panel.memo,
                    color = textColor,
                    fontSize = (sizeSp.value - 3f).coerceAtLeast(10f).sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Etiqueta de carga para la vista previa ([AC]/[USB]/[Wireless]); null si no carga. */
private fun previewChargingLabel(context: android.content.Context): String? {
    val plugged = context.registerReceiver(
        null,
        android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED),
    )?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
    return when (plugged) {
        BatteryManager.BATTERY_PLUGGED_AC -> "[AC]"
        BatteryManager.BATTERY_PLUGGED_USB -> "[USB]"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "[Wireless]"
        else -> null
    }
}

/** Construye la línea principal del OSD (fecha, hora, [carga], [volumen], [batería]). */
private fun osdMainText(panel: OsdPanel, nowMs: Long, vol: Int, batt: Int, charging: String?, locale: Locale): String {
    val parts = mutableListOf<String>()
    if (panel.showDate) parts += SimpleDateFormat("EEE d MMM", locale).format(Date(nowMs))
    if (panel.showTime) {
        val pattern = when {
            panel.use24h && panel.showSeconds -> "H:mm:ss"
            panel.use24h -> "H:mm"
            panel.showSeconds -> "h:mm:ss"
            else -> "h:mm"
        }
        parts += SimpleDateFormat(pattern, locale).format(Date(nowMs))
    }
    if (panel.showCharging && charging != null) parts += charging
    if (panel.showVolume) parts += "[$vol]"
    if (panel.showBattery) parts += "[$batt%]"
    return parts.joinToString("  ").ifBlank { " " }
}
