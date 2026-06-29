package com.minitimer

import com.minitimer.model.DisplayMode
import com.minitimer.model.StepKind
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** Comandos enviados al [com.minitimer.notify.WorkoutPlayerService]. */
enum class PlayerCommand { PAUSE, RESUME, NEXT, STOP }

/**
 * Instantánea del estado del player publicada por el servicio para que la UI la
 * observe (sobrevive a la muerte de la Activity mientras el proceso viva).
 */
data class PlayerSnapshot(
    val trainingId: Long,
    val name: String,
    val index: Int,
    val totalSteps: Int,
    val stepKind: StepKind,
    val stepTitle: String,
    val ownerName: String,
    val ownerExerciseId: String,
    val workoutName: String,
    val workoutIndex: Int,
    val totalWorkouts: Int,
    val setIndex: Int,
    val totalSets: Int,
    val reps: Int,
    val timeBased: Boolean,
    val display: DisplayMode,
    val finalCount: Int,
    val colorArgb: Long,
    val weighted: Boolean,
    val weightTotal: Double,
    val weightLabel: String,
    val remainingMs: Long,
    val running: Boolean,
    val finished: Boolean,
)

/**
 * Canal compartido entre el servicio del player y la UI (igual filosofía que
 * [TimerBus]). [state] = estado actual (null si no hay player activo).
 */
object PlayerBus {
    val state = MutableStateFlow<PlayerSnapshot?>(null)
    val command = MutableSharedFlow<PlayerCommand>(extraBufferCapacity = 8)
}
