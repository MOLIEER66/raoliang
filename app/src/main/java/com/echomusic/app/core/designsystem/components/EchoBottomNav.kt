package com.echomusic.app.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import com.echomusic.app.core.designsystem.icon.EchoIcons
import com.echomusic.app.core.designsystem.theme.EchoMotion

/**
 * 底部导航（DESIGN-SYSTEM §5.1 / S4 声波律动导航）：总高 = 内容 56 + 手势区（系统 inset），
 * 水平 padding 10，4 项等分；背景 surface 88% + 顶部 1dp 发丝线，磨砂延伸至手势条之下。
 * 激活项图标位替换为声波指示器（3 根 3dp 柱，高 8/16/11，随播放律动、暂停即静止），
 * 标签 primary / 680 字重；未激活 onSurfaceVariant。
 */
@Composable
fun EchoBottomNav(
    items: List<NavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    val surface = MaterialTheme.colorScheme.surface
    Box(
        modifier
            .fillMaxWidth()
            .background(surface.copy(alpha = 0.88f)),
    ) {
        // 顶部 1dp 发丝线（§4.3 L2 浮层：深色仅发丝线 + 顶部 1px 高光）
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                items.forEachIndexed { index, item ->
                    NavItemView(
                        item = item,
                        selected = index == selected,
                        playing = playing,
                        onClick = { onSelect(index) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            // 手势区：背景延伸至导航栏 inset 之下（Edge-to-edge）
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(0.dp),
            )
        }
    }
}

data class NavItem(val label: String, val icon: ImageVector)

@Composable
private fun NavItemView(
    item: NavItem,
    selected: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val labelColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = EchoMotion.echoSpring(),
        label = "navLabel",
    )
    Column(
        modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                // S4：激活态 = 声波指示器（图标位整体替换）
                EchoWaveIndicator(playing = playing, color = MaterialTheme.colorScheme.primary)
            } else {
                Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(24.dp), tint = labelColor)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = item.label,
            style = TextStyle(fontSize = 11.sp, fontWeight = if (selected) FontWeight(680) else FontWeight(500), lineHeight = 14.sp, letterSpacing = 0.3.sp),
            color = labelColor,
        )
    }
}

/** 导航总高（内容 56 + 手势区 inset），迷你条/列表底部内边距的锚点 */
@Composable
fun rememberBottomNavHeight(): Dp {
    val density = LocalDensity.current
    val gestureInset = WindowInsets.navigationBars.getBottom(density)
    return 56.dp + with(density) { gestureInset.toDp() }
}
