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
import android.widget.Toast
import com.minitimer.MainActivity
import com.minitimer.R
import com.minitimer.TimerBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
        val notification = buildNotification()
        startForegroundCompat(notification)
        observe()
        diagnose(notification)
    }

    private fun diagnose(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 36) {
            val nm = getSystemService(NotificationManager::class.java)
            val canPost = nm.canPostPromotedNotifications()
            val promotable = notification.hasPromotableCharacteristics()
            scope.launch {
                delay(800)
                val active = nm.activeNotifications.firstOrNull { it.id == NOTIF_ID }
                val promoted = active != null &&
                    (active.notification.flags and Notification.FLAG_PROMOTED_ONGOING) != 0
                Toast.makeText(
                    this@LiveTimerService,
                    "canPost=$canPost promotable=$promotable promoted=$promoted",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun observe() {
        // Re-publicar SOLO cuando cambia el estado (corriendo/pausa/fin/color), no
        // cada segundo: el cronómetro del chip se actualiza solo. Re-publicar muy
        // seguido hace que el sistema colapse la cápsula a solo-ícono.
        job = scope.launch {
            launch {
                combine(
                    TimerBus.done,
                    TimerBus.paused,
                    TimerBus.accent,
                ) { _, _, _ -> Unit }.collect { repost() }
            }
            // Refresco lento de la barra de progreso mientras corre.
            launch {
                while (true) {
                    delay(15_000)
                    repost()
                }
            }
        }
    }

    private fun repost() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val accent = TimerBus.accent.value.toInt()
        val remaining = TimerBus.remainingMs.value
        val total = TimerBus.totalMs.value
        val done = TimerBus.done.value
        val paused = TimerBus.paused.value
        val endAt = TimerBus.endAt.value
        val display = TimerBus.display.value.ifEmpty { "0:00" }
        val running = !done && !paused

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
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setOngoing(!done)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)

        // Mostrar la notificación de inmediato (sin el diferimiento de 10s que
        // Android aplica por defecto a los foreground services desde API 31).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        // El countdown del chip (Live Update) se alimenta del cronómetro sobre `when`.
        if (running && endAt > 0L) {
            builder.setShowWhen(true)
            builder.setWhen(endAt)
            builder.setUsesChronometer(true)
            builder.setChronometerCountDown(true)
        } else {
            builder.setUsesChronometer(false)
            builder.setShowWhen(false)
        }

        val max = 1000
        val elapsed = if (total > 0) {
            (((total - remaining).toDouble() / total) * max).toInt().coerceIn(0, max)
        } else {
            max
        }

        if (Build.VERSION.SDK_INT >= 36) {
            builder.setRequestPromotedOngoing(true)
            // El chip usa el cronómetro mientras corre; el texto corto solo cuando
            // no hay cronómetro (pausa), para no competir con la cuenta regresiva.
            if (!running) {
                builder.setShortCriticalText(display)
            }
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
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
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
        private const val CHANNEL_ID = "mini_timer_live_v2"
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
