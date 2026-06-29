package com.minitimer

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.minitimer.data.ExerciseCatalog
import com.minitimer.data.SettingsStore
import com.minitimer.data.WorkoutStore
import com.minitimer.model.ConfirmMode
import com.minitimer.model.DisplayMode
import com.minitimer.model.Exercise
import com.minitimer.model.ExerciseDef
import com.minitimer.model.PlayerStep
import com.minitimer.model.SessionLog
import com.minitimer.model.StepKind
import com.minitimer.model.Training
import com.minitimer.model.WeightType
import com.minitimer.model.WorkMode
import com.minitimer.model.WorkSet
import com.minitimer.model.Workout
import com.minitimer.model.isWeighted
import com.minitimer.model.setAt
import com.minitimer.model.weightTotal
import com.minitimer.notify.WorkoutPlayerService
import kotlinx.coroutines.launch

/**
 * Estado y lógica de la sección Athlete (jerarquía Training > Workout > Exercise).
 * Mantiene la lista de trainings persistida y un "draft" en edición que contiene
 * todo el árbol (workouts → exercises) hasta que se guarda.
 */
class AthleteViewModel(app: Application) : AndroidViewModel(app) {

    private val store = WorkoutStore(app)

    val trainings = mutableStateListOf<Training>()
    private val customExercises = mutableStateListOf<ExerciseDef>()
    private var nextId = System.currentTimeMillis()

    private fun newId(): Long = nextId++

    init {
        trainings.addAll(store.loadTrainings())
        customExercises.addAll(store.loadCustomExercises())
        observePlayer()
    }

    private fun persist() = store.saveTrainings(trainings.toList())

    /** Recarga desde almacenamiento (tras restaurar un backup). */
    fun reload() {
        trainings.clear()
        trainings.addAll(store.loadTrainings())
        customExercises.clear()
        customExercises.addAll(store.loadCustomExercises())
    }

    private fun lang(): String = SettingsStore(getApplication()).load().language

    // ---------- Catálogo de ejercicios ----------

    fun catalog(): List<ExerciseDef> {
        val l = lang()
        return (customExercises.toList() + ExerciseCatalog.base(l)).sortedBy { it.name.lowercase() }
    }

    fun addCustomExercise(name: String): ExerciseDef {
        val def = ExerciseDef(id = "custom_${newId()}", name = name.trim(), custom = true)
        customExercises.add(def)
        store.saveCustomExercises(customExercises.toList())
        return def
    }

    // ---------- Navegación / drafts ----------

    /** Training en edición; null = lista de trainings. */
    var draft by mutableStateOf<Training?>(null)
        private set

    /** Workout abierto dentro del draft (editor de workout). */
    var editingWorkoutId by mutableStateOf<Long?>(null)
        private set

    /** Ejercicio abierto dentro del workout (editor de ejercicio). */
    var editingExerciseId by mutableStateOf<Long?>(null)
        private set

    /** Selector de ejercicios abierto (añade al workout en edición). */
    var choosingExercise by mutableStateOf(false)
        private set

    fun editingWorkout(): Workout? =
        draft?.workouts?.firstOrNull { it.id == editingWorkoutId }

    fun editingExercise(): Exercise? =
        editingWorkout()?.exercises?.firstOrNull { it.id == editingExerciseId }

    private fun updateDraft(transform: (Training) -> Training) {
        draft = draft?.let(transform)
    }

    private fun updateWorkout(id: Long, transform: (Workout) -> Workout) = updateDraft { t ->
        t.copy(workouts = t.workouts.map { if (it.id == id) transform(it) else it })
    }

    private fun updateExercise(workoutId: Long, exerciseId: Long, transform: (Exercise) -> Exercise) =
        updateWorkout(workoutId) { w ->
            w.copy(exercises = w.exercises.map { if (it.id == exerciseId) transform(it) else it })
        }

    // ---------- Lista de Trainings ----------

    fun startNewTraining() {
        val now = System.currentTimeMillis()
        draft = Training(id = newId(), name = "", createdAt = now, updatedAt = now)
        editingWorkoutId = null
        editingExerciseId = null
        choosingExercise = false
    }

