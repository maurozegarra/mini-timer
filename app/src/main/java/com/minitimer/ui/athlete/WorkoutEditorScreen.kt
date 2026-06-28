package com.minitimer.ui.athlete

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minitimer.AthleteViewModel
import com.minitimer.i18n.Strings
import com.minitimer.model.ExerciseItem
import com.minitimer.model.ExerciseMode
import com.minitimer.model.RestItem
import com.minitimer.model.Round
import com.minitimer.model.WorkoutItem
import com.minitimer.ui.theme.BG
import com.minitimer.ui.theme.ON_ACCENT
import com.minitimer.ui.theme.SURFACE
import com.minitimer.ui.theme.TEXT_DIM
import com.minitimer.ui.theme.TRACK

private val ITEM_HEIGHT = 64.dp
private val ITEM_SPACING = 8.dp

/** Editor de un workout (draft en [AthleteViewModel]). */
@Composable
fun WorkoutEditorScreen(vm: AthleteViewModel, accent: Color, t: Strings) {
    val draft = vm.draft ?: return

    var editingExercise by remember { mutableStateOf<Pair<Long, ExerciseItem>?>(null) }
    var editingRest by remember { mutableStateOf<Pair<Long, RestItem>?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "name") {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { vm.setDraftName(it) },
                    singleLine = true,
                    placeholder = { Text(t.workoutNameHint, color = TEXT_DIM) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            draft.rounds.forEachIndexed { index, round ->
                item(key = round.id) {
                    RoundBlock(
                        vm = vm,
                        t = t,
                        round = round,
                        index = index,
                        total = draft.rounds.size,
                        onEditExercise = { item -> editingExercise = round.id to item },
                        onEditRest = { item -> editingRest = round.id to item },
                        onAddExercise = { vm.openExercisePicker(round.id) },
                    )
                }
            }

            item(key = "add_round") {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(TRACK)
                        .clickable { vm.addRound() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White)
                }
            }
        }

        // Píldora "Save Workout".
        val saveEnabled = vm.draftValid
        Text(
            t.saveWorkout,
            color = if (saveEnabled) ON_ACCENT else TEXT_DIM,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(if (saveEnabled) Color.White else SURFACE)
                .clickable(enabled = saveEnabled) { vm.saveDraft() }
                .padding(horizontal = 28.dp, vertical = 14.dp),
        )
    }

    editingExercise?.let { (roundId, item) ->
        ExerciseValueSheet(
            t = t,
            accent = accent,
            item = item,
            onSave = { mode, reps, dur, ttp ->
                vm.updateExercise(roundId, item.id, mode, reps, dur, ttp)
                editingExercise = null
            },
            onDismiss = { editingExercise = null },
        )
    }

    editingRest?.let { (roundId, item) ->
        RestValueSheet(
            t = t,
            accent = accent,
            item = item,
            onSave = { dur ->
                vm.updateRest(roundId, item.id, dur)
                editingRest = null
            },
            onDismiss = { editingRest = null },
        )
    }

}

@Composable
private fun RoundBlock(
    vm: AthleteViewModel,
    t: Strings,
    round: Round,
    index: Int,
    total: Int,
    onEditExercise: (ExerciseItem) -> Unit,
    onEditRest: (RestItem) -> Unit,
    onAddExercise: () -> Unit,
) {
    val density = LocalDensity.current
    val rowPx = with(density) { (ITEM_HEIGHT + ITEM_SPACING).toPx() }

    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Header "Round i/N" + menú.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${t.round} ${index + 1}/$total",
                color = TEXT_DIM,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = null, tint = TEXT_DIM)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(t.duplicate) },
                        onClick = { menu = false; vm.duplicateRound(round.id) },
                    )
                    if (total > 1) {
                        DropdownMenuItem(
                            text = { Text(t.delete) },
                            onClick = { menu = false; vm.deleteRound(round.id) },
                        )
                    }
                }
            }
        }

        // Items del round (reordenables arrastrando el handle).
        Column(verticalArrangement = Arrangement.spacedBy(ITEM_SPACING)) {
            round.items.forEach { item ->
                val dragging = item.id == draggingId
                ItemCard(
                    vm = vm,
                    t = t,
                    roundId = round.id,
                    item = item,
                    dragging = dragging,
                    dragOffset = if (dragging) dragOffset else 0f,
                    onClick = {
                        when (item) {
                            is ExerciseItem -> onEditExercise(item)
                            is RestItem -> onEditRest(item)
                        }
                    },
                    dragModifier = Modifier.pointerInput(round.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { draggingId = item.id; dragOffset = 0f },
                            onDragEnd = { draggingId = null; dragOffset = 0f },
                            onDragCancel = { draggingId = null; dragOffset = 0f },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                val items = vm.draft?.rounds?.firstOrNull { it.id == round.id }?.items
                                    ?: return@detectDragGesturesAfterLongPress
                                val cur = items.indexOfFirst { it.id == draggingId }
                                if (cur < 0) return@detectDragGesturesAfterLongPress
                                if (dragOffset > rowPx / 2 && cur < items.size - 1) {
                                    vm.moveItem(round.id, cur, cur + 1)
                                    dragOffset -= rowPx
                                } else if (dragOffset < -rowPx / 2 && cur > 0) {
                                    vm.moveItem(round.id, cur, cur - 1)
                                    dragOffset += rowPx
                                }
                            },
                        )
                    },
                )
            }
        }

        // Botones añadir.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AddButton(label = t.exercise, modifier = Modifier.weight(1f), onClick = onAddExercise)
            AddButton(label = t.rest, modifier = Modifier.weight(1f), onClick = { vm.addRest(round.id) })
        }
    }
}

