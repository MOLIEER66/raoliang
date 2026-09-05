package com.echomusic.app.core.designsystem.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.echomusic.app.core.designsystem.theme.EchoMotion

/**
 * 声波指示器（DESIGN-SYSTEM §1.4 S1/S4 声纹家族的最小单元）：
 * 3 根 3dp 宽圆角柱（高 8/16/11），随播放律动、暂停即静止。
 *
 * 出现位置：底部导航激活项（§5.1，heightScale=1）与列表「正在播放」行（§5.3，
 * 整体 12dp 宽 → heightScale≈0.66）。律动 = 各柱 scaleY 在 0.45–1 间以相位差呼吸
 * （mockups 的 @keyframes eq）。
 */
@Composable
fun EchoWaveIndicator(
    playing: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    heightScale: Float = 1f,
) {
    val transition = rememberInfiniteTransition(label = "waveIndicator")
    // 三根柱以 1/3 周期相位差律动（600ms 一轮，emphasized 曲线）
    val phases = listOf(0f, 1f / 3f, 2f / 3f).map { phase ->
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(600, delayMillis = (phase * 600).toInt(), easing = EchoMotion.Standard)),
            label = "wavePhase$phase",
        )
    }
    val baseHeights = listOf(8f, 16f, 11f)
    val scales = phases.map { it.value }
    val paused = !playing

    Canvas(modifier.size(width = 12.dp * heightScale, height = 16.dp * heightScale)) {
        val barWidth = 3.dp.toPx() * heightScale
        val gap = (size.width - barWidth * 3) / 2f
        val maxH = 16.dp.toPx() * heightScale
        baseHeights.forEachIndexed { i, h ->
            // 播放中：0.45–1.0 呼吸；暂停：静止于满高（mockups 暂停态 = animation none）
            val scale = if (paused) 1f else 0.45f + 0.55f * (0.5f - 0.5f * kotlin.math.cos(scales[i] * 2f * Math.PI.toFloat()))
            val barH = (h / 16f) * maxH * scale.coerceIn(0.2f, 1f)
            val x = i * (barWidth + gap)
            drawRoundRect(
                color = color,
                topLeft = Offset(x, size.height - barH),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}
