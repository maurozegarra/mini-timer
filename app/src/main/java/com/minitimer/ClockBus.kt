package com.minitimer

import com.minitimer.model.ClockConfig
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Posición (en px de pantalla) del reloj de la barra de estado del sistema,
 * medida por el [com.minitimer.notify.ClockAlignAccessibilityService]. Sirve para
 * que un panel OSD con `alignToSystemClock` se alinee horizontalmente al reloj.
 */
data class ClockAnchor(val left: Int, val top: Int, val width: Int, val height: Int)

/**
 * Estado del reloj OSD compartido en el proceso, para que el
 * [com.minitimer.notify.ClockOverlayService] pinte las franjas flotantes sin
 * acoplarse al ViewModel. El ViewModel publica aquí la [ClockConfig] al cambiar.
 */
object ClockBus {
    val config = MutableStateFlow(ClockConfig())

    /**
     * Ancla del reloj del sistema (px), o null si aún no se ha medido o el
     * servicio de accesibilidad no está activo. La publica
     * [com.minitimer.notify.ClockAlignAccessibilityService] y la consume
     * [com.minitimer.notify.ClockOverlayService] para alinear los paneles.
     */
    val clockAnchor = MutableStateFlow<ClockAnchor?>(null)

    /** true mientras el servicio de accesibilidad de alineación está conectado. */
    val accessibilityConnected = MutableStateFlow(false)

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
