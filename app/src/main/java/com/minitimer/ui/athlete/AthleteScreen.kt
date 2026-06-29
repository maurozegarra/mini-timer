package com.minitimer.ui.athlete

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minitimer.AthleteViewModel
import com.minitimer.i18n.Strings
import com.minitimer.model.Workout
import com.minitimer.ui.theme.ON_ACCENT
import com.minitimer.ui.theme.SURFACE
import com.minitimer.ui.theme.TEXT_DIM
import com.minitimer.ui.theme.TRACK

/**
 * Sección Athlete (MVP "Your workouts"): lista de workouts creados con menú
 * Edit/Delete/Duplicate y píldora "Create Workout".
 */
@Composable
fun AthleteScreen(vm: AthleteViewModel, accent: Color, t: Strings) {
    if (vm.playerWorkoutId != null) {
        PlayerScreen(vm, accent, t)
        return
    }
    if (vm.draft != null) {
        if (vm.choosingForRound != null) {
            ChooseExerciseScreen(vm, accent, t)
        } else {
            WorkoutEditorScreen(vm, accent, t)
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Tab "Created" (sin Favorites en MVP).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        t.createdTab,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .width(72.dp)
                            .height(2.dp)
                            .background(accent),
                    )
                }
            }

            Text(
                t.savedHint,
                color = TEXT_DIM,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            val density = LocalDensity.current
            val spacingPx = with(density) { 12.dp.toPx() }
            var draggingId by remember { mutableStateOf<Long?>(null) }
            var dragOffset by remember { mutableStateOf(0f) }
            var rowHeightPx by remember { mutableStateOf(0f) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                vm.workouts.forEach { w ->
                    val dragging = w.id == draggingId
                    WorkoutCard(
                        workout = w,
                        t = t,
                        dragging = dragging,
                        dragOffset = if (dragging) dragOffset else 0f,
                        onHeight = { h -> if (rowHeightPx == 0f) rowHeightPx = h.toFloat() },
                        onOpen = { vm.openPlayer(w.id) },
                        onEdit = { vm.startEditWorkout(w.id) },
                        onDelete = { vm.deleteWorkout(w.id) },
                        onDuplicate = { vm.duplicateWorkout(w.id) },
                        dragModifier = Modifier.pointerInput(w.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { draggingId = w.id; dragOffset = 0f },
                                onDragEnd = { draggingId = null; dragOffset = 0f },
                                onDragCancel = { draggingId = null; dragOffset = 0f },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    val step = rowHeightPx + spacingPx
                                    if (step <= 0f) return@detectDragGesturesAfterLongPress
                                    val cur = vm.workouts.indexOfFirst { it.id == draggingId }
                                    if (cur < 0) return@detectDragGesturesAfterLongPress
                                    if (dragOffset > step / 2 && cur < vm.workouts.size - 1) {
                                        vm.moveWorkout(cur, cur + 1)
                                        dragOffset -= step
                                    } else if (dragOffset < -step / 2 && cur > 0) {
                                        vm.moveWorkout(cur, cur - 1)
                                        dragOffset += step
                                    }
                                },
                            )
                        },
                    )
                }
            }
        }

        // Píldora "Create Workout".
        Text(
            t.createWorkout,
            color = ON_ACCENT,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White)
                .clickable { vm.startNewWorkout() }
                .padding(horizontal = 28.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun WorkoutCard(
    workout: Workout,
    t: Strings,
    dragging: Boolean,
    dragOffset: Float,
    onHeight: (Int) -> Unit,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    dragModifier: Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onHeight(it.height) }
            .graphicsLayer {
                translationY = dragOffset
                if (dragging) { scaleX = 1.03f; scaleY = 1.03f }
            }
            .clip(RoundedCornerShape(16.dp))
            .background(if (dragging) TRACK else SURFACE)
            // Tap para abrir (exterior) + mantener presionado para reordenar (interior).
            .pointerInput(workout.id) { detectTapGestures(onTap = { onOpen() }) }
            .then(dragModifier)
            .padding(start = 20.dp, end = 8.dp, top = 18.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            workout.name.ifBlank { t.noName },
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = null, tint = TEXT_DIM)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(t.edit) },
                    onClick = { menu = false; onEdit() },
                )
                DropdownMenuItem(
                    text = { Text(t.delete) },
                    onClick = { menu = false; onDelete() },
                )
                DropdownMenuItem(
                    text = { Text(t.duplicate) },
                    onClick = { menu = false; onDuplicate() },
                )
            }
        }
    }
}

