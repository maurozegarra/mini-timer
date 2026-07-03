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
     * Petición de restaurar la posición inicial de un panel (índice + nonce para
     * que emisiones consecutivas del mismo panel también se reciban). El
     * [com.minitimer.notify.ClockOverlayService] la observa y reubica el panel
     * debajo del reloj del sistema.
     */
    data class ResetPosReq(val index: Int, val nonce: Long)

    val resetPos = MutableStateFlow<ResetPosReq?>(null)

    fun requestResetPos(index: Int) {
        resetPos.value = ResetPosReq(index, System.nanoTime())
    }
}
