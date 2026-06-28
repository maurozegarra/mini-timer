package com.minitimer.model

/** Cómo se mide un ejercicio: por repeticiones o por duración (segundos). */
enum class ExerciseMode { REPS, DURATION }

/** Un elemento dentro de un round: un ejercicio o un descanso. */
sealed interface WorkoutItem {
    val id: Long
}

/**
 * Ejercicio dentro de un round. Según [mode] se usa [reps] (REPS) o
 * [durationSec] (DURATION). [timeToPositionSec] es la preparación opcional
 * antes de empezar (cuenta regresiva en el player).
 */
data class ExerciseItem(
    override val id: Long,
    val exerciseId: String,
    val name: String,
    val mode: ExerciseMode = ExerciseMode.REPS,
    val reps: Int = 10,
    val durationSec: Int = 0,
    val timeToPositionSec: Int = 0,
) : WorkoutItem

/** Descanso dentro de un round, con duración en segundos. */
data class RestItem(
    override val id: Long,
    val durationSec: Int = 30,
) : WorkoutItem

/** Un round es un grupo ordenado de items que se juega una vez. */
data class Round(
    val id: Long,
    val items: List<WorkoutItem> = emptyList(),
)

/** Rutina creada por el usuario: nombre + lista de rounds. */
data class Workout(
    val id: Long,
    val name: String = "",
    val rounds: List<Round> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/**
 * Definición de un ejercicio del catálogo (solo nombre por ahora; el
 * placeholder de animación se añadirá más adelante). [custom] = creado por el
 * usuario.
 */
data class ExerciseDef(
    val id: String,
    val name: String,
    val custom: Boolean = false,
)

/** Registro de una sesión completada (base para el futuro streak/dashboard). */
data class SessionLog(
    val id: Long,
    val workoutId: Long,
    val workoutName: String,
    val completedAt: Long,
)
