package com.minitimer.ui.athlete

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minitimer.data.ExerciseIcons
import com.minitimer.i18n.Strings
import com.minitimer.ui.AppOutlineButton
import com.minitimer.ui.AppPrimaryButton
import com.minitimer.ui.AppStepper
import com.minitimer.ui.WheelTimePicker
import com.minitimer.ui.theme.AppTheme
import com.minitimer.ui.theme.Dims
import com.minitimer.util.pad2

/** Paleta de colores para etapas (ARGB Long), igual orden que en los mocks. */
internal val STAGE_COLORS: List<Long> = listOf(
    0xFFE2641EL, 0xFFEFAA2AL, 0xFF2E9E5BL, 0xFF159E8CL,
    0xFF1565C0L, 0xFF5E48C8L, 0xFFB4318FL, 0xFFC0392BL,
    0xFF455A64L, 0xFF6D4C41L,
)

internal fun fmtSec(s: Int): String = if (s < 60) "${s}s" else "${s / 60}:${pad2(s % 60)}"

internal fun fmtKg(d: Double): String {
    val r = (d * 10).toLong()
    return if (r % 10 == 0L) (r / 10).toString() else (r / 10.0).toString()
}

@Composable
internal fun PrimaryButton(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) = AppPrimaryButton(label = label, accent = accent, modifier = modifier, enabled = enabled, onClick = onClick)

@Composable
internal fun AddButton(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) = AppOutlineButton(label = "+  $label", accent = accent, modifier = modifier, onClick = onClick)

@Composable
internal fun Stepper(
    label: String,
    value: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    min: Int = 0,
    max: Int = 3600,
    step: Int = 1,
    format: (Int) -> String = { it.toString() },
    onChange: (Int) -> Unit,
) = AppStepper(label, value, accent, modifier, min, max, step, format, onChange)

@Composable
internal fun DurationWheelField(
    label: String,
    value: Int,
    accent: Color,
    t: Strings,
    modifier: Modifier = Modifier,
    min: Int = 0,
    max: Int = 36000,
    onChange: (Int) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = AppTheme.colors.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(AppTheme.colors.track)
                .clickable { editing = true }
                .padding(horizontal = 18.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(fmtSec(value), color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
    }
    if (editing) {
        DurationWheelDialog(
            value = value, min = min, max = max, accent = accent, t = t,
            onDismiss = { editing = false },
            onConfirm = { onChange(it); editing = false },
        )
    }
}

@Composable
private fun DurationWheelDialog(
    value: Int,
    min: Int,
    max: Int,
    accent: Color,
    t: Strings,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var h by remember { mutableStateOf(value / 3600) }
    var m by remember { mutableStateOf((value % 3600) / 60) }
    var s by remember { mutableStateOf(value % 60) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppTheme.colors.surface,
        titleContentColor = AppTheme.colors.textPrimary,
        title = { Text(t.durationTitle) },
        text = {
            WheelTimePicker(
                h = h, m = m, s = s, accent = accent, t = t,
                onChange = { nh, nm, ns -> h = nh; m = nm; s = ns },
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm((h * 3600 + m * 60 + s).coerceIn(min, max))
            }) { Text(t.save, color = accent, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t.cancel, color = AppTheme.colors.textDim) }
        },
    )
}

/** Conjunto de chips tipo segmented control. */
@Composable
internal fun SegmentToggle(
    options: List<Pair<String, String>>,
    selected: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppTheme.colors.track)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (key, text) ->
            val active = key == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) accent else Color.Transparent)
                    .clickable { onSelect(key) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text,
                    color = if (active) AppTheme.colors.onAccent else AppTheme.colors.textDim,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
internal fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dims.card))
            .background(AppTheme.colors.surface)
            .padding(16.dp),
    ) { content() }
}

@Composable
internal fun ColorDot(color: Long, size: Int = 18, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color(color)),
    )
}

@Composable
internal fun ExerciseGlyph(name: String, color: Long, sizeDp: Int = 44, exerciseId: String = "") {
    val emoji = ExerciseIcons.emoji(exerciseId, name)
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(color).copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        if (emoji != null) {
            Text(emoji, fontSize = (sizeDp / 1.9).sp)
        } else {
            Text(
                name.trim().take(1).uppercase().ifBlank { "?" },
                color = Color(color),
                fontWeight = FontWeight.Bold,
                fontSize = (sizeDp / 2.2).sp,
            )
        }
    }
}

@Composable
internal fun VSpace(h: Int) = Spacer(Modifier.height(h.dp))
