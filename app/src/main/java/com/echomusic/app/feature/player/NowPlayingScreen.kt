package com.echomusic.app.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echomusic.app.R
import com.echomusic.app.core.designsystem.components.PlayPauseCircle
import com.echomusic.app.core.designsystem.components.SongCover
import com.echomusic.app.core.designsystem.components.SourceBadge
import com.echomusic.app.core.designsystem.components.WaveformProgress
import com.echomusic.app.core.designsystem.components.formatPlayerTime
import com.echomusic.app.core.designsystem.icon.EchoIcons
import com.echomusic.app.core.designsystem.palette.EchoPalette
import com.echomusic.app.core.designsystem.palette.LocalEchoPalette
import com.echomusic.app.core.designsystem.theme.EchoColors
import com.echomusic.app.core.designsystem.theme.tabularNums
import com.echomusic.app.core.model.PlayMode
import com.echomusic.app.core.model.Song
import com.echomusic.app.core.playback.PlaybackMediaId
import com.echomusic.app.core.playback.PlaybackStatus
import com.echomusic.app.core.playback.PlayModePolicy
import org.koin.androidx.compose.koinViewModel

/**
 * 正在播放（SCREENS §2 · 沉浸 · S2 刊头式构图）：
 * 歌名竖排刊头（≤6 字，超限自动转横排）+ 264dp 右置封面 + 信息区主体的非对称双栏，
 * 与大封面居中构图肉眼可辨地拉开。M1 歌词未接：信息区以「专辑 / 年份 / 格式」替代，
 * TODO(M2-lyrics) 锚点已留。背景 = Echo Palette 沉浸渐变（radial 光晕×2 + linear 基底，§2.4）。
 */
@Composable
fun NowPlayingScreen(
    onCollapse: () -> Unit,
    playerViewModel: PlayerViewModel = koinViewModel(),
) {
    val uiState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val positionMs by playerViewModel.positionMs.collectAsStateWithLifecycle()
    val queue by playerViewModel.queue.collectAsStateWithLifecycle()
    val palette = LocalEchoPalette.current
    val song = uiState.song

    var showQueueSheet by remember { mutableStateOf(false) }
    // 封面在根坐标系中的圆心与半径（S3 回声扩散的圆心）
    var coverCenter by remember { mutableStateOf(Offset.Zero) }
    var coverRadiusPx by remember { mutableFloatStateOf(0f) }

    Box(
        Modifier
            .fillMaxSize()
            .immersiveBackground(palette),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            // ---- 顶栏：inset + 14，高 24，水平 26（§2）----
            NowPlayingTopBar(
                onCollapse = onCollapse,
                onQueue = { showQueueSheet = true },
                modifier = Modifier.padding(top = 14.dp, bottom = 10.dp),
            )

            // ---- 刊头舞台：margin 24/26/0，高 264（S2）----
            MastheadStage(
                song = song,
                palette = palette,
                onCoverBounds = { bounds ->
                    coverCenter = bounds.center
                    coverRadiusPx = bounds.width / 2f
                },
            )

            // ---- 信息区（M1：专辑/年份/格式替代歌词三行；TODO(M2-lyrics)）----
            InfoBlock(song = song)

            // ---- 操作行：心形 24（M1 收藏只留位，落 M3；双击封面同入口）----
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
                    .padding(horizontal = 26.dp)
                    .height(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    EchoIcons.HeartOutline,
                    contentDescription = stringResource(R.string.np_favorite_placeholder),
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---- 波形进度（S1）：margin 22/26/0 ----
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp)
                    .padding(horizontal = 26.dp),
            ) {
                WaveformProgress(
                    progress = if (uiState.durationMs > 0) positionMs.toFloat() / uiState.durationMs else 0f,
                    playing = uiState.status == PlaybackStatus.PLAYING,
                    durationMs = uiState.durationMs,
                    onSeek = { fraction -> playerViewModel.seekTo((fraction * uiState.durationMs).toLong()) },
                    seed = song?.songKey ?: "",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp),
                )
                Spacer(Modifier.height(9.dp))
                TimeRow(positionMs = positionMs, durationMs = uiState.durationMs)
            }

            // ---- 控制行：shuffle 23 / prev 34 / 播放 74 / next 34 / repeat 23 ----
            ControlRow(
                playMode = uiState.playMode,
                playing = uiState.status == PlaybackStatus.PLAYING,
                buffering = uiState.status == PlaybackStatus.BUFFERING,
                onShuffle = {
                    playerViewModel.setPlayMode(
                        if (uiState.playMode == PlayMode.SHUFFLE) PlayMode.REPEAT_ALL else PlayMode.SHUFFLE,
                    )
                },
                onPrev = playerViewModel::previous,
                onTogglePlay = playerViewModel::togglePlayPause,
                onNext = playerViewModel::next,
                onRepeat = { playerViewModel.setPlayMode(PlayModePolicy.next(uiState.playMode)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .padding(horizontal = 26.dp),
            )

            // ---- 来源行：徽章 + 11.5 说明 ----
            SourceRow(
                song = song,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .padding(horizontal = 26.dp),
            )
        }

        // ---- S3 回声扩散：切歌时从封面圆心荡开（背景 crossfade 由 EchoTheme 承担）----
        EchoRippleOverlay(
            waveKey = song?.id,
            coverCenter = coverCenter,
            coverRadiusPx = coverRadiusPx,
            color = palette.seed,
            modifier = Modifier.fillMaxSize(),
        )
    }

    if (showQueueSheet) {
        QueueSheet(
            queue = queue,
            currentSongId = song?.let(PlaybackMediaId::of),
            onDismiss = { showQueueSheet = false },
        )
    }
}

