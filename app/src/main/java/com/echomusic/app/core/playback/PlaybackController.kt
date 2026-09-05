package com.echomusic.app.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.echomusic.app.core.model.PlayMode
import com.echomusic.app.core.model.Song
import kotlinx.coroutines.flow.StateFlow

/**
 * UI↔session 解耦层（BREAKDOWN T6）：MediaController 桥对 UI 波次暴露的唯一门面。
 *
 * 使用约定（UI 波次接线指南）：
 *  - 首帧（LaunchedEffect）调用一次 [connect]，之后任意线程安全地订阅/操作；
 *  - `uiState`（曲目/播放态/队列位置/模式）与 `positionMs`（进度，播放中 500ms 刷新）
 *    都是 StateFlow，`collectAsStateWithLifecycle` 直订；
 *  - 无播放时 [PlaybackUiState.status] == [PlaybackStatus.NONE]，迷你条整条隐藏（DESIGN-SYSTEM §5.2）。
 * 不写任何 Compose（T7/T8 UI 波次消费本接口）。
 */
interface PlaybackController {

    /** 播放态快照（当前曲目/播放态/队列位置/播放模式/时长） */
    val uiState: StateFlow<PlaybackUiState>

    /** 播放进度（毫秒；播放中约 500ms 一跳，暂停时静止） */
    val positionMs: StateFlow<Long>

    /** 异步连接 PlaybackService 的 session（幂等；失败自动可重试，再调一次即可） */
    fun connect()

    /**
     * 入队并立即播放（库页点播、「随机播放全部」都走这里）。
     * @param startIndex 起播曲目下标（随机模式下为「第一首播谁」，后续顺序由 shuffle 决定）
     */
    fun playQueue(songs: List<Song>, startIndex: Int = 0)

    fun play()
    fun pause()
    fun togglePlayPause()
    fun seekTo(positionMs: Long)

    /** 下一首/上一首（边界语义交给播放器：REPEAT_ALL 回绕，SHUFFLE 走乱序） */
    fun next()
    fun previous()

    /** 切换播放模式：持久化到 DataStore + 应用到播放器（REPEAT_MODE/SHUFFLE 映射见 PlayModePolicy） */
    fun setPlayMode(mode: PlayMode)

    /** 释放 MediaController（进程内单例通常不调；测试/必要时显式释放） */
    fun release()
}

/**
 * 迷你播放条/正在播放页所需的播放态快照（SCREENS §5.2 四态：播放中/暂停/缓冲/无播放）。
 */
data class PlaybackUiState(
    val status: PlaybackStatus = PlaybackStatus.NONE,
    val currentSong: Song? = null,
    /** 当前曲目 mediaId（有队列但控制器未缓存曲目详情时也可用，M2 在线曲依赖此字段） */
    val currentMediaId: String? = null,
    val queueIndex: Int = -1,
    val queueSize: Int = 0,
    val playMode: PlayMode = PlayMode.REPEAT_ALL,
    /** 当前曲目时长（毫秒；未知为 0） */
    val durationMs: Long = 0L,
) {
    val hasQueue: Boolean get() = queueSize > 0
}

/** 播放态（迷你条四态，DESIGN-SYSTEM §5.2：NONE = 无播放，整条隐藏） */
enum class PlaybackStatus {
    /** 无队列/无当前曲目 */
    NONE,

    /** 缓冲中（播放键换 20dp 环形进度） */
    BUFFERING,

    /** 播放中（声波律动） */
    PLAYING,

    /** 暂停（图标切换、声波静止） */
    PAUSED,
}

/**
 * —— 以下为 MediaController 的「接口抽象缝」，专为 JVM 单测设（BREAKDOWN §3.1：命令映射逻辑
 * 用假体可替身）。真实现 [MediaControllerSessionHandle]/[MediaSessionConnector] 包装 media3，
 * 纯逻辑 [PlaybackControllerImpl] 只依赖本抽象。
 */

/** 会话侧事件（真实现由 Player.Listener 翻译；[PlaybackControllerImpl] 据此推导 UI 状态） */
interface SessionEvents {

    /** 当前队列变化（入队/切歌/队列清空）。count==0 或 mediaId==null 表示无当前曲目 */
    fun onQueueChanged(queueIndex: Int, queueSize: Int, currentMediaId: String?)

    /** 是否正在出声（isPlaying） */
    fun onPlayingChanged(isPlaying: Boolean)

    /** 是否缓冲中（STATE_BUFFERING） */
    fun onBufferingChanged(isBuffering: Boolean)

    /** 当前曲目时长（STATE_READY 后上报；未知传 0） */
    fun onDurationChanged(durationMs: Long)
}

/** 当前队列快照 */
data class SessionQueueInfo(val index: Int, val count: Int, val mediaId: String?)

/**
 * MediaController 的最小命令/查询面（media3 类型仅 MediaItem 与 Player.REPEAT_MODE_* 常量）。
 * 断线后命令安全丢弃由 [PlaybackControllerImpl] 兜底，实现内不必判空。
 */
@OptIn(UnstableApi::class)
interface SessionHandle {

    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun seekToNext()
    fun seekToPrevious()

    /** 入队（ADR-0004 D1：MediaItem 只带业务标识，URI 由 PlaybackService 解析） */
    fun setQueue(items: List<MediaItem>, startIndex: Int)

    fun prepare()

    /** 应用播放模式配置（PlayModePolicy.playerConfig 的产物） */
    fun applyPlayerConfig(config: PlayModePolicy.PlayerConfig)

    val isPlaying: Boolean
    val positionMs: Long
    val durationMs: Long

    fun currentQueueInfo(): SessionQueueInfo

    fun release()
}

/** MediaController 连接抽象（真实现走 SessionToken → PlaybackService，测试喂假体） */
interface SessionConnector {

    /** 主线程安全的异步连接；失败返回 null（桥层回到「无播放」，允许重试） */
    suspend fun connect(events: SessionEvents): SessionHandle?
}
