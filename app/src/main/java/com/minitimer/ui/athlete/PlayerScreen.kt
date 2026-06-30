package com.minitimer.ui.athlete

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minitimer.AthleteViewModel
import com.minitimer.i18n.Strings
import com.minitimer.model.DisplayMode
import com.minitimer.model.PlayerStep
import com.minitimer.model.StepKind
import com.minitimer.ui.theme.ON_ACCENT
import com.minitimer.ui.theme.SURFACE
import com.minitimer.ui.theme.TEXT_DIM
import com.minitimer.ui.theme.TRACK
import com.minitimer.util.formatRemaining

@Composable
fun PlayerScreen(vm: AthleteViewModel, accent: Color, t: Strings) {
    when {
        vm.playerFinished -> FinishedView(vm, accent, t)
        !vm.playerStarted -> PreviewView(vm, accent, t)
        else -> RunningView(vm, accent, t)
    }
}

@Composable
private fun PreviewView(vm: AthleteViewModel, accent: Color, t: Strings) {
    val steps = vm.playerSteps
    val exercises = steps.filter { it.kind == StepKind.WORK }
        .map { Triple(it.workoutName, it.ownerName, it.ownerExerciseId) }
        .distinct()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(vm.playerName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text("${exercises.size} ${t.exercise}", color = TEXT_DIM, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
            }
            items(exercises) { (workout, name, exId) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SURFACE)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ExerciseGlyph(name = name, color = 0xFF2E9E5BL, sizeDp = 38, exerciseId = exId)
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text(name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        if (workout.isNotBlank()) Text(workout, color = TEXT_DIM, fontSize = 12.sp)
                    }
                }
            }
        }
        PrimaryButton(
            label = t.start,
            accent = accent,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            onClick = { vm.startPlayerRun() },
        )
    }
}

@Composable
private fun RunningView(vm: AthleteViewModel, accent: Color, t: Strings) {
    val step = vm.playerStep ?: return
    val color = Color(step.colorArgb)
    val stageLabel = when (step.kind) {
        StepKind.PREP -> t.prepare
        StepKind.WORK -> step.title.ifBlank { t.exercise }
        StepKind.REST -> t.rest
        StepKind.COOLDOWN -> t.cooldown
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color.copy(alpha = 0.16f))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        if (step.kind != StepKind.WORK && step.ownerName.isNotBlank()) {
            ExerciseGlyph(name = step.ownerName, color = step.colorArgb, sizeDp = 40, exerciseId = step.ownerExerciseId)
            Spacer(Modifier.height(8.dp))
        }
        val repByRep = step.kind == StepKind.WORK && !step.timeBased && step.reps == 1 && step.totalSets > 1
        Text(stageLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 26.sp, textAlign = TextAlign.Center)
        if (step.note.isNotBlank()) {
            Text(step.note, color = TEXT_DIM, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        if (step.kind != StepKind.WORK && step.ownerName.isNotBlank()) {
            Text(step.ownerName, color = TEXT_DIM, fontSize = 15.sp)
        }
        if (step.kind == StepKind.WORK && step.totalSets > 1 && !repByRep) {
            Text("${step.setIndex + 1} ${t.ofLabel} ${step.totalSets}", color = TEXT_DIM, fontSize = 15.sp)
        }

        Spacer(Modifier.height(28.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (step.kind == StepKind.WORK && !step.timeBased) {
                RepsDisplay(step, repByRep, t)
            } else {
                ClockDisplay(step, vm.playerRemainingMs, color)
            }
        }

        if (step.weighted) {
            WeightFeedback(vm, step, accent, t)
            Spacer(Modifier.height(12.dp))
        }

        Controls(vm, step, accent, t)
    }
}

@Composable
private fun RepsDisplay(step: PlayerStep, repByRep: Boolean, t: Strings) {
    val transition = rememberInfiniteTransition(label = "bob")
    val offset by transition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "bobOffset",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.graphicsLayer { translationY = offset }) {
            ExerciseGlyph(name = step.ownerName, color = step.colorArgb, sizeDp = 96, exerciseId = step.ownerExerciseId)
        }
        Spacer(Modifier.height(16.dp))
        if (repByRep) {
            Text(t.repLabel, color = TEXT_DIM, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("${step.setIndex + 1} / ${step.totalSets}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 56.sp)
        } else {
            Text("× ${step.reps}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 56.sp)
        }
    }
}

@Composable
private fun ClockDisplay(step: PlayerStep, remainingMs: Long, color: Color) {
    val shown = if (step.display == DisplayMode.COUNTUP) {
        (step.durationSec * 1000L - remainingMs).coerceAtLeast(0L)
    } else remainingMs
    Text(formatRemaining(shown), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 72.sp)
}

@Composable
private fun WeightFeedback(vm: AthleteViewModel, step: PlayerStep, accent: Color, t: Strings) {
    val current = vm.weightFeedback[step.ownerName]?.second
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SURFACE)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "${fmtKg(step.weightTotal)} ${t.kg}" + if (step.weightLabel.isNotBlank()) "  ·  ${step.weightLabel}" else "",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(t.howWeightFelt, color = TEXT_DIM, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeedbackChip("${t.tooHeavy} ↓", current == -2.5, accent) {
                vm.recordFeedback(step.ownerName, step.weightTotal, -2.5)
            }
            FeedbackChip(t.justRight, current == 0.0, accent) {
                vm.recordFeedback(step.ownerName, step.weightTotal, 0.0)
            }
            FeedbackChip("${t.tooLight} ↑", current == 2.5, accent) {
                vm.recordFeedback(step.ownerName, step.weightTotal, 2.5)
            }
        }
    }
}

@Composable
private fun FeedbackChip(label: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) accent else TRACK)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) ON_ACCENT else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun Controls(vm: AthleteViewModel, step: PlayerStep, accent: Color, t: Strings) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!step.manual) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(SURFACE)
                    .clickable { if (vm.playerRunning) vm.pausePlayer() else vm.resumePlayer() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (vm.playerRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }
        PrimaryButton(
            label = if (step.manual) t.doneLabel else t.nextLabel,
            accent = accent,
            modifier = Modifier.weight(1f),
            onClick = { vm.nextStep() },
        )
    }
}

@Composable
private fun FinishedView(vm: AthleteViewModel, accent: Color, t: Strings) {
    val suggestions = vm.weightSuggestions()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 32.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Text("🎉", fontSize = 56.sp)
                Text(t.workoutComplete, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                Spacer(Modifier.height(8.dp))
            }
            if (suggestions.isNotEmpty()) {
                item {
                    Text(t.nextSuggestions, color = TEXT_DIM, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                }
                items(suggestions) { (name, _, next) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SURFACE)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(name, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text("${fmtKg(next)} ${t.kg}", color = accent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
        PrimaryButton(
            label = t.close,
            accent = accent,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            onClick = { vm.closePlayer() },
        )
    }
}
