package com.minitimer.notify

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.minitimer.ClockAnchor
import com.minitimer.ClockBus

/**
 * Servicio de accesibilidad OPCIONAL, cuyo único fin es medir la posición del
 * reloj de la barra de estado del sistema (SystemUI) y publicarla en
 * [ClockBus.clockAnchor]. Con esa ancla, el [ClockOverlayService] alinea
 * horizontalmente el panel OSD que tenga `alignToSystemClock` activo, para que
 * no se desalinee cuando el reloj del sistema cambia de ancho (p. ej. 9:59 ->
 * 10:00) o usa fuente proporcional.
 *
 * Privacidad: solo lee los límites (bounds) del nodo del reloj; no recopila ni
 * envía ningún contenido de pantalla.
 */
class ClockAlignAccessibilityService : AccessibilityService() {

    private var lastMeasureMs = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        ClockBus.accessibilityConnected.value = true
        measureClock()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // La barra de estado suele pertenecer a SystemUI; re-medimos cuando cambia
        // su contenido/estado. Se ignora el resto salvo throttle mínimo.
        if (!ClockBus.config.value.anyAlignToClock) return
        val now = System.currentTimeMillis()
        if (now - lastMeasureMs < THROTTLE_MS) return
        lastMeasureMs = now
        measureClock()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        ClockBus.accessibilityConnected.value = false
    }

    /**
     * Busca el nodo del reloj en las ventanas del sistema (barra de estado) y
     * publica sus bounds en pantalla. No hace nada si no lo encuentra.
     */
    private fun measureClock() {
        val systemWindows = runCatching {
            windows.filter { it.type == AccessibilityWindowInfo.TYPE_SYSTEM }
        }.getOrNull() ?: return
        for (w in systemWindows) {
            val root = runCatching { w.root }.getOrNull() ?: continue
            val clock = findClock(root) ?: continue
            val r = Rect()
            clock.getBoundsInScreen(r)
            if (r.width() > 0 && r.height() > 0) {
                ClockBus.clockAnchor.value = ClockAnchor(r.left, r.top, r.width(), r.height())
                return
            }
        }
    }

    /** Localiza el nodo del reloj: primero por id conocido, luego por texto-hora. */
    private fun findClock(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (id in CLOCK_IDS) {
            val hit = runCatching { root.findAccessibilityNodeInfosByViewId(id) }
                .getOrNull()?.firstOrNull { it != null }
            if (hit != null) return hit
        }
        return findByTimeText(root)
    }

    /** Recorre el árbol buscando el primer nodo cuyo texto sea una hora. */
    private fun findByTimeText(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val txt = node.text?.toString()?.trim()
        if (txt != null && TIME_REGEX.matches(txt)) return node
        for (i in 0 until node.childCount) {
            val child = runCatching { node.getChild(i) }.getOrNull()
            findByTimeText(child)?.let { return it }
        }
        return null
    }

    private companion object {
        const val THROTTLE_MS = 400L

        // Ids del reloj en distintas variantes de SystemUI.
        val CLOCK_IDS = listOf(
            "com.android.systemui:id/clock",
            "com.android.systemui:id/clock_view",
            "com.android.systemui:id/statusBarClock",
        )

        // "8:12", "13:45", "8:12:59", "8:12 AM", etc.
        val TIME_REGEX = Regex("""\d{1,2}:\d{2}(:\d{2})?(\s?[AaPp]\.?[Mm]\.?)?""")
    }
}
