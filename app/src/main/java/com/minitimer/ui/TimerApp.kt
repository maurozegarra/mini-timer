package com.minitimer.ui

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minitimer.AthleteViewModel
import com.minitimer.Phase
import com.minitimer.R
import com.minitimer.TimerViewModel
import com.minitimer.ui.athlete.AthleteScreen
import com.minitimer.i18n.I18n
import com.minitimer.model.TimerItem
import com.minitimer.ui.theme.BG
import com.minitimer.ui.theme.DONE_RED
import com.minitimer.ui.theme.JetBrainsMono
import com.minitimer.ui.theme.Kanit
import com.minitimer.ui.theme.ON_ACCENT
import com.minitimer.ui.theme.SURFACE
import com.minitimer.ui.theme.TEXT_DIM
import com.minitimer.ui.theme.TEXT_FADED
import com.minitimer.ui.theme.TRACK
import com.minitimer.util.formatClock
import com.minitimer.util.formatLastFinished
import com.minitimer.util.formatRemaining
import com.minitimer.util.incLabel
import com.minitimer.util.pad2
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerApp(vm: TimerViewModel, athleteVm: AthleteViewModel = viewModel()) {
    val t = I18n.get(vm.settings.language)
    val accent = Color(vm.settings.accent)

    // Mantener la pantalla encendida siempre que la app esté abierta (en primer plano).
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showSheet by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    val onBlocked = { scope.launch { snackbar.showSnackbar(t.blockedActive) }; Unit }

    // El timer cuyo detalle está abierto (si existe en la lista).
    val detail: TimerItem? = vm.detailId?.let { vm.item(it) }

    // Editor / selector / player abiertos (sección Athlete).
    val athleteEditing = selectedTab == 1 && athleteVm.draft != null
    val athleteChoosing = selectedTab == 1 && athleteVm.choosingForRound != null
    val athletePlaying = selectedTab == 1 && athleteVm.playerWorkoutId != null
    val athleteFull = athleteEditing || athletePlaying

    BackHandler(enabled = vm.showSettings || vm.detailId != null || athleteFull) {
        when {
            vm.showSettings -> vm.showSettings = false
            vm.detailId != null -> vm.detailId = null
            athletePlaying -> athleteVm.closePlayer()
            athleteChoosing -> athleteVm.closeExercisePicker()
            athleteEditing -> athleteVm.closeEditor()
        }
    }

    Scaffold(
        containerColor = BG,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    when {
                        vm.showSettings -> Text(t.settings, color = Color.White, fontWeight = FontWeight.SemiBold)
                        detail != null -> EditableTitle(
                            name = detail.name,
                            accent = accent,
                            placeholder = t.noName,
                            onCommit = { vm.renameTimer(detail.id, it) },
                        )
                        athletePlaying -> Text(athleteVm.playerName, color = Color.White, fontFamily = Kanit, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontSize = 20.sp)
                        athleteChoosing -> Text(t.chooseExercise, color = Color.White, fontFamily = Kanit, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontSize = 20.sp)
                        athleteEditing -> Text(t.createWorkout, color = Color.White, fontFamily = Kanit, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, fontSize = 20.sp)
                        selectedTab == 1 -> Text(
                            t.tabAthlete,
                            color = Color.White,
                            fontFamily = Kanit,
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic,
                            fontSize = 22.sp,
                        )
                        else -> Text(
                            if (selectedTab == 2) t.tabWater else t.title,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                navigationIcon = {
                    if (vm.showSettings || vm.detailId != null || athleteFull) {
                        IconButton(onClick = {
                            when {
                                vm.showSettings -> vm.showSettings = false
                                vm.detailId != null -> vm.detailId = null
                                athletePlaying -> athleteVm.closePlayer()
                                athleteChoosing -> athleteVm.closeExercisePicker()
                                athleteEditing -> athleteVm.closeEditor()
                            }
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                            )
                        }
                    }
                },
                actions = {
                    when {
                        vm.showSettings -> {}
                        athleteFull -> {}
                        detail != null -> {
                            var menu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { menu = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "Menu", tint = TEXT_DIM)
                                }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(t.delete) },
                                        onClick = { menu = false; vm.deleteTimer(detail.id); vm.detailId = null },
                                    )
                                }
                            }
                        }
                        else -> IconButton(onClick = { vm.showSettings = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = TEXT_DIM)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        floatingActionButton = {
            if (!vm.showSettings && vm.detailId == null && selectedTab == 0) {
                FloatingActionButton(
                    onClick = { vm.prepareNewTimer(); showSheet = true },
                    containerColor = accent,
                    contentColor = ON_ACCENT,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = t.newTimer)
                }
            }
        },
        bottomBar = {
            if (!vm.showSettings && vm.detailId == null && !athleteFull) {
                BottomNavBar(
                    selected = selectedTab,
                    accent = accent,
                    t = t,
                    onSelect = { selectedTab = it },
                )
            }
        },
    ) { inner ->
        Box(modifier = Modifier.fillMaxSize().padding(inner)) {
            when {
                vm.showSettings -> Box(Modifier.padding(horizontal = 24.dp)) { SettingsScreen(vm, athleteVm) }
                detail != null -> TimerDetailBody(vm, detail, accent, t, onBlocked)
                selectedTab == 0 -> TimerListScreen(
                    vm = vm,
                    accent = accent,
                    t = t,
                    onOpen = { vm.detailId = it },
                    onBlocked = onBlocked,
                )
                selectedTab == 1 -> AthleteScreen(athleteVm, accent, t)
                else -> ComingSoonScreen(
                    icon = R.drawable.ic_tab_water,
                    title = t.tabWater,
                    subtitle = t.comingSoon,
                )
            }
        }
    }

    if (showSheet) {
        NewTimerSheet(
            vm = vm,
            accent = accent,
            t = t,
            onDismiss = { showSheet = false },
            onBlocked = onBlocked,
        )
    }
}

