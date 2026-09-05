package com.echomusic.app.feature.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echomusic.app.R
import com.echomusic.app.core.data.sync.SyncState
import com.echomusic.app.core.designsystem.components.EchoEmptyState
import com.echomusic.app.core.designsystem.components.EchoLinearProgress
import com.echomusic.app.core.designsystem.components.EchoPillButton
import com.echomusic.app.core.designsystem.components.EchoPrimaryButton
import com.echomusic.app.core.designsystem.components.EchoTabs
import com.echomusic.app.core.designsystem.components.SongListRow
import com.echomusic.app.core.designsystem.components.SongRowSkeleton
import com.echomusic.app.core.designsystem.icon.EchoIcons
import com.echomusic.app.core.designsystem.theme.EchoSpacing
import com.echomusic.app.core.designsystem.theme.tabularNums
import com.echomusic.app.core.model.LibraryStats
import com.echomusic.app.core.model.Song
import com.echomusic.app.core.playback.PlaybackMediaId
import com.echomusic.app.core.playback.PlaybackStatus
import org.koin.androidx.compose.koinViewModel
import java.text.NumberFormat
import java.util.Locale

/**
 * 音乐库（SCREENS §1 / BREAKDOWN T4 四态 + T7 布局）。
 * 状态机：未授权 → 扫描中 → 空 / 错误 → 正常；订阅 syncState 与三个 observe API。
 * 正常态：大标题滚动收缩吸顶 + 标签页吸附其下 + 「随机播放全部」药丸行。
 */
enum class LibraryTab(val labelRes: Int) {
    ALL(R.string.tab_all),
    RECENT(R.string.tab_recent),
    MOST(R.string.tab_most),
}

/** 列表底部内边距（SCREENS §0：迷你条隐藏 114；迷你条出现再加 62+8） */
internal val LIBRARY_BOTTOM_PAD = 114.dp

@Composable
fun LibraryScreen(
    extraBottomPadding: Dp = 0.dp,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val audioPermission = rememberAudioPermissionState()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val playbackUi by viewModel.playbackUi.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(LibraryTab.ALL) }
    val songs by when (selectedTab) {
        LibraryTab.ALL -> viewModel.allSongs
        LibraryTab.RECENT -> viewModel.recentSongs
        LibraryTab.MOST -> viewModel.mostSongs
    }.collectAsStateWithLifecycle()

    // 授权后自动触发同步（进程内一次；增量同步幂等，冷启动进入同样执行）
    LaunchedEffect(audioPermission.granted) {
        if (audioPermission.granted) viewModel.ensureSyncRequested()
    }
    // 通知权限（13+）：媒体通知显示前提（T1），音频授权后补请求、拒绝不影响播放
    RequestNotificationPermissionOnce(audioGranted = audioPermission.granted)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when {
            !audioPermission.granted -> {
                LibraryHeader(stats = stats)
                PermissionState(audioPermission)
            }
            syncState.isInProgress -> {
                LibraryHeader(stats = stats)
                ScanningState(syncState)
            }
            songs.isEmpty() && syncState.phase == SyncState.Phase.FAILED -> {
                LibraryHeader(stats = stats)
                ScanErrorState(error = syncState.error, onRetry = viewModel::rescan)
            }
            songs.isEmpty() -> {
                LibraryHeader(stats = stats)
                EmptyState(onScan = viewModel::rescan)
            }
            else -> NormalLibrary(
                stats = stats,
                tabs = LibraryTab.entries.map { stringResource(it.labelRes) },
                selectedTab = selectedTab.ordinal,
                onTabSelect = { selectedTab = LibraryTab.entries[it] },
                songs = songs,
                currentMediaId = playbackUi.currentMediaId,
                isPlaying = playbackUi.status == PlaybackStatus.PLAYING,
                onSongClick = { index -> viewModel.play(songs, index) },
                onShuffleAll = { viewModel.playAllShuffled(viewModel.allSongs.value) },
                extraBottomPadding = extraBottomPadding,
            )
        }
    }
}

/**
 * 大标题区（SCREENS §1）：padding 66/22/0（状态栏 inset + 14 / 水平 22）+ 统计行。
 * 非正常态（未授权/扫描/空/错误）使用静态标题；正常态的吸顶标题见 [CollapsingHeader]。
 */
