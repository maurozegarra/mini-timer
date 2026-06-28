package com.minitimer

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import com.minitimer.data.WorkoutStore
import com.minitimer.model.ExerciseItem
import com.minitimer.model.RestItem
import com.minitimer.model.Round
import com.minitimer.model.Workout

/**
 * Lógica de la sección "Athlete" (Your workouts). Mantiene la lista de workouts
 * y sus operaciones CRUD, persistidas con [WorkoutStore]. La navegación interna
 * (editor, player) se hará por estado en fases siguientes.
 */
class AthleteViewModel(app: Application) : AndroidViewModel(app) {

    private val store = WorkoutStore(app)

    /** Lista de workouts; el más reciente queda al inicio. */
    val workouts = mutableStateListOf<Workout>()

    private var nextId = 1L

    init {
        workouts.addAll(store.loadWorkouts())
        nextId = (allIds().maxOrNull() ?: 0L) + 1
    }

    private fun allIds(): List<Long> = buildList {
        workouts.forEach { w ->
            add(w.id)
            w.rounds.forEach { r ->
                add(r.id)
                r.items.forEach { add(it.id) }
            }
        }
    }

    /** Genera un id único para cualquier entidad (workout/round/item). */
    fun newId(): Long = nextId++

    private fun persist() = store.saveWorkouts(workouts.toList())

    fun createWorkout(name: String): Workout {
        val now = System.currentTimeMillis()
        val w = Workout(
            id = newId(),
            name = name.trim(),
            rounds = listOf(Round(id = newId())),
            createdAt = now,
            updatedAt = now,
        )
        workouts.add(0, w)
        persist()
        return w
    }

    fun renameWorkout(id: Long, name: String) {
        val i = workouts.indexOfFirst { it.id == id }
        if (i < 0) return
        workouts[i] = workouts[i].copy(
            name = name.trim(),
            updatedAt = System.currentTimeMillis(),
        )
        persist()
    }

    fun deleteWorkout(id: Long) {
        workouts.removeAll { it.id == id }
        persist()
    }

    fun duplicateWorkout(id: Long) {
        val src = workouts.firstOrNull { it.id == id } ?: return
        val now = System.currentTimeMillis()
        val copy = src.copy(
            id = newId(),
            name = duplicateName(src.name),
            rounds = src.rounds.map { r ->
                r.copy(
                    id = newId(),
                    items = r.items.map { item ->
                        when (item) {
                            is ExerciseItem -> item.copy(id = newId())
                            is RestItem -> item.copy(id = newId())
                        }
                    },
                )
            },
            createdAt = now,
            updatedAt = now,
        )
        val i = workouts.indexOfFirst { it.id == id }
        workouts.add(i + 1, copy)
        persist()
    }

    private fun duplicateName(name: String): String =
        if (name.isBlank()) name else "$name (copy)"
}
