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
    private var expanded: LinearLayout? = null
    private var collapsedChrono: Chronometer? = null
    private var expandedChrono: Chronometer? = null
    private var infoView: TextView? = null
    private var btnCancel: ImageButton? = null
    private var btnPause: ImageButton? = null

    // Ventana INDEPENDIENTE del anillo de la cámara (solo visual, no táctil).
    private var ringRoot: View? = null
    private var ringParams: WindowManager.LayoutParams? = null
    private var cameraRing: CameraRingView? = null

    private var params: WindowManager.LayoutParams? = null
    private var isExpanded = false

    val isShowing get() = root != null

    fun show() {
        if (root != null) return
        // --- Ventana de la cápsula (timer + tarjeta expandida) ---
        val v = inflater.inflate(R.layout.overlay_timer, null)
        root = v
        collapsed = v.findViewById(R.id.overlay_collapsed)
        expanded = v.findViewById(R.id.overlay_expanded)
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
        attachTap(v)
        applyExpanded()
        try {
            wm?.addView(v, lp)
        } catch (_: Exception) {
            root = null
            return
        }

        // --- Ventana INDEPENDIENTE del anillo, sobre la cámara, no táctil ---
        val r = inflater.inflate(R.layout.overlay_ring, null)
        ringRoot = r
        cameraRing = r.findViewById(R.id.overlay_camera_ring)
        val rlp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        rlp.gravity = Gravity.TOP or Gravity.START
        positionRing(rlp)
        ringParams = rlp
        try {
            wm?.addView(r, rlp)
        } catch (_: Exception) {
            ringRoot = null
        }

        update()
    }

    /** Centra el anillo sobre la cámara (centro-superior) + offset fino del usuario. */
    private fun positionRing(lp: WindowManager.LayoutParams) {
        val (offX, offY) = SettingsStore(context).loadRingOffset()
        lp.x = screenWidth() / 2 - dp(RING_W_DP) / 2 + dp(offX)
        lp.y = ((statusBarHeight() - dp(RING_H_DP)) / 2 + dp(offY)).coerceAtLeast(0)
    }

    fun hide() {
        root?.let {
            try {
                wm?.removeView(it)
            } catch (_: Exception) {
            }
        }
        root = null
        ringRoot?.let {
            try {
                wm?.removeView(it)
            } catch (_: Exception) {
            }
        }
        ringRoot = null
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
        // La cápsula muestra el timer con el color de acento seleccionado.
        collapsedChrono?.setTextColor(accent)
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
        // Centradas horizontalmente. La cápsula (colapsada) se sube un poco más,
        // pegada al borde inferior de la barra de estado; la tarjeta expandida
        // mantiene un margen mayor. El anillo es independiente.
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.x = 0
        lp.y = statusBarHeight() + if (isExpanded) dp(8) else dp(1)
        if (root != null) {
            try {
                wm?.updateViewLayout(root, lp)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Un toque sin desplazamiento alterna colapsar/expandir. El posicionamiento
     * del anillo se hace con los controles +/- de ajustes (offset persistido),
     * no arrastrando, para tener un centrado fino y reproducible.
     */
    private fun attachTap(v: View) {
        var downX = 0f
        var downY = 0f
        var moved = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!moved && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        moved = true
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) toggleExpanded()
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
        // Dimensiones (dp) del anillo independiente, deben coincidir con las del
        // CameraRingView en overlay_ring.xml. Se usan para centrarlo sobre la
        // cámara antes de aplicar el offset fino del usuario.
        const val RING_W_DP = 38
        const val RING_H_DP = 32
    }
}
