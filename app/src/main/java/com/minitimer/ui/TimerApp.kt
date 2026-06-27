package com.minitimer.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerApp(vm: TimerViewModel) {
    val t = I18n.get(vm.settings.language)
    val accent = Color(vm.settings.accent)

    // Mantener la pantalla encendida siempre que la app esté abierta (en primer plano).
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    BackHandler(enabled = vm.showSettings) { vm.showSettings = false }

    Scaffold(
        containerColor = BG,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (vm.showSettings) t.settings else t.title,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    if (vm.showSettings) {
                        IconButton(onClick = { vm.showSettings = false }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                            )
                        }
                    }
                },
                actions = {
                    if (!vm.showSettings && vm.phase == Phase.SETUP) {
                        IconButton(onClick = { vm.showSettings = true }) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = "Settings",
                                tint = TEXT_DIM,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 24.dp)
        ) {
            when {
                vm.showSettings -> SettingsScreen(vm)
                vm.phase == Phase.SETUP -> SetupScreen(vm, accent, t)
                else -> CountdownScreen(vm, accent, t)
            }
        }
    }
}

@Composable
private fun SetupScreen(vm: TimerViewModel, accent: Color, t: com.minitimer.i18n.Strings) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))

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
        Button(
            onClick = { vm.start() },
            enabled = canStart,
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accent,
                contentColor = ON_ACCENT,
                disabledContainerColor = TRACK,
                disabledContentColor = TEXT_DIM,
            ),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
        ) {
            Text(t.start, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CountdownScreen(vm: TimerViewModel, accent: Color, t: com.minitimer.i18n.Strings) {
    val isDone = vm.phase == Phase.DONE
    val progress = if (vm.totalMs > 0) (vm.remainingMs.toFloat() / vm.totalMs).coerceIn(0f, 1f) else 0f
    val endEpoch = System.currentTimeMillis() + vm.remainingMs

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))

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

        // Stickman animado (jumping jacks) mientras el timer corre; se congela en
        // pausa y se oculta al terminar.
        if (!isDone) {
            Spacer(Modifier.height(40.dp))
            StickmanJumpingJacks(
                accent = accent,
                running = vm.phase == Phase.RUNNING,
                modifier = Modifier.size(140.dp),
            )
        }
    }
}

/**
 * Stickman haciendo "jumping jacks", al estilo de los iconos de ejercicio de
 * Samsung Health ("My exercises"): figura clara de trazo grueso con extremos
 * redondeados, cabeza sólida y articulaciones (codos/rodillas) sobre un fondo
 * circular con gradiente del color de acento. La animación avanza solo mientras
 * [running] es true (se congela en pausa). Sin dependencias ni assets.
 */
@Composable
private fun StickmanJumpingJacks(
    accent: Color,
    running: Boolean,
    modifier: Modifier = Modifier,
) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = 0L
        while (true) {
            val now = withFrameNanos { it }
            if (last != 0L) t += (now - last) / 1_000_000_000f
            last = now
        }
    }
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val theta = t * (2f * PI.toFloat()) * 1.2f
        // p: 0 = posición cerrada (brazos abajo, piernas juntas), 1 = abierta.
        val p = (1f - cos(theta)) / 2f
        val hop = -h * 0.035f * p

        fun lp(a: Float, b: Float, f: Float) = a + (b - a) * f
        fun pt(x: Float, y: Float) = Offset(x, y + hop)

        // Sin fondo: figura del color de acento, trazo grueso redondeado.
        val figColor = accent
        val stroke = h * 0.045f
        fun limb(a: Offset, joint: Offset, end: Offset) {
            drawLine(figColor, a, joint, strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(figColor, joint, end, strokeWidth = stroke, cap = StrokeCap.Round)
        }

        val headR = h * 0.085f
        val shoulderY = h * 0.34f
        val hipY = h * 0.56f
        val headCY = shoulderY - headR * 1.6f
        val shoulderDX = w * 0.05f
        val hipDX = w * 0.04f
        val kneeY = h * 0.70f
        val footY = h * 0.84f

        // Cabeza sólida + cuello/columna.
        drawCircle(color = figColor, radius = headR, center = pt(cx, headCY))
        drawLine(figColor, pt(cx, shoulderY - headR * 0.2f), pt(cx, hipY), strokeWidth = stroke, cap = StrokeCap.Round)

        // Piernas (cadera -> rodilla -> pie), se separan al abrir.
        val kneeDX = lp(w * 0.05f, w * 0.13f, p)
        val footDX = lp(w * 0.05f, w * 0.22f, p)
        limb(pt(cx - hipDX, hipY), pt(cx - kneeDX, kneeY), pt(cx - footDX, footY))
        limb(pt(cx + hipDX, hipY), pt(cx + kneeDX, kneeY), pt(cx + footDX, footY))

        // Brazos: rotación alrededor del hombro con longitud fija, codos
        // flexionados. Barren en arco entre casi tocarse sobre la cabeza (clap,
        // p=0, en fase con pies juntos) y el costado abajo (p=1, pies separados).
        // Ángulo medido desde el eje vertical hacia abajo.
        val rad = PI.toFloat() / 180f
        val armAngle = lp(190f * rad, 15f * rad, p)
        val elbowBend = 38f * rad
        val upperLen = h * 0.135f
        val foreLen = h * 0.125f
        // Brazo izquierdo (dirección hacia la izquierda-arriba según el ángulo).
        val lShoulder = pt(cx - shoulderDX, shoulderY)
        val lUp = Offset(-sin(armAngle), cos(armAngle))
        val lElbow = Offset(lShoulder.x + lUp.x * upperLen, lShoulder.y + lUp.y * upperLen)
        val lFore = Offset(-sin(armAngle + elbowBend), cos(armAngle + elbowBend))
        val lHand = Offset(lElbow.x + lFore.x * foreLen, lElbow.y + lFore.y * foreLen)
        drawLine(figColor, lShoulder, lElbow, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(figColor, lElbow, lHand, strokeWidth = stroke, cap = StrokeCap.Round)
        // Brazo derecho: espejo del izquierdo respecto al eje central.
        fun mirror(o: Offset) = Offset(2f * cx - o.x, o.y)
        val rShoulder = mirror(lShoulder)
        val rElbow = mirror(lElbow)
        val rHand = mirror(lHand)
        drawLine(figColor, rShoulder, rElbow, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(figColor, rElbow, rHand, strokeWidth = stroke, cap = StrokeCap.Round)
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
    if (muted) {
        FilledTonalButton(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = SURFACE,
                contentColor = Color.White,
            ),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            Text(label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = accent,
                contentColor = ON_ACCENT,
            ),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            Text(label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