// ---------- Barra de navegación inferior (Material 3) ----------
@Composable
private fun BottomNavBar(
    selected: Int,
    accent: Color,
    t: com.minitimer.i18n.Strings,
    onSelect: (Int) -> Unit,
) {
    val items = listOf(
        Triple(R.drawable.ic_tab_timer, t.title, 0),
        Triple(R.drawable.ic_tab_athlete, t.tabAthlete, 1),
        Triple(R.drawable.ic_tab_water, t.tabWater, 2),
    )
    NavigationBar(containerColor = SURFACE, tonalElevation = 0.dp) {
        items.forEach { (iconRes, label, index) ->
            NavigationBarItem(
                selected = selected == index,
                onClick = { onSelect(index) },
                icon = {
                    if (index == 1) {
                        AthleteTabIcon(modifier = Modifier.size(24.dp))
                    } else {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = label,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                label = { Text(label, fontSize = 12.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ON_ACCENT,
                    selectedTextColor = accent,
                    indicatorColor = accent,
                    unselectedIconColor = TEXT_DIM,
                    unselectedTextColor = TEXT_DIM,
                ),
            )
        }
    }
}

@Composable
private fun AthleteTabIcon(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    val measurer = rememberTextMeasurer()
    Canvas(modifier = modifier) {
        val sx = size.width / 24f
        val sy = size.height / 24f
        val pts = listOf(
            12f to 3f, 19.04f to 6.39f, 20.77f to 14f,
            15.91f to 20.11f, 8.09f to 20.11f, 3.23f to 14f, 4.96f to 6.39f,
        )
        val hept = Path().apply {
            moveTo(pts[0].first * sx, pts[0].second * sy)
            for (i in 1 until pts.size) lineTo(pts[i].first * sx, pts[i].second * sy)
            close()
        }
        val canvas = drawContext.canvas
        canvas.saveLayer(Rect(Offset.Zero, size), Paint())
        rotate(-12f, pivot = Offset(size.width / 2f, size.height / 2f)) {
            drawPath(hept, color)
        }
        val layout = measurer.measure(
            text = "M",
            style = TextStyle(
                fontFamily = Kanit,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
                fontSize = (size.height * 0.62f).toSp(),
            ),
        )
        drawText(
            textLayoutResult = layout,
            color = Color.Black,
            topLeft = Offset(
                (size.width - layout.size.width) / 2f,
                (size.height - layout.size.height) / 2f,
            ),
            blendMode = BlendMode.DstOut,
        )
        canvas.restore()
    }
}

@Composable
private fun ComingSoonScreen(icon: Int, title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = title,
            tint = TEXT_FADED,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(title, color = TEXT_DIM, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = TEXT_FADED, fontSize = 14.sp)
    }
}

@Composable
private fun TimerListScreen(
    vm: TimerViewModel,
    accent: Color,
    t: com.minitimer.i18n.Strings,
    onOpen: (Long) -> Unit,
    onBlocked: () -> Unit,
) {
    if (vm.timers.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(t.noTimers, color = TEXT_DIM, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(t.noTimersDesc, color = TEXT_FADED, fontSize = 14.sp)
        }
        return
    }
    val active = vm.activeId
    val density = LocalDensity.current
    val spacingPx = with(density) { 14.dp.toPx() }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var rowHeightPx by remember { mutableStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        vm.timers.forEach { item ->
            val dragging = item.id == draggingId
            TimerCard(
                item = item,
                accent = accent,
                t = t,
                blocked = active != null && active != item.id,
                incSec = vm.settings.addIncrementSec,
                dragging = dragging,
                dragOffset = if (dragging) dragOffset else 0f,
                onHeight = { h -> if (rowHeightPx == 0f) rowHeightPx = h.toFloat() },
                onOpen = { onOpen(item.id) },
                onToggle = { if (!vm.togglePlay(item.id)) onBlocked() },
                onReset = { vm.resetTimer(item.id) },
                onDismiss = { vm.dismissTimer(item.id) },
                onAddTime = { vm.addTime(item.id) },
                onStar = { vm.toggleStar(item.id) },
                dragModifier = Modifier.pointerInput(item.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { draggingId = item.id; dragOffset = 0f },
                        onDragEnd = { draggingId = null; dragOffset = 0f },
                        onDragCancel = { draggingId = null; dragOffset = 0f },
                        onDrag = { change, amount ->
                            change.consume()
                            dragOffset += amount.y
                            val step = rowHeightPx + spacingPx
                            if (step <= 0f) return@detectDragGesturesAfterLongPress
                            val cur = vm.timers.indexOfFirst { it.id == draggingId }
                            if (cur < 0) return@detectDragGesturesAfterLongPress
                            if (dragOffset > step / 2 && cur < vm.timers.size - 1) {
                                vm.moveTimer(cur, cur + 1)
                                dragOffset -= step
                            } else if (dragOffset < -step / 2 && cur > 0) {
                                vm.moveTimer(cur, cur - 1)
                                dragOffset += step
                            }
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun TimerCard(
    item: TimerItem,
    accent: Color,
    t: com.minitimer.i18n.Strings,
    blocked: Boolean,
    incSec: Int,
    dragging: Boolean,
    dragOffset: Float,
    onHeight: (Int) -> Unit,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    onAddTime: () -> Unit,
    onStar: () -> Unit,
    dragModifier: Modifier,
) {
    val done = item.phase == Phase.DONE
    val running = item.phase == Phase.RUNNING
    val active = running || item.phase == Phase.PAUSED
    val timeColor = when {
        done -> DONE_RED
        running -> accent
        else -> Color.White
    }
    val borderColor = when {
        running -> accent
        done -> DONE_RED
        else -> Color.Transparent
    }
    val progress = if (item.totalMs > 0) {
        ((item.totalMs - item.remainingMs).toFloat() / item.totalMs).coerceIn(0f, 1f)
    } else 0f

    // Texto contextual (una sola línea, a la derecha del nombre).
    val captionText: String? = when {
        done -> t.timeUp
        running -> "${t.endsAt} ${formatClock(item.endAt, t.locale)}"
        item.phase == Phase.PAUSED -> t.paused
        item.lastFinished > 0L ->
            formatLastFinished(item.lastFinished, t.locale, t.yesterday, t.daysAgo)
        else -> null
    }
    val captionColor = if (done) DONE_RED else TEXT_DIM

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onHeight(it.height) }
            .graphicsLayer {
                translationY = dragOffset
                if (dragging) { scaleX = 1.03f; scaleY = 1.03f }
            }
            .clip(RoundedCornerShape(24.dp))
            .background(if (dragging) TRACK else SURFACE)
            .then(
                if (borderColor != Color.Transparent)
                    Modifier.border(1.dp, borderColor, RoundedCornerShape(24.dp))
                else Modifier
            )
            .pointerInput(item.id) { detectTapGestures(onTap = { onOpen() }) }
            .then(dragModifier),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onStar, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "Star",
                        tint = if (item.starred) Color(0xFFFFC24B) else TEXT_FADED,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    item.name.ifBlank { t.noName },
                    color = when {
                        running -> accent
                        item.name.isBlank() -> TEXT_FADED
                        else -> Color.White
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (captionText != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        captionText,
                        color = captionColor,
                        fontSize = 12.sp,
                        fontWeight = if (done) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 180.dp),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        formatRemaining(item.remainingMs),
                        color = timeColor,
                        fontSize = 40.sp,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Light,
                    )
                    if (!done) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            incLabel(incSec),
                            color = accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onAddTime() }
                                .background(TRACK)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                when {
                    done -> RoundCtrl(bg = DONE_RED, onClick = onDismiss) {
                        Icon(Icons.Filled.Check, contentDescription = t.dismiss, tint = Color(0xFF2A0000))
                    }
                    active -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RoundCtrl(bg = TRACK, onClick = onReset) {
                            Icon(Icons.Filled.Refresh, contentDescription = t.cancel, tint = Color.White)
                        }
                        RoundCtrl(bg = accent, dim = blocked, onClick = onToggle) {
                            if (running) PauseIcon(ON_ACCENT)
                            else Icon(Icons.Filled.PlayArrow, contentDescription = t.resume, tint = ON_ACCENT)
                        }
                    }
                    else -> RoundCtrl(bg = accent, dim = blocked, onClick = onToggle) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = t.start, tint = ON_ACCENT)
                    }
                }
            }
        }

        // Progreso como línea fina pegada al borde inferior (no agrega altura).
        if (active) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(TRACK),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(accent),
                )
            }
        }
    }
}

