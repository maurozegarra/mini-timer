package com.minitimer.model

/** Tipo de paso en el recorrido del player. */
enum class StepKind {
    /** Preparación previa (time-to-position) antes de un ejercicio por duración. */
    PREP,

    /** Ejercicio por duración (cuenta regresiva, auto-avance). */
    TIMED,

    /** Ejercicio por repeticiones (avance manual). */
    REPS,

    /** Descanso (cuenta regresiva, auto-avance). */
    REST,
}

/**
 * Paso "aplanado" de un workout para reproducirlo: cada item del workout se
 * convierte en uno o más [PlayerStep] en orden de rounds e items.
 */
data class PlayerStep(
    val kind: StepKind,
    val title: String,
    val roundIndex: Int,
    val totalRounds: Int,
    val durationSec: Int = 0,
    val reps: Int = 0,
)
