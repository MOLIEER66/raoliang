package com.echomusic.app.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import com.echomusic.app.core.designsystem.theme.EchoMotion

/**
 * 库页标签页（SCREENS §1）：「全部歌曲 / 最近播放 / 最常播放」，gap 24，文本 14/600，
 * 激活 onSurface + 2.5dp primary 指示条（pb 11），底部 1dp outlineVariant 分隔；吸顶锚点。
 * 指示条随选中项平移（spring，§6.2 导航声波同族曲线）。
 */
@Composable
fun EchoTabs(
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 22.dp,
) {
    var selectedLeft by remember { mutableStateOf(Dp.Unspecified) }
    var selectedWidth by remember { mutableStateOf(Dp.Unspecified) }
    val density = LocalDensity.current
    val indicatorLeft by animateDpAsState(
        targetValue = if (selectedLeft.isUnspecified) 0.dp else selectedLeft,
        animationSpec = EchoMotion.echoSpring(),
        label = "tabIndicatorLeft",
    )
    val indicatorWidth by animateDpAsState(
        targetValue = if (selectedWidth.isUnspecified) 0.dp else selectedWidth,
        animationSpec = EchoMotion.echoSpring(),
        label = "tabIndicatorWidth",
    )

    Box(modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = horizontalPadding)) {
            tabs.forEachIndexed { index, label ->
                val color by animateColorAsState(
                    if (index == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = EchoMotion.echoSpring(),
                    label = "tabColor$index",
                )
                Text(
                    text = label,
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight(600), lineHeight = 20.sp),
                    color = color,
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 11.dp)
                        .padding(horizontal = 2.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(index) }
                        .onGloballyPositioned { coords ->
                            if (index == selected) {
                                selectedLeft = with(density) { coords.positionInParent().x.toDp() } + horizontalPadding
                                selectedWidth = with(density) { coords.size.width.toDp() }
                            }
                        },
                )
                if (index != tabs.lastIndex) Spacer(Modifier.width(24.dp))
            }
        }
        // 1dp 分隔线（full width）
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        // 2.5dp primary 指示条
        if (!indicatorWidth.isUnspecified) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = indicatorLeft)
                    .width(indicatorWidth)
                    .height(2.5.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}
