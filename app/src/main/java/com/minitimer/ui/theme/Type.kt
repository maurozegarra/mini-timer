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

// Solo branding de Athlete: Neuropol para el título "ATHLETE" y Wallpoet para la "M" del ícono.
val Neuropol = FontFamily(Font(R.font.neuropol_nova_regular))
val Wallpoet = FontFamily(Font(R.font.wallpoet_regular))
