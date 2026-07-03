package com.minitimer.data

import android.content.Context
import com.minitimer.Phase
import com.minitimer.model.AlarmConfig
import com.minitimer.model.AppConfig
import com.minitimer.model.AthleteConfig
import com.minitimer.model.ClockConfig
import com.minitimer.model.GeneralConfig
import com.minitimer.model.OsdPanel
import com.minitimer.model.TimerConfig
import com.minitimer.model.TimerItem
import com.minitimer.model.WaterConfig
import org.json.JSONArray
import org.json.JSONObject

/** Persistencia simple de los ajustes con SharedPreferences. */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("mini_timer", Context.MODE_PRIVATE)

    /**
     * Carga la configuración completa. Orden: (1) esquema nuevo JSON por sección;
     * (2) migración one-time desde las claves planas antiguas (usuario existente);
     * (3) valores por defecto (instalación limpia).
     */
    fun loadConfig(): AppConfig {
        val generalJson = prefs.getString(KEY_CFG_GENERAL, null)
        if (generalJson != null) {
            return AppConfig(
                general = generalFromJson(generalJson),
                timer = timerFromJson(prefs.getString(KEY_CFG_TIMER, null)),
                athlete = athleteFromJson(prefs.getString(KEY_CFG_ATHLETE, null)),
                clock = clockFromJson(prefs.getString(KEY_CFG_CLOCK, null)),
                water = waterFromJson(prefs.getString(KEY_CFG_WATER, null)),
            )
        }
        if (prefs.contains(KEY_ACCENT) || prefs.contains(KEY_LANGUAGE)) {
            val migrated = migrateLegacy()
            saveConfig(migrated)
            return migrated
        }
        return AppConfig()
    }

    fun saveConfig(c: AppConfig) {
        prefs.edit()
            .putString(KEY_CFG_GENERAL, generalToJson(c.general).toString())
            .putString(KEY_CFG_TIMER, timerToJson(c.timer).toString())
            .putString(KEY_CFG_ATHLETE, athleteToJson(c.athlete).toString())
            .putString(KEY_CFG_CLOCK, clockToJson(c.clock).toString())
            .putString(KEY_CFG_WATER, waterToJson(c.water).toString())
            .also { clearLegacyKeys(it) }
            .apply()
    }

    // ---------- Migración desde el esquema plano antiguo ----------

    private fun migrateLegacy(): AppConfig {
        val d = AlarmConfig()
        val legacyAlarm = AlarmConfig(
            soundUri = prefs.getString(KEY_ALARM_URI, d.soundUri),
            soundName = prefs.getString(KEY_ALARM_NAME, d.soundName),
            volume = prefs.getFloat(KEY_ALARM_VOLUME, d.volume),
            vibrationEnabled = prefs.getBoolean(KEY_VIBRATION_ENABLED, d.vibrationEnabled),
            vibrationPattern = prefs.getInt(KEY_VIBRATION_PATTERN, d.vibrationPattern),
            ignoreSilent = prefs.getBoolean(KEY_IGNORE_SILENT, d.ignoreSilent),
            headsetMode = prefs.getInt(KEY_HEADSET_MODE, d.headsetMode),
        )
        val presets = prefs.getString(KEY_PRESETS, null)
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?: TimerConfig().presets
        val tg = TimerConfig()
        val gd = GeneralConfig()
        return AppConfig(
            general = GeneralConfig(
                accent = prefs.getLong(KEY_ACCENT, gd.accent),
                language = prefs.getString(KEY_LANGUAGE, gd.language) ?: gd.language,
                themeMode = prefs.getInt(KEY_THEME_MODE, gd.themeMode),
                developerMode = prefs.getBoolean(KEY_DEV_MODE, gd.developerMode),
            ),
            timer = TimerConfig(
                presets = presets,
                autoDismiss = prefs.getInt(KEY_AUTO_DISMISS, tg.autoDismiss),
                addIncrementSec = prefs.getInt(KEY_ADD_INC, tg.addIncrementSec),
                showRing = prefs.getBoolean(KEY_SHOW_RING, tg.showRing),
                showOverlay = prefs.getBoolean(KEY_SHOW_OVERLAY, tg.showOverlay),
                showNowBar = prefs.getBoolean(KEY_SHOW_NOW_BAR, tg.showNowBar),
                alarm = legacyAlarm,
            ),
            // Athlete compartía la misma alarma antes: se copia para conservarla.
            athlete = AthleteConfig(
                padPlayerClock = prefs.getBoolean(KEY_PAD_CLOCK, AthleteConfig().padPlayerClock),
                alarm = legacyAlarm,
            ),
            water = WaterConfig(),
        )
    }

    // ---------- (De)serialización JSON de la configuración ----------

    private fun alarmToJson(a: AlarmConfig) = JSONObject()
        .put("soundUri", a.soundUri ?: JSONObject.NULL)
        .put("soundName", a.soundName ?: JSONObject.NULL)
        .put("volume", a.volume.toDouble())
        .put("vibrationEnabled", a.vibrationEnabled)
        .put("vibrationPattern", a.vibrationPattern)
        .put("ignoreSilent", a.ignoreSilent)
        .put("headsetMode", a.headsetMode)

    private fun alarmFromJson(o: JSONObject?): AlarmConfig {
        val d = AlarmConfig()
        if (o == null) return d
        return AlarmConfig(
            soundUri = if (o.has("soundUri") && !o.isNull("soundUri")) o.getString("soundUri") else null,
            soundName = if (o.has("soundName") && !o.isNull("soundName")) o.getString("soundName") else null,
            volume = o.optDouble("volume", d.volume.toDouble()).toFloat(),
            vibrationEnabled = o.optBoolean("vibrationEnabled", d.vibrationEnabled),
            vibrationPattern = o.optInt("vibrationPattern", d.vibrationPattern),
            ignoreSilent = o.optBoolean("ignoreSilent", d.ignoreSilent),
            headsetMode = o.optInt("headsetMode", d.headsetMode),
        )
    }

    private fun generalToJson(g: GeneralConfig) = JSONObject()
        .put("accent", g.accent)
        .put("language", g.language)
        .put("themeMode", g.themeMode)
        .put("developerMode", g.developerMode)

    private fun generalFromJson(json: String?): GeneralConfig {
        val d = GeneralConfig()
        val o = json?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return d
        return GeneralConfig(
            accent = o.optLong("accent", d.accent),
            language = o.optString("language", d.language),
            themeMode = o.optInt("themeMode", d.themeMode),
            developerMode = o.optBoolean("developerMode", d.developerMode),
        )
    }

    private fun timerToJson(tc: TimerConfig) = JSONObject()
        .put("presets", JSONArray(tc.presets))
        .put("autoDismiss", tc.autoDismiss)
        .put("addIncrementSec", tc.addIncrementSec)
        .put("showRing", tc.showRing)
        .put("showOverlay", tc.showOverlay)
        .put("showNowBar", tc.showNowBar)
        .put("alarm", alarmToJson(tc.alarm))

    private fun timerFromJson(json: String?): TimerConfig {
        val d = TimerConfig()
        val o = json?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return d
        val presets = o.optJSONArray("presets")?.let { arr ->
            (0 until arr.length()).map { arr.getInt(it) }
        }?.takeIf { it.isNotEmpty() } ?: d.presets
        return TimerConfig(
            presets = presets,
            autoDismiss = o.optInt("autoDismiss", d.autoDismiss),
            addIncrementSec = o.optInt("addIncrementSec", d.addIncrementSec),
            showRing = o.optBoolean("showRing", d.showRing),
            showOverlay = o.optBoolean("showOverlay", d.showOverlay),
            showNowBar = o.optBoolean("showNowBar", d.showNowBar),
            alarm = alarmFromJson(o.optJSONObject("alarm")),
        )
    }

    private fun athleteToJson(ac: AthleteConfig) = JSONObject()
        .put("padPlayerClock", ac.padPlayerClock)
        .put("alarm", alarmToJson(ac.alarm))

    private fun athleteFromJson(json: String?): AthleteConfig {
        val d = AthleteConfig()
        val o = json?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return d
        return AthleteConfig(
            padPlayerClock = o.optBoolean("padPlayerClock", d.padPlayerClock),
            alarm = alarmFromJson(o.optJSONObject("alarm")),
        )
    }

    private fun waterToJson(wc: WaterConfig) = JSONObject()
        .put("alarm", alarmToJson(wc.alarm))

    private fun waterFromJson(json: String?): WaterConfig {
        val d = WaterConfig()
        val o = json?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return d
        return WaterConfig(alarm = alarmFromJson(o.optJSONObject("alarm")))
    }

    private fun panelToJson(p: OsdPanel) = JSONObject()
        .put("enabled", p.enabled)
        .put("showTime", p.showTime)
        .put("showSeconds", p.showSeconds)
        .put("showVolume", p.showVolume)
        .put("showBattery", p.showBattery)
        .put("showCharging", p.showCharging)
        .put("use24h", p.use24h)
        .put("showDate", p.showDate)
        .put("showMemo", p.showMemo)
        .put("memo", p.memo)
        .put("textSizeSp", p.textSizeSp)
        .put("textColor", p.textColor)
        .put("autoDarkColor", p.autoDarkColor)
        .put("bgAlpha", p.bgAlpha.toDouble())

    private fun panelFromJson(o: JSONObject?): OsdPanel {
        val d = OsdPanel()
        if (o == null) return d
        return OsdPanel(
            enabled = o.optBoolean("enabled", d.enabled),
            showTime = o.optBoolean("showTime", d.showTime),
            showSeconds = o.optBoolean("showSeconds", d.showSeconds),
            showVolume = o.optBoolean("showVolume", d.showVolume),
            showBattery = o.optBoolean("showBattery", d.showBattery),
            showCharging = o.optBoolean("showCharging", d.showCharging),
            use24h = o.optBoolean("use24h", d.use24h),
            showDate = o.optBoolean("showDate", d.showDate),
            showMemo = o.optBoolean("showMemo", d.showMemo),
            memo = o.optString("memo", d.memo),
            textSizeSp = o.optInt("textSizeSp", d.textSizeSp),
            textColor = o.optLong("textColor", d.textColor),
            autoDarkColor = o.optBoolean("autoDarkColor", d.autoDarkColor),
            bgAlpha = o.optDouble("bgAlpha", d.bgAlpha.toDouble()).toFloat(),
        )
    }

    private fun clockToJson(cc: ClockConfig) = JSONObject()
        .put("panel1", panelToJson(cc.panel1))
        .put("panel2", panelToJson(cc.panel2))

    private fun clockFromJson(json: String?): ClockConfig {
        val d = ClockConfig()
        val o = json?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return d
        return ClockConfig(
            panel1 = panelFromJson(o.optJSONObject("panel1")),
            panel2 = panelFromJson(o.optJSONObject("panel2")),
        )
    }

    private fun clearLegacyKeys(e: android.content.SharedPreferences.Editor) {
        e.remove(KEY_ACCENT).remove(KEY_LANGUAGE).remove(KEY_PRESETS)
            .remove(KEY_AUTO_DISMISS).remove(KEY_IGNORE_SILENT).remove(KEY_ALARM_URI)
            .remove(KEY_ALARM_NAME).remove(KEY_HEADSET_MODE).remove(KEY_VIBRATION_ENABLED)
            .remove(KEY_VIBRATION_PATTERN).remove(KEY_ALARM_VOLUME).remove(KEY_SHOW_RING)
            .remove(KEY_SHOW_OVERLAY).remove(KEY_SHOW_NOW_BAR).remove(KEY_ADD_INC)
            .remove(KEY_DEV_MODE).remove(KEY_PAD_CLOCK).remove(KEY_THEME_MODE)
    }

    // ---------- Lista de timers (sobrevive a la muerte del proceso) ----------

    /** Persiste la lista de timers y cuál es el activo (id) como JSON. */
    fun saveTimers(items: List<TimerItem>, activeId: Long?) {
        val arr = JSONArray()
        items.forEach { it ->
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("totalMs", it.totalMs)
                    .put("remainingMs", it.remainingMs)
                    .put("phase", it.phase.name)
                    .put("endAt", it.endAt)
                    .put("starred", it.starred)
                    .put("lastFinished", it.lastFinished),
            )
        }
        prefs.edit()
            .putString(KEY_TIMERS, arr.toString())
            .putLong(KEY_ACTIVE_ID, activeId ?: -1L)
            .apply()
    }

    /** Timers de ejemplo sembrados en la primera ejecución (instalación limpia). */
    private fun defaultTimers(): List<TimerItem> = listOf(
        TimerItem(id = 1L, name = "rest", totalMs = 60_000L, remainingMs = 60_000L, phase = Phase.IDLE),
        TimerItem(id = 2L, name = "potty", totalMs = 300_000L, remainingMs = 300_000L, phase = Phase.IDLE),
    )

    /** Carga la lista de timers persistida y el id del activo (null si ninguno). */
    fun loadTimers(): Pair<List<TimerItem>, Long?> {
        val raw = prefs.getString(KEY_TIMERS, null) ?: return defaultTimers() to null
        val items = mutableListOf<TimerItem>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val phase = runCatching { Phase.valueOf(o.getString("phase")) }
                    .getOrDefault(Phase.IDLE)
                items.add(
                    TimerItem(
                        id = o.getLong("id"),
                        name = o.optString("name", ""),
                        totalMs = o.getLong("totalMs"),
                        remainingMs = o.getLong("remainingMs"),
                        phase = phase,
                        endAt = o.optLong("endAt", 0L),
                        starred = o.optBoolean("starred", false),
                        lastFinished = o.optLong("lastFinished", 0L),
                    ),
                )
            }
        } catch (_: Exception) {
            return emptyList<TimerItem>() to null
        }
        val active = prefs.getLong(KEY_ACTIVE_ID, -1L).takeIf { it >= 0 }
        return items to active
    }

    /** Última duración (en segundos) que el usuario inició, para pre-rellenarla. */
    fun saveLastDuration(seconds: Int) {
        prefs.edit().putInt(KEY_LAST_DURATION, seconds).apply()
    }

    fun loadLastDuration(): Int = prefs.getInt(KEY_LAST_DURATION, 0)

    /** Último nombre usado, para pre-rellenarlo en el siguiente timer. */
    fun saveLastLabel(name: String) {
        prefs.edit().putString(KEY_LAST_LABEL, name).apply()
    }

    fun loadLastLabel(): String = prefs.getString(KEY_LAST_LABEL, "") ?: ""

    /** Si ya se pidió (una vez) el permiso de overlay "Mostrar sobre otras apps". */
    fun overlayAsked(): Boolean = prefs.getBoolean(KEY_OVERLAY_ASKED, false)

    fun setOverlayAsked() {
        prefs.edit().putBoolean(KEY_OVERLAY_ASKED, true).apply()
    }

    /**
     * Offset fino (en dp) del anillo/píldora respecto a su posición por defecto,
     * para centrarlo sobre la cámara con los controles +/- de ajustes. Se guarda
     * fuera de [Settings] para evitar sobreescrituras al copiar el modelo.
     */
    fun saveRingOffset(x: Int, y: Int) {
        prefs.edit().putInt(KEY_RING_OFF_X, x).putInt(KEY_RING_OFF_Y, y).apply()
    }

    fun loadRingOffset(): Pair<Int, Int> =
        prefs.getInt(KEY_RING_OFF_X, 0) to prefs.getInt(KEY_RING_OFF_Y, RING_OFFSET_Y_DEFAULT)

    /** Pestaña inferior seleccionada (0=Timer, 1=Athlete, 2=Reloj, 3=Water). */
    fun saveSelectedTab(index: Int) {
        prefs.edit().putInt(KEY_SELECTED_TAB, index).apply()
    }

    fun loadSelectedTab(): Int = prefs.getInt(KEY_SELECTED_TAB, 0)

    /**
     * Posición (x,y en px) de cada panel OSD del reloj. Se guarda fuera de
     * [AppConfig] (como el offset del anillo) para que el arrastre desde el
     * servicio no sea sobrescrito por la copia en memoria de la config.
     */
    fun saveOsdPos(panel: Int, x: Int, y: Int) {
        prefs.edit()
            .putInt("$KEY_OSD_POS_X$panel", x)
            .putInt("$KEY_OSD_POS_Y$panel", y)
            .apply()
    }

    fun loadOsdPos(panel: Int): Pair<Int, Int> {
        val defX = if (panel == 0) OSD_POS_X0_DEFAULT else OSD_POS_X1_DEFAULT
        return prefs.getInt("$KEY_OSD_POS_X$panel", defX) to
            prefs.getInt("$KEY_OSD_POS_Y$panel", OSD_POS_Y_DEFAULT)
    }

    /** ¿El usuario ya fijó (arrastró/restauró) la posición de este panel? */
    fun hasOsdPos(panel: Int): Boolean = prefs.contains("$KEY_OSD_POS_X$panel")

    private companion object {
        // Desplazamiento vertical (dp) por defecto para centrar el anillo sobre
        // la cámara con las dimensiones actuales del anillo (38x32dp).
        const val RING_OFFSET_Y_DEFAULT = 3

        // Posición por defecto (px) de los paneles OSD: en la misma horizontal,
        // Panel 1 a la izquierda y Panel 2 a la derecha (x grande -> el servicio
        // lo ajusta al borde derecho al pintarlo).
        const val OSD_POS_X0_DEFAULT = 24
        const val OSD_POS_X1_DEFAULT = 100_000
        const val OSD_POS_Y_DEFAULT = 90
        const val KEY_OSD_POS_X = "osd_pos_x_"
        const val KEY_OSD_POS_Y = "osd_pos_y_"

        // Esquema nuevo: un JSON por sección.
        const val KEY_CFG_GENERAL = "cfg_general"
        const val KEY_CFG_TIMER = "cfg_timer"
        const val KEY_CFG_ATHLETE = "cfg_athlete"
        const val KEY_CFG_CLOCK = "cfg_clock"
        const val KEY_CFG_WATER = "cfg_water"

        // Claves planas antiguas (solo se leen en la migración one-time).
        const val KEY_ACCENT = "accent"
        const val KEY_LANGUAGE = "language"
        const val KEY_PRESETS = "presets"
        const val KEY_AUTO_DISMISS = "autoDismiss"
        const val KEY_IGNORE_SILENT = "ignoreSilent"
        const val KEY_ALARM_URI = "alarmSoundUri"
        const val KEY_ALARM_NAME = "alarmSoundName"
        const val KEY_HEADSET_MODE = "headsetMode"
        const val KEY_VIBRATION_ENABLED = "vibrationEnabled"
        const val KEY_VIBRATION_PATTERN = "vibrationPattern"
        const val KEY_ALARM_VOLUME = "alarmVolume"
        const val KEY_SHOW_RING = "showRing"
        const val KEY_SHOW_OVERLAY = "showOverlay"
        const val KEY_SHOW_NOW_BAR = "showNowBar"
        const val KEY_ADD_INC = "addIncrement"
        const val KEY_DEV_MODE = "developerMode"
        const val KEY_PAD_CLOCK = "padPlayerClock"
        const val KEY_THEME_MODE = "themeMode"
        const val KEY_TIMERS = "timers_json"
        const val KEY_ACTIVE_ID = "active_id"
        const val KEY_LAST_DURATION = "last_duration"
        const val KEY_LAST_LABEL = "last_label"
        const val KEY_OVERLAY_ASKED = "overlay_asked"
        const val KEY_RING_OFF_X = "ring_off_x"
        const val KEY_RING_OFF_Y = "ring_off_y"
        const val KEY_SELECTED_TAB = "selected_tab"
    }
}
