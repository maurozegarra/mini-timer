package com.minitimer

import android.app.Application
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.minitimer.data.ExerciseCatalog
import com.minitimer.data.SettingsStore
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    private val settingsStore = SettingsStore(app)

    init {
        workouts.addAll(store.loadWorkouts())
        customExercises.addAll(store.loadCustomExercises())
        sessions.addAll(store.loadSessions())
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

    // ---------- Player ----------

    /** Workout en reproducción; null = player cerrado. */
    var playerWorkoutId by mutableStateOf<Long?>(null)
        private set

    /** Pasos aplanados del workout en reproducción. */
    var playerSteps by mutableStateOf<List<PlayerStep>>(emptyList())
        private set

    var playerName by mutableStateOf("")
        private set
    var playerIndex by mutableStateOf(0)
        private set
    var playerRemainingMs by mutableStateOf(0L)
        private set
    var playerRunning by mutableStateOf(false)
        private set
    var playerStarted by mutableStateOf(false)
        private set
    var playerFinished by mutableStateOf(false)
        private set

    private var playerEndAt = 0L
    private var playerJob: Job? = null

    val currentStep: PlayerStep? get() = playerSteps.getOrNull(playerIndex)

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

    /** Abre el player en modo previa (Summary + Start), sin iniciar el conteo. */
    fun openPlayer(workoutId: Long) {
        val w = workouts.firstOrNull { it.id == workoutId } ?: return
        val steps = buildSteps(w)
        if (steps.isEmpty()) return
        stopTick()
        playerSteps = steps
        playerWorkoutId = workoutId
        playerName = w.name
        playerIndex = 0
        playerStarted = false
        playerFinished = false
        playerRunning = false
        playerRemainingMs = steps[0].durationSec * 1000L
    }

    fun closePlayer() {
        stopTick()
        playerWorkoutId = null
        playerSteps = emptyList()
        playerStarted = false
        playerRunning = false
        playerFinished = false
    }

    /** Comienza el recorrido desde el primer paso. */
    fun startPlayerRun() {
        playerStarted = true
        beginStep(0)
    }

    private fun beginStep(i: Int) {
        playerIndex = i
        val step = playerSteps.getOrNull(i) ?: return
        if (step.kind == StepKind.REPS) {
            playerRunning = false
            stopTick()
            playerRemainingMs = 0L
        } else {
            playerRemainingMs = step.durationSec * 1000L
            resumePlayer()
        }
    }

    fun pausePlayer() {
        playerRunning = false
        stopTick()
    }

    fun resumePlayer() {
        val step = currentStep ?: return
        if (step.kind == StepKind.REPS) return
        playerRunning = true
        playerEndAt = System.currentTimeMillis() + playerRemainingMs
        startTick()
    }

    /** Avanza al siguiente paso (manual en REPS, o "saltar"). */
    fun nextStep() = advance()

    private fun advance() {
        val next = playerIndex + 1
        if (next >= playerSteps.size) {
            finishPlayer()
        } else {
            playBeep()
            beginStep(next)
        }
    }

    private fun finishPlayer() {
        stopTick()
        playerRunning = false
        playerFinished = true
        playBeep()
        playerWorkoutId?.let { addSession(it, playerName) }
    }

    private fun startTick() {
        stopTick()
        playerJob = viewModelScope.launch {
            while (playerRunning) {
                val left = playerEndAt - System.currentTimeMillis()
                playerRemainingMs = left.coerceAtLeast(0L)
                if (left <= 0L) {
                    advance()
                    break
                }
                delay(200)
            }
        }
    }

    private fun stopTick() {
        playerJob?.cancel()
        playerJob = null
    }

    private fun addSession(workoutId: Long, name: String) {
        sessions.add(0, SessionLog(id = newId(), workoutId = workoutId, workoutName = name, completedAt = System.currentTimeMillis()))
        store.saveSessions(sessions.toList())
    }

    /** Beep de transición usando el tono y volumen de alarma configurados. */
    private fun playBeep() {
        val s = settingsStore.load()
        val uri = s.alarmSoundUri ?: return
        try {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(getApplication(), Uri.parse(uri))
                val v = s.alarmVolume.coerceIn(0f, 1f)
                setVolume(v, v)
                setOnCompletionListener { it.release() }
                prepare()
                start()
            }
            // Evita reproducir indefinidamente si el tono es largo.
            viewModelScope.launch {
                delay(1500)
                runCatching { if (mp.isPlaying) { mp.stop(); mp.release() } }
            }
        } catch (_: Exception) {
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopTick()
    }
}
