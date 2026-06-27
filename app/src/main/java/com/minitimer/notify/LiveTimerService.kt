package com.minitimer.notify

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
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
    private var overlay: TimerOverlay? = null
    private val keyguard by lazy { getSystemService(KeyguardManager::class.java) }

    // Re-evalúa overlay y promoción cuando cambia el estado de bloqueo/pantalla.
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refresh()
    }

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
        overlay = TimerOverlay(this)
        ensureChannel()
        startForegroundCompat(buildNotification())
        registerScreenReceiver()
        observe()
        refresh()
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
    }

    /** El permiso "Mostrar sobre otras apps" está concedido. */
    private fun canOverlay(): Boolean = Settings.canDrawOverlays(this)

    private fun isLocked(): Boolean = keyguard?.isKeyguardLocked == true

    /**
     * Condiciones base de runtime para dibujar cualquier overlay propio (cápsula
     * o anillo): hay permiso, el timer no terminó, la app está en background y el
     * dispositivo está desbloqueado (no puede dibujarse sobre el lock screen).
     */
    private fun canDrawOverlayNow(): Boolean =
        canOverlay() &&
            !TimerBus.done.value &&
            !TimerBus.appForeground.value &&
            !isLocked()

    /** La cápsula flotante: habilitada por ajuste + condiciones de runtime. */
    private fun shouldShowOverlay(): Boolean =
        TimerBus.showOverlay.value && canDrawOverlayNow()

    /** El anillo sobre la cámara: habilitado por ajuste + condiciones de runtime. */
    private fun shouldShowRing(): Boolean =
        TimerBus.showRing.value && canDrawOverlayNow()

    /**
     * La promoción (chip / Now Bar del sistema) requiere el ajuste activo y se
     * muestra siempre que estés fuera de la app (background o bloqueo), salvo
     * que la cápsula flotante se esté mostrando (para no duplicar con ella).
     */
    private fun shouldPromote(): Boolean =
        TimerBus.showNowBar.value &&
            !TimerBus.appForeground.value &&
            !shouldShowOverlay()

    /** Re-publica la notificación y muestra/oculta cada overlay según el estado. */
    private fun refresh() {
        repost()
        if (shouldShowOverlay()) overlay?.showCapsule() else overlay?.hideCapsule()
        if (shouldShowRing()) overlay?.showRing() else overlay?.hideRing()
        overlay?.update()
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
                ) { _, _, _ -> Unit }.collect { refresh() }
            }
            // Re-evaluar al cambiar los interruptores de anillo/overlay/Now Bar.
            launch {
                combine(
                    TimerBus.showRing,
                    TimerBus.showOverlay,
                    TimerBus.showNowBar,
                ) { _, _, _ -> Unit }.collect { refresh() }
            }
            // Mostrar/ocultar el overlay y conmutar la promoción al entrar/salir
            // de la app (foreground/background).
            launch { TimerBus.appForeground.collect { refresh() } }
            // Salvaguarda periódica: re-evaluar overlay/promoción.
            launch {
                while (true) {
                    delay(30_000)
                    refresh()
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

        // Cuando NO se promueve (desbloqueado/background), el ícono de la barra de
        // estado del foreground service se hace transparente para que quede
        // invisible (el FGS siempre ocupa un slot, pero así no se ve). Al promover
        // (bloqueado) se usa el ícono real para la cápsula / Now Bar.
        val smallIcon = if (shouldPromote()) {
            R.drawable.ic_stat_timer
        } else {
            R.drawable.ic_stat_transparent
        }

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(smallIcon)
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

        // Sin barra de progreso: usamos "Standard Style" (sin Notification.Style),
        // que también califica como Live Update y clona el look del Timer de
        // Samsung (cápsula + expandida sin progress bar).
        if (Build.VERSION.SDK_INT >= 36) {
            // Promover (chip / Now Bar) solo cuando corresponde (ver shouldPromote):
            // al estar bloqueado, o siempre si no hay permiso de overlay.
            builder.setRequestPromotedOngoing(shouldPromote())
            // El chip usa el cronómetro mientras corre; el texto corto solo cuando
            // no hay cronómetro (pausa), para no competir con la cuenta regresiva.
            if (!running) {
                builder.setShortCriticalText(display)
            }
        }

        return builder.build()
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
        // IMPORTANCE_MIN: la notificación del foreground service NO muestra ícono
        // en la barra de estado (queda al fondo de la sombra). La cápsula/Now Bar
        // del lock screen sigue apareciendo vía promoción (setRequestPromotedOngoing)
        // cuando corresponde. Así, desbloqueado+background solo se ve el overlay.
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Timer",
            NotificationManager.IMPORTANCE_MIN,
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
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {
        }
        overlay?.hide()
        overlay = null
    }

    companion object {
        private const val CHANNEL_ID = "mini_timer_live_v3"
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
