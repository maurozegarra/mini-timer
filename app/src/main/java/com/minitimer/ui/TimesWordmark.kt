package com.minitimer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Wordmark "TIMES" derivado de la fuente Wallpoet:
// - T y M sin cambios; E = M rotada 90 ccw; S con el corte vertical puenteado.
// - I como barra inclinada "\" a altura de mayuscula.
// Coordenadas en el viewport VW x VH (y-down). Generado con tools/wordmark_build.py.
private const val VW = 3432.86f
private const val VH = 575f

private const val REST_D =
    "M 689 113 L 401 113 L 401 575 L 263 575 L 263 113 L 0 113 L 0 0 L 689 0 Z " +
    "M 799 0 L 925 0 L 1105 575 L 979 575 Z " +
    "M 2114 575 L 2114 479.86 L 2275.9 307.95 L 2401.92 307.95 L 2249.2 469.85 L 2593.86 469.85 L 2593.86 575 L 2114 575 Z " +
    "M 2114 0 L 2114 94.3 L 2275.9 267.05 L 2401.92 267.05 L 2249.2 105.15 L 2593.86 105.15 L 2593.86 0 L 2114 0 Z " +
    "M 2743.86 69 L 2857.86 0 L 3063.86 0 L 3063.86 118 L 2865.86 118 L 2865.86 231 L 3063.86 231 L 3063.86 348 L 2857.86 348 L 2743.86 280 Z " +
    "M 3432.86 300 L 3314.86 231 L 3108.86 231 L 3108.86 348 L 3306.86 348 L 3306.86 462 L 3112.86 462 L 3112.86 575 L 3314.86 575 L 3432.86 507 Z " +
    "M 2869.86 393 L 2869.86 462 L 3063.86 462 L 3063.86 575 L 2860.86 575 L 2743.86 507 L 2743.86 393 Z " +
    "M 3432.86 186 L 3432.86 69 L 3314.86 0 L 3108.86 0 L 3108.86 118 L 3306.86 118 L 3306.86 186 Z " +
    "M 3063.86 0 L 3108.86 0 L 3108.86 118 L 3063.86 118 Z " +
    "M 3063.86 231 L 3108.86 231 L 3108.86 348 L 3063.86 348 Z " +
    "M 3063.86 462 L 3112.86 462 L 3112.86 575 L 3063.86 575 Z"

private const val M_D =
    "M 1245 0 L 1359 0 L 1565 194 L 1565 345 L 1371 162 L 1371 575 L 1245 575 L 1245 0 Z " +
    "M 1934 0 L 1821 0 L 1614 194 L 1614 345 L 1808 162 L 1808 575 L 1934 575 L 1934 0 Z"

/**
 * Wordmark "TIMES". La M resalta con [accent]; el resto usa [restColor].
 * Se dimensiona por [height]; el ancho se calcula manteniendo la proporcion.
 */
@Composable
fun TimesWordmark(
    accent: Color,
    modifier: Modifier = Modifier,
    height: Dp = 20.dp,
    restColor: Color = Color.White,
) {
    val rest = remember { PathParser().parsePathString(REST_D).toPath() }
    val m = remember { PathParser().parsePathString(M_D).toPath() }
    Canvas(
        modifier
            .height(height)
            .aspectRatio(VW / VH),
    ) {
        val s = size.height / VH
        scale(s, s, pivot = Offset.Zero) {
            drawPath(rest, restColor)
            drawPath(m, accent)
        }
    }
}
