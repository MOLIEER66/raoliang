package com.echomusic.app.core.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.echomusic.app.core.designsystem.theme.EchoRadius
import com.echomusic.app.core.designsystem.theme.EchoSpacing

/**
 * 扫描态骨架（DESIGN-SYSTEM §5.8 加载：列表用 3–5 行骨架（64dp 灰块 + 1.2s 微光扫过），
 * 禁止全屏 spinner）。结构镜像 §5.3 歌曲行：44(r10) 封面块 + 双行文字条。
 */
@Composable
fun SongRowSkeleton(rows: Int = 5, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeletonShimmer")
    val shimmer by transition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "skeletonShimmerProgress",
    )
    val blockColor = MaterialTheme.colorScheme.surfaceContainer
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)

    @Composable
    fun shimmerBlock(baseWidth: Dp? = null, widthFraction: Float = 1f, height: Dp, shape: RoundedCornerShape) {
        // 微光 = 一道随时间平移的高光窗（colorStops 窗口在 0–1 间滑过）
        val windowStart = (shimmer - 0.18f).coerceIn(0f, 1f)
        val windowEnd = (shimmer + 0.18f).coerceIn(0f, 1f)
        val brush = if (windowEnd <= windowStart) {
            Brush.horizontalGradient(0f to blockColor, 1f to blockColor)
        } else {
            Brush.horizontalGradient(
                0f to blockColor,
                windowStart to blockColor,
                (windowStart + windowEnd) / 2f to blockColor.copy(alpha = 0f).composite(blockColor, highlight),
                windowEnd to blockColor,
                1f to blockColor,
            )
        }
        var m = if (baseWidth != null) Modifier.width(baseWidth) else Modifier.fillMaxWidth(widthFraction)
        m = m.height(height).background(brush, shape)
        Box(m)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        repeat(rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = EchoSpacing.s20, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                shimmerBlock(baseWidth = 44.dp, height = 44.dp, shape = RoundedCornerShape(EchoRadius.sm))
                Spacer(Modifier.width(EchoSpacing.s12))
                Column {
                    shimmerBlock(widthFraction = 0.52f, height = 14.dp, shape = RoundedCornerShape(EchoRadius.xs))
                    Spacer(Modifier.height(8.dp))
                    shimmerBlock(widthFraction = 0.34f, height = 12.dp, shape = RoundedCornerShape(EchoRadius.xs))
                }
            }
        }
    }
}

/** 两个色块之间的微光中间色（高光叠在块色上，视觉=变亮一档） */
private fun Color.composite(base: Color, overlay: Color): Color =
    Color(
        red = base.red + (overlay.red - base.red) * 0.9f,
        green = base.green + (overlay.green - base.green) * 0.9f,
        blue = base.blue + (overlay.blue - base.blue) * 0.9f,
        alpha = 1f,
    )

/**
 * 2dp 线性进度（SCREENS §1 扫描态：顶部 2dp 线性进度 primary）。
 * [fraction] 为 null 时走不确定态（MediaStore 查询阶段拿不到总数）。
 */
@Composable
fun EchoLinearProgress(
    modifier: Modifier = Modifier,
    fraction: Float? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.outlineVariant,
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(trackColor),
    ) {
        if (fraction == null) {
            val transition = rememberInfiniteTransition(label = "indeterminate")
            val progress by transition.animateFloat(
                initialValue = -0.32f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
                label = "indeterminateProgress",
            )
            Box(
                Modifier
                    .offset(x = maxWidth * progress)
                    .width(maxWidth * 0.32f)
                    .height(2.dp)
                    .background(color),
            )
        } else {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(2.dp)
                    .background(color),
            )
        }
    }
}
