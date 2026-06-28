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
import com.minitimer.util.formatRemaining
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

    /**
     * Mantener la pantalla encendida cuando el timer corre en segundo plano y la
     * pantalla está desbloqueada (no tiene sentido forzarla con el dispositivo
     * bloqueado). Requiere permiso de overlay para la ventana invisible.
     */
    private fun shouldKeepScreenOn(): Boolean =
        canOverlay() &&
            !TimerBus.appForeground.value &&
            !TimerBus.done.value &&
            !TimerBus.paused.value &&
            !isLocked()

    /** Re-publica la notificación y muestra/oculta cada overlay según el estado. */
    private fun refresh() {
        repost()
        if (shouldShowOverlay()) overlay?.showCapsule() else overlay?.hideCapsule()
        if (shouldShowRing()) overlay?.showRing() else overlay?.hideRing()
        if (shouldKeepScreenOn()) overlay?.showKeepAwake() else overlay?.hideKeepAwake()
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
                    TimerBus.label,
                ) { _, _, _, _ -> Unit }.collect { refresh() }
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
            // El contentTitle lleva el countdown, y tanto el Now Bar colapsado
            // (bloqueado) como la fila grande de la notificación (desbloqueado en la
            // bandeja) lo muestran sin animarlo solos. Re-publicamos cada segundo
            // siempre que el timer corra y la app esté en segundo plano (bloqueado o
            // desbloqueado) para que el número avance en vivo.
            launch {
                while (true) {
                    delay(1_000)
                    if (!TimerBus.appForeground.value &&
                        !TimerBus.done.value &&
                        !TimerBus.paused.value
                    ) {
                        repost()
                    }
                    // Reaccionar a bloqueo/desbloqueo (no hay un flow para esto):
                    // mantener o liberar la pantalla encendida cada segundo.
                    if (shouldKeepScreenOn()) {
                        overlay?.showKeepAwake()
                    } else {
                        overlay?.hideKeepAwake()
                    }
                }
            }
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
        val running = !done && !paused

        val t = I18n.get(SettingsStore(this).load().language)

        // Restante en vivo derivado de endAt (estable).
        val remainingMs = if (endAt > 0L) {
            (endAt - System.currentTimeMillis()).coerceAtLeast(0)
        } else {
            TimerBus.remainingMs.value
        }
        val remainingText = formatRemaining(remainingMs)
        // El Now Bar colapsado (lock screen) muestra el contentTitle, no el
        // cronómetro, así que el countdown va en el propio título. En pantalla
        // bloqueada se re-publica cada segundo (ver observe()); desbloqueado, el
        // chip del sistema usa el cronómetro. Sin duración ni hora final.
        val title = when {
            done -> t.timeUp
            paused -> "$remainingText · ${t.paused}"
            else -> remainingText
        }
        // Segunda línea (expandido): "<nombre|duración> / <hora de término>".
        // El primer segmento es el NOMBRE si se ha seteado; si no, la duración.
        val label = TimerBus.label.value
        val durationLabel = formatDurationShort(total)
        val leadLabel = if (label.isNotBlank()) label else durationLabel
        val subText = when {
            done -> ""
            endAt > 0L -> "$leadLabel / ${formatClock(endAt, t.locale)}"
            else -> leadLabel
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
            // El countdown en vivo lo provee el cronómetro; el título solo indica
            // el estado (Timer / pausa / ¡Tiempo!), sin duración ni hora final.
            .setContentTitle(title)
            // Segunda línea (expandido): duración / hora de término.
            .setContentText(subText)
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

        // Sin cronómetro en la cabecera (se quitó para evitar la redundancia con
        // el countdown del título). El countdown en vivo va en el contentTitle, que
        // se re-publica cada segundo cuando la app está en segundo plano.
        builder.setUsesChronometer(false)
        builder.setShowWhen(false)

        if (Build.VERSION.SDK_INT >= 36) {
            // Sin ProgressStyle: usamos Standard Style (también válido como Live
            // Update). El countdown en vivo va en el título y la duración/hora final
            // en la segunda línea; no se muestra barra de progreso.
            // Promover (chip / Now Bar) solo cuando corresponde (ver shouldPromote).
            builder.setRequestPromotedOngoing(shouldPromote())
            // Sin cronómetro: el texto corto del chip colapsado lleva el countdown
            // (o "¡Tiempo!" al finalizar). Se actualiza vía re-publicación.
            builder.setShortCriticalText(if (done) t.timeUp else remainingText)
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
        // Cancelar primero los colectores/bucles para que no haya re-publicaciones
        // tardías (al cancelar, el cambio a SETUP dispara un repost que podía dejar
        // una notificación huérfana congelada tras detener el servicio).
        job?.cancel()
        scope.cancel()
        // Quitar explícitamente la notificación del foreground service.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
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
