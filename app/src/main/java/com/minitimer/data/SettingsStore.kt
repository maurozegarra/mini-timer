package com.minitimer.data

import android.content.Context
import com.minitimer.model.Settings

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
            .apply()
    }

    // ---------- Estado del timer activo (sobrevive a la muerte del proceso) ----------

    /** Estado persistido de un timer en curso/pausado/terminado. */
    data class TimerState(
        val phase: String,
        val endAt: Long,
        val remainingMs: Long,
        val totalMs: Long,
    )

    fun saveTimerState(state: TimerState) {
        prefs.edit()
            .putString(KEY_T_PHASE, state.phase)
            .putLong(KEY_T_END_AT, state.endAt)
            .putLong(KEY_T_REMAINING, state.remainingMs)
            .putLong(KEY_T_TOTAL, state.totalMs)
            .apply()
    }

    fun loadTimerState(): TimerState? {
        val phase = prefs.getString(KEY_T_PHASE, null) ?: return null
        return TimerState(
            phase = phase,
            endAt = prefs.getLong(KEY_T_END_AT, 0L),
            remainingMs = prefs.getLong(KEY_T_REMAINING, 0L),
            totalMs = prefs.getLong(KEY_T_TOTAL, 0L),
        )
    }

    fun clearTimerState() {
        prefs.edit()
            .remove(KEY_T_PHASE)
            .remove(KEY_T_END_AT)
            .remove(KEY_T_REMAINING)
            .remove(KEY_T_TOTAL)
            .apply()
    }

    /** Última duración (en segundos) que el usuario inició, para pre-rellenarla. */
    fun saveLastDuration(seconds: Int) {
        prefs.edit().putInt(KEY_LAST_DURATION, seconds).apply()
    }

    fun loadLastDuration(): Int = prefs.getInt(KEY_LAST_DURATION, 0)

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
        prefs.getInt(KEY_RING_OFF_X, 0) to prefs.getInt(KEY_RING_OFF_Y, 0)

    private companion object {
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
        const val KEY_T_PHASE = "timer_phase"
        const val KEY_T_END_AT = "timer_end_at"
        const val KEY_T_REMAINING = "timer_remaining"
        const val KEY_T_TOTAL = "timer_total"
        const val KEY_LAST_DURATION = "last_duration"
        const val KEY_OVERLAY_ASKED = "overlay_asked"
        const val KEY_RING_OFF_X = "ring_off_x"
        const val KEY_RING_OFF_Y = "ring_off_y"
    }
}
