package com.echomusic.app.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.echomusic.app.core.designsystem.theme.EchoSpacing

/**
 * 回声波纹插画（DESIGN-SYSTEM §5.8：64dp 线性插画，同心圆弧「回声波纹」，onSurface 24%）。
 * 自绘 Canvas，零 emoji 零位图；三道弧按距离渐隐，中心一枚实心圆点为「声源」。
 */
@Composable
fun EchoRippleArtwork(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 64.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f),
) {
    Canvas(modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val maxR = this.size.minDimension / 2f
        // 声源实心点
        drawCircle(color = tint, radius = maxR * 0.10f, center = center)
        // 三道回声弧：半径递增、透明度递减、开口交错（波纹扩散未合拢的瞬间）
        val radii = listOf(0.32f, 0.58f, 0.86f)
        val alphas = listOf(1f, 0.62f, 0.34f)
        val sweeps = listOf(260f, 210f, 160f)
        val starts = listOf(-90f, -45f, 0f)
        radii.forEachIndexed { i, r ->
            drawArc(
                color = tint.copy(alpha = tint.alpha * alphas[i]),
                startAngle = starts[i],
                sweepAngle = sweeps[i],
                useCenter = false,
                topLeft = Offset(center.x - maxR * r, center.y - maxR * r),
                size = androidx.compose.ui.geometry.Size(maxR * r * 2f, maxR * r * 2f),
                style = Stroke(width = this.size.minDimension * 0.035f, cap = StrokeCap.Round),
            )
        }
    }
}

/**
 * 空状态三件套（DESIGN-SYSTEM §5.8）：插画 + titleMedium 标题 + bodyMedium 说明 + 动作。
 * 文案禁「暂无数据」，必须给原因和动作。
 */
@Composable
fun EchoEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    artwork: @Composable () -> Unit = { EchoRippleArtwork() },
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(EchoSpacing.s8),
    ) {
        artwork()
        Spacer(Modifier.height(EchoSpacing.s8))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = EchoSpacing.s32),
        )
        action?.let {
            Spacer(Modifier.height(EchoSpacing.s8))
            it()
        }
    }
}
