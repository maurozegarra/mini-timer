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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.minitimer.model.WorkoutVariant
import com.minitimer.ui.theme.SURFACE
import com.minitimer.ui.theme.TEXT_DIM
import com.minitimer.ui.theme.TEXT_FADED
import com.minitimer.ui.theme.TRACK

/** Editor de un workout rotativo: lista de variantes que se alternan al completar. */
@Composable
fun VariantListScreen(vm: AthleteViewModel, accent: Color, t: Strings) {
    val workout = vm.editingWorkout() ?: return

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = vm.editorName(),
                    onValueChange = { vm.setEditorName(it) },
                    placeholder = { Text(t.workoutNameHint, color = TEXT_FADED) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = TRACK,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = accent,
                    ),
                )
            }

            itemsIndexed(workout.variants, key = { _, v -> v.id }) { index, v ->
                VariantRow(
                    variant = v,
                    index = index,
                    t = t,
                    onOpen = { vm.openVariant(v.id) },
                    onDuplicate = { vm.duplicateVariant(v.id) },
                    onDelete = { vm.deleteVariant(v.id) },
                )
            }

            item {
                AddButton(label = t.addVariant, accent = accent, onClick = { vm.addVariant() })
            }
        }

        PrimaryButton(
            label = t.save,
            accent = accent,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            onClick = { vm.closeWorkoutEditor() },
        )
    }
}

@Composable
private fun VariantRow(
    variant: WorkoutVariant,
    index: Int,
    t: Strings,
    onOpen: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SURFACE)
            .clickable(onClick = onOpen)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                variant.name.ifBlank { "${t.variant} ${index + 1}" },
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
            Text("${variant.exercises.size} ${t.exercise}", color = TEXT_DIM, fontSize = 13.sp)
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = null, tint = TEXT_DIM)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(t.duplicate) }, onClick = { menu = false; onDuplicate() })
                DropdownMenuItem(text = { Text(t.delete) }, onClick = { menu = false; onDelete() })
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TEXT_DIM)
    }
}
