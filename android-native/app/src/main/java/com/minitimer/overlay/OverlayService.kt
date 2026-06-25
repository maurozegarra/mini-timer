package com.minitimer.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.minitimer.R
import com.minitimer.TimerBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano que dibuja una ventana flotante (system overlay)
 * sobre otras apps, mostrando el tiempo restante en vivo desde TimerBus.
 */
class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var textView: TextView? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundCompat()
        addOverlay()
        observeBus()
    }

    private fun addOverlay() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val tv = TextView(this).apply {
            text = TimerBus.display.value
            setTextColor(TimerBus.accent.value.toInt())
            textSize = 22f
            setBackgroundColor(Color.argb(220, 0, 0, 0))
            setPadding(36, 18, 36, 18)
            try {
                typeface = ResourcesCompat.getFont(this@OverlayService, R.font.jetbrains_mono_light)
            } catch (_: Exception) {
                typeface = Typeface.MONOSPACE
            }
        }
        textView = tv

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 120
        }

        try {
            wm.addView(tv, params)
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun observeBus() {
        collectJob = scope.launch {
            combine(TimerBus.display, TimerBus.accent, TimerBus.done) { text, accent, done ->
                Triple(text, accent, done)
            }.collect { (text, accent, done) ->
                textView?.text = text
                textView?.setTextColor(if (done) 0xFFFF5252.toInt() else accent.toInt())
            }
        }
    }

    private fun startForegroundCompat() {
        val channelId = "mini_timer_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                channelId,
                "Timer overlay",
                NotificationManager.IMPORTANCE_LOW,
            )
            nm.createNotificationChannel(channel)
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val notification: Notification = builder
            .setContentTitle("Mini Timer")
            .setContentText("Floating timer active")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .build()
        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        collectJob?.cancel()
        scope.cancel()
        textView?.let { tv ->
            try {
                windowManager?.removeView(tv)
            } catch (_: Exception) {
            }
        }
        textView = null
        windowManager = null
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
