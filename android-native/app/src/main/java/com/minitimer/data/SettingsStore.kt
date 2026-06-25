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
            floatingWindow = prefs.getBoolean(KEY_FLOATING, defaults.floatingWindow),
        )
    }

    fun save(s: Settings) {
        prefs.edit()
            .putLong(KEY_ACCENT, s.accent)
            .putString(KEY_LANGUAGE, s.language)
            .putString(KEY_PRESETS, s.presets.joinToString(","))
            .putInt(KEY_AUTO_DISMISS, s.autoDismiss)
            .putBoolean(KEY_FLOATING, s.floatingWindow)
            .apply()
    }

    private companion object {
        const val KEY_ACCENT = "accent"
        const val KEY_LANGUAGE = "language"
        const val KEY_PRESETS = "presets"
        const val KEY_AUTO_DISMISS = "autoDismiss"
        const val KEY_FLOATING = "floatingWindow"
    }
}
