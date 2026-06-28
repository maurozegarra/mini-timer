package com.minitimer

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.minitimer.data.WorkoutStore
import com.minitimer.model.ExerciseItem
import com.minitimer.model.ExerciseMode
import com.minitimer.model.RestItem
import com.minitimer.model.Round
import com.minitimer.model.Workout
import com.minitimer.model.WorkoutItem

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

    // ---------- Editor (draft) ----------

    /** Workout en edición; null = se muestra la lista. */
    var draft by mutableStateOf<Workout?>(null)
        private set

    private var draftIsNew = false

    /** Se puede guardar si tiene nombre y al menos un item en algún round. */
    val draftValid: Boolean
        get() = draft?.let { d ->
            d.name.isNotBlank() && d.rounds.any { it.items.isNotEmpty() }
        } ?: false

    fun startNewWorkout() {
        val now = System.currentTimeMillis()
        draft = Workout(
            id = newId(),
            name = "",
            rounds = listOf(Round(id = newId())),
            createdAt = now,
            updatedAt = now,
        )
        draftIsNew = true
    }

    fun startEditWorkout(id: Long) {
        draft = workouts.firstOrNull { it.id == id } ?: return
        draftIsNew = false
    }

    fun closeEditor() {
        draft = null
    }

    fun saveDraft() {
        val d = draft ?: return
        if (d.name.isBlank()) return
        val updated = d.copy(updatedAt = System.currentTimeMillis())
        val idx = workouts.indexOfFirst { it.id == d.id }
        if (idx >= 0) workouts[idx] = updated else workouts.add(0, updated)
        persist()
        draft = null
    }

    private fun updateDraft(transform: (Workout) -> Workout) {
        draft = draft?.let(transform)
    }

    private fun updateRound(roundId: Long, transform: (Round) -> Round) {
        updateDraft { w ->
            w.copy(rounds = w.rounds.map { if (it.id == roundId) transform(it) else it })
        }
    }

    private fun cloneItem(item: WorkoutItem): WorkoutItem = when (item) {
        is ExerciseItem -> item.copy(id = newId())
        is RestItem -> item.copy(id = newId())
    }

    fun setDraftName(name: String) = updateDraft { it.copy(name = name) }

    fun addRound() = updateDraft { it.copy(rounds = it.rounds + Round(id = newId())) }

    fun duplicateRound(roundId: Long) = updateDraft { w ->
        val i = w.rounds.indexOfFirst { it.id == roundId }
        if (i < 0) return@updateDraft w
        val src = w.rounds[i]
        val copy = src.copy(id = newId(), items = src.items.map { cloneItem(it) })
        w.copy(rounds = w.rounds.toMutableList().apply { add(i + 1, copy) })
    }

    fun deleteRound(roundId: Long) = updateDraft { w ->
        if (w.rounds.size <= 1) w else w.copy(rounds = w.rounds.filterNot { it.id == roundId })
    }

    /** Inserta un ejercicio al inicio del round. */
    fun addExercise(roundId: Long, name: String) = updateRound(roundId) { r ->
        r.copy(items = listOf(ExerciseItem(id = newId(), exerciseId = "", name = name.trim())) + r.items)
    }

    /** Inserta un descanso al inicio del round. */
    fun addRest(roundId: Long) = updateRound(roundId) { r ->
        r.copy(items = listOf(RestItem(id = newId())) + r.items)
    }

    fun updateExercise(
        roundId: Long,
        itemId: Long,
        mode: ExerciseMode,
        reps: Int,
        durationSec: Int,
        timeToPositionSec: Int,
    ) = updateRound(roundId) { r ->
        r.copy(items = r.items.map {
            if (it.id == itemId && it is ExerciseItem) {
                it.copy(
                    mode = mode,
                    reps = reps,
                    durationSec = durationSec,
                    timeToPositionSec = timeToPositionSec,
                )
            } else it
        })
    }

    fun updateRest(roundId: Long, itemId: Long, durationSec: Int) = updateRound(roundId) { r ->
        r.copy(items = r.items.map {
            if (it.id == itemId && it is RestItem) it.copy(durationSec = durationSec) else it
        })
    }

    fun deleteItem(roundId: Long, itemId: Long) = updateRound(roundId) { r ->
        r.copy(items = r.items.filterNot { it.id == itemId })
    }

    fun duplicateItem(roundId: Long, itemId: Long) = updateRound(roundId) { r ->
        val i = r.items.indexOfFirst { it.id == itemId }
        if (i < 0) return@updateRound r
        r.copy(items = r.items.toMutableList().apply { add(i + 1, cloneItem(r.items[i])) })
    }

    fun moveItem(roundId: Long, from: Int, to: Int) = updateRound(roundId) { r ->
        if (from !in r.items.indices || to !in r.items.indices || from == to) return@updateRound r
        r.copy(items = r.items.toMutableList().apply { add(to, removeAt(from)) })
    }

    // ---------- Lista ----------

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
