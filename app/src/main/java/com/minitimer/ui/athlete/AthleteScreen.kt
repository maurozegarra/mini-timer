package com.minitimer.ui.athlete

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minitimer.AthleteViewModel
import com.minitimer.i18n.Strings
import com.minitimer.model.Training
import com.minitimer.model.hasContent
import com.minitimer.ui.theme.ON_ACCENT
import com.minitimer.ui.theme.SURFACE
import com.minitimer.ui.theme.TEXT_DIM

/** Router de la sección Athlete según el estado de navegación del ViewModel. */
@Composable
fun AthleteScreen(vm: AthleteViewModel, accent: Color, t: Strings) {
    when {
        vm.playerTrainingId != null -> PlayerScreen(vm, accent, t)
        vm.choosingExercise -> ChooseExerciseScreen(vm, accent, t)
        vm.editingExerciseId != null -> ExerciseEditorScreen(vm, accent, t)
        vm.editingVariantId != null -> WorkoutEditorScreen(vm, accent, t)
        vm.editingWorkoutId != null && vm.editingWorkout()?.rotating == true -> VariantListScreen(vm, accent, t)
        vm.editingWorkoutId != null -> WorkoutEditorScreen(vm, accent, t)
        vm.draft != null -> TrainingEditorScreen(vm, accent, t)
        else -> TrainingsList(vm, accent, t)
    }
}

@Composable
private fun TrainingsList(vm: AthleteViewModel, accent: Color, t: Strings) {
    Box(Modifier.fillMaxSize()) {
        if (vm.trainings.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(t.emptyTrainings, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text(t.savedHint, color = TEXT_DIM, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(vm.trainings, key = { it.id }) { tr ->
                    TrainingCard(
                        training = tr,
                        accent = accent,
                        t = t,
                        onPlay = { vm.openPlayer(tr.id) },
                        onEdit = { vm.startEditTraining(tr.id) },
                        onDuplicate = { vm.duplicateTraining(tr.id) },
                        onDelete = { vm.deleteTraining(tr.id) },
                    )
                }
            }
        }

        PrimaryButton(
            label = "+  ${t.createTraining}",
            accent = accent,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            onClick = { vm.startNewTraining() },
        )
    }
}

@Composable
private fun TrainingCard(
    training: Training,
    accent: Color,
    t: Strings,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val exercises = training.workouts.sumOf { w ->
        if (w.variants.isNotEmpty()) w.variants.sumOf { it.exercises.size } else w.exercises.size
    }
    val canPlay = training.workouts.any { it.hasContent() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SURFACE)
            .clickable(enabled = canPlay, onClick = onPlay)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                training.name.ifBlank { t.noName },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Text(
                "${training.workouts.size} ${t.workout} · $exercises ${t.exercise}",
                color = TEXT_DIM,
                fontSize = 13.sp,
            )
        }

        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = null, tint = TEXT_DIM)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(t.edit) }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(text = { Text(t.duplicate) }, onClick = { menu = false; onDuplicate() })
                DropdownMenuItem(text = { Text(t.delete) }, onClick = { menu = false; onDelete() })
            }
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (canPlay) accent else SURFACE)
                .clickable(enabled = canPlay, onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = t.start,
                tint = if (canPlay) ON_ACCENT else TEXT_DIM,
            )
        }
    }
}
