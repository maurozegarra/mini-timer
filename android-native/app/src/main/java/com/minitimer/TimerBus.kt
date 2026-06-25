package com.minitimer

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Estado global del timer compartido en el proceso, para que el
 * OverlayService (ventana flotante) pueda mostrar el tiempo restante
 * en vivo sin acoplarse al ViewModel.
 */
object TimerBus {
    /** Texto a mostrar en la ventana flotante (tiempo restante o "¡Tiempo!"). */
    val display = MutableStateFlow("")

    /** Color de acento actual (ARGB). */
    val accent = MutableStateFlow(0xFFFF5252)

    /** true si hay un timer corriendo/pausado/terminado (no en setup). */
    val active = MutableStateFlow(false)

    /** true si el timer ya terminó (para colorear en rojo). */
    val done = MutableStateFlow(false)
}
