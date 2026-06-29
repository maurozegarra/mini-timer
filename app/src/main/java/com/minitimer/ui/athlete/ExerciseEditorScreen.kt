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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minitimer.AthleteViewModel
import com.minitimer.i18n.Strings
import com.minitimer.model.ConfirmMode
import com.minitimer.model.DisplayMode
import com.minitimer.model.Exercise
import com.minitimer.model.StageConfig
import com.minitimer.model.StepKind
import com.minitimer.model.WeightType
import com.minitimer.model.WorkMode
import com.minitimer.model.WorkSet
import com.minitimer.model.setAt
import com.minitimer.ui.theme.BG
import com.minitimer.ui.theme.SURFACE
import com.minitimer.ui.theme.TEXT_DIM
import com.minitimer.ui.theme.TRACK

@Composable
fun ExerciseEditorScreen(vm: AthleteViewModel, accent: Color, t: Strings) {
    val initial = vm.editingExercise() ?: return
    var ex by remember(initial.id) { mutableStateOf(initial) }
    var tab by remember { mutableStateOf("simple") }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SegmentToggle(
                    options = listOf("simple" to t.simple, "advanced" to t.advanced),
                    selected = tab,
                    accent = accent,
                    onSelect = { tab = it },
                )
            }

            if (tab == "simple") {
                item { SimpleTab(ex, accent, t) { ex = it } }
            } else {
                item { AdvancedTab(ex, accent, t) { ex = it } }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SURFACE)
                    .clickable { vm.deleteExercise(ex.id); vm.closeExerciseEditor() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = t.delete, tint = TEXT_DIM)
            }
            PrimaryButton(
                label = t.save,
                accent = accent,
                modifier = Modifier.weight(1f),
                onClick = { vm.saveExercise(ex) },
            )
        }
    }
}

