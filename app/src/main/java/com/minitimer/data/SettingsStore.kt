package com.minitimer.data

import android.content.Context
import com.minitimer.Phase
import com.minitimer.model.Settings
import com.minitimer.model.TimerItem
import org.json.JSONArray
import org.json.JSONObject

/** Persistencia simple de los ajustes con SharedPreferences. */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("mini_timer", Context.MODE_PRIVATE)

    fun load(): Settings {
        val defaults = Settings()
        val presetsCsv = prefs.getString(KEY_PRESETS, null)
        val presets = presetsCsv
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.takeIf { it.isNotEmpty() }
            ?: defaults.presets
        return Settings(
            accent = prefs.getLong(KEY_ACCENT, defaults.accent),
            language = prefs.getString(KEY_LANGUAGE, defaults.language) ?: defaults.language,
            presets = presets,
            autoDismiss = prefs.getInt(KEY_AUTO_DISMISS, defaults.autoDismiss),
            ignoreSilent = prefs.getBoolean(KEY_IGNORE_SILENT, defaults.ignoreSilent),
            alarmSoundUri = prefs.getString(KEY_ALARM_URI, defaults.alarmSoundUri),
            alarmSoundName = prefs.getString(KEY_ALARM_NAME, defaults.alarmSoundName),
            headsetMode = prefs.getInt(KEY_HEADSET_MODE, defaults.headsetMode),
            vibrationEnabled = prefs.getBoolean(KEY_VIBRATION_ENABLED, defaults.vibrationEnabled),
            vibrationPattern = prefs.getInt(KEY_VIBRATION_PATTERN, defaults.vibrationPattern),
            alarmVolume = prefs.getFloat(KEY_ALARM_VOLUME, defaults.alarmVolume),
            showRing = prefs.getBoolean(KEY_SHOW_RING, defaults.showRing),
            showOverlay = prefs.getBoolean(KEY_SHOW_OVERLAY, defaults.showOverlay),
            showNowBar = prefs.getBoolean(KEY_SHOW_NOW_BAR, defaults.showNowBar),
            addIncrementSec = prefs.getInt(KEY_ADD_INC, defaults.addIncrementSec),
        )
    }

    fun save(s: Settings) {
        prefs.edit()
            .putLong(KEY_ACCENT, s.accent)
            .putString(KEY_LANGUAGE, s.language)
            .putString(KEY_PRESETS, s.presets.joinToString(","))
            .putInt(KEY_AUTO_DISMISS, s.autoDismiss)
            .putBoolean(KEY_IGNORE_SILENT, s.ignoreSilent)
            .putString(KEY_ALARM_URI, s.alarmSoundUri)
            .putString(KEY_ALARM_NAME, s.alarmSoundName)
            .putInt(KEY_HEADSET_MODE, s.headsetMode)
            .putBoolean(KEY_VIBRATION_ENABLED, s.vibrationEnabled)
            .putInt(KEY_VIBRATION_PATTERN, s.vibrationPattern)
            .putFloat(KEY_ALARM_VOLUME, s.alarmVolume)
            .putBoolean(KEY_SHOW_RING, s.showRing)
            .putBoolean(KEY_SHOW_OVERLAY, s.showOverlay)
            .putBoolean(KEY_SHOW_NOW_BAR, s.showNowBar)
            .putInt(KEY_ADD_INC, s.addIncrementSec)
            .apply()
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

    /** Carga la lista de timers persistida y el id del activo (null si ninguno). */
    fun loadTimers(): Pair<List<TimerItem>, Long?> {
        val raw = prefs.getString(KEY_TIMERS, null) ?: return emptyList<TimerItem>() to null
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

    /** Pestaña inferior seleccionada (0=Timer, 1=Athlete, 2=Water). */
    fun saveSelectedTab(index: Int) {
        prefs.edit().putInt(KEY_SELECTED_TAB, index).apply()
    }

    fun loadSelectedTab(): Int = prefs.getInt(KEY_SELECTED_TAB, 0)

    private companion object {
        // Desplazamiento vertical (dp) por defecto para centrar el anillo sobre
        // la cámara con las dimensiones actuales del anillo (38x32dp).
        const val RING_OFFSET_Y_DEFAULT = 3

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
