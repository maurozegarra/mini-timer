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
}
