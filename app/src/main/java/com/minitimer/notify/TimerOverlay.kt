package com.minitimer.notify

import android.content.Context
import android.graphics.PixelFormat
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.minitimer.R
import com.minitimer.TimerBus
import com.minitimer.TimerCommand
import com.minitimer.data.SettingsStore
import com.minitimer.i18n.I18n
import com.minitimer.util.formatClock
import com.minitimer.util.formatDurationShort
import kotlin.math.abs

/**
 * Ventana overlay (TYPE_APPLICATION_OVERLAY) que clona el control del Timer de
 * Samsung sobre otras apps: colapsada (píldora con countdown) y expandida
 * (countdown grande + duración/hora + botones Cancel/Pausa). Mantiene la
 * pantalla encendida con FLAG_KEEP_SCREEN_ON mientras es visible. El estado se
 * lee de [TimerBus]; los botones disparan [TimerBus.command].
 */
class TimerOverlay(private val context: Context) {

    private val wm = context.getSystemService(WindowManager::class.java)
    private val inflater = LayoutInflater.from(context)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var root: View? = null
    private var collapsed: FrameLayout? = null
    private var pill: LinearLayout? = null
    private var expanded: LinearLayout? = null
    private var collapsedIcon: ImageView? = null
    private var cameraRing: CameraRingView? = null
    private var collapsedChrono: Chronometer? = null
    private var expandedChrono: Chronometer? = null
    private var infoView: TextView? = null
    private var btnCancel: ImageButton? = null
    private var btnPause: ImageButton? = null

    private var params: WindowManager.LayoutParams? = null
    private var isExpanded = false

    // Posición del estado colapsado (se actualiza al arrastrar). Por defecto:
    // pegado arriba (status bar) y a la derecha de la cámara central.
    private var collapsedX = 0
    private var collapsedY = 0

    val isShowing get() = root != null

