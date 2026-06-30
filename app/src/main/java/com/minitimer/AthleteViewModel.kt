package com.minitimer

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.minitimer.data.AthleteDefaults
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
import com.minitimer.model.WorkoutVariant
import com.minitimer.model.activeExercises
import com.minitimer.model.activeName
import com.minitimer.model.activeVariant
import com.minitimer.model.hasContent
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
        val firstRun = store.isFirstRun()
        trainings.addAll(store.loadTrainings())
        customExercises.addAll(store.loadCustomExercises())
        if (firstRun && trainings.isEmpty()) {
            trainings.add(AthleteDefaults.masterTraining(lang()))
            trainings.add(AthleteDefaults.frikiNikiTraining(lang()))
            store.setFrikiSeeded()
            persist()
        } else if (!store.isFrikiSeeded()) {
            val masterIdx = trainings.indexOfFirst { it.name == "Master" }
            val friki = AthleteDefaults.frikiNikiTraining(lang())
            if (masterIdx >= 0) trainings.add(masterIdx + 1, friki) else trainings.add(friki)
            store.setFrikiSeeded()
            persist()
        }
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

    /** Variante abierta dentro de un workout rotativo (editor de variante). */
    var editingVariantId by mutableStateOf<Long?>(null)
        private set

    /** Selector de ejercicios abierto (añade al contenedor en edición). */
    var choosingExercise by mutableStateOf(false)
        private set

    fun editingWorkout(): Workout? =
        draft?.workouts?.firstOrNull { it.id == editingWorkoutId }

    /** Variante actualmente en edición (o null si se edita el workout simple). */
    fun editingVariant(): WorkoutVariant? =
        editingVariantId?.let { vId -> editingWorkout()?.variants?.firstOrNull { it.id == vId } }

    /** Lista de ejercicios del contenedor en edición (variante si hay, si no workout). */
    fun editorExercises(): List<Exercise> {
        val w = editingWorkout() ?: return emptyList()
        val vId = editingVariantId ?: return w.exercises
        return w.variants.firstOrNull { it.id == vId }?.exercises ?: emptyList()
    }

    /** Nombre del contenedor en edición (variante si hay, si no workout). */
    fun editorName(): String {
        val w = editingWorkout() ?: return ""
        val vId = editingVariantId ?: return w.name
        return w.variants.firstOrNull { it.id == vId }?.name ?: ""
    }

    fun editingExercise(): Exercise? =
        editorExercises().firstOrNull { it.id == editingExerciseId }

    private fun updateDraft(transform: (Training) -> Training) {
        draft = draft?.let(transform)
    }

    private fun updateWorkout(id: Long, transform: (Workout) -> Workout) = updateDraft { t ->
        t.copy(workouts = t.workouts.map { if (it.id == id) transform(it) else it })
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
        editingVariantId = null
        editingExerciseId = null
        choosingExercise = false
    }

    fun closeTrainingEditor() {
        draft = null
        editingWorkoutId = null
        editingVariantId = null
        editingExerciseId = null
        choosingExercise = false
    }

    val canSaveTraining: Boolean
        get() = draft?.let { it.name.isNotBlank() && it.workouts.any { w -> w.hasContent() } } == true

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
        editingVariantId = null
    }

    fun closeWorkoutEditor() {
        editingWorkoutId = null
        editingVariantId = null
    }

    // ---------- Workouts rotativos / variantes ----------

    /** Convierte un workout simple en rotativo: mueve sus ejercicios a una variante. */
    fun makeWorkoutRotating(id: Long) = updateWorkout(id) { w ->
        if (w.rotating) return@updateWorkout w
        val first = WorkoutVariant(
            id = newId(),
            name = w.name.ifBlank { "A" },
            exercises = w.exercises,
        )
        w.copy(rotating = true, rotationIndex = 0, variants = listOf(first), exercises = emptyList())
    }

    /** Vuelve simple un workout rotativo: conserva los ejercicios de la 1ª variante. */
    fun makeWorkoutSimple(id: Long) = updateWorkout(id) { w ->
        if (!w.rotating) return@updateWorkout w
        w.copy(
            rotating = false,
            rotationIndex = 0,
            exercises = w.variants.firstOrNull()?.exercises ?: w.exercises,
            variants = emptyList(),
        )
    }

    fun addVariant() {
        val wId = editingWorkoutId ?: return
        val id = newId()
        updateWorkout(wId) { it.copy(variants = it.variants + WorkoutVariant(id = id, name = "")) }
        editingVariantId = id
    }

    fun openVariant(id: Long) {
        editingVariantId = id
    }

    fun closeVariantEditor() {
        editingVariantId = null
    }

    fun deleteVariant(id: Long) {
        val wId = editingWorkoutId ?: return
        updateWorkout(wId) { w -> w.copy(variants = w.variants.filterNot { it.id == id }) }
    }

    fun duplicateVariant(id: Long) {
        val wId = editingWorkoutId ?: return
        updateWorkout(wId) { w ->
            val i = w.variants.indexOfFirst { it.id == id }
            if (i < 0) return@updateWorkout w
            val src = w.variants[i]
            val copy = src.copy(
                id = newId(),
                name = duplicateName(src.name),
                exercises = src.exercises.map { it.copy(id = newId()) },
            )
            w.copy(variants = w.variants.toMutableList().apply { add(i + 1, copy) })
        }
    }

    fun moveVariant(from: Int, to: Int) {
        val wId = editingWorkoutId ?: return
        updateWorkout(wId) { w ->
            if (from == to || from !in w.variants.indices || to !in w.variants.indices) return@updateWorkout w
            w.copy(variants = w.variants.toMutableList().apply { add(to, removeAt(from)) })
        }
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

    /** Renombra el contenedor en edición: variante si hay una abierta, si no el workout. */
    fun setEditorName(name: String) {
        val wId = editingWorkoutId ?: return
        val vId = editingVariantId
        if (vId == null) {
            updateWorkout(wId) { it.copy(name = name) }
        } else {
            updateWorkout(wId) { w ->
                w.copy(variants = w.variants.map { if (it.id == vId) it.copy(name = name) else it })
            }
        }
    }

    /** Aplica una transformación a la lista de ejercicios del contenedor en edición. */
    private fun updateEditorExercises(transform: (List<Exercise>) -> List<Exercise>) {
        val wId = editingWorkoutId ?: return
        val vId = editingVariantId
        if (vId == null) {
            updateWorkout(wId) { it.copy(exercises = transform(it.exercises)) }
        } else {
            updateWorkout(wId) { w ->
                w.copy(variants = w.variants.map { if (it.id == vId) it.copy(exercises = transform(it.exercises)) else it })
            }
        }
    }

    fun openExercisePicker() {
        choosingExercise = true
    }

    fun closeExercisePicker() {
        choosingExercise = false
    }

    /** Crea un ejercicio por defecto desde el catálogo y abre su editor. */
    fun pickExercise(def: ExerciseDef) {
        if (editingWorkoutId == null) return
        val id = newId()
        val ex = Exercise(id = id, exerciseId = def.id, name = def.name)
        updateEditorExercises { it + ex }
        choosingExercise = false
        editingExerciseId = id
    }

    fun openExercise(id: Long) {
        editingExerciseId = id
    }

    fun closeExerciseEditor() {
        editingExerciseId = null
    }

    fun deleteExercise(id: Long) = updateEditorExercises { list -> list.filterNot { it.id == id } }

    fun duplicateExercise(id: Long) = updateEditorExercises { list ->
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) list else list.toMutableList().apply { add(i + 1, list[i].copy(id = newId())) }
    }

    fun moveExercise(from: Int, to: Int) = updateEditorExercises { list ->
        if (from == to || from !in list.indices || to !in list.indices) list
        else list.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** Persiste los cambios del editor de ejercicio en el draft. */
    fun saveExercise(updated: Exercise) {
        updateEditorExercises { list -> list.map { if (it.id == updated.id) updated else it } }
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
            val wName = w.activeName()
            val wVariant = if (w.rotating) (w.activeVariant()?.name ?: "") else ""
            w.activeExercises().forEach { e ->
                if (e.prepareSec > 0) {
                    add(stageStep(StepKind.PREP, e, wName, wi, tw, durationSec = e.prepareSec, workoutBase = w.name, variant = wVariant, rotating = w.rotating))
                }
                val sets = e.sets.coerceAtLeast(1)
                for (s in 0 until sets) {
                    if (e.workMode == WorkMode.TIME) {
                        add(stageStep(StepKind.WORK, e, wName, wi, tw, durationSec = e.workValue, setIndex = s, totalSets = sets, timeBased = true, workoutBase = w.name, variant = wVariant, rotating = w.rotating))
                    } else {
                        val ws = e.setAt(s)
                        add(
                            stageStep(
                                StepKind.WORK, e, wName, wi, tw,
                                reps = ws.reps, setIndex = s, totalSets = sets, timeBased = false,
                                weighted = e.isWeighted,
                                weightTotal = if (e.isWeighted) e.weightTotal(ws) else 0.0,
                                weightLabel = if (e.isWeighted) weightLabel(e, ws) else "",
                                workoutBase = w.name, variant = wVariant, rotating = w.rotating,
                            ),
                        )
                    }
                    val lastSet = s == sets - 1
                    if (e.restSec > 0 && !(e.restSkipOnLastSet && lastSet)) {
                        add(stageStep(StepKind.REST, e, wName, wi, tw, durationSec = e.restSec, setIndex = s, totalSets = sets, workoutBase = w.name, variant = wVariant, rotating = w.rotating))
                    }
                }
                if (e.cooldownSec > 0) {
                    add(stageStep(StepKind.COOLDOWN, e, wName, wi, tw, durationSec = e.cooldownSec, workoutBase = w.name, variant = wVariant, rotating = w.rotating))
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
        workoutBase: String = "",
        variant: String = "",
        rotating: Boolean = false,
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
            note = e.note,
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
            workoutBaseName = workoutBase,
            variantName = variant,
            rotating = rotating,
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

    /** Evita avanzar la rotación más de una vez por corrida (finished puede repetir). */
    private var rotationAdvanced = false

    /** Avanza el índice de rotación de los workouts rotativos del training (al completar). */
    private fun advanceRotation(trainingId: Long) {
        val i = trainings.indexOfFirst { it.id == trainingId }
        if (i < 0) return
        val t = trainings[i]
        if (t.workouts.none { it.rotating && it.variants.isNotEmpty() }) return
        trainings[i] = t.copy(
            workouts = t.workouts.map { w ->
                if (w.rotating && w.variants.isNotEmpty())
                    w.copy(rotationIndex = (w.rotationIndex + 1) % w.variants.size)
                else w
            },
        )
        persist()
    }

    fun openPlayer(trainingId: Long) {
        val t = trainings.firstOrNull { it.id == trainingId } ?: return
        val steps = buildSteps(t)
        if (steps.isEmpty()) return
        PlayerBus.state.value = null
        rotationAdvanced = false
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
                    note = snap.note,
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
                if (snap.finished) {
                    playerRunning = false
                    if (!rotationAdvanced) {
                        rotationAdvanced = true
                        playerTrainingId?.let { advanceRotation(it) }
                    }
                }
            }
        }
    }
}
