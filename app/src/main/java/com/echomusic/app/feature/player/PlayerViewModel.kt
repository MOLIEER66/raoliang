package com.echomusic.app.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echomusic.app.core.data.repository.LibraryRepository
import com.echomusic.app.core.model.PlayMode
import com.echomusic.app.core.model.Song
import com.echomusic.app.core.playback.PlaybackController
import com.echomusic.app.core.playback.PlaybackMediaId
import com.echomusic.app.core.playback.PlaybackStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 播放页/迷你条共用的播放状态（UI 波次对 [PlaybackController] 的唯一消费面）。
 *
 * 关键补解析（播放层交接项）：进程重启后 `currentSong` 只有 mediaId（桥层队列缓存清空），
 * 这里经 `LibraryRepository.observeSong(mediaId 解码出的 songId)` 从库回填曲目详情，
 * 迷你条/播放页不出现「只有进度没有歌名」的空壳态。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel(
    private val controller: PlaybackController,
    repository: LibraryRepository,
) : ViewModel() {

    /** 合成后的播放态：controller 快照 + mediaId→Song 回填 */
    val uiState: StateFlow<PlayerUiState> = controller.uiState
        .flatMapLatest { playback ->
            val resolvedSong: kotlinx.coroutines.flow.Flow<Song?> = when {
                playback.currentSong != null -> flowOf(playback.currentSong)
                else -> {
                    val decoded = PlaybackMediaId.parse(playback.currentMediaId)
                    if (decoded != null && decoded.source == com.echomusic.app.core.model.SongSource.LOCAL) {
                        repository.observeSong(decoded.songId)
                    } else {
                        flowOf(null)
                    }
                }
            }
            resolvedSong.map { song ->
                PlayerUiState(
                    status = playback.status,
                    song = song,
                    queueIndex = playback.queueIndex,
                    queueSize = playback.queueSize,
                    playMode = playback.playMode,
                    durationMs = playback.durationMs,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerUiState())

    /** 进度（播放中 500ms 一跳，波形进度条数据源） */
    val positionMs: StateFlow<Long> = controller.positionMs

    /** 首帧连接 session（幂等；失败可重试） */
    fun connect() = controller.connect()

    fun togglePlayPause() = controller.togglePlayPause()

    fun next() = controller.next()

    fun previous() = controller.previous()

    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)

    fun setPlayMode(mode: PlayMode) = controller.setPlayMode(mode)

    fun playQueue(songs: List<Song>, startIndex: Int) = controller.playQueue(songs, startIndex)
}

/**
 * UI 消费的播放态快照（[PlaybackUiState] 的展示层同构 + 回填后的 song）。
 * 无播放 = status == [PlaybackStatus.NONE]，迷你条整条隐藏（§5.2）。
 */
data class PlayerUiState(
    val status: PlaybackStatus = PlaybackStatus.NONE,
    val song: Song? = null,
    val queueIndex: Int = -1,
    val queueSize: Int = 0,
    val playMode: PlayMode = PlayMode.REPEAT_ALL,
    val durationMs: Long = 0L,
) {
    val hasQueue: Boolean get() = queueSize > 0 && song != null
}
