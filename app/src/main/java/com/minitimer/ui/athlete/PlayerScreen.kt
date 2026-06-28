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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minitimer.AthleteViewModel
import com.minitimer.i18n.Strings
import com.minitimer.model.PlayerStep
import com.minitimer.model.StepKind
import com.minitimer.ui.theme.JetBrainsMono
import com.minitimer.ui.theme.ON_ACCENT
import com.minitimer.ui.theme.SURFACE
import com.minitimer.ui.theme.TEXT_DIM
import com.minitimer.ui.theme.TRACK

/** Player de la rutina: previa (Summary + Start), recorrido y pantalla final. */
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
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    t.summary,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            val steps = vm.playerSteps
            items(steps.indices.toList()) { i ->
                val step = steps[i]
                val showRound = step.totalRounds > 1 && (i == 0 || steps[i - 1].roundIndex != step.roundIndex)
                Column {
                    if (showRound) {
                        Text(
                            "${t.round} ${step.roundIndex + 1}/${step.totalRounds}",
                            color = TEXT_DIM,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    SummaryRow(step, t)
                }
            }
        }

        Text(
            t.start,
            color = ON_ACCENT,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White)
                .clickable { vm.startPlayerRun() }
                .padding(horizontal = 40.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun SummaryRow(step: PlayerStep, t: Strings) {
    val label = stepLabel(step, t)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SURFACE)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White)
    }
}

@Composable
private fun RunningView(vm: AthleteViewModel, accent: Color, t: Strings) {
    val kind = vm.playerStepKind
    val title = when (kind) {
        StepKind.PREP -> t.getReady
        StepKind.REST -> t.rest
        else -> vm.playerStepTitle.ifBlank { t.exercise }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (vm.playerTotalRounds > 1) {
            Text(
                "${t.round} ${vm.playerRoundIndex + 1}/${vm.playerTotalRounds}",
                color = TEXT_DIM,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(8.dp))
        }
        Text(
            title,
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(36.dp))

        if (kind == StepKind.REPS) {
            Text(
                "${vm.playerReps}x",
                color = accent,
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = JetBrainsMono,
            )
            Spacer(Modifier.height(36.dp))
            PillButton(t.doneLabel, filled = true) { vm.nextStep() }
        } else {
            Text(
                fmtClock(vm.playerRemainingMs),
                color = Color.White,
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = JetBrainsMono,
            )
            Spacer(Modifier.height(36.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(if (vm.playerRunning) t.pause else t.resume, filled = false) {
                    if (vm.playerRunning) vm.pausePlayer() else vm.resumePlayer()
                }
                PillButton(t.nextLabel, filled = true) { vm.nextStep() }
            }
        }
    }
}

@Composable
private fun FinishedView(vm: AthleteViewModel, accent: Color, t: Strings) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            t.workoutComplete,
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(vm.playerName, color = TEXT_DIM, fontSize = 16.sp)
        Spacer(Modifier.height(36.dp))
        PillButton(t.close, filled = true) { vm.closePlayer() }
    }
}

@Composable
private fun PillButton(label: String, filled: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (filled) ON_ACCENT else Color.White,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(28.dp))
            .background(if (filled) Color.White else TRACK)
            .clickable { onClick() }
            .padding(horizontal = 32.dp, vertical = 14.dp),
    )
}

private fun stepLabel(step: PlayerStep, t: Strings): String = when (step.kind) {
    StepKind.PREP -> "${fmtSec(step.durationSec)} · ${t.getReady}"
    StepKind.TIMED -> "${fmtSec(step.durationSec)} · ${step.title}"
    StepKind.REPS -> "${step.reps}x · ${step.title}"
    StepKind.REST -> "${fmtSec(step.durationSec)} · ${t.rest}"
}

private fun fmtSec(sec: Int): String =
    if (sec < 60) "${sec}s" else "%d:%02d".format(sec / 60, sec % 60)

private fun fmtClock(ms: Long): String {
    val total = ((ms + 999) / 1000).toInt()
    return "%d:%02d".format(total / 60, total % 60)
}
