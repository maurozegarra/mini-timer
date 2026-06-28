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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import com.minitimer.AthleteViewModel
import com.minitimer.i18n.Strings
import com.minitimer.model.ExerciseDef
import com.minitimer.ui.theme.BG
import com.minitimer.ui.theme.TEXT_DIM
import com.minitimer.ui.theme.TRACK

/** Selector de ejercicios (catálogo base + propios) con buscador. */
@Composable
fun ChooseExerciseScreen(vm: AthleteViewModel, accent: Color, t: Strings) {
    val lang = t.locale.language
    var query by remember { mutableStateOf("") }

    val all = vm.catalog(lang)
    val filtered = if (query.isBlank()) all
    else all.filter { it.name.contains(query.trim(), ignoreCase = true) }
    val showCreate = query.isNotBlank() && filtered.none { it.name.equals(query.trim(), ignoreCase = true) }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TEXT_DIM) },
            placeholder = { Text(t.searchHint, color = TEXT_DIM) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (showCreate) {
                item(key = "create") {
                    CreateRow(label = "${t.add} \"${query.trim()}\"") {
                        val def = vm.addCustomExercise(query)
                        vm.pickExercise(def)
                    }
                }
            }
            items(filtered, key = { it.id }) { def ->
                ExerciseRow(def = def) { vm.pickExercise(def) }
            }
        }
    }
}

@Composable
private fun ExerciseRow(def: ExerciseDef, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(TRACK)
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Placeholder (animación futura del ejercicio).
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(BG),
        )
        Spacer(Modifier.width(14.dp))
        Text(def.name, color = Color.White, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CreateRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(TRACK)
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(BG),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White)
        }
        Spacer(Modifier.width(14.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.Medium)
    }
}
