package com.minitimer.ui

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minitimer.Phase
import com.minitimer.TimerViewModel
import com.minitimer.i18n.I18n
import com.minitimer.ui.theme.BG
import com.minitimer.ui.theme.DONE_RED
import com.minitimer.ui.theme.ON_ACCENT
import com.minitimer.ui.theme.SURFACE
import com.minitimer.ui.theme.TEXT_DIM
import com.minitimer.ui.theme.TEXT_FADED
import com.minitimer.ui.theme.TRACK
import com.minitimer.ui.theme.JetBrainsMono
import com.minitimer.util.formatClock
import com.minitimer.util.formatRemaining
import com.minitimer.util.pad2

private val KEYS = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf("00", "0", "del"),
)

@Composable
fun TimerApp(vm: TimerViewModel, requestOverlayPermission: () -> Unit) {
    val t = I18n.get(vm.settings.language)
    val accent = Color(vm.settings.accent)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .padding(horizontal = 24.dp)
    ) {
        when {
            vm.showSettings -> SettingsScreen(vm, requestOverlayPermission)
            vm.phase == Phase.SETUP -> SetupScreen(vm, accent, t)
            else -> CountdownScreen(vm, accent, t)
        }
    }
}

@Composable
private fun SetupScreen(vm: TimerViewModel, accent: Color, t: com.minitimer.i18n.Strings) {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopBar(title = t.title, onSettings = { vm.showSettings = true })

        Spacer(Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            TimePart(pad2(vm.setH), "h", vm.setH > 0, accent)
            TimePart(pad2(vm.setM), "m", vm.setM > 0 || vm.setH > 0, accent)
            TimePart(pad2(vm.setS), "s", true, accent)
        }

        Spacer(Modifier.height(24.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(vm.settings.presets) { sec ->
                PresetChip(sec, accent) { vm.startWithSeconds(sec) }
            }
        }

        Spacer(Modifier.height(16.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            KEYS.forEach { row ->
                Row(
                    modifier = Modifier.widthIn(max = 340.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    row.forEach { key -> KeyButton(key) { vm.onKey(key) } }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(12.dp))

        val canStart = vm.setH * 3600 + vm.setM * 60 + vm.setS > 0
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(32.dp))
                .background(if (canStart) accent else TRACK)
                .clickable(enabled = canStart) { vm.start() }
                .padding(horizontal = 48.dp, vertical = 16.dp),
        ) {
            Text(t.start, color = ON_ACCENT, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CountdownScreen(vm: TimerViewModel, accent: Color, t: com.minitimer.i18n.Strings) {
    val isDone = vm.phase == Phase.DONE
    val progress = if (vm.totalMs > 0) (vm.remainingMs.toFloat() / vm.totalMs).coerceIn(0f, 1f) else 0f
    val endEpoch = System.currentTimeMillis() + vm.remainingMs

    Column(
        modifier = Modifier.fillMaxSize().padding(top = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(t.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)

        Spacer(Modifier.height(36.dp))

        ProgressRing(progress = if (isDone) 0f else progress, accent = if (isDone) DONE_RED else accent) {
            if (isDone) {
                Text(t.timeUp, color = DONE_RED, fontSize = 40.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Text(
                    formatRemaining(vm.remainingMs),
                    color = Color.White,
                    fontSize = 52.sp,
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Light,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (vm.phase == Phase.PAUSED) t.paused
                    else "${t.endsAt} ${formatClock(endEpoch, t.locale)}",
                    color = TEXT_DIM,
                    fontSize = 14.sp,
                )
            }
        }

        Spacer(Modifier.height(48.dp))

        Row(
            modifier = Modifier.widthIn(max = 340.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (isDone) {
                ControlButton(t.dismiss, Modifier.weight(1f), accent, muted = true) { vm.dismiss() }
                ControlButton(t.restart, Modifier.weight(1f), accent) { vm.restart() }
            } else {
                ControlButton(t.cancel, Modifier.weight(1f), accent, muted = true) { vm.cancel() }
                if (vm.phase == Phase.RUNNING) {
                    ControlButton(t.pause, Modifier.weight(1f), accent) { vm.pause() }
                } else {
                    ControlButton(t.resume, Modifier.weight(1f), accent) { vm.resume() }
                }
            }
        }
    }
}

@Composable
private fun ProgressRing(progress: Float, accent: Color, content: @Composable () -> Unit) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(300.dp)) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(300.dp)) {
            val stroke = 14.dp.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = TRACK,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
fun TopBar(title: String, onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Spacer(Modifier.size(40.dp))
        Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).clickable { onSettings() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = TEXT_DIM)
        }
    }
}

@Composable
private fun TimePart(value: String, unit: String, active: Boolean, accent: Color) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(horizontal = 6.dp)) {
        Text(
            value,
            color = if (active) Color.White else TEXT_FADED,
            fontSize = 56.sp,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Light,
        )
        Text(
            unit,
            color = if (active) accent else TEXT_FADED,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 2.dp, bottom = 10.dp),
        )
    }
}

@Composable
private fun PresetChip(sec: Int, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SURFACE)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            formatRemaining(sec * 1000L),
            color = accent,
            fontSize = 15.sp,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun KeyButton(key: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(96.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (key == "del") "\u232B" else key,
            color = Color.White,
            fontSize = 30.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ControlButton(
    label: String,
    modifier: Modifier = Modifier,
    accent: Color,
    muted: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .background(if (muted) SURFACE else accent)
            .clickable { onClick() }
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (muted) Color.White else ON_ACCENT,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
