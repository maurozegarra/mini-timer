package com.minitimer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BG = Color(0xFF000000)
val SURFACE = Color(0xFF1C1E1F)
val TRACK = Color(0xFF2A2D2F)
val TEXT_DIM = Color(0xFF9AA0A3)
val TEXT_FADED = Color(0xFF5A5D5F)
val DONE_RED = Color(0xFFFF5252)
val ON_ACCENT = Color(0xFF001316)

@Composable
fun MiniTimerTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        background = BG,
        surface = SURFACE,
        onBackground = Color.White,
        onSurface = Color.White,
    )
    MaterialTheme(colorScheme = colors, content = content)
}
