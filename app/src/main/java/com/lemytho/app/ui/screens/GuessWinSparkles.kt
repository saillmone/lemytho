package com.lemytho.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

private data class SparkleSpec(
    val angle: Float,
    val extraDp: Float,
    val radiusDp: Float,
    val delay: Float
)

/**
 * Burst unique de paillettes dorées autour du contenu (victoire de l'Inconnu).
 * Les points partent du contour du texte, pas du centre.
 */
@Composable
fun GuessWinSparkles(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2800, easing = LinearOutSlowInEasing)
        )
    }
    val sparkles = remember {
        List(18) { i ->
            val deg = i * (360f / 18f) + 8f
            SparkleSpec(
                angle = Math.toRadians(deg.toDouble()).toFloat(),
                extraDp = 56f + (i % 4) * 18f,
                radiusDp = 5.5f + (i % 3) * 2.2f,
                delay = (i % 5) * 0.05f
            )
        }
    }
    val gold = Color(0xFFFFD54F)
    val core = Color(0xFFFFF8E1)
    Box(
        modifier = modifier.padding(80.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
        Canvas(modifier = Modifier.matchParentSize()) {
            val t = progress.value
            val cx = size.width / 2f
            val cy = size.height / 2f
            val pad = 80.dp.toPx()
            val rx = ((size.width - pad * 2f) / 2f).coerceAtLeast(24.dp.toPx())
            val ry = ((size.height - pad * 2f) / 2f).coerceAtLeast(14.dp.toPx())
            sparkles.forEach { spec ->
                val local = ((t - spec.delay) / (1f - spec.delay)).coerceIn(0f, 1f)
                if (local <= 0f) return@forEach
                val travel = spec.extraDp.dp.toPx() * local
                val x = cx + cos(spec.angle) * (rx + travel)
                val y = cy + sin(spec.angle) * (ry + travel)
                val alpha = when {
                    local < 0.12f -> local / 0.12f
                    local < 0.5f -> 1f
                    else -> ((1f - local) / 0.5f).coerceAtLeast(0f)
                } * 0.95f
                val r = spec.radiusDp.dp.toPx() * (1.2f - local * 0.35f)
                drawCircle(
                    color = gold.copy(alpha = alpha),
                    radius = r,
                    center = Offset(x, y)
                )
                drawCircle(
                    color = core.copy(alpha = alpha * 0.85f),
                    radius = r * 0.4f,
                    center = Offset(x, y)
                )
            }
        }
    }
}