    fun startEditTraining(id: Long) {
        draft = trainings.firstOrNull { it.id == id }?.copy() ?: return
        editingWorkoutId = null
        editingExerciseId = null
        choosingExercise = false
    }

    fun closeTrainingEditor() {
        draft = null
        editingWorkoutId = null
        editingExerciseId = null
        choosingExercise = false
    }

    val canSaveTraining: Boolean
        get() = draft?.let { it.name.isNotBlank() && it.workouts.any { w -> w.exercises.isNotEmpty() } } == true

    fun saveTraining() {
        val d = draft ?: return
        if (!canSaveTraining) return
        val updated = d.copy(updatedAt = System.currentTimeMillis())
        val i = trainings.indexOfFirst { it.id == updated.id }
        if (i >= 0) trainings[i] = updated else trainings.add(updated)
        persist()
        closeTrainingEditor()
    }

    fun deleteTraining(id: Long) {
        trainings.removeAll { it.id == id }
        persist()
    }

    fun moveTraining(from: Int, to: Int) {
        if (from == to || from !in trainings.indices || to !in trainings.indices) return
        trainings.add(to, trainings.removeAt(from))
        persist()
    }

    fun duplicateTraining(id: Long) {
        val src = trainings.firstOrNull { it.id == id } ?: return
        val copy = src.copy(
            id = newId(),
            name = duplicateName(src.name),
            workouts = src.workouts.map { w ->
                w.copy(id = newId(), exercises = w.exercises.map { it.copy(id = newId()) })
            },
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        val i = trainings.indexOfFirst { it.id == id }
        trainings.add(i + 1, copy)
        persist()
    }

    private fun duplicateName(name: String): String = if (name.isBlank()) name else "$name (copy)"

    // ---------- Editor de Training (workouts) ----------

    fun setTrainingName(name: String) = updateDraft { it.copy(name = name) }

    fun addWorkout() {
        val id = newId()
        updateDraft { it.copy(workouts = it.workouts + Workout(id = id, name = "")) }
        editingWorkoutId = id
    }

    fun openWorkout(id: Long) {
        editingWorkoutId = id
    }

    fun closeWorkoutEditor() {
        editingWorkoutId = null
    }

    fun deleteWorkout(id: Long) = updateDraft { t ->
        t.copy(workouts = t.workouts.filterNot { it.id == id })
    }

    fun duplicateWorkout(id: Long) = updateDraft { t ->
        val i = t.workouts.indexOfFirst { it.id == id }
        if (i < 0) return@updateDraft t
        val src = t.workouts[i]
        val copy = src.copy(
            id = newId(),
            name = duplicateName(src.name),
            exercises = src.exercises.map { it.copy(id = newId()) },
        )
        t.copy(workouts = t.workouts.toMutableList().apply { add(i + 1, copy) })
    }

    fun moveWorkout(from: Int, to: Int) = updateDraft { t ->
        if (from == to || from !in t.workouts.indices || to !in t.workouts.indices) return@updateDraft t
        t.copy(workouts = t.workouts.toMutableList().apply { add(to, removeAt(from)) })
    }

    // ---------- Editor de Workout (exercises) ----------

    fun setWorkoutName(name: String) {
        val id = editingWorkoutId ?: return
        updateWorkout(id) { it.copy(name = name) }
    }

    fun openExercisePicker() {
        choosingExercise = true
    }

    fun closeExercisePicker() {
        choosingExercise = false
    }

    /** Crea un ejercicio por defecto desde el catálogo y abre su editor. */
    fun pickExercise(def: ExerciseDef) {
        val wId = editingWorkoutId ?: return
        val id = newId()
        val ex = Exercise(id = id, exerciseId = def.id, name = def.name)
        updateWorkout(wId) { it.copy(exercises = it.exercises + ex) }
        choosingExercise = false
        editingExerciseId = id
    }

    fun openExercise(id: Long) {
        editingExerciseId = id
    }

    fun closeExerciseEditor() {
        editingExerciseId = null
    }

    fun deleteExercise(id: Long) {
        val wId = editingWorkoutId ?: return
        updateWorkout(wId) { w -> w.copy(exercises = w.exercises.filterNot { it.id == id }) }
    }

    fun duplicateExercise(id: Long) {
        val wId = editingWorkoutId ?: return
        updateWorkout(wId) { w ->
            val i = w.exercises.indexOfFirst { it.id == id }
            if (i < 0) return@updateWorkout w
            val copy = w.exercises[i].copy(id = newId())
            w.copy(exercises = w.exercises.toMutableList().apply { add(i + 1, copy) })
        }
    }

    fun moveExercise(from: Int, to: Int) {
        val wId = editingWorkoutId ?: return
        updateWorkout(wId) { w ->
            if (from == to || from !in w.exercises.indices || to !in w.exercises.indices) return@updateWorkout w
            w.copy(exercises = w.exercises.toMutableList().apply { add(to, removeAt(from)) })
        }
    }

    /** Persiste los cambios del editor de ejercicio en el draft. */
    fun saveExercise(updated: Exercise) {
        val wId = editingWorkoutId ?: return
        updateExercise(wId, updated.id) { updated }
        editingExerciseId = null
    }

    // ---------- Construcción de pasos del player ----------

    private fun fmtKg(d: Double): String {
        val r = (d * 10).toLong()
        return if (r % 10 == 0L) (r / 10).toString() else (r / 10.0).toString()
    }

    private fun weightLabel(e: Exercise, s: WorkSet): String = when (e.weightType) {
        WeightType.BARBELL -> "${fmtKg(e.barWeight)} + ${fmtKg(s.weight)}"
        WeightType.DUMBBELL -> "2 × ${fmtKg(s.weight)}"
        WeightType.TOTAL, WeightType.NONE -> ""
    }

    private fun buildSteps(t: Training): List<PlayerStep> = buildList {
        val tw = t.workouts.size.coerceAtLeast(1)
        t.workouts.forEachIndexed { wi, w ->
            w.exercises.forEach { e ->
                if (e.prepareSec > 0) {
                    add(stageStep(StepKind.PREP, e, w.name, wi, tw, durationSec = e.prepareSec))
                }
                val sets = e.sets.coerceAtLeast(1)
                for (s in 0 until sets) {
                    if (e.workMode == WorkMode.TIME) {
                        add(stageStep(StepKind.WORK, e, w.name, wi, tw, durationSec = e.workValue, setIndex = s, totalSets = sets, timeBased = true))
                    } else {
                        val ws = e.setAt(s)
                        add(
                            stageStep(
                                StepKind.WORK, e, w.name, wi, tw,
                                reps = ws.reps, setIndex = s, totalSets = sets, timeBased = false,
                                weighted = e.isWeighted,
                                weightTotal = if (e.isWeighted) e.weightTotal(ws) else 0.0,
                                weightLabel = if (e.isWeighted) weightLabel(e, ws) else "",
                            ),
                        )
                    }
                    val lastSet = s == sets - 1
                    if (e.restSec > 0 && !(e.restSkipOnLastSet && lastSet)) {
                        add(stageStep(StepKind.REST, e, w.name, wi, tw, durationSec = e.restSec, setIndex = s, totalSets = sets))
                    }
                }
                if (e.cooldownSec > 0) {
                    add(stageStep(StepKind.COOLDOWN, e, w.name, wi, tw, durationSec = e.cooldownSec))
                }
            }
        }
    }

    private fun stageStep(
        kind: StepKind,
        e: Exercise,
        workoutName: String,
        workoutIndex: Int,
        totalWorkouts: Int,
        durationSec: Int = 0,
        reps: Int = 0,
        setIndex: Int = 0,
        totalSets: Int = 1,
        timeBased: Boolean = true,
        weighted: Boolean = false,
        weightTotal: Double = 0.0,
        weightLabel: String = "",
    ): PlayerStep {
        val cfg = when (kind) {
            StepKind.PREP -> e.prepareCfg
            StepKind.WORK -> e.workCfg
            StepKind.REST -> e.restCfg
            StepKind.COOLDOWN -> e.cooldownCfg
        }
        return PlayerStep(
            kind = kind,
            title = if (kind == StepKind.WORK) e.name else "",
            ownerName = e.name,
            ownerExerciseId = e.exerciseId,
            workoutName = workoutName,
            workoutIndex = workoutIndex,
            totalWorkouts = totalWorkouts,
            setIndex = setIndex,
            totalSets = totalSets,
            durationSec = durationSec,
            reps = reps,
            timeBased = timeBased,
            display = cfg.display,
            confirm = cfg.confirm,
            finalCount = cfg.finalCount,
            colorArgb = cfg.color,
            weighted = weighted,
            weightTotal = weightTotal,
            weightLabel = weightLabel,
        )
    }

    // ---------- Player ----------

    var playerTrainingId by mutableStateOf<Long?>(null)
        private set
    var playerSteps by mutableStateOf<List<PlayerStep>>(emptyList())
        private set
    var playerName by mutableStateOf("")
        private set
    var playerStarted by mutableStateOf(false)
        private set
    var playerFinished by mutableStateOf(false)
        private set
    var playerRunning by mutableStateOf(false)
        private set
    var playerIndex by mutableStateOf(0)
        private set
    var playerTotalSteps by mutableStateOf(0)
        private set
    var playerRemainingMs by mutableStateOf(0L)
        private set
    var playerStep by mutableStateOf<PlayerStep?>(null)
        private set

    /** Feedback de peso recogido durante el run: nombre ejercicio -> (peso, delta kg). */
    val weightFeedback = mutableStateMapOf<String, Pair<Double, Double>>()

    fun recordFeedback(exerciseName: String, weight: Double, deltaKg: Double) {
        weightFeedback[exerciseName] = weight to deltaKg
    }

    /** Sugerencias de ajuste para la próxima vez (solo las que cambian). */
    fun weightSuggestions(): List<Triple<String, Double, Double>> =
        weightFeedback.entries
            .filter { it.value.second != 0.0 }
            .map { Triple(it.key, it.value.first, it.value.first + it.value.second) }

    fun openPlayer(trainingId: Long) {
        val t = trainings.firstOrNull { it.id == trainingId } ?: return
        val steps = buildSteps(t)
        if (steps.isEmpty()) return
        PlayerBus.state.value = null
        weightFeedback.clear()
        playerSteps = steps
        playerTrainingId = trainingId
        playerName = t.name
        playerStarted = false
        playerFinished = false
        playerRunning = false
        playerIndex = 0
        playerStep = steps[0]
        playerTotalSteps = steps.size
        playerRemainingMs = steps[0].durationSec * 1000L
    }

    fun startPlayerRun() {
        val id = playerTrainingId ?: return
        if (playerSteps.isEmpty()) return
        playerStarted = true
        WorkoutPlayerService.start(getApplication(), id, playerName, playerSteps)
    }

    fun pausePlayer() = PlayerBus.command.tryEmit(PlayerCommand.PAUSE)
    fun resumePlayer() = PlayerBus.command.tryEmit(PlayerCommand.RESUME)
    fun nextStep() = PlayerBus.command.tryEmit(PlayerCommand.NEXT)

    fun closePlayer() {
        WorkoutPlayerService.stop(getApplication())
        PlayerBus.state.value = null
        playerTrainingId = null
        playerSteps = emptyList()
        playerStarted = false
        playerRunning = false
        playerFinished = false
        playerStep = null
    }

    private fun observePlayer() {
        viewModelScope.launch {
            PlayerBus.state.collect { snap ->
                if (snap == null) return@collect
                playerIndex = snap.index
                playerTotalSteps = snap.totalSteps
                playerRemainingMs = snap.remainingMs
                playerRunning = snap.running
                playerFinished = snap.finished
                playerName = snap.name
                playerStep = playerSteps.getOrNull(snap.index) ?: PlayerStep(
                    kind = snap.stepKind,
                    title = snap.stepTitle,
                    ownerName = snap.ownerName,
                    ownerExerciseId = snap.ownerExerciseId,
                    workoutName = snap.workoutName,
                    workoutIndex = snap.workoutIndex,
                    totalWorkouts = snap.totalWorkouts,
                    setIndex = snap.setIndex,
                    totalSets = snap.totalSets,
                    reps = snap.reps,
                    timeBased = snap.timeBased,
                    display = snap.display,
                    finalCount = snap.finalCount,
                    colorArgb = snap.colorArgb,
                    weighted = snap.weighted,
                    weightTotal = snap.weightTotal,
                    weightLabel = snap.weightLabel,
                )
                if (snap.finished) playerRunning = false
            }
        }
    }
}
