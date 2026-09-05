package com.echomusic.app.feature.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echomusic.app.R
import org.koin.androidx.compose.koinViewModel
import com.echomusic.app.core.data.sync.SyncState
import com.echomusic.app.core.designsystem.components.EchoEmptyState
import com.echomusic.app.core.designsystem.components.EchoLinearProgress
import com.echomusic.app.core.designsystem.components.EchoPrimaryButton
import com.echomusic.app.core.designsystem.components.EchoRippleArtwork
import com.echomusic.app.core.designsystem.components.EchoTabs
import com.echomusic.app.core.designsystem.components.SongListRow
import com.echomusic.app.core.designsystem.components.SongRowSkeleton
import com.echomusic.app.core.designsystem.theme.EchoSpacing
import com.echomusic.app.core.designsystem.theme.tabularNums
import com.echomusic.app.core.model.LibraryStats
import com.echomusic.app.core.model.Song
import com.echomusic.app.core.playback.PlaybackMediaId
import com.echomusic.app.core.playback.PlaybackStatus
import java.text.NumberFormat
import java.util.Locale

/**
 * 音乐库（SCREENS §1 / BREAKDOWN T4 四态 + T7 布局）。
 * 状态机：未授权 → 扫描中 → 空 / 错误 → 正常；订阅 syncState 与三个 observe API。
 */
enum class LibraryTab(val labelRes: Int) {
    ALL(R.string.tab_all),
    RECENT(R.string.tab_recent),
    MOST(R.string.tab_most),
}

@Composable
fun LibraryScreen(viewModel: LibraryViewModel = koinViewModel()) {
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
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        LibraryHeader(stats = stats, scanning = syncState.isInProgress)
        when {
            !audioPermission.granted -> PermissionState(audioPermission)
            syncState.isInProgress -> ScanningState(syncState)
            songs.isEmpty() && syncState.phase == SyncState.Phase.FAILED -> ScanErrorState(
                error = syncState.error,
                onRetry = viewModel::rescan,
            )
            songs.isEmpty() -> EmptyState(onScan = viewModel::rescan)
            else -> SongList(
                tabs = LibraryTab.entries.map { stringResource(it.labelRes) },
                selectedTab = selectedTab.ordinal,
                onTabSelect = { selectedTab = LibraryTab.entries[it] },
                songs = songs,
                currentMediaId = playbackUi.currentMediaId,
                isPlaying = playbackUi.status == PlaybackStatus.PLAYING,
                onSongClick = { index -> viewModel.play(songs, index) },
            )
        }
    }
}

/** 大标题区：padding 66/22/0（状态栏 inset + 14 / 水平 22）+ 统计行（SCREENS §1） */
@Composable
private fun LibraryHeader(stats: LibraryStats, scanning: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .padding(horizontal = 22.dp),
    ) {
        Text(
            text = stringResource(R.string.library_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = statsLine(stats),
            style = MaterialTheme.typography.labelLarge.tabularNums(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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

/** 正常态：标签页 + 64dp 歌曲行（§5.3）；列表底部内边距按 SCREENS §0 */
@Composable
private fun SongList(
    tabs: List<String>,
    selectedTab: Int,
    onTabSelect: (Int) -> Unit,
    songs: List<Song>,
    currentMediaId: String?,
    isPlaying: Boolean,
    onSongClick: (Int) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        EchoTabs(
            tabs = tabs,
            selected = selectedTab,
            onSelect = onTabSelect,
        )
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 114.dp),
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