// ---- 顶栏 ----

@Composable
private fun NowPlayingTopBar(onCollapse: () -> Unit, onQueue: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 26.dp)
            .height(24.dp),
    ) {
        Icon(
            EchoIcons.ChevronDown,
            contentDescription = stringResource(R.string.np_collapse),
            modifier = Modifier
                .size(24.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCollapse),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.np_title),
            style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight(650), letterSpacing = 3.sp, lineHeight = 14.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.Center),
        )
        Icon(
            EchoIcons.Queue,
            contentDescription = stringResource(R.string.np_queue),
            modifier = Modifier
                .size(24.dp)
                .align(Alignment.CenterEnd)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onQueue),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ---- 刊头舞台（S2）----

@Composable
private fun MastheadStage(
    song: Song?,
    palette: EchoPalette,
    onCoverBounds: (Rect) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
            .padding(horizontal = 26.dp)
            .height(264.dp),
    ) {
        // 静态余波双环（-14/-36dp，1.5dp 主色描边，透明度 .16/.07——S3 的静态暗示）
        StaticEchoRings(color = palette.seed, modifier = Modifier.align(Alignment.Center))
        // 封面 264×264 r26 右置（shadow-3 色相取自光晕）
        SongCover(
            song = song,
            shape = RoundedCornerShape(26.dp),
            contentDescription = song?.let { "${it.title} · ${it.artist}" },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(264.dp)
                .shadow(
                    elevation = 32.dp,
                    shape = RoundedCornerShape(26.dp),
                    ambientColor = palette.coverShadow,
                    spotColor = palette.coverShadow,
                )
                .onGloballyPositioned { onCoverBounds(it.boundsInRoot()) },
        )
        // glossy 高光（155deg 白 20% → 42% 处透明，mockups .stagecv::after）
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .size(264.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.linearGradient(
                        0f to Color.White.copy(alpha = 0.20f),
                        0.42f to Color.Transparent,
                    ),
                ),
        )
        // 左侧刊头（与封面同高）：竖排歌名 → 2×32dp 主色竖线（12dp 泛光）→ 竖排副题
        val title = song?.title.orEmpty()
        val subtitle = song?.let { "${it.artist} · ${it.album}" }.orEmpty()
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .padding(start = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (title.length in 1..6) {
                // 竖排刊头：33sp/780/字距 7（行高 40 = 33 + 7）
                Text(
                    text = title.toCharArray().joinToString("\n"),
                    style = TextStyle(fontSize = 33.sp, fontWeight = FontWeight(780), lineHeight = 40.sp, textAlign = TextAlign.Center),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 6,
                )
            } else {
                // 长歌名（>6 字）：转横排 27/720 单行省略（SCREENS §2 长歌名态）
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(96.dp),
                )
            }
            Spacer(Modifier.height(13.dp))
            // 2×32dp 主色竖线 + 12dp 泛光（blur 在 S- 上优雅降级为无泛光）
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(32.dp)
                        .blur(12.dp)
                        .background(palette.seed),
                )
                Box(
                    Modifier
                        .width(2.dp)
                        .height(32.dp)
                        .background(palette.seed, RoundedCornerShape(1.dp)),
                )
            }
            if (subtitle.length <= 12) {
                Spacer(Modifier.height(13.dp))
                // 竖排副题 10.5/560/字距 1.5
                Text(
                    text = subtitle.toCharArray().joinToString("\n"),
                    style = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight(560), lineHeight = 12.sp, letterSpacing = 1.5.sp, textAlign = TextAlign.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 12,
                )
            }
        }
    }
}

