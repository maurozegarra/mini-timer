package com.minitimer.notify

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Anillo de progreso que rodea el hueco de la cámara (punch-hole) en la píldora
 * colapsada del overlay. Dibuja un arco que se vacía según el progreso de la
 * cuenta regresiva, emulando el "edge lighting" alrededor del cutout.
 *
 * Mientras el timer corre se auto-anima derivando el progreso de `endAt`/`total`
 * con un ticker ligero (no depende de updates externos por segundo). En pausa o
 * al terminar muestra un valor estático.
 */
class CameraRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val stroke = 3.5f * density
    private val ringInset = 1f * density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        color = 0x33FFFFFF
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }

    private val oval = RectF()
    private var progress = 1f

    private var running = false
    private var endAt = 0L
    private var totalMs = 0L

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            val total = totalMs
            val p = if (total > 0L) {
                ((endAt - System.currentTimeMillis()).toFloat() / total).coerceIn(0f, 1f)
            } else {
                0f
            }
            setProgressInternal(p)
            if (p > 0f) postDelayed(this, TICK_MS)
        }
    }

    /** Color del arco de progreso (acento). */
    fun setColor(color: Int) {
        progressPaint.color = color
        invalidate()
    }

    /** Inicia la animación derivando el progreso de la cuenta regresiva. */
    fun setRunning(endAtMs: Long, total: Long) {
        endAt = endAtMs
        totalMs = total
        running = true
        removeCallbacks(ticker)
        post(ticker)
    }

    /** Muestra un progreso fijo (pausa o fin) y detiene la animación. */
    fun setStatic(value: Float) {
        running = false
        removeCallbacks(ticker)
        setProgressInternal(value)
    }

    private fun setProgressInternal(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        if (clamped != progress) {
            progress = clamped
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f - stroke / 2f - ringInset
        if (radius <= 0f) return
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)
        // Pista tenue de fondo + arco de progreso (empieza arriba, sentido horario).
        canvas.drawCircle(cx, cy, radius, trackPaint)
        canvas.drawArc(oval, -90f, 360f * progress, false, progressPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(ticker)
    }

    private companion object {
        const val TICK_MS = 250L
    }
}
