package com.minitimer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minitimer.Phase
import com.minitimer.TimerViewModel
import com.minitimer.i18n.I18n
import com.minitimer.model.TimerItem
import com.minitimer.ui.theme.BG
import com.minitimer.ui.theme.DONE_RED
import com.minitimer.ui.theme.JetBrainsMono
import com.minitimer.ui.theme.ON_ACCENT
import com.minitimer.ui.theme.SURFACE
import com.minitimer.ui.theme.TEXT_DIM
import com.minitimer.ui.theme.TEXT_FADED
import com.minitimer.ui.theme.TRACK
import com.minitimer.util.formatLastFinished
import com.minitimer.util.formatRemaining
import com.minitimer.util.incLabel
import com.minitimer.util.pad2
import kotlinx.coroutines.launch

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

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showSheet by remember { mutableStateOf(false) }
    var renameId by remember { mutableStateOf<Long?>(null) }

    BackHandler(enabled = vm.showSettings) { vm.showSettings = false }

    Scaffold(
        containerColor = BG,
        snackbarHost = { SnackbarHost(snackbar) },
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
                    if (!vm.showSettings) {
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
        floatingActionButton = {
            if (!vm.showSettings) {
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
    ) { inner ->
        Box(modifier = Modifier.fillMaxSize().padding(inner)) {
            if (vm.showSettings) {
                Box(Modifier.padding(horizontal = 24.dp)) { SettingsScreen(vm) }
            } else {
                TimerListScreen(
                    vm = vm,
                    accent = accent,
                    t = t,
                    onRename = { renameId = it },
                    onBlocked = { scope.launch { snackbar.showSnackbar(t.blockedActive) } },
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
            onBlocked = { scope.launch { snackbar.showSnackbar(t.blockedActive) } },
        )
    }

    renameId?.let { id ->
        RenameDialog(
            initial = vm.item(id)?.name ?: "",
            accent = accent,
            t = t,
            onConfirm = { vm.renameTimer(id, it); renameId = null },
            onDismiss = { renameId = null },
        )
    }
}

@Composable
private fun TimerListScreen(
    vm: TimerViewModel,
    accent: Color,
    t: com.minitimer.i18n.Strings,
    onRename: (Long) -> Unit,
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(vm.timers, key = { it.id }) { item ->
            TimerCard(
                item = item,
                accent = accent,
                t = t,
                blocked = active != null && active != item.id,
                incSec = vm.settings.addIncrementSec,
                onToggle = { if (!vm.togglePlay(item.id)) onBlocked() },
                onReset = { vm.resetTimer(item.id) },
                onDismiss = { vm.dismissTimer(item.id) },
                onAddTime = { vm.addTime(item.id) },
                onStar = { vm.toggleStar(item.id) },
                onRename = { onRename(item.id) },
                onDelete = { vm.deleteTimer(item.id) },
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
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    onAddTime: () -> Unit,
    onStar: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(SURFACE)
            .then(
                if (borderColor != Color.Transparent)
                    Modifier.border(1.dp, borderColor, RoundedCornerShape(24.dp))
                else Modifier
            )
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onStar, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "Star",
                    tint = if (item.starred) Color(0xFFFFC24B) else TEXT_FADED,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onRename() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    item.name.ifBlank { t.noName },
                    color = when {
                        running -> accent
                        item.name.isBlank() -> TEXT_FADED
                        else -> Color.White
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = TEXT_FADED,
                    modifier = Modifier.size(14.dp),
                )
            }
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Menu", tint = TEXT_DIM)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(t.rename) },
                        onClick = { menu = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text(t.delete) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                Spacer(Modifier.height(8.dp))
                when {
                    done -> Text(
                        t.timeUp,
                        color = DONE_RED,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    active -> Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { progress },
                            color = accent,
                            trackColor = TRACK,
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            formatRemaining(item.totalMs - item.remainingMs),
                            color = accent,
                            fontSize = 12.sp,
                            fontFamily = JetBrainsMono,
                        )
                    }
                    item.lastFinished > 0L -> Text(
                        "${t.endedAt} ${formatLastFinished(item.lastFinished, t.locale)}",
                        color = TEXT_DIM,
                        fontSize = 12.sp,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            if (done) {
                RoundCtrl(bg = DONE_RED, onClick = onDismiss) {
                    Icon(Icons.Filled.Check, contentDescription = t.dismiss, tint = Color(0xFF2A0000))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RoundCtrl(bg = TRACK, onClick = onReset) {
                        Icon(Icons.Filled.Refresh, contentDescription = t.reset, tint = Color.White)
                    }
                    RoundCtrl(bg = accent, dim = blocked, onClick = onToggle) {
                        if (running) PauseIcon(ON_ACCENT)
                        else Icon(Icons.Filled.PlayArrow, contentDescription = t.start, tint = ON_ACCENT)
                    }
                }
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
            Row(verticalAlignment = Alignment.Bottom) {
                TimePart(pad2(vm.setH), "h", vm.setH > 0, accent)
                TimePart(pad2(vm.setM), "m", vm.setM > 0 || vm.setH > 0, accent)
                TimePart(pad2(vm.setS), "s", true, accent)
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
            Spacer(Modifier.height(8.dp))
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

@Composable
private fun RenameDialog(
    initial: String,
    accent: Color,
    t: com.minitimer.i18n.Strings,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SURFACE,
        title = { Text(t.rename, color = Color.White, fontWeight = FontWeight.SemiBold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(40) },
                singleLine = true,
                placeholder = { Text(t.timerName, color = TEXT_FADED) },
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
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(t.select, color = accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(t.cancel, color = TEXT_DIM)
            }
        },
    )
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

