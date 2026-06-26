package com.minitimer.model

/** Ajustes configurables, equivalentes a los de la versión web. */
data class Settings(
    val accent: Long = 0xFFFF5252,
    val language: String = "en",
    val presets: List<Int> = listOf(60, 180, 300, 600, 900, 1800),
    val autoDismiss: Int = 3,
    val ignoreSilent: Boolean = true,
)

/** Paleta de colores de acento disponibles. */
val ACCENT_COLORS: List<Long> = listOf(
    0xFF4AC0D6,
    0xFF4A90D6,
    0xFF3DDC84,
    0xFFA06CFF,
    0xFFFF9F43,
    0xFFFF5C8A,
    0xFFFF5252,
)

/** Opciones de auto-descarte en segundos (0 = desactivado). */
val AUTO_DISMISS_OPTIONS: List<Int> = listOf(0, 3, 5, 10, 30, 60)
