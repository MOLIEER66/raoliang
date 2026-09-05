package com.echomusic.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.echomusic.app.core.designsystem.icon.EchoIcons
import com.echomusic.app.core.designsystem.theme.EchoRadius
import com.echomusic.app.core.designsystem.theme.EchoSpacing
import com.echomusic.app.core.designsystem.theme.tabularNums
import com.echomusic.app.core.model.Song

/**
 * 歌曲列表行（DESIGN-SYSTEM §5.3）：高 64，水平 20（行内 padding 12、按压 r-md 14 底），
 * 封面 44(r10) → 标题 titleSmall + 副文字 labelLarge「歌手 · 专辑」→ 时长 12sp tabular。
 * 正在播放态：标题转 primary + 12dp 声波指示器（S1）。
 * 音源徽章 M1 本地曲目默认不显示（§5.7 降噪规则），组件在 M2 接入在线曲后启用。
 */
@Composable
fun SongListRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = EchoSpacing.s20, vertical = 0.dp)
            .background(
                if (pressed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else androidx.compose.ui.graphics.Color.Transparent,
                RoundedCornerShape(EchoRadius.md),
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = EchoSpacing.s12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SongCover(
            song = song,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.width(EchoSpacing.s12))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${song.artist} · ${song.album}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCurrent) {
            // 正在播放：标题转 primary + 12dp 声波指示器（§5.3），时长照常在右
            AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
                EchoWaveIndicator(playing = isPlaying, heightScale = 0.66f)
            }
            Spacer(Modifier.width(EchoSpacing.s12))
        }
        Text(
            text = formatDuration(song.durationMs),
            style = MaterialTheme.typography.labelLarge.tabularNums(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(EchoSpacing.s12))
        Icon(
            imageVector = EchoIcons.More,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
    }
}

/** 时长格式：列表行口径 mm:ss（分钟补零，mockups「04:29」） */
fun formatDuration(durationMs: Long): String {
    val totalSec = durationMs / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
