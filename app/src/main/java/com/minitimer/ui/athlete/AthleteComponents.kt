package com.minitimer.ui.athlete

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minitimer.data.ExerciseIcons
import com.minitimer.ui.theme.ON_ACCENT
import com.minitimer.ui.theme.SURFACE
import com.minitimer.ui.theme.TEXT_DIM
import com.minitimer.ui.theme.TRACK
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
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) accent else TRACK)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) ON_ACCENT else TEXT_DIM,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
    }
}

@Composable
internal fun AddButton(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, TRACK, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("+  $label", color = accent, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

/** Stepper "− valor +" con etiqueta y formateo configurable. */
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
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        StepCircle("−", accent) { onChange((value - step).coerceAtLeast(min)) }
        Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) {
            Text(format(value), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        StepCircle("+", accent) { onChange((value + step).coerceAtMost(max)) }
    }
}

/**
 * Stepper de duración (segundos) con +/- y, al tocar el valor, un diálogo para
 * escribir minutos:segundos directamente (evita decenas de taps para llegar a
 * tiempos largos como 30 min).
 */
@Composable
internal fun DurationStepper(
    label: String,
    value: Int,
    accent: Color,
    dialogTitle: String,
    minLabel: String,
    secLabel: String,
    cancelLabel: String,
    okLabel: String,
    modifier: Modifier = Modifier,
    min: Int = 0,
    max: Int = 36000,
    step: Int = 5,
    onChange: (Int) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        StepCircle("−", accent) { onChange((value - step).coerceAtLeast(min)) }
        Box(
            modifier = Modifier
                .width(72.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable { editing = true }
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(fmtSec(value), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        StepCircle("+", accent) { onChange((value + step).coerceAtMost(max)) }
    }
    if (editing) {
        DurationDialog(
            value = value, min = min, max = max, accent = accent,
            title = dialogTitle, minLabel = minLabel, secLabel = secLabel,
            cancelLabel = cancelLabel, okLabel = okLabel,
            onDismiss = { editing = false },
            onConfirm = { onChange(it); editing = false },
        )
    }
}

@Composable
private fun DurationDialog(
    value: Int,
    min: Int,
    max: Int,
    accent: Color,
    title: String,
    minLabel: String,
    secLabel: String,
    cancelLabel: String,
    okLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var mins by remember { mutableStateOf((value / 60).toString()) }
    var secs by remember { mutableStateOf((value % 60).toString()) }
    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = accent,
        unfocusedBorderColor = TRACK,
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        cursorColor = accent,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SURFACE,
        titleContentColor = Color.White,
        title = { Text(title) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = mins,
                    onValueChange = { mins = it.filter(Char::isDigit).take(3) },
                    label = { Text(minLabel, color = TEXT_DIM) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = colors,
                    modifier = Modifier.weight(1f),
                )
                Text(" : ", color = Color.White, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = secs,
                    onValueChange = { secs = it.filter(Char::isDigit).take(2) },
                    label = { Text(secLabel, color = TEXT_DIM) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = colors,
                    modifier = Modifier.weight(1f),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val total = (mins.toIntOrNull() ?: 0) * 60 + (secs.toIntOrNull() ?: 0)
                onConfirm(total.coerceIn(min, max))
            }) { Text(okLabel, color = accent, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(cancelLabel, color = TEXT_DIM) }
        },
    )
}

@Composable
private fun StepCircle(symbol: String, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(TRACK)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = accent, fontWeight = FontWeight.Bold, fontSize = 22.sp)
    }
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
            .background(TRACK)
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
                    color = if (active) ON_ACCENT else TEXT_DIM,
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
            .clip(RoundedCornerShape(18.dp))
            .background(SURFACE)
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
