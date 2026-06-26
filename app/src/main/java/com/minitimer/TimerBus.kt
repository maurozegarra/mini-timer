package com.minitimer

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** Comandos que la notificación Live Update envía al ViewModel. */
enum class TimerCommand { PAUSE, RESUME, CANCEL }

/**
 * Estado global del timer compartido en el proceso, para que el
 * LiveTimerService (notificación Live Update) pueda mostrar el tiempo
 * restante en vivo sin acoplarse al ViewModel.
 */
object TimerBus {

    /**
     * Comandos disparados desde los botones del Now Bar (Pausa/Reanudar/Cancelar).
     * El ViewModel los colecciona y ejecuta la acción correspondiente.
     */
    val command = MutableSharedFlow<TimerCommand>(extraBufferCapacity = 8)
    /** Texto a mostrar (tiempo restante o "¡Tiempo!"). */
    val display = MutableStateFlow("")

    /** Color de acento actual (ARGB). */
    val accent = MutableStateFlow(0xFFFF5252)

    /** true si el timer ya terminó (para colorear en rojo). */
    val done = MutableStateFlow(false)

    /** true si el timer está pausado. */
    val paused = MutableStateFlow(false)

    /** Milisegundos restantes del timer. */
    val remainingMs = MutableStateFlow(0L)

    /** Duración total del timer en milisegundos. */
    val totalMs = MutableStateFlow(0L)

    /** Instante (epoch millis) en que el timer llega a cero. Estable mientras corre. */
    val endAt = MutableStateFlow(0L)
}