@Composable
private fun RoundCtrl(
    bg: Color,
    dim: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg.copy(alpha = if (dim) 0.4f else 1f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun PauseIcon(color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(width = 5.dp, height = 18.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Box(Modifier.size(width = 5.dp, height = 18.dp).clip(RoundedCornerShape(2.dp)).background(color))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewTimerSheet(
    vm: TimerViewModel,
    accent: Color,
    t: com.minitimer.i18n.Strings,
    onDismiss: () -> Unit,
    onBlocked: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SURFACE,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(t.newTimer, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = vm.draftName,
                onValueChange = { vm.updateDraftName(it) },
                placeholder = { Text(t.timerName, color = TEXT_FADED) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = TRACK,
                    unfocusedContainerColor = TRACK,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = accent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(16.dp))
            WheelTimePicker(
                h = vm.setH,
                m = vm.setM,
                s = vm.setS,
                accent = accent,
                t = t,
                onChange = { h, m, s -> vm.setDraftTime(h, m, s) },
            )
            Spacer(Modifier.height(20.dp))
            val canStart = vm.setH * 3600 + vm.setM * 60 + vm.setS > 0
            Button(
                onClick = {
                    val started = vm.confirmNewTimer()
                    onDismiss()
                    if (!started) onBlocked()
                },
                enabled = canStart,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = ON_ACCENT,
                    disabledContainerColor = TRACK,
                    disabledContentColor = TEXT_DIM,
                ),
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 14.dp),
            ) {
                Text(t.start, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---------- Pantalla de detalle ----------
@Composable
private fun TimerDetailBody(
    vm: TimerViewModel,
    item: TimerItem,
    accent: Color,
    t: com.minitimer.i18n.Strings,
    onBlocked: () -> Unit,
) {
    val phase = item.phase
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (phase == Phase.IDLE) {
            var h by remember(item.id) { mutableStateOf((item.totalMs / 3_600_000L).toInt()) }
            var m by remember(item.id) { mutableStateOf(((item.totalMs % 3_600_000L) / 60_000L).toInt()) }
            var s by remember(item.id) { mutableStateOf(((item.totalMs % 60_000L) / 1000L).toInt()) }
            Spacer(Modifier.weight(1f))
            WheelTimePicker(
                h = h,
                m = m,
                s = s,
                accent = accent,
                t = t,
                onChange = { a, b, c ->
                    h = a; m = b; s = c
                    vm.setTimerTotal(item.id, a * 3600 + b * 60 + c)
                },
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { if (!vm.startTimer(item.id)) onBlocked() },
                enabled = h * 3600 + m * 60 + s > 0,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(30.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = ON_ACCENT,
                    disabledContainerColor = SURFACE,
                    disabledContentColor = TEXT_DIM,
                ),
                contentPadding = PaddingValues(vertical = 18.dp),
            ) { Text(t.start, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        } else {
            val isDone = phase == Phase.DONE
            val progress = if (item.totalMs > 0) {
                (item.remainingMs.toFloat() / item.totalMs).coerceIn(0f, 1f)
            } else 0f
            Spacer(Modifier.weight(1f))
            ProgressRing(progress = if (isDone) 0f else progress, accent = if (isDone) DONE_RED else accent) {
                if (isDone) {
                    Text(t.timeUp, color = DONE_RED, fontSize = 40.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Text(
                        formatRemaining(item.remainingMs),
                        color = if (phase == Phase.RUNNING) accent else Color.White,
                        fontSize = 52.sp,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Light,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (phase == Phase.PAUSED) t.paused
                        else "${t.endsAt} ${formatClock(item.endAt, t.locale)}",
                        color = TEXT_DIM,
                        fontSize = 14.sp,
                    )
                }
            }
            if (!isDone) {
                Spacer(Modifier.height(18.dp))
                Text(
                    incLabel(vm.settings.addIncrementSec),
                    color = accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { vm.addTime(item.id) }
                        .background(accent.copy(alpha = 0.16f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (isDone) {
                    ControlButton(t.dismiss, Modifier.weight(1f), accent, muted = true) { vm.dismissTimer(item.id) }
                    ControlButton(t.restart, Modifier.weight(1f), accent, done = true) {
                        if (!vm.restartTimer(item.id)) onBlocked()
                    }
                } else {
                    ControlButton(t.cancel, Modifier.weight(1f), accent, muted = true) { vm.resetTimer(item.id) }
                    if (phase == Phase.RUNNING) {
                        ControlButton(t.pause, Modifier.weight(1f), accent) { vm.pauseTimer(item.id) }
                    } else {
                        ControlButton(t.resume, Modifier.weight(1f), accent) {
                            if (!vm.startTimer(item.id)) onBlocked()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlButton(
    label: String,
    modifier: Modifier = Modifier,
    accent: Color,
    muted: Boolean = false,
    done: Boolean = false,
    onClick: () -> Unit,
) {
    val container = when {
        done -> DONE_RED
        muted -> SURFACE
        else -> accent
    }
    val contentColor = when {
        done -> Color(0xFF2A0000)
        muted -> Color.White
        else -> ON_ACCENT
    }
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(30.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = contentColor),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ProgressRing(progress: Float, accent: Color, content: @Composable () -> Unit) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(280.dp)) {
        Canvas(modifier = Modifier.size(280.dp)) {
            val stroke = 14.dp.toPx()
            val d = size.minDimension - stroke
            val tl = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            val arc = Size(d, d)
            drawArc(
                color = TRACK,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = tl,
                size = arc,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = tl,
                size = arc,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

// ---------- Título editable (inline) ----------
@Composable
private fun EditableTitle(
    name: String,
    accent: Color,
    placeholder: String,
    onCommit: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(name) }
    if (editing) {
        val focus = remember { FocusRequester() }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(40) },
            singleLine = true,
            placeholder = { Text(placeholder, color = TEXT_FADED) },
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            ),
            modifier = Modifier.widthIn(max = 240.dp).focusRequester(focus),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = TRACK,
                unfocusedContainerColor = TRACK,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = accent,
                focusedIndicatorColor = accent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCommit(text); editing = false }),
        )
        LaunchedEffect(Unit) { focus.requestFocus() }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { text = name; editing = true }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                name.ifBlank { placeholder },
                color = if (name.isBlank()) TEXT_DIM else Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Outlined.Edit, contentDescription = "Edit name", tint = TEXT_DIM, modifier = Modifier.size(16.dp))
        }
    }
}

// ---------- Selector tipo rueda (Hours/Minutes/Seconds) ----------
private val WHEEL_COL_W = 72.dp
private val WHEEL_ITEM_H = 56.dp

@Composable
private fun WheelTimePicker(
    h: Int,
    m: Int,
    s: Int,
    accent: Color,
    t: com.minitimer.i18n.Strings,
    onChange: (Int, Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.Center) {
            WheelLabel(t.hours)
            Spacer(Modifier.width(16.dp))
            WheelLabel(t.minutes)
            Spacer(Modifier.width(16.dp))
            WheelLabel(t.seconds)
        }
        Spacer(Modifier.height(4.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.height(WHEEL_ITEM_H * 3)) {
            // Banda de selección (cápsula) detrás de los números.
            Box(
                modifier = Modifier
                    .width(WHEEL_COL_W * 3 + 32.dp)
                    .height(WHEEL_ITEM_H)
                    .clip(RoundedCornerShape(20.dp))
                    .background(accent.copy(alpha = 0.14f))
                    .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(20.dp)),
            )
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                WheelColumn(range = 100, value = h) { onChange(it, m, s) }
                WheelColon()
                WheelColumn(range = 60, value = m) { onChange(h, it, s) }
                WheelColon()
                WheelColumn(range = 60, value = s) { onChange(h, m, it) }
            }
        }
    }
}

@Composable
private fun WheelLabel(text: String) {
    Box(modifier = Modifier.width(WHEEL_COL_W), contentAlignment = Alignment.Center) {
        Text(text, color = TEXT_DIM, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun WheelColon() {
    Box(modifier = Modifier.width(16.dp), contentAlignment = Alignment.Center) {
        Text(":", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun WheelColumn(range: Int, value: Int, onValue: (Int) -> Unit) {
    val view = LocalView.current
    val blocks = 2000
    val start = range * (blocks / 2) + value
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = start - 1)
    val fling = rememberSnapFlingBehavior(lazyListState = listState)
    val center by remember {
        derivedStateOf {
            val li = listState.layoutInfo
            val visible = li.visibleItemsInfo
            if (visible.isEmpty()) start
            else {
                val vc = (li.viewportStartOffset + li.viewportEndOffset) / 2
                visible.minByOrNull { abs((it.offset + it.size / 2) - vc) }!!.index
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { center }
            .drop(1)
            .distinctUntilChanged()
            .collect { idx ->
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onValue(idx % range)
            }
    }
    LazyColumn(
        state = listState,
        flingBehavior = fling,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(WHEEL_COL_W).height(WHEEL_ITEM_H * 3),
    ) {
        items(range * blocks) { index ->
            val selected = index == center
            Box(modifier = Modifier.width(WHEEL_COL_W).height(WHEEL_ITEM_H), contentAlignment = Alignment.Center) {
                Text(
                    pad2(index % range),
                    color = if (selected) Color.White else TEXT_FADED,
                    fontSize = if (selected) 40.sp else 24.sp,
                    fontFamily = JetBrainsMono,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Light,
                )
            }
        }
    }
}

