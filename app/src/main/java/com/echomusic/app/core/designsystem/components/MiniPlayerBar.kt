package com.echomusic.app.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echomusic.app.core.designsystem.icon.EchoIcons
import com.echomusic.app.core.designsystem.theme.EchoMotion
import com.echomusic.app.core.designsystem.theme.EchoRadius
import com.echomusic.app.core.model.Song

/**
 * 迷你播放条（DESIGN-SYSTEM §5.2）：浮动卡片，左右 14、高 62、圆角 18；
 * 封面 44(r10) → 标题 titleSingle + 副标题 labelMedium → 播放/暂停 40 圆形 primary → 下一首 40；
 * 顶部进度线 2dp（距左右 16）；无播放整条隐藏（调用方不渲染）。
 * 背景磨砂的 M1 折衷：surfaceContainer 92% + 发丝线（真实 backdrop blur 需 RenderEffect，
 * 见交付说明）。
 */
@Composable
fun MiniPlayerBar(
    song: Song?,
    progress: Float,
    playing: Boolean,
    buffering: Boolean,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(EchoRadius.lg)
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(62.dp)
            .clip(shape)
            .background(surfaceContainer.copy(alpha = 0.92f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onOpen),
    ) {
        // 顶部 2dp 进度线（距左右 16）
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(2.dp)
                .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(1.dp)),
        ) {
            val target = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)
            val animated by animateFloatAsState(target, animationSpec = EchoMotion.echoSpring(), label = "miniProgress")
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary,
                            ),
                        ),
                        RoundedCornerShape(1.dp),
                    ),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SongCover(song = song, modifier = Modifier.size(44.dp), contentDescription = song?.title)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = song?.title ?: "",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = song?.artist ?: "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PlayPauseCircle(
                playing = playing,
                buffering = buffering,
                size = 40.dp,
                iconSize = 22.dp,
                onToggle = onTogglePlay,
            )
            IconButtonCircle(size = 40.dp, onClick = onNext) {
                Icon(
                    EchoIcons.SkipNext,
                    contentDescription = "下一首",
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** 圆形播放/暂停键：40dp primary（迷你条）· 缓冲换 20dp 环形进度（§5.2） */
@Composable
fun PlayPauseCircle(
    playing: Boolean,
    buffering: Boolean,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(size)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (buffering) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Icon(
                if (playing) EchoIcons.Pause else EchoIcons.Play,
                contentDescription = if (playing) "暂停" else "播放",
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/** 通用 40 圆形图标钮（迷你条「下一首」等） */
@Composable
fun IconButtonCircle(
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .size(size)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
