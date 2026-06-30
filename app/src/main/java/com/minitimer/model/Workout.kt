package com.minitimer.model

/** Cómo se mide el trabajo (WORK) de un ejercicio. */
enum class WorkMode { TIME, REPS }

/** Cómo se muestra el reloj de una etapa en el player. */
enum class DisplayMode { COUNTDOWN, STATIC, COUNTUP }

/** Confirmación para avanzar de una etapa a la siguiente. */
enum class ConfirmMode { AUTO, MANUAL }

/** Tipo de carga de un ejercicio por repeticiones. */
enum class WeightType { NONE, TOTAL, BARBELL, DUMBBELL }

/**
 * Configuración avanzada común a cada etapa (prepare/work/rest/cooldown).
 * [color] es ARGB empaquetado en Long. [finalCount] = segundos finales con
 * cuenta/alarma (0 = desactivado).
 */
data class StageConfig(
    val color: Long = COLOR_WORK,
    val display: DisplayMode = DisplayMode.COUNTDOWN,
    val alarm: Boolean = true,
    val finalCount: Int = 0,
    val confirm: ConfirmMode = ConfirmMode.AUTO,
) {
    companion object {
        const val COLOR_PREPARE = 0xFFE2641EL
        const val COLOR_WORK = 0xFFC0392BL
        const val COLOR_REST = 0xFF1565C0L
        const val COLOR_COOLDOWN = 0xFF455A64L
    }
}

/** Una serie: repeticiones y peso "crudo" (su significado depende de [WeightType]). */
data class WorkSet(
    val reps: Int = 12,
    val weight: Double = 0.0,
)

/**
 * Ejercicio dentro de un workout (modelo MOCK-B): tiene su propia estructura de
 * etapas (prepare → sets×(work, rest) → cooldown). En modo REPS soporta peso
 * por serie vía [setList] + [weightType].
 */
data class Exercise(
    val id: Long,
    val exerciseId: String,
    val name: String,
    val note: String = "",
    val prepareSec: Int = 0,
    val sets: Int = 1,
    val workMode: WorkMode = WorkMode.TIME,
    val workValue: Int = 30,
    val restSec: Int = 30,
    val restSkipOnLastSet: Boolean = true,
    val cooldownSec: Int = 0,
    val weightType: WeightType = WeightType.NONE,
    val barWeight: Double = 20.0,
    val setList: List<WorkSet> = emptyList(),
    val prepareCfg: StageConfig = StageConfig(color = StageConfig.COLOR_PREPARE, finalCount = 3),
    val workCfg: StageConfig = StageConfig(color = StageConfig.COLOR_WORK),
    val restCfg: StageConfig = StageConfig(color = StageConfig.COLOR_REST, finalCount = 3),
    val cooldownCfg: StageConfig = StageConfig(color = StageConfig.COLOR_COOLDOWN),
)

/**
 * Variante de un workout rotativo: un conjunto con nombre propio de ejercicios
 * (ej. "Running", "Lower"). El player elige una variante por corrida.
 */
data class WorkoutVariant(
    val id: Long,
    val name: String = "",
    val exercises: List<Exercise> = emptyList(),
)

/**
 * Workout = bloque/agrupador ordenado de ejercicios (Warmup, Cardio, Lower…).
 * Si [rotating] es true, en cada corrida se reproduce UNA de [variants] según
 * [rotationIndex], que avanza al completar el training (rotación "por completar").
 */
data class Workout(
    val id: Long,
    val name: String = "",
    val exercises: List<Exercise> = emptyList(),
    val rotating: Boolean = false,
    val rotationIndex: Int = 0,
    val variants: List<WorkoutVariant> = emptyList(),
)

/** Variante activa de un workout rotativo (o null si no rota). */
fun Workout.activeVariant(): WorkoutVariant? =
    if (rotating && variants.isNotEmpty()) variants[rotationIndex % variants.size] else null

/** Ejercicios que se reproducen en la corrida actual (resuelve rotación). */
fun Workout.activeExercises(): List<Exercise> = activeVariant()?.exercises ?: exercises

/** Nombre a mostrar en la corrida actual (variante o nombre del workout). */
fun Workout.activeName(): String = activeVariant()?.name?.ifBlank { name } ?: name

/** Indica si el workout tiene contenido reproducible. */
fun Workout.hasContent(): Boolean =
    exercises.isNotEmpty() || variants.any { it.exercises.isNotEmpty() }

/**
 * Training = nivel superior que agrupa workouts y es lo que se EJECUTA de corrido
 * en el player (ej. "Hybrid Strength").
 */
data class Training(
    val id: Long,
    val name: String = "",
    val workouts: List<Workout> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/** Devuelve la serie [i] del ejercicio, con valores por defecto si falta. */
fun Exercise.setAt(i: Int): WorkSet = setList.getOrElse(i) { WorkSet(reps = workValue) }

/** Peso total (kg) de una serie según el tipo de carga del ejercicio. */
fun Exercise.weightTotal(s: WorkSet): Double = when (weightType) {
    WeightType.BARBELL -> barWeight + s.weight
    WeightType.DUMBBELL -> 2.0 * s.weight
    WeightType.TOTAL -> s.weight
    WeightType.NONE -> 0.0
}

/** Indica si el ejercicio lleva peso (reps + tipo de carga distinto de NONE). */
val Exercise.isWeighted: Boolean
    get() = workMode == WorkMode.REPS && weightType != WeightType.NONE

/**
 * Definición de un ejercicio del catálogo. [custom] = creado por el usuario.
 */
data class ExerciseDef(
    val id: String,
    val name: String,
    val custom: Boolean = false,
)

/** Registro de un training completado. */
data class SessionLog(
    val id: Long,
    val trainingId: Long,
    val trainingName: String,
    val completedAt: Long,
)
