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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
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
import com.minitimer.model.ExerciseDef
import com.minitimer.ui.theme.SURFACE
import com.minitimer.ui.theme.TEXT_DIM
import com.minitimer.ui.theme.TEXT_FADED
import com.minitimer.ui.theme.TRACK

@Composable
fun ChooseExerciseScreen(vm: AthleteViewModel, accent: Color, t: Strings) {
    var query by remember { mutableStateOf("") }
    val all = remember { vm.catalog() }
    val q = query.trim().lowercase()
    val filtered = if (q.isEmpty()) all else all.filter { it.name.lowercase().contains(q) }
    val exactMatch = all.any { it.name.equals(query.trim(), ignoreCase = true) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(t.searchHint, color = TEXT_FADED) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TEXT_DIM) },
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

        Spacer(Modifier.width(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (query.isNotBlank() && !exactMatch) {
                item {
                    CreateCustomRow(name = query.trim(), accent = accent, t = t) {
                        val def = vm.addCustomExercise(query.trim())
                        vm.pickExercise(def)
                    }
                }
            }
            items(filtered, key = { it.id }) { def ->
                ExercisePickRow(def = def) { vm.pickExercise(def) }
            }
        }
    }
}

@Composable
private fun ExercisePickRow(def: ExerciseDef, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SURFACE)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExerciseGlyph(name = def.name, color = 0xFF2E9E5BL, sizeDp = 38, exerciseId = def.id)
        Spacer(Modifier.width(12.dp))
        Text(def.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CreateCustomRow(name: String, accent: Color, t: Strings, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SURFACE)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = accent)
        Spacer(Modifier.width(12.dp))
        Text("${t.add} \"$name\"", color = accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
