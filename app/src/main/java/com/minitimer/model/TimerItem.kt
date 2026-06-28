package com.minitimer.model

import com.minitimer.Phase

/**
 * Un temporizador de la lista (multi-timer). En la v1 solo uno puede estar
 * "activo" (RUNNING/PAUSED/DONE) a la vez; el resto permanece en IDLE.
 *
 * - [remainingMs] mientras RUNNING es derivado de [endAt]; se recalcula al
 *   restaurar tras la muerte del proceso.
 * - [lastFinished] es el epoch (ms) de la última vez que el timer llegó a cero
 *   de principio a fin; 0 = nunca. Se muestra como "Finalizó el ..." en IDLE.
 */
data class TimerItem(
    val id: Long,
    val name: String = "",
    val totalMs: Long,
    val remainingMs: Long,
    val phase: Phase,
    val endAt: Long = 0L,
    val starred: Boolean = false,
    val lastFinished: Long = 0L,
)
