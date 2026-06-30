package com.minitimer.ui.athlete

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minitimer.AthleteViewModel
import com.minitimer.i18n.Strings
import com.minitimer.ui.AnimatedGlowBorder
import com.minitimer.ui.glowColors
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

private data class PreviewExercise(val name: String, val exerciseId: String, val meta: String)

private data class PreviewGroup(
    val index: Int,
    val title: String,
    val rotating: Boolean,
    val variant: String,
    val durationSec: Int,
    val exercises: List<PreviewExercise>,
)

private fun metaFor(s: PlayerStep): String = when {
    s.timeBased -> formatRemaining(s.durationSec * 1000L)
    s.totalSets > 1 && s.reps > 1 -> "${s.totalSets}×${s.reps}"
    s.totalSets > 1 -> "${s.totalSets}×"
    s.reps > 0 -> "×${s.reps}"
    else -> ""
}

private fun buildPreviewGroups(steps: List<PlayerStep>): List<PreviewGroup> =
    steps.groupBy { it.workoutIndex }.entries.sortedBy { it.key }.map { (idx, list) ->
        val first = list.first()
        val exercises = list.filter { it.kind == StepKind.WORK }
            .distinctBy { it.ownerName + "|" + it.ownerExerciseId }
            .map { s -> PreviewExercise(s.ownerName, s.ownerExerciseId, metaFor(s)) }
        PreviewGroup(
            index = idx,
            title = first.workoutBaseName.ifBlank { first.workoutName },
            rotating = first.rotating,
            variant = first.variantName,
            durationSec = list.sumOf { it.durationSec },
            exercises = exercises,
        )
    }

@Composable
private fun PreviewView(vm: AthleteViewModel, accent: Color, t: Strings) {
    val steps = vm.playerSteps
    val groups = remember(steps) { buildPreviewGroups(steps) }
    val totalExercises = groups.sumOf { it.exercises.size }
    val expanded = remember(steps) { mutableStateMapOf<Int, Boolean>() }
    val firstIndex = groups.firstOrNull()?.index

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(vm.playerName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text(
                    "$totalExercises ${t.exercise} · ${groups.size} ${t.workout}",
                    color = TEXT_DIM,
                    fontSize = 14.sp,
                )
            }
            items(groups, key = { it.index }) { g ->
                val open = expanded[g.index] ?: (g.index == firstIndex)
                WorkoutGroupCard(g, open, accent, t) { expanded[g.index] = !open }
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
private fun WorkoutGroupCard(
    g: PreviewGroup,
    open: Boolean,
    accent: Color,
    t: Strings,
    onToggle: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SURFACE)
            .clickable(onClick = onToggle)
            .padding(14.dp)
            .animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        g.title.ifBlank { t.workout },
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                    if (g.rotating) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(accent.copy(alpha = 0.22f))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(t.rotatingTag, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                val sub = buildString {
                    if (g.rotating && g.variant.isNotBlank()) {
                        append("${t.activeVariantLabel}: ${g.variant}")
                    } else {
                        append("${g.exercises.size} ${t.exercise}")
                    }
                    if (g.durationSec > 0) append(" · ${formatRemaining(g.durationSec * 1000L)}")
                }
                Text(sub, color = TEXT_DIM, fontSize = 12.sp)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TEXT_DIM,
                modifier = Modifier.rotate(if (open) 90f else 0f),
            )
        }
        if (open) {
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                g.exercises.forEachIndexed { i, ex ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TimelineRail(isFirst = i == 0, isLast = i == g.exercises.lastIndex, accent = accent)
                        Spacer(Modifier.width(10.dp))
                        Row(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(TRACK)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ExerciseGlyph(name = ex.name, color = 0xFF2E9E5BL, sizeDp = 30, exerciseId = ex.exerciseId)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                ex.name,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            if (ex.meta.isNotBlank()) {
                                Text(ex.meta, color = TEXT_DIM, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineRail(isFirst: Boolean, isLast: Boolean, accent: Color) {
    Box(
        Modifier
            .width(18.dp)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(if (isFirst) Color.Transparent else TRACK),
            )
            Box(
                Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(if (isLast) Color.Transparent else TRACK),
            )
        }
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (isFirst) accent else TEXT_DIM),
        )
    }
}

@Composable
private fun WorkoutProgressBar(step: PlayerStep, accent: Color, t: Strings) {
    val name = step.workoutName.ifBlank { step.workoutBaseName }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.20f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${t.workout} ${step.workoutIndex + 1} / ${step.totalWorkouts}",
                color = TEXT_DIM,
                fontSize = 12.sp,
            )
            if (name.isNotBlank()) {
                Text(name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(step.totalWorkouts) { i ->
                val c = when {
                    i < step.workoutIndex -> accent
                    i == step.workoutIndex -> Color.White
                    else -> Color.White.copy(alpha = 0.25f)
                }
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(c),
                )
            }
        }
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

    Box(
        Modifier
            .fillMaxSize()
            .background(color.copy(alpha = 0.16f)),
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (step.totalWorkouts > 1) {
            WorkoutProgressBar(step, accent, t)
        }
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
        AnimatedGlowBorder(cornerRadius = 0.dp, colors = glowColors(color), strokeWidth = 3.dp)
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
