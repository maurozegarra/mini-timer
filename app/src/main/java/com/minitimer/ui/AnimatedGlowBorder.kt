package com.minitimer.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Paleta para un único destello que recorre el borde: arranca y termina en alpha 0
 * (sin costura) con un arco brillante en el medio.
 */
fun glowColors(c: Color): List<Color> = listOf(
    c.copy(alpha = 0f),
    c.copy(alpha = 0f),
    c.copy(alpha = 0f),
    c.copy(alpha = 0.55f),
    c.copy(alpha = 1f),
    c.copy(alpha = 0.55f),
    c.copy(alpha = 0f),
    c.copy(alpha = 0f),
    c.copy(alpha = 0f),
)

/**
 * Borde con gradiente animado: un destello recorre el contorno de un rectángulo
 * redondeado. Pensado como overlay dentro de un Box: dibuja solo el anillo del borde
 * (usa una capa offscreen + BlendMode.Clear para recortar el interior) sin tapar el
 * contenido. Replica el efecto del recuadro animado de referencia.
 */
@Composable
fun BoxScope.AnimatedGlowBorder(
    cornerRadius: Dp,
    colors: List<Color>,
    strokeWidth: Dp = 1.5.dp,
    durationMillis: Int = 3500,
) {
    val transition = rememberInfiniteTransition(label = "glowBorder")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing)),
        label = "glowAngle",
    )
    Box(
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawBehind {
                val r = cornerRadius.toPx()
                val sw = strokeWidth.toPx()
                val outer = Path().apply {
                    addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r, r)))
                }
                clipPath(outer) {
                    rotate(angle, pivot = center) {
                        val d = maxOf(size.width, size.height) * 2f
                        drawRect(
                            brush = Brush.sweepGradient(colors, center = center),
                            topLeft = Offset(center.x - d, center.y - d),
                            size = Size(d * 2f, d * 2f),
                        )
                    }
                }
                drawRoundRect(
                    color = Color.Black,
                    topLeft = Offset(sw, sw),
                    size = Size(
                        (size.width - 2f * sw).coerceAtLeast(0f),
                        (size.height - 2f * sw).coerceAtLeast(0f),
                    ),
                    cornerRadius = CornerRadius(
                        (r - sw).coerceAtLeast(0f),
                        (r - sw).coerceAtLeast(0f),
                    ),
                    blendMode = BlendMode.Clear,
                )
            },
    )
}
