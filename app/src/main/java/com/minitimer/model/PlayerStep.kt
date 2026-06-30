package com.minitimer.model

/** Tipo de etapa en el recorrido del player. */
enum class StepKind { PREP, WORK, REST, COOLDOWN }

/**
 * Paso "aplanado" de un program para reproducirlo: cada ejercicio se expande en
 * prepare → sets×(work, rest) → cooldown, encadenando todos los workouts del
 * program en orden.
 */
data class PlayerStep(
    val kind: StepKind,
    /** WORK: nombre del ejercicio. Otras etapas: vacío (la UI localiza el rótulo). */
    val title: String,
    /** Nota del ejercicio (ej. "each side", "alternado"). */
    val note: String = "",
    /** Ejercicio al que pertenece la etapa (para mostrar dueño en PREP/REST/COOLDOWN). */
    val ownerName: String = "",
    /** Id del catálogo del ejercicio dueño (para icono/animación). */
    val ownerExerciseId: String = "",
    val workoutName: String = "",
    val workoutIndex: Int = 0,
    val totalWorkouts: Int = 1,
    /** Índice de serie (0-based) y total de series del ejercicio (solo WORK relevante). */
    val setIndex: Int = 0,
    val totalSets: Int = 1,
    val durationSec: Int = 0,
    val reps: Int = 0,
    /** true = etapa por tiempo; false = WORK por repeticiones (avance manual). */
    val timeBased: Boolean = true,
    val display: DisplayMode = DisplayMode.COUNTDOWN,
    val confirm: ConfirmMode = ConfirmMode.AUTO,
    val finalCount: Int = 0,
    val colorArgb: Long = StageConfig.COLOR_WORK,
    val weighted: Boolean = false,
    val weightTotal: Double = 0.0,
    val weightLabel: String = "",
    /** Nombre base del workout (sin resolver variante), para agrupar en la vista previa. */
    val workoutBaseName: String = "",
    /** Nombre de la variante activa si el workout es rotativo (vacío si no rota). */
    val variantName: String = "",
    /** true si el workout es rotativo. */
    val rotating: Boolean = false,
) {
    /** La etapa requiere confirmación manual (TAP) para avanzar. */
    val manual: Boolean
        get() = (kind == StepKind.WORK && !timeBased) ||
            confirm == ConfirmMode.MANUAL ||
            display == DisplayMode.STATIC
}
