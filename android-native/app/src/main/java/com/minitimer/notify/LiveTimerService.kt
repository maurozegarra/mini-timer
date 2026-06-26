package com.minitimer.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.minitimer.MainActivity
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
 * Servicio en primer plano que publica la notificación "Live Update"
 * (Promoted Ongoing Notification) del temporizador: en Android 16+ aparece
 * como chip en la barra de estado / Now Bar con cuenta regresiva y progreso.
 * En versiones anteriores se degrada a una notificación de progreso normal.
 */
class LiveTimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForegroundCompat(buildNotification())
        observe()
    }

    private fun observe() {
        job = scope.launch {
            combine(
                TimerBus.display,
                TimerBus.done,
                TimerBus.paused,
                TimerBus.accent,
            ) { _, _, _, _ -> Unit }.collect {
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NOTIF_ID, buildNotification())
            }
        }
    }

    private fun buildNotification(): Notification {
        val accent = TimerBus.accent.value.toInt()
        val remaining = TimerBus.remainingMs.value
        val total = TimerBus.totalMs.value
        val done = TimerBus.done.value
        val display = TimerBus.display.value.ifEmpty { "0:00" }

        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("Mini Timer")
            .setContentText(display)
            .setColor(accent)
            .setColorized(true)
            .setOngoing(!done)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)

        val max = 1000
        val elapsed = if (total > 0) {
            (((total - remaining).toDouble() / total) * max).toInt().coerceIn(0, max)
        } else {
            max
        }

        if (Build.VERSION.SDK_INT >= 36) {
            builder.setShortCriticalText(display)
            builder.setRequestPromotedOngoing(true)
            val segment = Notification.ProgressStyle.Segment(max).setColor(accent)
            val style = Notification.ProgressStyle()
                .addProgressSegment(segment)
                .setProgress(elapsed)
            builder.style = style
        } else {
            @Suppress("DEPRECATION")
            builder.setProgress(max, elapsed, false)
        }

        return builder.build()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Timer",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        nm.createNotificationChannel(channel)
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
        scope.cancel()
    }

    companion object {
        private const val CHANNEL_ID = "mini_timer_live"
        private const val NOTIF_ID = 42

        fun start(context: Context) {
            val intent = Intent(context, LiveTimerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LiveTimerService::class.java))
        }
    }
}
