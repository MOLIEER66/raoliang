package com.echomusic.app.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.echomusic.app.core.designsystem.theme.EchoMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 回声扩散转场（DESIGN-SYSTEM §1.4 S3 / §6.3b）：切歌时新配色以封面为圆心做 2–3 道波前
 * 扩散——圆环从封面边界 scale 0.9→1.6、透明度 .2→0（600ms emphasized，间隔 90ms）。
 * [waveKey] 变化（切歌）即重放。背景配色的 800ms crossfade 由 EchoTheme 承担；
 * 静态余波（-14/-36dp 双环，透明度 .16/.07）由刊头舞台的 [StaticEchoRings] 呈现。
 */
@Composable
fun EchoRippleOverlay(
    waveKey: Any?,
    coverCenter: Offset,
    coverRadiusPx: Float,
    color: Color,
    modifier: Modifier = Modifier,
    waveCount: Int = 3,
) {
    val waves = remember(waveCount) { List(waveCount) { Animatable(1f) } }
    LaunchedEffect(waveKey, coverCenter, coverRadiusPx) {
        if (coverRadiusPx <= 0f) return@LaunchedEffect
        waves.forEach { it.snapTo(0f) }
        val jobs = waves.mapIndexed { index, animatable ->
            launch {
                delay(index * 90L)
                animatable.animateTo(1f, animationSpec = tween(EchoMotion.EXPRESSIVE_MS, easing = EchoMotion.Emphasized))
            }
        }
        jobs.joinAll()
    }
    Canvas(modifier) {
        waves.forEach { animatable ->
            val t = animatable.value
            if (t > 0f && t < 1f) {
                val scale = 0.9f + 0.7f * t // 0.9 → 1.6
                drawCircle(
                    color = color.copy(alpha = 0.2f * (1f - t)), // .2 → 0
                    radius = coverRadiusPx * scale,
                    center = coverCenter,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
    }
}

private suspend fun List<kotlinx.coroutines.Job>.joinAll() {
    forEach { it.join() }
}