    fun show() {
        if (root != null) return
        val v = inflater.inflate(R.layout.overlay_timer, null)
        root = v
        collapsed = v.findViewById(R.id.overlay_collapsed)
        pill = v.findViewById(R.id.overlay_pill)
        expanded = v.findViewById(R.id.overlay_expanded)
        collapsedIcon = v.findViewById(R.id.overlay_collapsed_icon)
        cameraRing = v.findViewById(R.id.overlay_camera_ring)
        collapsedChrono = v.findViewById(R.id.overlay_collapsed_chrono)
        expandedChrono = v.findViewById(R.id.overlay_expanded_chrono)
        infoView = v.findViewById(R.id.overlay_info)
        btnCancel = v.findViewById(R.id.overlay_btn_cancel)
        btnPause = v.findViewById(R.id.overlay_btn_pause)

        btnCancel?.setOnClickListener { TimerBus.command.tryEmit(TimerCommand.CANCEL) }
        btnPause?.setOnClickListener {
            if (TimerBus.paused.value) {
                TimerBus.command.tryEmit(TimerCommand.RESUME)
            } else {
                TimerBus.command.tryEmit(TimerCommand.PAUSE)
            }
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        params = lp
        // Píldora delgada. El alto total = referencia superior + extra inferior,
        // de modo que el borde superior conserve la misma distancia al hueco y el
        // grosor adicional se añada únicamente por debajo de la cámara.
        val pillH = dp(PILL_TOP_REF_DP + PILL_BOTTOM_EXTRA_DP)
        pill?.minimumHeight = pillH
        // Posición colapsada por defecto: con el hueco (paddingStart + ícono +
        // mitad del gap) centrado sobre la cámara del medio de la pantalla. El
        // borde superior se ancla usando solo la referencia superior, así el
        // extra inferior engrosa la píldora hacia abajo.
        collapsedX = screenWidth() / 2 - dp(CAMERA_GAP_CENTER_DP)
        collapsedY = ((statusBarHeight() - dp(PILL_TOP_REF_DP)) / 2).coerceAtLeast(0)
        // Si el usuario ya posicionó la píldora arrastrándola, respetar esa elección.
        SettingsStore(context).loadOverlayPos()?.let { (x, y) ->
            collapsedX = x
            collapsedY = y
        }
        attachDrag(v, lp)
        applyExpanded()
        try {
            wm?.addView(v, lp)
        } catch (_: Exception) {
            root = null
            return
        }
        update()
    }

    fun hide() {
        val v = root ?: return
        try {
            wm?.removeView(v)
        } catch (_: Exception) {
        }
        root = null
    }

    /** Re-vincula el estado actual del timer a las vistas del overlay. */
    fun update() {
        if (root == null) return
        val accent = TimerBus.accent.value.toInt()
        val paused = TimerBus.paused.value
        val running = !TimerBus.done.value && !paused
        // Mientras corre, el restante se deriva de endAt (estable) para no
        // depender de que el ViewModel siga emitiendo remainingMs.
        val remaining = if (running && TimerBus.endAt.value > 0L) {
            (TimerBus.endAt.value - System.currentTimeMillis()).coerceAtLeast(0)
        } else {
            TimerBus.remainingMs.value
        }
        val base = SystemClock.elapsedRealtime() + remaining

        for (chrono in listOf(collapsedChrono, expandedChrono)) {
            chrono ?: continue
            chrono.isCountDown = true
            chrono.base = base
            if (running) chrono.start() else chrono.stop()
        }
        collapsedChrono?.setTextColor(0xFFFFFFFF.toInt())
        collapsedIcon?.setColorFilter(accent)
        expandedChrono?.setTextColor(accent)

        // Anillo de progreso alrededor del hueco de la cámara.
        cameraRing?.setColor(accent)
        val total = TimerBus.totalMs.value
        if (running && TimerBus.endAt.value > 0L && total > 0L) {
            cameraRing?.setRunning(TimerBus.endAt.value, total)
        } else {
            val p = if (total > 0L) remaining.toFloat() / total else 0f
            cameraRing?.setStatic(p)
        }
        infoView?.text = buildInfo()
        btnPause?.setImageResource(
            if (paused) R.drawable.ic_notif_play else R.drawable.ic_notif_pause,
        )
    }

    private fun buildInfo(): String {
        val t = I18n.get(SettingsStore(context).load().language)
        val total = TimerBus.totalMs.value
        val endAt = TimerBus.endAt.value
        val durationLabel = formatDurationShort(total)
        val paused = TimerBus.paused.value
        return when {
            TimerBus.done.value -> t.timeUp
            !paused && endAt > 0L -> "$durationLabel / ${formatClock(endAt, t.locale)}"
            paused -> "$durationLabel · ${t.paused}"
            else -> durationLabel
        }
    }

    private fun toggleExpanded() {
        isExpanded = !isExpanded
        applyExpanded()
        update()
    }

    private fun applyExpanded() {
        collapsed?.visibility = if (isExpanded) View.GONE else View.VISIBLE
        expanded?.visibility = if (isExpanded) View.VISIBLE else View.GONE
        val lp = params ?: return
        if (isExpanded) {
            // Tarjeta centrada, justo debajo de la barra de estado.
            lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            lp.x = 0
            lp.y = statusBarHeight() + dp(8)
        } else {
            // Píldora arriba (status bar), anclada desde la izquierda a la
            // derecha de la cámara.
            lp.gravity = Gravity.TOP or Gravity.START
            lp.x = collapsedX
            lp.y = collapsedY
        }
        if (root != null) {
            try {
                wm?.updateViewLayout(root, lp)
            } catch (_: Exception) {
            }
        }
    }

    /** Permite arrastrar la ventana; un toque sin desplazamiento alterna colapsar/expandir. */
    private fun attachDrag(v: View, lp: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!moved && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        moved = true
                    }
                    if (moved) {
                        lp.x = startX + dx.toInt()
                        lp.y = startY + dy.toInt()
                        try {
                            wm?.updateViewLayout(v, lp)
                        } catch (_: Exception) {
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        toggleExpanded()
                    } else if (!isExpanded) {
                        // Recordar y PERSISTIR la posición colapsada tras arrastrar,
                        // para que el usuario pueda centrar el anillo manualmente y
                        // su elección sobreviva a reinicios del overlay.
                        collapsedX = lp.x
                        collapsedY = lp.y
                        SettingsStore(context).saveOverlayPos(collapsedX, collapsedY)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun screenWidth(): Int = context.resources.displayMetrics.widthPixels

    private fun statusBarHeight(): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else dp(28)
    }

    private companion object {
        // Distancia (dp) desde el borde izquierdo de la píldora hasta el centro
        // del hueco de la cámara = paddingStart (10) + ícono (16) + margen (6) +
        // mitad del gap (34/2 = 17).
        const val CAMERA_GAP_CENTER_DP = 49

        // Alto de la píldora colapsada (más delgada que la barra de estado).
        // Se reparte en una referencia superior (define la distancia del borde
        // superior al hueco) y un extra que engrosa la píldora hacia abajo.
        const val PILL_TOP_REF_DP = 26
        const val PILL_BOTTOM_EXTRA_DP = 6
    }
}
