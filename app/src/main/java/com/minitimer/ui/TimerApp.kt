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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
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

    val focusManager = LocalFocusManager.current

    BackHandler(enabled = vm.showSettings) { vm.showSettings = false }

    Scaffold(
        containerColor = BG,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    if (vm.showSettings) {
                        Text(
                            t.settings,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else {
                        EditableTimerTitle(vm = vm, accent = accent, placeholder = t.title)
                    }
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
                // Tocar cualquier zona vacía quita el foco del título editable,
                // lo que guarda el nombre y cierra el teclado.
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
        ) {
            when {
                vm.showSettings -> SettingsScreen(vm)
                vm.phase == Phase.SETUP -> SetupScreen(vm, accent, t)
                else -> CountdownScreen(vm, accent, t)
            }
        }
    }
}

/**
 * Título de la barra superior editable: muestra el nombre del timer (o el
 * placeholder por defecto) y, al tocarlo, se convierte en un campo de texto
 * inline centrado. Confirma con Done o al perder el foco.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditableTimerTitle(vm: TimerViewModel, accent: Color, placeholder: String) {
    var editing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(TextFieldValue(vm.label)) }

    // Sincronizar con el estado externo (p. ej. al cancelar se limpia).
    LaunchedEffect(vm.label) { if (!editing) text = TextFieldValue(vm.label) }

    if (editing) {
        val focusRequester = remember { FocusRequester() }
        // Evita salir de edición por el evento inicial de "sin foco" que llega
        // antes de que requestFocus() tome efecto: solo se sale si ya hubo foco.
        var hasFocused by remember { mutableStateOf(false) }

        // Guardar al pulsar el botón Atrás del sistema mientras se edita.
        BackHandler(enabled = true) {
            vm.commitLabel(text.text)
            editing = false
        }
        // Guardar al ocultar el teclado (swipe down / botón ocultar IME): el foco
        // no se pierde solo, así que detectamos la transición visible -> oculto.
        val imeVisible = WindowInsets.isImeVisible
        var imeWasVisible by remember { mutableStateOf(false) }
        LaunchedEffect(imeVisible) {
            if (imeVisible) {
                imeWasVisible = true
            } else if (imeWasVisible) {
                vm.commitLabel(text.text)
                editing = false
            }
        }
        BasicTextField(
            value = text,
            onValueChange = {
                text = it.copy(text = it.text.take(40))
                vm.setDraftLabel(text.text)
            },
            singleLine = true,
            textStyle = TextStyle(
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
            ),
            cursorBrush = SolidColor(accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                vm.commitLabel(text.text)
                editing = false
            }),
            modifier = Modifier
                .widthIn(min = 140.dp, max = 240.dp)
                .focusRequester(focusRequester)
                .onFocusChanged {
                    if (it.isFocused) {
                        hasFocused = true
                    } else if (hasFocused && editing) {
                        vm.commitLabel(text.text)
                        editing = false
                    }
                },
            decorationBox = { inner ->
                // Contenedor estilo "filled text field" de MD3: superficie
                // redondeada con un indicador inferior en color de acento.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .background(SURFACE)
                        .drawBehind {
                            val stroke = 2.dp.toPx()
                            drawLine(
                                color = accent,
                                start = Offset(0f, size.height - stroke / 2),
                                end = Offset(size.width, size.height - stroke / 2),
                                strokeWidth = stroke,
                            )
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    if (text.text.isEmpty()) {
                        Text(
                            placeholder,
                            color = TEXT_FADED,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 20.sp,
                        )
                    }
                    inner()
                }
            },
        )
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    } else {
        // Affordance MD3: nombre + lápiz tenue, con ripple redondeado al tocar.
        val hasName = vm.label.isNotBlank()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    // Cursor al final del texto al volver a editar.
                    text = TextFieldValue(
                        text = vm.label,
                        selection = TextRange(vm.label.length),
                    )
                    editing = true
                }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = vm.label.ifBlank { placeholder },
                color = if (hasName) Color.White else TEXT_DIM,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = "Edit name",
                tint = TEXT_DIM,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SetupScreen(vm: TimerViewModel, accent: Color, t: com.minitimer.i18n.Strings) {
    val focusManager = LocalFocusManager.current
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
                PresetChip(sec, accent) {
                    focusManager.clearFocus()
                    vm.startWithSeconds(sec)
                }
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
            onClick = {
                focusManager.clearFocus()
                vm.start()
            },
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StickmanJumpingJacks(
                    accent = accent,
                    running = vm.phase == Phase.RUNNING,
                    modifier = Modifier.size(130.dp),
                )
                StickmanPistolSquat(
                    accent = accent,
                    running = vm.phase == Phase.RUNNING,
                    modifier = Modifier.size(130.dp),
                )
            }
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
    // Al pausar, la figura transiciona a la pose de "parado en espera"
    // (brazos abajo, pies juntos), en lugar de congelarse a mitad del salto.
    val rest by animateFloatAsState(
        targetValue = if (running) 0f else 1f,
        animationSpec = tween(durationMillis = 320),
        label = "stickRest",
    )
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val rad = PI.toFloat() / 180f
        fun lp(a: Float, b: Float, f: Float) = a + (b - a) * f

        // Una repetición por segundo (theta avanza 2π por segundo).
        val theta = t * (2f * PI.toFloat())
        // p oscila 0..1: 0 = brazos arriba / piernas juntas; 1 = brazos abajo /
        // piernas abiertas. Al reposar se mezcla hacia la pose de pie.
        val p = (1f - cos(theta)) / 2f

        // Ángulos (grados) medidos desde el eje vertical-abajo.
        val armUp = 120f
        val armDown = 14f
        val bendUp = 45f
        val legClosed = 6f
        val legOpen = 34f
        val legBend = 7f * rad
        // Pose en reposo (de pie): brazos abajo a los costados, piernas juntas.
        val armAngle = lp(lp(armUp, armDown, p), armDown, rest) * rad
        val armBend = lp(lp(bendUp, 0f, p), 0f, rest) * rad
        val legAngle = lp(lp(legClosed, legOpen, p), legClosed, rest) * rad
        // Salto a mitad del movimiento (máximo en p=0.5), anulado en reposo.
        val hop = -h * 0.08f * sin(p * PI.toFloat()) * (1f - rest)

        fun pt(x: Float, y: Float) = Offset(x, y + hop)

        // Figura del color de acento, trazo grueso redondeado.
        val figColor = accent
        val strokeBody = h * 0.075f
        val strokeArm = h * 0.06f
        val strokeLeg = h * 0.065f

        val headR = h * 0.085f
        val headCY = h * 0.22f
        val torsoTop = h * 0.30f
        val shoulderY = h * 0.33f
        val hipY = h * 0.59f
        val upperArm = h * 0.15f
        val foreArm = h * 0.135f
        val thigh = h * 0.16f
        val shin = h * 0.15f

        // Extremidad de dos segmentos que rota alrededor de [origin]; [side] = -1
        // izquierda, +1 derecha; [angle]/[bend] en radianes desde la vertical.
        fun limb(origin: Offset, side: Float, angle: Float, bend: Float, upper: Float, lower: Float, stroke: Float) {
            val jx = origin.x + side * upper * sin(angle)
            val jy = origin.y + upper * cos(angle)
            val ex = jx + side * lower * sin(angle + bend)
            val ey = jy + lower * cos(angle + bend)
            drawLine(figColor, origin, Offset(jx, jy), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(figColor, Offset(jx, jy), Offset(ex, ey), strokeWidth = stroke, cap = StrokeCap.Round)
        }

        // Cabeza sólida + columna.
        drawCircle(color = figColor, radius = headR, center = pt(cx, headCY))
        drawLine(figColor, pt(cx, torsoTop), pt(cx, hipY), strokeWidth = strokeBody, cap = StrokeCap.Round)

        // Piernas (cadera -> rodilla -> pie), se abren en V.
        val hip = pt(cx, hipY)
        limb(hip, -1f, legAngle, legBend, thigh, shin, strokeLeg)
        limb(hip, +1f, legAngle, legBend, thigh, shin, strokeLeg)

        // Brazos (hombro -> codo -> mano), suben en V con codos flexionados.
        val shoulder = pt(cx, shoulderY)
        limb(shoulder, -1f, armAngle, armBend, upperArm, foreArm, strokeArm)
        limb(shoulder, +1f, armAngle, armBend, upperArm, foreArm, strokeArm)
    }
}

/**
 * Stickman de perfil haciendo "pistol squats" (sentadilla a una pierna): de pie
 * sobre una pierna con la otra extendida al frente, baja flexionando la pierna
 * de apoyo (cinemática inversa, pie plantado) y sube. Brazos al frente para
 * balance. Avanza solo mientras [running] es true (se endereza al pausar).
 */
