package com.minitimer

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
    val workoutId: Long,
    val name: String,
    val index: Int,
    val totalSteps: Int,
    val stepKind: StepKind,
    val stepTitle: String,
    val roundIndex: Int,
    val totalRounds: Int,
    val reps: Int,
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
