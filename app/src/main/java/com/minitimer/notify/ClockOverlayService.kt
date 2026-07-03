package com.minitimer.notify

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.minitimer.ClockBus
import com.minitimer.MainActivity
import com.minitimer.R
import com.minitimer.data.SettingsStore
import com.minitimer.i18n.I18n
import com.minitimer.model.ClockConfig
import com.minitimer.model.OsdPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Servicio en primer plano que dibuja el/los OSD del reloj (hasta 2 paneles
 * INDEPENDIENTES) como franjas flotantes sobre otras apps mediante ventanas
 * [WindowManager] TYPE_APPLICATION_OVERLAY. Cada panel muestra hora (con
 * segundos en vivo), volumen multimedia [n] y batería [n%]; opcionalmente fecha
 * y memo. El contenido se refresca cada segundo. Cada franja es arrastrable y su
 * posición se guarda en [SettingsStore].
 */
class ClockOverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    private val wm by lazy { getSystemService(WindowManager::class.java) }
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private val battery by lazy { getSystemService(BatteryManager::class.java) }
    private val store by lazy { SettingsStore(this) }

    private val panels = arrayOfNulls<PanelWindow>(2)
    private var locale: Locale = Locale.getDefault()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onCreate() {
        super.onCreate()
        locale = I18n.get(store.loadConfig().general.language).locale
        ensureChannel()
        // Si el sistema no permite iniciar el FGS (p. ej. arranque restringido en
        // segundo plano), degradar con elegancia en vez de crashear.
        try {
            startForegroundCompat(buildNotification())
        } catch (_: Exception) {
            stopSelf()
            return
        }
        observe()
        refresh()
    }

    /**
     * Si el usuario quita la app de Recientes, reprograma un reinicio para que el
     * OSD sobreviva al swipe (algunos fabricantes matan el servicio). Best-effort.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (ClockBus.config.value.anyEnabled) {
            runCatching {
                val restart = Intent(applicationContext, ClockOverlayService::class.java)
                val pi = PendingIntent.getService(
                    this,
                    2,
                    restart,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT,
                )
                getSystemService(android.app.AlarmManager::class.java)
                    ?.set(android.app.AlarmManager.RTC, System.currentTimeMillis() + 1_000, pi)
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun observe() {
        job = scope.launch {
            launch { ClockBus.config.collect { refresh() } }
            // Tick de 1s: hora con segundos, volumen y batería en vivo.
            launch {
                while (true) {
                    delay(1_000)
                    updateContent()
                }
            }
        }
    }

    private fun canOverlay(): Boolean = Settings.canDrawOverlays(this)

    /** Muestra/oculta cada panel según su estado; se detiene si ninguno está activo. */
    private fun refresh() {
        val cfg = ClockBus.config.value
        if (!cfg.anyEnabled) {
            stopSelf()
            return
        }
        for (i in 0..1) {
            val panel = cfg.panel(i)
            if (panel.enabled && canOverlay()) showPanel(i, panel) else hidePanel(i)
        }
        updateContent()
    }

    private fun showPanel(index: Int, panel: OsdPanel) {
        val existing = panels[index]
        if (existing != null) {
            existing.applyStyle(panel)
            return
        }
        val pw = PanelWindow(index)
        if (pw.attach(panel)) panels[index] = pw
    }

    private fun hidePanel(index: Int) {
        panels[index]?.detach()
        panels[index] = null
    }

    /** Refresca el texto (hora/volumen/batería/carga/fecha/memo) de los paneles visibles. */
    private fun updateContent() {
        if (panels.all { it == null }) return
        val now = Date()
        val vol = audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        val batt = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
        val charging = chargingLabel()
        val cfg = ClockBus.config.value
        for (i in 0..1) {
            panels[i]?.bind(cfg.panel(i), now, vol, batt, charging)
        }
    }

    /** Etiqueta del estado de carga ([AC]/[USB]/[Wireless]); null si no carga. */
    private fun chargingLabel(): String? {
        val plugged = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "[AC]"
            BatteryManager.BATTERY_PLUGGED_USB -> "[USB]"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "[Wireless]"
            else -> null
        }
    }

    /** Color efectivo del texto: automático por modo oscuro, o el color elegido. */
    private fun resolveColor(panel: OsdPanel): Int =
        if (panel.autoDarkColor) {
            if (isNightMode()) Color.WHITE else Color.BLACK
        } else {
            panel.textColor.toInt()
        }

    private fun isNightMode(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun mainText(panel: OsdPanel, now: Date, vol: Int, batt: Int, charging: String?): String {
        val parts = mutableListOf<String>()
        if (panel.showDate) parts += dateFmt(panel).format(now)
        if (panel.showTime) parts += timeFmt(panel).format(now)
        if (panel.showCharging && charging != null) parts += charging
        if (panel.showVolume) parts += "[$vol]"
        if (panel.showBattery) parts += "[$batt%]"
        return parts.joinToString("  ")
    }

    private fun timeFmt(panel: OsdPanel): SimpleDateFormat {
        val pattern = when {
            panel.use24h && panel.showSeconds -> "H:mm:ss"
            panel.use24h -> "H:mm"
            panel.showSeconds -> "h:mm:ss"
            else -> "h:mm"
        }
        return SimpleDateFormat(pattern, locale)
    }

    private fun dateFmt(panel: OsdPanel): SimpleDateFormat = SimpleDateFormat("EEE d MMM", locale)

    // ---------------------------------------------------------------------
    // Una ventana overlay por panel: franja arrastrable con hora + indicadores.
    // ---------------------------------------------------------------------
    private inner class PanelWindow(private val index: Int) {
        private lateinit var container: LinearLayout
        private lateinit var mainView: TextView
        private lateinit var memoView: TextView
        private val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )

        fun attach(panel: OsdPanel): Boolean {
            val typeface = runCatching {
                ResourcesCompat.getFont(this@ClockOverlayService, R.font.jetbrains_mono_semibold)
            }.getOrNull()
            mainView = TextView(this@ClockOverlayService).apply {
                setSingleLine(true)
                setTypeface(typeface)
            }
            memoView = TextView(this@ClockOverlayService).apply {
                setSingleLine(true)
                setTypeface(typeface)
            }
            container = LinearLayout(this@ClockOverlayService).apply {
                orientation = LinearLayout.VERTICAL
                addView(mainView)
                addView(memoView)
            }
            lp.gravity = Gravity.TOP or Gravity.START
            val (x, y) = store.loadOsdPos(index)
            lp.x = x
            lp.y = y
            attachDrag()
            applyStyle(panel)
            return try {
                wm?.addView(container, lp)
                // Rescata paneles cuya posición guardada quedó fuera de la zona
                // segura (p. ej. atrapados en la barra de estado antes del fix).
                container.post { clampToBounds() }
                true
            } catch (_: Exception) {
                false
            }
        }

        private var snapAnim: ValueAnimator? = null

        /** Anima el panel desde su posición actual hasta ([targetX], [targetY])
         *  con desaceleración, como si el borde lo atrajera. Guarda al terminar. */
        private fun animateSnap(targetX: Int, targetY: Int) {
            snapAnim?.cancel()
            val fromX = lp.x
            val fromY = lp.y
            if (fromX == targetX && fromY == targetY) {
                store.saveOsdPos(index, targetX, targetY)
                return
            }
            snapAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 260L
                interpolator = DecelerateInterpolator(1.6f)
                addUpdateListener { a ->
                    val f = a.animatedValue as Float
                    lp.x = (fromX + (targetX - fromX) * f).roundToInt()
                    lp.y = (fromY + (targetY - fromY) * f).roundToInt()
                    try {
                        wm?.updateViewLayout(container, lp)
                    } catch (_: Exception) {
                    }
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        lp.x = targetX
                        lp.y = targetY
                        try {
                            wm?.updateViewLayout(container, lp)
                        } catch (_: Exception) {
                        }
                        store.saveOsdPos(index, targetX, targetY)
                    }
                })
                start()
            }
        }

        /** Reubica el panel dentro de la zona segura si quedó fuera. */
        private fun clampToBounds() {
            val b = dragBounds(container)
            val nx = lp.x.coerceIn(b[0], b[2])
            val ny = lp.y.coerceIn(b[1], b[3])
            if (nx != lp.x || ny != lp.y) {
                lp.x = nx
                lp.y = ny
                try {
                    wm?.updateViewLayout(container, lp)
                } catch (_: Exception) {
                }
                store.saveOsdPos(index, lp.x, lp.y)
            }
        }

        fun detach() {
            snapAnim?.cancel()
            try {
                wm?.removeView(container)
            } catch (_: Exception) {
            }
        }

        /** Aplica tamaño, fondo y padding (apariencia del panel). El color se
         *  fija en [bind] para que el modo oscuro automático se refresque. */
        fun applyStyle(panel: OsdPanel) {
            val sizeSp = panel.textSizeSp.toFloat()
            mainView.textSize = sizeSp
            memoView.textSize = (sizeSp - 3f).coerceAtLeast(10f)
            val padH = dp(10)
            val padV = dp(4)
            container.setPadding(padH, padV, padH, padV)
            val bgAlpha = (panel.bgAlpha.coerceIn(0f, 1f) * 255).roundToInt()
            container.background = if (bgAlpha == 0) {
                null
            } else {
                GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(Color.argb(bgAlpha, 0, 0, 0))
                }
            }
        }

        /** Vincula el contenido de texto (hora/indicadores/memo) y el color. */
        fun bind(panel: OsdPanel, now: Date, vol: Int, batt: Int, charging: String?) {
            val color = resolveColor(panel)
            mainView.setTextColor(color)
            memoView.setTextColor(color)
            mainView.text = mainText(panel, now, vol, batt, charging)
            val showMemo = panel.showMemo && panel.memo.isNotBlank()
            memoView.visibility = if (showMemo) View.VISIBLE else View.GONE
            if (showMemo) memoView.text = panel.memo
        }

        private fun attachDrag() {
            var downRawX = 0f
            var downRawY = 0f
            var startX = 0
            var startY = 0
            container.setOnTouchListener { _, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        snapAnim?.cancel()
                        downRawX = e.rawX
                        downRawY = e.rawY
                        startX = lp.x
                        startY = lp.y
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // Limitar a la zona segura: nunca entra en la barra de
                        // estado ni en la de navegación (evita quedar atrapado).
                        val b = dragBounds(container)
                        lp.x = (startX + (e.rawX - downRawX).toInt()).coerceIn(b[0], b[2])
                        lp.y = (startY + (e.rawY - downRawY).toInt()).coerceIn(b[1], b[3])
                        try {
                            wm?.updateViewLayout(container, lp)
                        } catch (_: Exception) {
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (abs(e.rawX - downRawX) > 2 || abs(e.rawY - downRawY) > 2) {
                            // Magnet: al soltar, el lado más cercano "atrae" el panel
                            // con un movimiento suave (no un salto brusco).
                            val b = dragBounds(container)
                            val (sw, _) = screenBounds()
                            val centerX = lp.x + container.width / 2
                            val targetX = if (centerX < sw / 2) b[0] else b[2]
                            val targetY = lp.y.coerceIn(b[1], b[3])
                            animateSnap(targetX, targetY)
                        }
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    /** Ancho/alto de la pantalla completa (incluye barras del sistema). */
    private fun screenBounds(): Pair<Int, Int> {
        val w = wm
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && w != null) {
            val b = w.maximumWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val dm = resources.displayMetrics
            dm.widthPixels to dm.heightPixels
        }
    }

    private fun statusBarHeightFallback(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(24)
    }

    /**
     * Zona segura para arrastrar un panel: [minX, minY, maxX, maxY]. Deja fuera la
     * barra de estado (arriba) y la de navegación (abajo) para que el OSD no quede
     * atrapado en el "shade" del sistema ni bajo los gestos de navegación.
     */
    private fun dragBounds(view: View): IntArray {
        val (sw, sh) = screenBounds()
        var top = 0
        var bottom = 0
        var left = 0
        var right = 0
        view.rootWindowInsets?.let { ins ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val s = ins.getInsets(WindowInsets.Type.systemBars())
                top = s.top; bottom = s.bottom; left = s.left; right = s.right
            } else {
                @Suppress("DEPRECATION")
                run {
                    top = ins.systemWindowInsetTop
                    bottom = ins.systemWindowInsetBottom
                    left = ins.systemWindowInsetLeft
                    right = ins.systemWindowInsetRight
                }
            }
        }
        if (top <= 0) top = statusBarHeightFallback()
        val margin = dp(4)
        val vw = view.width
        val vh = view.height
        val minX = left + margin
        val minY = top + margin
        val maxX = (sw - right - vw - margin).coerceAtLeast(minX)
        val maxY = (sh - bottom - vh - margin).coerceAtLeast(minY)
        return intArrayOf(minX, minY, maxX, maxY)
    }

    private fun buildNotification(): Notification {
        val t = I18n.get(store.loadConfig().general.language)
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_transparent)
            .setContentTitle(t.tabClock)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .setContentIntent(pi)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Clock overlay",
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
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
        scope.cancel()
        for (i in 0..1) hidePanel(i)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
    }

    companion object {
        private const val CHANNEL_ID = "mini_timer_clock_osd"
        private const val NOTIF_ID = 43

        /** Arranca o refresca el servicio si algún panel está activo; si no, lo detiene. */
        fun sync(context: Context, config: ClockConfig) {
            ClockBus.config.value = config
            if (config.anyEnabled) {
                val intent = Intent(context, ClockOverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else {
                context.stopService(Intent(context, ClockOverlayService::class.java))
            }
        }
    }
}
