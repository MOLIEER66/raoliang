package com.echomusic.app.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echomusic.app.core.designsystem.theme.EchoRadius
import com.echomusic.app.core.designsystem.theme.tabularNums
import kotlin.math.abs

/**
 * 波形进度条（DESIGN-SYSTEM §5.5 沉浸变体 / S1 声纹家族）：48 根 4dp 圆角声纹柱（gap 3，
 * 高 3–18 随频谱包络），已播段 primary、未播段 outlineVariant；拖拽时浮现 thumb（12dp +
 * 32dp 12% 光环）与时间气泡；暂停时声纹整体降为 60% 透明度。包络由曲目 seed 决定
 * （同曲同波形，确定性）。
 *
 * 高度由调用方给（波形带 20dp 头部余量供时间气泡；拖拽 seek 经 [onSeek] 回调 fraction）。
 */
@Composable
fun WaveformProgress(
    progress: Float,
    playing: Boolean,
    durationMs: Long,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    seed: String = "",
    barCount: Int = 48,
) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val bodyAlpha = if (playing) 1f else 0.6f

    var widthPx by remember { mutableFloatStateOf(0f) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val shownFraction = dragFraction ?: progress.coerceIn(0f, 1f)
    val envelope = remember(seed, barCount) { waveformEnvelope(seed, barCount) }
    val density = LocalDensity.current
    val totalWidthDp = with(density) { widthPx.toDp() }

    Box(modifier.onSizeChanged { widthPx = it.width.toFloat() }) {
        Canvas(
            Modifier
                .matchParentSize()
                .alpha(bodyAlpha)
                .pointerInput(barCount) {
                    detectTapGestures { offset ->
                        if (widthPx > 0) onSeek((offset.x / widthPx).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(barCount) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            if (widthPx > 0) dragFraction = (offset.x / widthPx).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            dragFraction?.let(onSeek)
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null },
                    ) { change, _ ->
                        if (widthPx > 0) {
                            dragFraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                        }
                    }
                },
        ) {
            val gap = 3.dp.toPx()
            val barWidth = (size.width - gap * (barCount - 1)) / barCount
            val maxBarH = size.height
            val boundary = shownFraction * barCount
            // 声纹柱：已播 primary / 未播 track；边界柱做部分填充（mockups 的 --f 细节）
            for (i in 0 until barCount) {
                val h = maxBarH * envelope[i].coerceIn(0.17f, 1f) // 3–18dp 高度带 → /18 归一
                val x = i * (barWidth + gap)
                val y = (size.height - h) / 2f
                val fillInBar = (boundary - i).coerceIn(0f, 1f)
                if (fillInBar > 0f) {
                    drawRoundRect(
                        color = primary,
                        topLeft = Offset(x, y),
                        size = Size(barWidth * fillInBar, h),
                        cornerRadius = CornerRadius(barWidth / 2f),
                    )
                }
                if (fillInBar < 1f) {
                    drawRoundRect(
                        color = track,
                        topLeft = Offset(x + barWidth * fillInBar, y),
                        size = Size(barWidth * (1f - fillInBar), h),
                        cornerRadius = CornerRadius(barWidth / 2f),
                    )
                }
            }
        }
        // 拖拽态：thumb 12dp + 32dp 12% 光环 + 时间气泡（§5.5 拖拽态规格）
        if (dragFraction != null && widthPx > 0) {
            val xDp = with(density) { (shownFraction * widthPx).toDp() }
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = xDp - 16.dp)
                    .size(32.dp)
                    .alpha(0.12f)
                    .background(primary, RoundedCornerShape(EchoRadius.full)),
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = xDp - 6.dp)
                    .size(12.dp)
                    .background(primary, RoundedCornerShape(EchoRadius.full)),
            )
            val bubbleWidth = 64.dp
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = (xDp - bubbleWidth / 2).coerceIn(0.dp, totalWidthDp - bubbleWidth),
                        y = (-24).dp,
                    )
                    .width(bubbleWidth)
                    .background(onSurface.copy(alpha = 0.9f), RoundedCornerShape(EchoRadius.xs)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = formatPlayerTime((shownFraction * durationMs).toLong()),
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight(640), lineHeight = 14.sp).tabularNums(),
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.padding(vertical = 3.dp),
                )
            }
        }
    }
}

/** 播放页时间口径：m:ss（无前导零，mockups「1:32」）；剩余带负号 */
fun formatPlayerTime(ms: Long, remaining: Boolean = false): String {
    val totalSec = abs(ms) / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    val body = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    return if (remaining) "-$body" else body
}

/**
 * 波形包络（确定性伪频谱）：seed 哈希驱动的双正弦叠加，裁剪到 0.17–1.0
 * （对应 3–18dp 高度带）。同一首歌永远同一波形（S1「这首歌的声纹」语义）。
 */
internal fun waveformEnvelope(seed: String, bars: Int): List<Float> {
    if (bars <= 0) return emptyList()
    var h = 2166136261L
    for (c in seed) h = (h xor c.code.toLong()) * 16777619L
    val a = (abs(h) % 1000) / 1000.0
    val b = (abs(h / 7919) % 1000) / 1000.0
    val f1 = 2.0 + a * 3.0 // 主波频率
    val f2 = 5.0 + b * 6.0 // 细波频率
    return List(bars) { i ->
        val t = i.toDouble() / bars
        val wave = 0.55 + 0.30 * kotlin.math.sin(f1 * Math.PI * 2 * t + a * 6.28) +
            0.15 * kotlin.math.sin(f2 * Math.PI * 2 * t + b * 6.28)
        wave.toFloat().coerceIn(0.17f, 1f)
    }
}