@Composable
private fun AddButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(TRACK)
            .clickable { onClick() }
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ItemCard(
    vm: AthleteViewModel,
    t: Strings,
    roundId: Long,
    item: WorkoutItem,
    dragging: Boolean,
    dragOffset: Float,
    onClick: () -> Unit,
    dragModifier: Modifier,
) {
    val title: String
    val subtitle: String
    when (item) {
        is ExerciseItem -> {
            title = item.name.ifBlank { t.exercise }
            subtitle = if (item.mode == ExerciseMode.REPS) "${item.reps}x" else fmtSec(item.durationSec)
        }
        is RestItem -> {
            title = t.rest
            subtitle = fmtSec(item.durationSec)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ITEM_HEIGHT)
            .graphicsLayer { translationY = dragOffset }
            .clip(RoundedCornerShape(12.dp))
            .background(if (dragging) SURFACE else TRACK)
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Placeholder (animación futura del ejercicio).
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(BG),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TEXT_DIM, fontSize = 13.sp)
        }
        var menu by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = null, tint = TEXT_DIM)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(t.edit) },
                    onClick = { menu = false; onClick() },
                )
                DropdownMenuItem(
                    text = { Text(t.delete) },
                    onClick = { menu = false; vm.deleteItem(roundId, item.id) },
                )
                DropdownMenuItem(
                    text = { Text(t.duplicate) },
                    onClick = { menu = false; vm.duplicateItem(roundId, item.id) },
                )
            }
        }
        Icon(
            Icons.Filled.DragHandle,
            contentDescription = null,
            tint = TEXT_DIM,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(24.dp)
                .then(dragModifier),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseValueSheet(
    t: Strings,
    accent: Color,
    item: ExerciseItem,
    onSave: (ExerciseMode, Int, Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mode by remember { mutableStateOf(item.mode) }
    var value by remember {
        mutableStateOf(if (item.mode == ExerciseMode.REPS) item.reps else item.durationSec)
    }
    var ttp by remember { mutableStateOf(item.timeToPositionSec) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = SURFACE) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                t.specifyRepsDuration,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            BigNumber(value = value, accent = accent, onChange = { value = it })
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                UnitToggle(t.repsUnit, mode == ExerciseMode.REPS) { mode = ExerciseMode.REPS }
                UnitToggle(t.secUnit, mode == ExerciseMode.DURATION) { mode = ExerciseMode.DURATION }
            }
            if (mode == ExerciseMode.DURATION) {
                Spacer(Modifier.height(28.dp))
                Text(
                    t.specifyTimeToPosition,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                BigNumber(value = ttp, accent = accent, onChange = { ttp = it })
                Spacer(Modifier.height(12.dp))
                UnitToggle(t.secUnit, true) {}
            }
            Spacer(Modifier.height(28.dp))
            SaveButton(t.save) {
                if (mode == ExerciseMode.REPS) {
                    onSave(mode, value.coerceAtLeast(1), 0, 0)
                } else {
                    onSave(mode, item.reps, value.coerceAtLeast(1), ttp.coerceAtLeast(0))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestValueSheet(
    t: Strings,
    accent: Color,
    item: RestItem,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var value by remember { mutableStateOf(item.durationSec) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = SURFACE) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                t.specifyRestDuration,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            BigNumber(value = value, accent = accent, onChange = { value = it })
            Spacer(Modifier.height(12.dp))
            UnitToggle(t.secUnit, true) {}
            Spacer(Modifier.height(28.dp))
            SaveButton(t.save) { onSave(value.coerceAtLeast(1)) }
        }
    }
}

@Composable
private fun BigNumber(value: Int, accent: Color, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    BasicTextField(
        value = text,
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(4)
            text = digits
            onChange(digits.toIntOrNull() ?: 0)
        },
        singleLine = true,
        textStyle = TextStyle(
            color = Color.White,
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        ),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(accent),
        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
        modifier = Modifier.width(160.dp),
    )
}

@Composable
private fun UnitToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 72.dp, height = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (selected) Modifier.background(Color.White)
                else Modifier.border(1.dp, Color.White, RoundedCornerShape(8.dp))
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) Color.Black else Color.White, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SaveButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Color.Black,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .clickable { onClick() }
            .padding(vertical = 16.dp),
        textAlign = TextAlign.Center,
    )
}

private fun fmtSec(sec: Int): String =
    if (sec < 60) "${sec}s" else "%d:%02d".format(sec / 60, sec % 60)
