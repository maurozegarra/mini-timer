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
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.widget.RemoteViews
import com.minitimer.MainActivity
import com.minitimer.R
import com.minitimer.TimerBus
import com.minitimer.TimerCommand
import com.minitimer.data.SettingsStore
import com.minitimer.i18n.I18n
import com.minitimer.util.formatClock
import com.minitimer.util.formatDurationShort
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
    private var screenLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    // No reiniciar el servicio con estado vacío si el sistema lo mata (p. ej.
    // al hacer swipe en Recientes): el estado se restaura desde el ViewModel.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> TimerBus.command.tryEmit(TimerCommand.PAUSE)
            ACTION_RESUME -> TimerBus.command.tryEmit(TimerCommand.RESUME)
            ACTION_CANCEL -> TimerBus.command.tryEmit(TimerCommand.CANCEL)
        }
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        acquireScreenLock()
        ensureChannel()
        startForegroundCompat(buildNotification())
        observe()
    }

    // Mantiene la pantalla encendida mientras el timer corre, tanto en primer
    // plano como en background (el FLAG_KEEP_SCREEN_ON solo cubre la Activity
    // visible). Usa el screen wake lock (deprecado pero aún funcional) porque es
    // la única API capaz de evitar el apagado de pantalla desde un servicio.
    private fun acquireScreenLock() {
        val pm = getSystemService(PowerManager::class.java) ?: return
        @Suppress("DEPRECATION")
        screenLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK,
            "MiniTimer:screen",
        ).apply {
            setReferenceCounted(false)
            acquire()
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

        val t = I18n.get(SettingsStore(this).load().language)

        // Línea secundaria: "<duración seleccionada> / <hora de término>".
        val durationLabel = formatDurationShort(total)
        val infoLine = when {
            done -> t.timeUp
            running && endAt > 0L -> "$durationLabel / ${formatClock(endAt, t.locale)}"
            paused -> "$durationLabel · ${t.paused}"
            else -> durationLabel
        }

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
            // El countdown en vivo lo provee el cronómetro; el título secundario
            // muestra la duración y la hora de término. No se repite "Mini Timer".
            .setContentTitle(infoLine)
            .setColor(accent)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setOngoing(!done)
            .setOnlyAlertOnce(true)
            // Mostrar el contenido completo (countdown + botones) también en la
            // pantalla de bloqueo / Now Bar, sin ocultarlo.
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(pi)

        // Botones del Now Bar: Cancelar (izquierda) + Pausa/Reanudar (derecha).
        if (!done) {
            builder.addAction(action(R.drawable.ic_notif_close, t.cancel, ACTION_CANCEL))
            if (running) {
                builder.addAction(action(R.drawable.ic_notif_pause, t.pause, ACTION_PAUSE))
            } else {
                builder.addAction(action(R.drawable.ic_notif_play, t.resume, ACTION_RESUME))
            }
        }

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

        // Extras propietarios de One UI (Live Notifications / Now Bar): replican el
        // look del Timer de Samsung -> cronómetro grande como sección primaria (sin
        // título) y la duración/hora como texto secundario. Si One UI no respeta
        // estos extras (apps no whitelisteadas), simplemente se ignoran y queda el
        // fallback estándar de arriba.
        if (!done) {
            builder.addExtras(buildOneUiExtras(remaining, infoLine, running, t.title))
        }

        return builder.build()
    }

    /** Extras de One UI para mostrar el cronómetro grande sin título en el Now Bar. */
    private fun buildOneUiExtras(
        remaining: Long,
        secondaryInfo: String,
        running: Boolean,
        chipText: String,
    ): Bundle {
        val rv = RemoteViews(packageName, R.layout.notif_chronometer)
        // El Chronometer usa base sobre elapsedRealtime (no epoch); en cuenta
        // regresiva muestra (base - ahora) = tiempo restante.
        val base = SystemClock.elapsedRealtime() + remaining
        rv.setChronometerCountDown(R.id.notif_chronometer, true)
        rv.setChronometer(R.id.notif_chronometer, base, null, running)
        return Bundle().apply {
            putParcelable("android.ongoingActivityNoti.chronometerRemoteView", rv)
            putInt("android.ongoingActivityNoti.chronometerRemoteViewPosition", 1)
            putString(
                "android.ongoingActivityNoti.chronometerRemoteViewTag",
                "ongoing_remote_views_tag",
            )
            putString("android.ongoingActivityNoti.chipExpandedText", chipText)
            putString("android.ongoingActivityNoti.secondaryInfo", secondaryInfo)
            putInt("android.ongoingActivityNoti.nowbarChronometerPosition", 1)
        }
    }

    /** Crea una acción de notificación que envía un comando al servicio. */
    private fun action(icon: Int, title: String, actionName: String): Notification.Action {
        val intent = Intent(this, LiveTimerService::class.java).setAction(actionName)
        val pi = PendingIntent.getService(
            this,
            actionName.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(
            android.graphics.drawable.Icon.createWithResource(this, icon),
            title,
            pi,
        ).build()
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
        screenLock?.let { if (it.isHeld) it.release() }
        screenLock = null
    }

    companion object {
        private const val CHANNEL_ID = "mini_timer_live_v2"
        private const val NOTIF_ID = 42
        private const val ACTION_PAUSE = "com.minitimer.action.PAUSE"
        private const val ACTION_RESUME = "com.minitimer.action.RESUME"
        private const val ACTION_CANCEL = "com.minitimer.action.CANCEL"

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
