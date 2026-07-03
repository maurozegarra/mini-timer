package com.minitimer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Constantes de la paleta oscura (compatibilidad con pantallas aún no migradas a AppColors).
val BG = Color(0xFF000000)
val SURFACE = Color(0xFF1C1E1F)
val TRACK = Color(0xFF2A2D2F)
val TEXT_DIM = Color(0xFF9AA0A3)
val TEXT_FADED = Color(0xFF5A5D5F)
val DONE_RED = Color(0xFFFF5252)
val ON_ACCENT = Color(0xFF001316)

/** Colores semánticos del tema actual (incluyen acento efectivo y su contraste). */
data class AppColors(
    val bg: Color,
    val surface: Color,
    val track: Color,
    val textPrimary: Color,
    val textDim: Color,
    val textFaded: Color,
    val accent: Color,
    val onAccent: Color,
    val isDark: Boolean,
)

/** ARGB del acento rosa que activa el tema especial "Barbie". */
private const val PINK_ACCENT = 0xFFFF69B4

private fun darkBase(accent: Color, onAccent: Color) = AppColors(
    bg = BG,
    surface = SURFACE,
    track = TRACK,
    textPrimary = Color.White,
    textDim = TEXT_DIM,
    textFaded = TEXT_FADED,
    accent = accent,
    onAccent = onAccent,
    isDark = true,
)

private fun lightBase(accent: Color, onAccent: Color) = AppColors(
    bg = Color(0xFFF5F6F8),
    surface = Color(0xFFFFFFFF),
    track = Color(0xFFE9ECEF),
    textPrimary = Color(0xFF1A1C1E),
    textDim = Color(0xFF5A6065),
    textFaded = Color(0xFF9AA0A3),
    accent = accent,
    onAccent = onAccent,
    isDark = false,
)

/** Tema especial "Barbie" (Opción B) cuando el acento seleccionado es el rosa. */
private val BarbieColors = AppColors(
    bg = Color(0xFFFFE0F0),
    surface = Color(0xFFFFF5FA),
    track = Color(0xFFFFC2DE),
    textPrimary = Color(0xFF2B0016),
    textDim = Color(0xFF9A3D6D),
    textFaded = Color(0xFFCF7BA8),
    accent = Color(0xFFFF1FA5),
    onAccent = Color.White,
    isDark = false,
)

/**
 * Tono del acento en modo CLARO: se oscurece para contrastar sobre superficies
 * claras (el mismo valor va con onAccent blanco).
 */
private fun accentForLight(argb: Long): Color = when (argb) {
    0xFF4AC0D6 -> Color(0xFF0E8FA3) // cian
    0xFF4A90D6 -> Color(0xFF2B6CB0) // azul
    0xFF3DDC84 -> Color(0xFF1E9E5A) // verde
    0xFFA06CFF -> Color(0xFF6C3FE0) // morado
    0xFF9E9E9E -> Color(0xFF5F6368) // plomo
    0xFFFF5252 -> Color(0xFFE03B3B) // rojo
    else -> Color(argb)
}

/** Devuelve la paleta según el acento seleccionado y el modo claro/oscuro. */
private fun appColorsFor(accentArgb: Long, dark: Boolean): AppColors = when {
    accentArgb == PINK_ACCENT -> BarbieColors
    dark -> darkBase(Color(accentArgb), ON_ACCENT)
    else -> lightBase(accentForLight(accentArgb), Color.White)
}

private val DEFAULT_DARK = darkBase(Color(0xFFFF5252), ON_ACCENT)

val LocalAppColors = staticCompositionLocalOf { DEFAULT_DARK }

/** Acceso a los colores semánticos del tema actual. */
object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable
        get() = LocalAppColors.current
}

@Composable
fun MiniTimerTheme(
    accent: Long = 0xFFFF5252,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val appColors = appColorsFor(accent, darkTheme)
    val scheme = if (appColors.isDark) {
        darkColorScheme(
            background = appColors.bg,
            surface = appColors.surface,
            onBackground = appColors.textPrimary,
            onSurface = appColors.textPrimary,
        )
    } else {
        lightColorScheme(
            background = appColors.bg,
            surface = appColors.surface,
            onBackground = appColors.textPrimary,
            onSurface = appColors.textPrimary,
        )
    }
    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