/** 封面外双环余波（S3 静态暗示）：-14/-36dp，1.5dp 主色描边，透明度 .16/.07，同心于封面 */
@Composable
private fun StaticEchoRings(color: Color, modifier: Modifier = Modifier) {
    Box(modifier) {
        Box(
            Modifier
                .size(264.dp + 28.dp) // 外扩 14
                .border(1.5.dp, color.copy(alpha = 0.16f), RoundedCornerShape(40.dp)),
        )
        Box(
            Modifier
                .align(Alignment.Center)
                .size(264.dp + 72.dp) // 外扩 36
                .border(1.5.dp, color.copy(alpha = 0.07f), RoundedCornerShape(62.dp)),
        )
    }
}

// ---- 信息区（M1 替代歌词三行）----

@Composable
private fun InfoBlock(song: Song?, modifier: Modifier = Modifier) {
    // 声标 3.5dp 主色（14dp 泛光）+ 三行左对齐：上一行 sub / 当前行 22/740 / 下一行 sub
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 34.dp)
            .padding(horizontal = 26.dp)
            .height(IntrinsicSize.Min),
    ) {
        Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .width(3.5.dp)
                    .height(56.dp)
                    .blur(14.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Box(
                Modifier
                    .width(3.5.dp)
                    .height(56.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.padding(vertical = 2.dp)) {
            Text(
                text = stringResource(R.string.np_info_album_label),
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight(500), lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = song?.album ?: "",
                style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight(740), lineHeight = 30.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = songInfoLine(song),
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight(500), lineHeight = 20.sp).tabularNums(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 「{年份} · {格式} · {大小}」 */
internal fun songInfoLine(song: Song?): String {
    song ?: return ""
    val parts = buildList {
        song.year?.let { add("${it} 年") }
        add(formatLabel(song))
        add(formatSize(song.sizeBytes))
    }
    return parts.joinToString(" · ")
}

/** 格式标签：按 MIME 推（FLAC / MP3 / WAV / OGG / AUDIO） */
internal fun formatLabel(song: Song?): String = when {
    song == null -> "AUDIO"
    song.mimeType?.contains("flac", ignoreCase = true) == true -> "FLAC"
    song.mimeType?.contains("mpeg", ignoreCase = true) == true -> "MP3"
    song.mimeType?.contains("wav", ignoreCase = true) == true -> "WAV"
    song.mimeType?.contains("ogg", ignoreCase = true) == true -> "OGG"
    else -> "AUDIO"
}

internal fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "未知大小"
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1) "%.1f MB".format(mb) else "${(bytes / 1024.0).toInt().coerceAtLeast(1)} KB"
}

// ---- 时间行 / 控制行 / 来源行 ----

@Composable
private fun TimeRow(positionMs: Long, durationMs: Long, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = formatPlayerTime(positionMs),
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight(640), lineHeight = 14.sp).tabularNums(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = formatPlayerTime(durationMs - positionMs, remaining = true),
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight(640), lineHeight = 14.sp).tabularNums(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ControlRow(
    playMode: PlayMode,
    playing: Boolean,
    buffering: Boolean,
    onShuffle: () -> Unit,
    onPrev: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val side = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 随机：SHUFFLE 态高亮 primary（播放模式高亮取 uiState.playMode）
        Icon(
            EchoIcons.Shuffle,
            contentDescription = stringResource(R.string.np_shuffle),
            modifier = Modifier
                .size(23.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onShuffle),
            tint = if (playMode == PlayMode.SHUFFLE) primary else side,
        )
        Icon(
            EchoIcons.SkipPrevious,
            contentDescription = stringResource(R.string.np_previous),
            modifier = Modifier
                .size(34.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onPrev),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        PlayPauseCircle(
            playing = playing,
            buffering = buffering,
            size = 74.dp,
            iconSize = 32.dp,
            onToggle = onTogglePlay,
            modifier = Modifier.shadow(
                elevation = 14.dp,
                shape = CircleShape,
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            ),
        )
        Icon(
            EchoIcons.SkipNext,
            contentDescription = stringResource(R.string.np_next),
            modifier = Modifier
                .size(34.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onNext),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        // 循环：REPEAT_ONE 用角标 1 图标（§4.4 单曲循环=填充语义角标）
        Icon(
            if (playMode == PlayMode.REPEAT_ONE) EchoIcons.RepeatOne else EchoIcons.Repeat,
            contentDescription = stringResource(R.string.np_repeat),
            modifier = Modifier
                .size(23.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onRepeat),
            tint = if (playMode == PlayMode.REPEAT_ALL) side else primary,
        )
    }
}

@Composable
private fun SourceRow(song: Song?, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        SourceBadge(
            label = "本地 " + formatLabel(song),
            dotColor = EchoColors.SourceLocal,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = song?.let { "本地文件 · ${formatLabel(it)} · ${formatSize(it.sizeBytes)}" } ?: "",
            style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight(400), lineHeight = 15.sp).tabularNums(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---- 队列面板（T8：顶栏「队列」入口 → 当前队列列表，读取播放层队列快照）----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(queue: List<Song>, currentSongId: String?, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 26.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.np_queue),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.np_queue_count, queue.size),
                    style = MaterialTheme.typography.labelLarge.tabularNums(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (queue.isEmpty()) {
                Text(
                    text = stringResource(R.string.np_queue_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 26.dp, vertical = 16.dp),
                )
            } else {
                Column(Modifier.padding(bottom = 24.dp)) {
                    queue.forEachIndexed { index, song ->
                        val isCurrent = PlaybackMediaId.of(song) == currentSongId
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(horizontal = 26.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelLarge.tabularNums(),
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(24.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = song.artist,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- 沉浸背景（§2.4：radial 光晕×2 + linear 基底；光晕 alpha 已在 palette 烘焙）----

private fun Modifier.immersiveBackground(palette: EchoPalette): Modifier = this.drawBehind {
    drawRect(palette.colorScheme.background)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(palette.glow, Color.Transparent),
            center = Offset(size.width * 0.10f, size.height * 0.08f),
            radius = size.maxDimension * 0.85f,
        ),
        radius = size.maxDimension * 0.85f,
        center = Offset(size.width * 0.10f, size.height * 0.08f),
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(palette.glowAlt, Color.Transparent),
            center = Offset(size.width * 0.92f, size.height * 0.88f),
            radius = size.maxDimension * 0.8f,
        ),
        radius = size.maxDimension * 0.8f,
        center = Offset(size.width * 0.92f, size.height * 0.88f),
    )
}
