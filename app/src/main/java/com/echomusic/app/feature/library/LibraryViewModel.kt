package com.echomusic.app.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echomusic.app.core.data.repository.LibraryRepository
import com.echomusic.app.core.data.sync.SyncState
import com.echomusic.app.core.model.LibraryStats
import com.echomusic.app.core.model.PlayMode
import com.echomusic.app.core.model.Song
import com.echomusic.app.core.playback.PlaybackController
import com.echomusic.app.core.playback.PlaybackUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 音乐库 ViewModel（SCREENS §1 数据面）：三个标签页对应仓库三个 observe API，
 * 统计行订阅 observeStats，扫描态订阅 syncState（T4）。
 * 点播/随机播放全部经 [PlaybackController]（T6 桥层契约）。
 */
class LibraryViewModel(
    private val repository: LibraryRepository,
    private val playback: PlaybackController,
) : ViewModel() {

    /** 库同步进度（扫描态：顶部 2dp 线性进度 + 实时计数） */
    val syncState: StateFlow<SyncState> = repository.syncState

    /** 统计行「N 首 · 本地 N · 洛雪 N」 */
    val stats: StateFlow<LibraryStats> = repository.observeStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryStats(0))

    /** 标签页一：全部歌曲（标题排序） */
    val allSongs: StateFlow<List<Song>> = repository.observeLibrary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 标签页二：最近播放（新→旧） */
    val recentSongs: StateFlow<List<Song>> = repository.observeRecentlyPlayed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 标签页三：最常播放（次数多→少） */
    val mostSongs: StateFlow<List<Song>> = repository.observeMostPlayed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 播放态快照（正在播放行高亮 + 声波指示器的数据源） */
    val playbackUi: StateFlow<PlaybackUiState> = playback.uiState

    /** 进度数据源（迷你条 2dp 进度线，T7 接线；这里只透出） */
    val positionMs: StateFlow<Long> = playback.positionMs

    private var syncRequested = false

    /** 授权后触发一次同步（进程内幂等；增量扫描成本低，冷启动进入也执行） */
    fun ensureSyncRequested() {
        if (syncRequested) return
        syncRequested = true
        rescan()
    }

    /** 手动扫描/重试（空态主按钮、错误态重试） */
    fun rescan() {
        viewModelScope.launch { repository.syncLibrary() }
    }

    /** 点播：入队并立即播放（P0-4） */
    fun play(songs: List<Song>, startIndex: Int) {
        playback.playQueue(songs, startIndex)
    }

    /** 「随机播放全部」：切随机模式 + 全库入队（BREAKDOWN T7） */
    fun playAllShuffled(songs: List<Song>) {
        if (songs.isEmpty()) return
        playback.setPlayMode(PlayMode.SHUFFLE)
        playback.playQueue(songs, 0)
    }

    fun togglePlayPause() = playback.togglePlayPause()

    fun next() = playback.next()
}
