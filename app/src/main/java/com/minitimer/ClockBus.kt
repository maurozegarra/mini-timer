package com.minitimer

import com.minitimer.model.ClockConfig
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Estado del reloj OSD compartido en el proceso, para que el
 * [com.minitimer.notify.ClockOverlayService] pinte las franjas flotantes sin
 * acoplarse al ViewModel. El ViewModel publica aquí la [ClockConfig] al cambiar.
 */
object ClockBus {
    val config = MutableStateFlow(ClockConfig())

    /**
     * Señal para recolocar los paneles OSD (tras ajustar sus offsets o al
     * restaurar). Guarda un nonce para que emisiones consecutivas también se
     * reciban. El [com.minitimer.notify.ClockOverlayService] la observa y
     * reposiciona todos los paneles visibles.
     */
    val relayout = MutableStateFlow(0L)

    fun requestRelayout() {
        relayout.value = System.nanoTime()
    }
}
