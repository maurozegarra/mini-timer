package com.minitimer

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.minitimer.data.ExerciseCatalog
import com.minitimer.data.WorkoutStore
import com.minitimer.model.ExerciseDef
import com.minitimer.model.ExerciseItem
import com.minitimer.model.ExerciseMode
import com.minitimer.model.PlayerStep
import com.minitimer.model.RestItem
import com.minitimer.model.Round
import com.minitimer.model.SessionLog
import com.minitimer.model.StepKind
import com.minitimer.model.Workout
import com.minitimer.model.WorkoutItem
import com.minitimer.notify.WorkoutPlayerService
import kotlinx.coroutines.launch

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

    /** Ejercicios creados por el usuario (persistidos). */
    val customExercises = mutableStateListOf<ExerciseDef>()

    /** Historial de sesiones completadas (el más reciente al inicio). */
    val sessions = mutableStateListOf<SessionLog>()

    init {
        workouts.addAll(store.loadWorkouts())
        customExercises.addAll(store.loadCustomExercises())
        sessions.addAll(store.loadSessions())
        nextId = (allIds().maxOrNull() ?: 0L) + 1
        observePlayer()
    }

    /** Recarga todo desde el store (tras restaurar un respaldo). */
    fun reload() {
        workouts.clear()
        workouts.addAll(store.loadWorkouts())
        customExercises.clear()
        customExercises.addAll(store.loadCustomExercises())
        sessions.clear()
        sessions.addAll(store.loadSessions())
        nextId = (allIds().maxOrNull() ?: 0L) + 1
    }

    /** Refleja el estado publicado por el servicio del player en la UI. */
    private fun observePlayer() {
        viewModelScope.launch {
            PlayerBus.state.collect { snap ->
                if (snap == null) return@collect
                playerWorkoutId = snap.workoutId
                playerName = snap.name
                playerStarted = true
                playerIndex = snap.index
                playerTotalSteps = snap.totalSteps
                playerRemainingMs = snap.remainingMs
                playerRunning = snap.running
                playerStepKind = snap.stepKind
                playerStepTitle = snap.stepTitle
                playerRoundIndex = snap.roundIndex
                playerTotalRounds = snap.totalRounds
                playerReps = snap.reps
                if (snap.finished && !playerFinished) {
                    sessions.clear()
                    sessions.addAll(store.loadSessions())
                }
                playerFinished = snap.finished
            }
        }
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
        choosingForRound = null
    }

    // ---------- Selector de ejercicios ----------

    /** Round al que se le agregará el ejercicio elegido; null = selector cerrado. */
    var choosingForRound by mutableStateOf<Long?>(null)
        private set

    fun openExercisePicker(roundId: Long) {
        choosingForRound = roundId
    }

    fun closeExercisePicker() {
        choosingForRound = null
    }

    /** Catálogo base (según idioma) + ejercicios propios, ordenado por nombre. */
    fun catalog(lang: String): List<ExerciseDef> =
        (ExerciseCatalog.base(lang) + customExercises).sortedBy { it.name.lowercase() }

    fun addCustomExercise(name: String): ExerciseDef {
        val def = ExerciseDef(id = "custom_${newId()}", name = name.trim(), custom = true)
        customExercises.add(def)
        store.saveCustomExercises(customExercises.toList())
        return def
    }

    fun pickExercise(def: ExerciseDef) {
        val roundId = choosingForRound ?: return
        addExercise(roundId, def.id, def.name)
        choosingForRound = null
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
    fun addExercise(roundId: Long, exerciseId: String, name: String) = updateRound(roundId) { r ->
        r.copy(items = listOf(ExerciseItem(id = newId(), exerciseId = exerciseId, name = name.trim())) + r.items)
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

    /** Reordena la lista de workouts (arrastre manual) y persiste el orden. */
    fun moveWorkout(from: Int, to: Int) {
        if (from == to) return
        if (from !in workouts.indices || to !in workouts.indices) return
        workouts.add(to, workouts.removeAt(from))
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

    // ---------- Player (ejecutado por WorkoutPlayerService) ----------

    /** Workout en reproducción; null = player cerrado. */
    var playerWorkoutId by mutableStateOf<Long?>(null)
        private set

    /** Pasos del workout (para la previa / Summary). */
    var playerSteps by mutableStateOf<List<PlayerStep>>(emptyList())
        private set

    var playerName by mutableStateOf("")
        private set
    var playerStarted by mutableStateOf(false)
        private set
    var playerFinished by mutableStateOf(false)
        private set

    // Campos espejo del estado publicado por el servicio.
    var playerRunning by mutableStateOf(false)
        private set
    var playerIndex by mutableStateOf(0)
        private set
    var playerTotalSteps by mutableStateOf(0)
        private set
    var playerRemainingMs by mutableStateOf(0L)
        private set
    var playerStepKind by mutableStateOf(StepKind.TIMED)
        private set
    var playerStepTitle by mutableStateOf("")
        private set
    var playerRoundIndex by mutableStateOf(0)
        private set
    var playerTotalRounds by mutableStateOf(1)
        private set
    var playerReps by mutableStateOf(0)
        private set

    /** Construye los pasos del player a partir de los rounds/items del workout. */
    private fun buildSteps(w: Workout): List<PlayerStep> = buildList {
        val rc = w.rounds.size
        w.rounds.forEachIndexed { ri, round ->
            round.items.forEach { item ->
                when (item) {
                    is ExerciseItem -> when (item.mode) {
                        ExerciseMode.DURATION -> {
                            if (item.timeToPositionSec > 0) {
                                add(PlayerStep(StepKind.PREP, item.name, ri, rc, durationSec = item.timeToPositionSec))
                            }
                            add(PlayerStep(StepKind.TIMED, item.name, ri, rc, durationSec = item.durationSec))
                        }
                        ExerciseMode.REPS ->
                            add(PlayerStep(StepKind.REPS, item.name, ri, rc, reps = item.reps))
                    }
                    is RestItem -> add(PlayerStep(StepKind.REST, "", ri, rc, durationSec = item.durationSec))
                }
            }
        }
    }

    /** Abre el player en modo previa (Summary + Start), sin iniciar el servicio. */
    fun openPlayer(workoutId: Long) {
        val w = workouts.firstOrNull { it.id == workoutId } ?: return
        val steps = buildSteps(w)
        if (steps.isEmpty()) return
        PlayerBus.state.value = null
        playerSteps = steps
        playerWorkoutId = workoutId
        playerName = w.name
        playerStarted = false
        playerFinished = false
        playerRunning = false
        playerIndex = 0
        playerTotalSteps = steps.size
        playerRemainingMs = steps[0].durationSec * 1000L
    }

    /** Inicia el recorrido lanzando el servicio en primer plano. */
    fun startPlayerRun() {
        val id = playerWorkoutId ?: return
        if (playerSteps.isEmpty()) return
        playerStarted = true
        WorkoutPlayerService.start(getApplication(), id, playerName, playerSteps)
    }

    fun pausePlayer() {
        PlayerBus.command.tryEmit(PlayerCommand.PAUSE)
    }

    fun resumePlayer() {
        PlayerBus.command.tryEmit(PlayerCommand.RESUME)
    }

    fun nextStep() {
        PlayerBus.command.tryEmit(PlayerCommand.NEXT)
    }

    fun closePlayer() {
        WorkoutPlayerService.stop(getApplication())
        PlayerBus.state.value = null
        playerWorkoutId = null
        playerSteps = emptyList()
        playerStarted = false
        playerRunning = false
        playerFinished = false
    }
}
