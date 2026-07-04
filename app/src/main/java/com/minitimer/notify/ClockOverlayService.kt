package com.minitimer.notify

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
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
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
import kotlin.math.roundToInt

/**
 * Servicio en primer plano que dibuja el/los OSD del reloj (hasta 2 paneles
 * INDEPENDIENTES) como franjas flotantes sobre otras apps mediante ventanas
 * [WindowManager] TYPE_APPLICATION_OVERLAY. Cada panel muestra hora (con
 * segundos en vivo), volumen multimedia [n] y batería [n%]; opcionalmente fecha
 * y memo. El contenido se refresca cada segundo. La posición es fija (anclada
 * bajo la barra de estado) y se ajusta con offsets por orientación desde Ajustes.
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
            launch { ClockBus.relayout.collect { relayoutAll() } }
            launch { ClockBus.clockAnchor.collect { relayoutAll() } }
            // Tick de 1s: hora con segundos, volumen y batería en vivo.
            launch {
                while (true) {
                    delay(1_000)
                    updateContent()
                }
            }
        }
    }

    /** Reposiciona todos los paneles visibles (tras cambiar offsets o rotar). */
    private fun relayoutAll() {
        for (i in 0..1) panels[i]?.applyPosition()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Al rotar cambia la orientación (y sus offsets/anclajes): recolocar.
        relayoutAll()
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
        // Carga, volumen y batería son "medidores": se agrupan con un separador
        // medio (·) en el orden [AC] · [vol] · [batt]. [AC] solo aparece si carga.
        val meters = mutableListOf<String>()
        if (panel.showCharging && charging != null) meters += charging
        if (panel.showVolume) meters += "[$vol]"
        if (panel.showBattery) meters += "[$batt%]"
        if (meters.isNotEmpty()) parts += meters.joinToString(METER_SEP)
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
    // Una ventana overlay por panel: franja fija con hora + indicadores.
    // ---------------------------------------------------------------------
    private inner class PanelWindow(private val index: Int) {
        private lateinit var container: LinearLayout
        private lateinit var mainView: TextView
        private lateinit var memoView: TextView
        private val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // NOT_TOUCHABLE: el panel es solo informativo (sin arrastre), así los
            // toques atraviesan la franja y llegan a la app de abajo (evita tapar
            // íconos accionables que queden bajo el overlay).
            // FLAG_LAYOUT_NO_LIMITS: permite colocar el panel sobre la barra de estado
            // (Y por encima del inset). Con esta bandera WindowManager NO re-ancla solo
            // una ventana WRAP_CONTENT, por eso el panel derecho recalcula su x en
            // bind() a partir del ancho medido, manteniendo fijo el borde derecho.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )

        fun attach(panel: OsdPanel): Boolean {
            val typeface = runCatching {
                ResourcesCompat.getFont(this@ClockOverlayService, R.font.jetbrains_mono_semibold)
            }.getOrNull()
            // Usamos maxLines = 1 (no setSingleLine): setSingleLine activa el
            // BoringLayout, que con esta fuente monoespaciada recorta el último
            // glifo (los segundos) al medir el ancho. maxLines mide con
            // StaticLayout y respeta el ancho real del texto.
            mainView = TextView(this@ClockOverlayService).apply {
                maxLines = 1
                setTypeface(typeface)
            }
            memoView = TextView(this@ClockOverlayService).apply {
                maxLines = 1
                setTypeface(typeface)
            }
            container = LinearLayout(this@ClockOverlayService).apply {
                orientation = LinearLayout.VERTICAL
                // Panel derecho: alinea sus líneas (medidores y memo) al borde
                // derecho para que queden pegadas al lado en que se ancla.
                gravity = if (index == 1) Gravity.END else Gravity.START
                addView(mainView)
                addView(memoView)
            }
            lp.gravity = Gravity.TOP or Gravity.START
            applyStyle(panel)
            return try {
                wm?.addView(container, lp)
                // Los overlays reciben los insets reales (barra de estado) de
                // forma ASÍNCRONA tras el layout: reposicionamos cuando lleguen
                // para anclar el panel bajo la barra (o donde el usuario lo haya
                // ajustado con los controles +/-).
                container.setOnApplyWindowInsetsListener { _, insets ->
                    applyPosition()
                    insets
                }
                container.requestApplyInsets()
                container.post { applyPosition() }
                true
            } catch (_: Exception) {
                false
            }
        }

        /**
         * Coloca el panel según su offset (dp) por orientación: anclado al borde
         * lateral (izquierda Panel 1 / derecha Panel 2), bajo la barra de estado,
         * más el ajuste fino del usuario. +X derecha, +Y abajo. Sin arrastre: la
         * posición se fija con los controles +/- de Ajustes.
         */
        fun applyPosition() {
            val portrait =
                resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
            val (offX, offY) = store.loadOsdOffset(index, portrait)
            val sa = safeArea(container)
            val margin = dp(4)
            val panel = ClockBus.config.value.panel(index)
            val anchor = ClockBus.clockAnchor.value
            lp.y = (sa.top + dp(BASE_Y_DP) + dp(offY)).coerceAtLeast(0)
            when {
                panel.alignToSystemClock && anchor != null -> {
                    // Alineado al reloj del sistema: PRECEDENCIA total sobre la X
                    // manual. El texto empieza donde el reloj (restando el padding
                    // lateral) e IGNORA offX; por eso el control X se deshabilita en
                    // Ajustes mientras la alineación está activa.
                    lp.gravity = Gravity.TOP or Gravity.START
                    lp.x = (anchor.left - dp(PAD_H)).coerceAtLeast(0)
                }
                index == 0 -> {
                    lp.gravity = Gravity.TOP or Gravity.START
                    lp.x = (sa.left + margin + dp(offX)).coerceAtLeast(0)
                }
                else -> {
                    // Panel derecho: gravedad START con x explícita = bordeDerecho -
                    // ancho medido. bind() vuelve a llamar aquí en cada actualización
                    // de contenido, así el BORDE DERECHO queda fijo aunque cambie el
                    // ancho (aparece/desaparece [AC], memo, etc.).
                    container.measure(
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    )
                    val w = container.measuredWidth
                    val screenW = resources.displayMetrics.widthPixels
                    val rightEdge = screenW - sa.right - margin + dp(offX)
                    lp.gravity = Gravity.TOP or Gravity.START
                    lp.x = (rightEdge - w).coerceAtLeast(0)
                }
            }
            try {
                wm?.updateViewLayout(container, lp)
            } catch (_: Exception) {
            }
        }

        fun detach() {
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
            val padH = dp(PAD_H)
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
            // Panel derecho: el ancho del contenido cambia (p. ej. [AC], memo); con
            // FLAG_LAYOUT_NO_LIMITS WindowManager no re-ancla solo, así que
            // recalculamos x aquí para mantener fijo el borde derecho.
            if (index == 1) applyPosition()
        }

    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun statusBarHeightFallback(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(24)
    }

    /** Área segura: alto de la barra de estado (arriba) e insets laterales,
     *  considerando el cutout (cámara). Sirve para anclar cada panel. */
    private data class SafeArea(val top: Int, val left: Int, val right: Int)

    private fun safeArea(view: View): SafeArea {
        var top = 0
        var left = 0
        var right = 0
        var cutoutTop = 0
        view.rootWindowInsets?.let { ins ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val s = ins.getInsets(WindowInsets.Type.systemBars())
                top = s.top; left = s.left; right = s.right
            } else {
                @Suppress("DEPRECATION")
                run {
                    top = ins.systemWindowInsetTop
                    left = ins.systemWindowInsetLeft
                    right = ins.systemWindowInsetRight
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                cutoutTop = ins.displayCutout?.safeInsetTop ?: 0
            }
        }
        // El top nunca menor que status_bar_height ni que el cutout (cámara),
        // que en algunos equipos hace la barra más alta que ese recurso.
        top = maxOf(top, cutoutTop, statusBarHeightFallback())
        return SafeArea(top, left, right)
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

        /** Padding horizontal (dp) del contenedor del panel; el texto empieza a
         *  esta distancia del borde. Se usa al alinear con el reloj del sistema. */
        private const val PAD_H = 10

        /** Desplazamiento vertical base (dp) para ambos paneles: sube la franja para
         *  que quede a la altura deseada. Este valor es el nuevo Y = 0 (offset 0). */
        private const val BASE_Y_DP = -12

        /** Separador entre medidores ([AC]·[vol]·[batt]). Middot pegado (sin
         *  espacios) porque en fuente monoespaciada cada espacio ocupa una celda
         *  completa y se veía demasiado ancho. Debe coincidir con el preview. */
        const val METER_SEP = "·"

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