@Composable
private fun LibraryHeader(stats: LibraryStats) {
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 14.dp),
    ) {
        Text(
            text = stringResource(R.string.library_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 22.dp),
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = statsLine(stats),
            style = MaterialTheme.typography.labelLarge.tabularNums(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 22.dp),
        )
    }
}

/** 统计行「1,247 首 · 本地 1,240 · 洛雪 7」（洛雪段 M1 恒为 0，不显示） */
internal fun statsLine(stats: LibraryStats): String {
    val format = NumberFormat.getIntegerInstance(Locale.CHINA)
    return buildString {
        append(format.format(stats.total)).append(" 首 · 本地 ").append(format.format(stats.localCount))
        if (stats.onlineCount > 0) {
            append(" · 洛雪 ").append(format.format(stats.onlineCount))
        }
    }
}

/**
 * 正常态（SCREENS §1 全量）：大标题滚动收缩为吸顶小标题（缩放+淡出）、统计行与
 * 「随机播放全部」随收缩淡出、标签页吸附其下（吸顶栏背景 surface 88% + 发丝线）。
 */
@Composable
private fun NormalLibrary(
    stats: LibraryStats,
    tabs: List<String>,
    selectedTab: Int,
    onTabSelect: (Int) -> Unit,
    songs: List<Song>,
    currentMediaId: String?,
    isPlaying: Boolean,
    onSongClick: (Int) -> Unit,
    onShuffleAll: () -> Unit,
    extraBottomPadding: Dp,
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val collapseRangePx = with(density) { 88.dp.toPx() }
    var expandedHeaderPx by remember { mutableStateOf(0f) }

    // 收缩进度：列表 item0 滚过 collapseRange → 1
    val collapseFraction by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset / collapseRangePx).coerceIn(0f, 1f)
            }
        }
    }
    // Tab 切换回顶部（§6.3d：内容 crossfade 150ms + 8dp 滑移由 AnimatedContent 承担）
    LaunchedEffect(selectedTab) { listState.scrollToItem(0) }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                (fadeIn(animationSpec = tween(150)) +
                    slideInVertically(animationSpec = tween(150)) { it / 8 })
                    .togetherWith(fadeOut(animationSpec = tween(150)))
            },
            modifier = Modifier.fillMaxSize(),
            label = "tabContent",
        ) { _ ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = with(density) { expandedHeaderPx.toDp() },
                    bottom = LIBRARY_BOTTOM_PAD + extraBottomPadding,
                ),
            ) {
                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    SongListRow(
                        song = song,
                        onClick = { onSongClick(index) },
                        isCurrent = PlaybackMediaId.of(song) == currentMediaId,
                        isPlaying = isPlaying,
                    )
                }
            }
        }
        // 吸顶栏（overlay）：标题收缩 + 统计行/药丸淡出 + 标签页常驻
        CollapsingHeader(
            collapseFraction = collapseFraction,
            stats = stats,
            tabs = tabs,
            selectedTab = selectedTab,
            onTabSelect = onTabSelect,
            onShuffleAll = onShuffleAll,
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { if (collapseFraction < 0.01f) expandedHeaderPx = it.height.toFloat() },
        )
    }
}

