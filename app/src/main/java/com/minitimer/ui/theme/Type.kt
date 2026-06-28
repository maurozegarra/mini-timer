package com.minitimer.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.minitimer.R

/** JetBrains Mono: su 0 ranurado se distingue claramente de la O. */
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_light, FontWeight.Light),
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
)

// Kanit (geométrica fuerte) para "Athlete".
// PENDIENTE: colocar el TTF en app/src/main/res/font/kanit_bold_italic.ttf y cambiar a:
//   val Kanit = FontFamily(Font(R.font.kanit_bold_italic, FontWeight.Bold, FontStyle.Italic))
// Por ahora usa la fuente por defecto (en negrita itálica desde las llamadas).
val Kanit = FontFamily.Default
