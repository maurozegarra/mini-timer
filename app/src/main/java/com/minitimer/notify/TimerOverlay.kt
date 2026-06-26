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
    private var collapsed: LinearLayout? = null
    private var expanded: LinearLayout? = null
    private var collapsedIcon: ImageView? = null
    private var collapsedChrono: Chronometer? = null
    private var expandedChrono: Chronometer? = null
    private var infoView: TextView? = null
    private var btnCancel: ImageButton? = null
    private var btnPause: ImageButton? = null

    private var params: WindowManager.LayoutParams? = null
    private var isExpanded = false

    val isShowing get() = root != null

    fun show() {
        if (root != null) return
        val v = inflater.inflate(R.layout.overlay_timer, null)
        root = v
        collapsed = v.findViewById(R.id.overlay_collapsed)
        expanded = v.findViewById(R.id.overlay_expanded)
        collapsedIcon = v.findViewById(R.id.overlay_collapsed_icon)
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
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(36)
        }
        params = lp
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
            chrono.setTextColor(accent)
        }
        collapsedIcon?.setColorFilter(accent)
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
                    if (!moved) toggleExpanded()
                    true
                }
                else -> false
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
