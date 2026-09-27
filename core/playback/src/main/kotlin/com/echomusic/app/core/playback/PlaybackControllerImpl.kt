package com.echomusic.app.core.playback

import com.echomusic.app.core.data.settings.PlaybackSettings
import com.echomusic.app.core.model.PlayMode
import com.echomusic.app.core.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * [PlaybackController] 的纯逻辑实现（BREAKDOWN T6 桥层）：
 * 命令映射 + 会话事件 → State 状态推导 + 进度轮询，全部只依赖 [SessionConnector]/[SessionHandle]
 * 抽象与 [PlaybackSettings]，可整类 JVM 单测（media3 真身隔离在 MediaControllerBridge）。
 *
 * 状态模型：
 *  - `uiState`：会话事件驱动（onQueueChanged/onPlayingChanged/onBufferingChanged/onDurationChanged），
 *    playQueue 时对曲目/队列位置做乐观更新（点播立即出迷你条，P0-4）；
 *  - `positionMs`：连接期间 500ms 轮询（进度线 2dp 的数据源，暂停时自然静止）。
 *
 * 线程模型：本类不绑定线程；media3 真实现的线程约束（主线程命令）封在 [MediaControllerBridge] 内。
 */
class PlaybackControllerImpl(
    private val settings: PlaybackSettings,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    private val connector: SessionConnector,
) : PlaybackController {

    private val _uiState = MutableStateFlow(PlaybackUiState())
    override val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    override val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private var handle: SessionHandle? = null
    private var connectRequested = false
    private var settingsSyncStarted = false
    private var tickerJob: Job? = null

    /** 连接建立前收到的命令（连接成功后按序补发，避免 UI 首帧点播与异步连接竞争丢命令） */
    private val pendingCommands = mutableListOf<(SessionHandle) -> Unit>()

    /** playQueue 下发的曲目快照：会话事件只带 mediaId/下标，曲目详情由此回查 */
    private var queueSongs: List<Song> = emptyList()

    // 会话侧的播放/缓冲标志（deriveStatus 的输入）
    private var isPlaying = false
    private var isBuffering = false

    private val eventsBridge = object : SessionEvents {
        override fun onQueueChanged(queueIndex: Int, queueSize: Int, currentMediaId: String?) {
            _uiState.update {
                it.copy(
                    queueIndex = queueIndex,
                    queueSize = queueSize,
                    currentMediaId = currentMediaId,
                    currentSong = queueSongs.getOrNull(queueIndex)
                        ?: it.currentSong.takeIf { _ -> currentMediaId == it.currentMediaId },
                )
            }
            recomputeStatus()
        }

        override fun onPlayingChanged(playing: Boolean) {
            isPlaying = playing
            recomputeStatus()
        }

        override fun onBufferingChanged(buffering: Boolean) {
            isBuffering = buffering
            recomputeStatus()
        }

        override fun onDurationChanged(durationMs: Long) {
            _uiState.update { it.copy(durationMs = durationMs) }
        }
    }

    override fun connect() {
        if (connectRequested) return
        connectRequested = true
        scope.launch {
            val h = connector.connect(eventsBridge)
            if (h == null) {
                // 连接失败（服务未起/会话被杀）：允许再次 connect() 重试
                connectRequested = false
                return@launch
            }
            handle = h
            syncSettingsToUiState()
            syncFromSession(h)
            startTicker(h)
            // 补发连接建立前缓存的命令（如 UI 首帧点播）
            val buffered = pendingCommands.toList()
            pendingCommands.clear()
            buffered.forEach { it(h) }
        }
    }

    override fun playQueue(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty()) return
        val index = startIndex.coerceIn(0, songs.lastIndex)
        queueSongs = songs
        _queue.value = songs
        // 乐观更新：点播立即出迷你条（status 短暂为 PAUSED，由会话事件修正为 BUFFERING/PLAYING）
        _uiState.update {
            it.copy(
                currentSong = songs[index],
                currentMediaId = PlaybackMediaId.of(songs[index]),
                queueIndex = index,
                queueSize = songs.size,
            )
        }
        recomputeStatus()
        withConnected { h ->
            h.applyPlayerConfig(PlayModePolicy.playerConfig(_uiState.value.playMode))
            h.setQueue(QueueMediaItemFactory.createAll(songs), index)
            h.prepare()
            h.play()
        }
    }

    override fun play() = withConnected { it.play() }

    override fun pause() = withConnected { it.pause() }

    override fun togglePlayPause() = withConnected { h ->
        if (h.isPlaying) h.pause() else h.play()
    }

    override fun seekTo(positionMs: Long) = withConnected { it.seekTo(positionMs) }

    override fun next() = withConnected { it.seekToNext() }

    override fun previous() = withConnected { it.seekToPrevious() }

    override fun setPlayMode(mode: PlayMode) {
        // 本地乐观更新 + 持久化（DataStore 流回流后同值幂等）
        _uiState.update { it.copy(playMode = mode) }
        scope.launch { settings.setPlayMode(mode) }
        withConnected { it.applyPlayerConfig(PlayModePolicy.playerConfig(mode)) }
    }

    override fun release() {
        tickerJob?.cancel()
        tickerJob = null
        pendingCommands.clear()
        _queue.value = emptyList()
        handle?.release()
        handle = null
        connectRequested = false
    }

    private fun withConnected(block: (SessionHandle) -> Unit) {
        val h = handle
        if (h != null) {
            block(h)
            return
        }
        // 未连接（如 UI 首帧没等到 connect 完成就点播）：缓存命令 + 发起连接
        pendingCommands += block
        connect()
    }

    /** 播放模式偏好 → UI 态（一次性收集，后续 setPlayMode 走乐观更新） */
    private fun syncSettingsToUiState() {
        if (settingsSyncStarted) return
        settingsSyncStarted = true
        scope.launch {
            settings.playMode.collect { mode ->
                if (_uiState.value.playMode != mode) {
                    _uiState.update { it.copy(playMode = mode) }
                }
            }
        }
    }

    private fun syncFromSession(h: SessionHandle) {
        val info = h.currentQueueInfo()
        isPlaying = h.isPlaying
        isBuffering = false
        // 会话尚无队列时保留本地乐观态（首帧点播与异步连接竞争不闪没迷你条），
        // 会话已有队列（断线重连/进程内恢复）则以会话为准
        val sessionHasQueue = info.count > 0
        _uiState.update {
            it.copy(
                queueIndex = if (sessionHasQueue) info.index else it.queueIndex,
                queueSize = if (sessionHasQueue) info.count else it.queueSize,
                currentMediaId = info.mediaId ?: it.currentMediaId,
                currentSong = queueSongs.getOrNull(info.index) ?: it.currentSong,
                durationMs = h.durationMs,
            )
        }
        recomputeStatus()
        // 重连/首连后把持久化的播放模式应用到会话
        h.applyPlayerConfig(PlayModePolicy.playerConfig(_uiState.value.playMode))
    }

    private fun recomputeStatus() = _uiState.update { it.copy(status = deriveStatus()) }

    private fun deriveStatus(): PlaybackStatus = when {
        _uiState.value.currentMediaId == null -> PlaybackStatus.NONE
        isBuffering -> PlaybackStatus.BUFFERING
        isPlaying -> PlaybackStatus.PLAYING
        else -> PlaybackStatus.PAUSED
    }

    private fun startTicker(h: SessionHandle) {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive && handle === h) {
                _positionMs.value = h.positionMs
                delay(POSITION_POLL_INTERVAL_MS)
            }
        }
    }

    private companion object {
        /** 进度轮询间隔（进度线 2dp / 播放页滑杆的数据刷新粒度） */
        const val POSITION_POLL_INTERVAL_MS = 500L
    }
}