@Composable
private fun SimpleTab(ex: Exercise, accent: Color, t: Strings, onChange: (Exercise) -> Unit) {
    SectionCard {
        Stepper(t.prepare, ex.prepareSec, accent, min = 0, max = 600, step = 5, format = ::fmtSec) {
            onChange(ex.copy(prepareSec = it))
        }
        VSpace(14)
        Stepper(t.setsLabel, ex.sets, accent, min = 1, max = 30) {
            onChange(ex.copy(sets = it))
        }
        VSpace(14)
        Text(t.work, color = TEXT_DIM, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        VSpace(8)
        SegmentToggle(
            options = listOf("TIME" to t.secUnit, "REPS" to t.repsUnit),
            selected = ex.workMode.name,
            accent = accent,
        ) { onChange(ex.copy(workMode = WorkMode.valueOf(it))) }
        VSpace(10)
        if (ex.workMode == WorkMode.TIME) {
            Stepper(t.secUnit, ex.workValue, accent, min = 1, max = 3600, step = 5, format = ::fmtSec) {
                onChange(ex.copy(workValue = it))
            }
        } else {
            Stepper(t.repsUnit, ex.workValue, accent, min = 1, max = 200) {
                onChange(ex.copy(workValue = it))
            }
        }
        VSpace(14)
        Stepper(t.rest, ex.restSec, accent, min = 0, max = 600, step = 5, format = ::fmtSec) {
            onChange(ex.copy(restSec = it))
        }
        VSpace(10)
        ToggleRow(t.restSkipLast, ex.restSkipOnLastSet, accent) {
            onChange(ex.copy(restSkipOnLastSet = it))
        }
        VSpace(14)
        Stepper(t.cooldown, ex.cooldownSec, accent, min = 0, max = 600, step = 5, format = ::fmtSec) {
            onChange(ex.copy(cooldownSec = it))
        }
    }

    if (ex.workMode == WorkMode.REPS) {
        VSpace(14)
        WeightSection(ex, accent, t, onChange)
    }
}

@Composable
private fun WeightSection(ex: Exercise, accent: Color, t: Strings, onChange: (Exercise) -> Unit) {
    SectionCard {
        Text(t.weight, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        VSpace(10)
        SegmentToggle(
            options = listOf(
                "NONE" to t.weightNone,
                "TOTAL" to t.weightTotalLabel,
                "BARBELL" to t.weightBarbell,
                "DUMBBELL" to t.weightDumbbell,
            ),
            selected = ex.weightType.name,
            accent = accent,
        ) { sel ->
            val type = WeightType.valueOf(sel)
            val list = if (type == WeightType.NONE) emptyList()
            else (0 until ex.sets).map { ex.setAt(it) }
            onChange(ex.copy(weightType = type, setList = list))
        }

        if (ex.weightType == WeightType.BARBELL) {
            VSpace(12)
            WeightStepper(t.barWeight, ex.barWeight, accent) { onChange(ex.copy(barWeight = it)) }
        }

        if (ex.weightType != WeightType.NONE) {
            VSpace(12)
            val sets = (0 until ex.sets).map { ex.setAt(it) }
            sets.forEachIndexed { i, ws ->
                SetRow(
                    index = i,
                    set = ws,
                    type = ex.weightType,
                    accent = accent,
                    t = t,
                    onChange = { newSet ->
                        val list = sets.toMutableList().also { it[i] = newSet }
                        onChange(ex.copy(setList = list))
                    },
                )
                if (i < sets.size - 1) VSpace(8)
            }
        }
    }
}

@Composable
private fun SetRow(
    index: Int,
    set: WorkSet,
    type: WeightType,
    accent: Color,
    t: Strings,
    onChange: (WorkSet) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TRACK)
            .padding(12.dp),
    ) {
        Text("SET ${index + 1}", color = TEXT_DIM, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        VSpace(6)
        Stepper(t.repsUnit, set.reps, accent, min = 1, max = 200) { onChange(set.copy(reps = it)) }
        VSpace(8)
        val label = when (type) {
            WeightType.DUMBBELL -> t.perHand
            WeightType.BARBELL -> t.plates
            else -> t.weight
        }
        WeightStepper(label, set.weight, accent) { onChange(set.copy(weight = it)) }
    }
}

@Composable
private fun WeightStepper(label: String, value: Double, accent: Color, onChange: (Double) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        StepBox("−", accent) { onChange((value - 2.5).coerceAtLeast(0.0)) }
        Box(Modifier.width(72.dp), contentAlignment = Alignment.Center) {
            Text("${fmtKg(value)} kg", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        StepBox("+", accent) { onChange(value + 2.5) }
    }
}

@Composable
private fun StepBox(symbol: String, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(SURFACE)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(symbol, color = accent, fontWeight = FontWeight.Bold, fontSize = 22.sp) }
}

@Composable
private fun AdvancedTab(ex: Exercise, accent: Color, t: Strings, onChange: (Exercise) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StageCard(t.prepare, ex.prepareCfg, StepKind.PREP, accent, t) { onChange(ex.copy(prepareCfg = it)) }
        StageCard(t.work, ex.workCfg, StepKind.WORK, accent, t) { onChange(ex.copy(workCfg = it)) }
        StageCard(t.rest, ex.restCfg, StepKind.REST, accent, t) { onChange(ex.copy(restCfg = it)) }
        StageCard(t.cooldown, ex.cooldownCfg, StepKind.COOLDOWN, accent, t) { onChange(ex.copy(cooldownCfg = it)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StageCard(
    title: String,
    cfg: StageConfig,
    kind: StepKind,
    accent: Color,
    t: Strings,
    onChange: (StageConfig) -> Unit,
) {
    var showColors by remember { mutableStateOf(false) }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            ColorDot(
                color = cfg.color,
                size = 26,
                modifier = Modifier.clip(CircleShape).clickable { showColors = true },
            )
        }
        VSpace(12)
        Text(t.displayLabel, color = TEXT_DIM, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        VSpace(6)
        SegmentToggle(
            options = listOf(
                "COUNTDOWN" to t.displayCountdown,
                "STATIC" to t.displayStatic,
                "COUNTUP" to t.displayCountup,
            ),
            selected = cfg.display.name,
            accent = accent,
        ) { onChange(cfg.copy(display = DisplayMode.valueOf(it))) }
        VSpace(12)
        ToggleRow(t.alarmLabel, cfg.alarm, accent) { onChange(cfg.copy(alarm = it)) }
        VSpace(10)
        Stepper(t.finalCountLabel, cfg.finalCount, accent, min = 0, max = 10) {
            onChange(cfg.copy(finalCount = it))
        }
        VSpace(12)
        Text(t.advanceLabel, color = TEXT_DIM, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        VSpace(6)
        SegmentToggle(
            options = listOf("AUTO" to t.advanceAuto, "MANUAL" to t.advanceManual),
            selected = cfg.confirm.name,
            accent = accent,
        ) { onChange(cfg.copy(confirm = ConfirmMode.valueOf(it))) }
    }

    if (showColors) {
        ModalBottomSheet(
            onDismissRequest = { showColors = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = BG,
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(STAGE_COLORS) { c ->
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .clickable { onChange(cfg.copy(color = c)); showColors = false },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (c == cfg.color) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                        }
                    }
                }
            }
            VSpace(16)
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, accent: Color, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accent,
                uncheckedTrackColor = TRACK,
            ),
        )
    }
}