@Composable
private fun CollapsingHeader(
    collapseFraction: Float,
    stats: LibraryStats,
    tabs: List<String>,
    selectedTab: Int,
    onTabSelect: (Int) -> Unit,
    onShuffleAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 吸顶栏背景：收缩后 surface 88% + 底部发丝线（SCREENS §1 吸顶栏）
    val barAlpha by animateFloatAsState(collapseFraction, label = "headerBarAlpha")
    Column(
        modifier
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f * barAlpha)),
    ) {
        // 大标题 30/750 → 吸顶小标题 20/600（缩放+淡出，随滚动驱动）
        Text(
            text = stringResource(R.string.library_title),
            style = TextStyle(
                fontSize = lerp(30.sp, 20.sp, collapseFraction),
                fontWeight = FontWeight(750 - (150 * collapseFraction).toInt()),
                lineHeight = lerp(38.sp, 28.sp, collapseFraction),
                letterSpacing = lerp((-0.4).sp, 0.sp, collapseFraction),
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(top = 14.dp)
                .padding(horizontal = 22.dp),
        )
        // 统计行：随收缩淡出（前 60% 收缩进度内完成）
        if (collapseFraction < 0.6f) {
            val statsAlpha = (1f - collapseFraction / 0.6f).coerceIn(0f, 1f)
            Text(
                text = statsLine(stats),
                style = MaterialTheme.typography.labelLarge.tabularNums(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 7.dp)
                    .padding(horizontal = 22.dp)
                    .alpha(statsAlpha),
            )
        }
        // 操作行：药丸「随机播放全部」（§5.4 药丸 38dp + shuffle 17），随收缩淡出
        if (collapseFraction < 0.6f) {
            val pillAlpha = (1f - collapseFraction / 0.6f).coerceIn(0f, 1f)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 8.dp)
                    .padding(horizontal = 22.dp)
                    .alpha(pillAlpha),
            ) {
                EchoPillButton(
                    text = stringResource(R.string.shuffle_all),
                    icon = EchoIcons.Shuffle,
                    onClick = onShuffleAll,
                )
            }
        }
        // 标签页：吸附其下（吸顶锚点）
        EchoTabs(tabs = tabs, selected = selectedTab, onSelect = onTabSelect)
        // 吸顶后 1dp 发丝线
        if (barAlpha > 0f) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = barAlpha)),
            )
        }
    }
}

@Composable
private fun PermissionState(audioPermission: AudioPermissionState) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EchoEmptyState(
            title = stringResource(R.string.permission_title),
            body = stringResource(R.string.permission_body),
            action = {
                EchoPrimaryButton(
                    text = if (audioPermission.permanentlyDenied) {
                        stringResource(R.string.permission_open_settings)
                    } else {
                        stringResource(R.string.permission_grant)
                    },
                    onClick = {
                        if (audioPermission.permanentlyDenied) audioPermission.openAppSettings() else audioPermission.request()
                    },
                )
            },
        )
    }
}

/** 扫描中：顶部 2dp 线性进度 + 实时计数 + 5 行骨架（SCREENS §1） */
@Composable
private fun ScanningState(syncState: SyncState) {
    Column {
        Spacer(Modifier.height(EchoSpacing.s8))
        EchoLinearProgress(
            fraction = if (syncState.phase == SyncState.Phase.SAVING && syncState.found > 0) {
                syncState.upserted.toFloat() / syncState.found
            } else {
                null
            },
        )
        Text(
            text = if (syncState.phase == SyncState.Phase.SAVING && syncState.found > 0) {
                stringResource(R.string.scanning_progress, syncState.upserted, syncState.found)
            } else {
                stringResource(R.string.scanning_query)
            },
            style = MaterialTheme.typography.labelLarge.tabularNums(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
        )
        SongRowSkeleton(rows = 5)
    }
}

@Composable
private fun EmptyState(onScan: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EchoEmptyState(
            title = stringResource(R.string.empty_title),
            body = stringResource(R.string.empty_body),
            action = {
                EchoPrimaryButton(
                    text = stringResource(R.string.empty_scan),
                    onClick = onScan,
                )
            },
        )
    }
}

/** 错误三件套（§5.8）：原因 + 细节（可读错误码）+ 出路（主按钮「重试」） */
@Composable
private fun ScanErrorState(error: String?, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EchoEmptyState(
            title = stringResource(R.string.scan_failed_title),
            body = stringResource(R.string.scan_failed_body, error ?: "UNKNOWN"),
            action = {
                EchoPrimaryButton(
                    text = stringResource(R.string.retry),
                    onClick = onRetry,
                )
            },
        )
    }
}

/** Android 13+ 通知权限：音频授权后补请求一次（拒绝不阻塞，仅媒体通知不显示） */
@Composable
private fun RequestNotificationPermissionOnce(audioGranted: Boolean) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    var requested by remember { mutableStateOf(false) }
    LaunchedEffect(audioGranted) {
        if (audioGranted && !requested && Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requested = true
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
