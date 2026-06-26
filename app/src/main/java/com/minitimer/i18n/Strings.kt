package com.minitimer.i18n

import java.util.Locale

/** Cadenas traducidas, equivalentes al objeto I18N de la versión web. */
data class Strings(
    val locale: Locale,
    val title: String,
    val start: String,
    val cancel: String,
    val pause: String,
    val resume: String,
    val dismiss: String,
    val restart: String,
    val timeUp: String,
    val paused: String,
    val endsAt: String,
    val settings: String,
    val language: String,
    val color: String,
    val presets: String,
    val add: String,
    val reset: String,
    val presetPlaceholder: String,
    val autoDismiss: String,
    val ignoreSilent: String,
    val ignoreSilentDesc: String,
    val on: String,
    val off: String,
)

object I18n {
    fun get(lang: String): Strings = if (lang == "es") ES else EN

    val ES = Strings(
        locale = Locale("es", "ES"),
        title = "Temporizador",
        start = "Iniciar",
        cancel = "Cancelar",
        pause = "Pausa",
        resume = "Reanudar",
        dismiss = "Descartar",
        restart = "Reiniciar",
        timeUp = "¡Tiempo!",
        paused = "En pausa",
        endsAt = "Termina a las",
        settings = "Configuración",
        language = "Idioma",
        color = "Color",
        presets = "Presets",
        add = "Agregar",
        reset = "Restablecer valores",
        presetPlaceholder = "mm:ss o hh:mm:ss",
        autoDismiss = "Auto descartar",
        ignoreSilent = "Ignorar modo silencio",
        ignoreSilentDesc = "Reproducir el sonido del temporizador en modo silencio o vibración",
        on = "Activado",
        off = "Desactivado",
    )

    val EN = Strings(
        locale = Locale.US,
        title = "Timer",
        start = "Start",
        cancel = "Cancel",
        pause = "Pause",
        resume = "Resume",
        dismiss = "Dismiss",
        restart = "Restart",
        timeUp = "Time's up!",
        paused = "Paused",
        endsAt = "Ends at",
        settings = "Settings",
        language = "Language",
        color = "Color",
        presets = "Presets",
        add = "Add",
        reset = "Reset to defaults",
        presetPlaceholder = "mm:ss or hh:mm:ss",
        autoDismiss = "Auto dismiss",
        ignoreSilent = "Ignore silence mode",
        ignoreSilentDesc = "Play timer sound in the silence or vibration mode",
        on = "On",
        off = "Off",
    )
}