@Composable
private fun StickmanPistolSquat(
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
    val rest by animateFloatAsState(
        targetValue = if (running) 0f else 1f,
        animationSpec = tween(durationMillis = 320),
        label = "pistolRest",
    )
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Cinemática en unidades del viewBox 200x220; se mapea al lienzo.
        val k = h / 220f
        val cx = w / 2f
        fun map(x: Float, y: Float) = Offset(cx + (x - 100f) * k, y * k)
        val rad = PI.toFloat() / 180f
        fun lp(a: Float, b: Float, f: Float) = a + (b - a) * f

        // 0.6 repeticiones por segundo; al reposar tiende a q=0 (de pie).
        val theta = t * (2f * PI.toFloat()) * 0.6f
        val q = ((1f - cos(theta)) / 2f) * (1f - rest)

        // Parámetros de la pose (profundidad 165, inclinación 38°).
        val ax = 96f
        val ay = 190f
        val hipX = lp(96f, 72f, q)
        val hipY = lp(100f, 165f, q)
        // Pierna de apoyo: IK de dos segmentos, rodilla hacia adelante.
        val l1 = 46f
        val l2 = 46f
        val dx = ax - hipX
        val dy = ay - hipY
        val d = hypot(dx, dy).coerceAtMost(l1 + l2 - 0.01f)
        val a1 = atan2(dy, dx)
        val cosA = ((l1 * l1 + d * d - l2 * l2) / (2f * l1 * d)).coerceIn(-1f, 1f)
        val ang = acos(cosA)
        val k1x = hipX + l1 * cos(a1 - ang)
        val k1y = hipY + l1 * sin(a1 - ang)
        val k2x = hipX + l1 * cos(a1 + ang)
        val k2y = hipY + l1 * sin(a1 + ang)
        val kneeX = if (k1x > k2x) k1x else k2x
        val kneeY = if (k1x > k2x) k1y else k2y

        val figColor = accent
        val sBody = 15f * k
        val sArm = 12f * k
        val sLeg = 13f * k
        fun seg(ax1: Float, ay1: Float, bx: Float, by: Float, stroke: Float) {
            drawLine(figColor, map(ax1, ay1), map(bx, by), strokeWidth = stroke, cap = StrokeCap.Round)
        }

        // Pierna libre extendida al frente (se dibuja detrás).
        val angF = lp(32f, -6f, q) * rad
        val ft = 44f
        val fs = 42f
        val kFx = hipX + ft * cos(angF)
        val kFy = hipY + ft * sin(angF)
        val fFx = kFx + fs * cos(angF - 4f * rad)
        val fFy = kFy + fs * sin(angF - 4f * rad)
        seg(hipX, hipY, kFx, kFy, 12f * k)
        seg(kFx, kFy, fFx, fFy, 11f * k)

        // Pierna de apoyo + pie.
        seg(hipX, hipY, kneeX, kneeY, sLeg)
        seg(kneeX, kneeY, ax, ay, 12f * k)
        seg(ax - 7f, ay, ax + 15f, ay, 11f * k)

        // Torso + cabeza.
        val lean = lp(-6f, 38f, q) * rad
        val torso = 52f
        val shX = hipX + torso * sin(lean)
        val shY = hipY - torso * cos(lean)
        seg(hipX, hipY, shX, shY, sBody)
        val headGap = 24f
        drawCircle(
            color = figColor,
            radius = 15f * k,
            center = map(shX + headGap * sin(lean), shY - headGap * cos(lean)),
        )

        // Brazos al frente (balance).
        val aa = lp(22f, 0f, q) * rad
        val ua = 34f
        val fa = 30f
        val elbX = shX + ua * cos(aa)
        val elbY = shY + ua * sin(aa)
        val hndX = elbX + fa * cos(aa - 6f * rad)
        val hndY = elbY + fa * sin(aa - 6f * rad)
        seg(shX, shY, elbX, elbY, sArm)
        seg(elbX, elbY, hndX, hndY, 11f * k)
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
