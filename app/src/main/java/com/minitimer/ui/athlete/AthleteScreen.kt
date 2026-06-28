package com.minitimer.ui.athlete

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import com.minitimer.AthleteViewModel
import com.minitimer.i18n.Strings
import com.minitimer.model.Workout
import com.minitimer.ui.theme.ON_ACCENT
import com.minitimer.ui.theme.SURFACE
import com.minitimer.ui.theme.TEXT_DIM

/**
 * Sección Athlete (MVP "Your workouts"): lista de workouts creados con menú
 * Edit/Delete/Duplicate y píldora "Create Workout".
 */
@Composable
fun AthleteScreen(vm: AthleteViewModel, accent: Color, t: Strings) {
    var createOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Workout?>(null) }

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

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(vm.workouts, key = { it.id }) { w ->
                    WorkoutCard(
                        workout = w,
                        t = t,
                        onEdit = { renaming = w },
                        onDelete = { vm.deleteWorkout(w.id) },
                        onDuplicate = { vm.duplicateWorkout(w.id) },
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
                .clickable { createOpen = true }
                .padding(horizontal = 28.dp, vertical = 14.dp),
        )
    }

    if (createOpen) {
        NameDialog(
            title = t.createWorkout,
            initial = "",
            hint = t.workoutNameHint,
            confirmLabel = t.save,
            cancelLabel = t.cancel,
            onConfirm = { name ->
                vm.createWorkout(name)
                createOpen = false
            },
            onDismiss = { createOpen = false },
        )
    }

    renaming?.let { w ->
        NameDialog(
            title = t.rename,
            initial = w.name,
            hint = t.workoutNameHint,
            confirmLabel = t.save,
            cancelLabel = t.cancel,
            onConfirm = { name ->
                vm.renameWorkout(w.id, name)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
}

@Composable
private fun WorkoutCard(
    workout: Workout,
    t: Strings,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SURFACE)
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

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    hint: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text(hint) },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(cancelLabel) }
        },
    )
}
