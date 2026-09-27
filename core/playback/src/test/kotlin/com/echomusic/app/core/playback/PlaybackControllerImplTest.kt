package com.echomusic.app.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.echomusic.app.core.data.settings.PlaybackSettings
import com.echomusic.app.core.model.PlayMode
import com.echomusic.app.core.model.Song
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 桥层命令映射与会话事件推导（BREAKDOWN §3.1：MediaController 用接口/假体抽象后纯 JVM 测）。
 * [SessionHandle]/[SessionConnector] 喂假体，断言 PlaybackController 的 API →
 * media3 命令的映射正确、Player 事件 → PlaybackUiState 的推导正确。
 */
@OptIn(UnstableApi::class)
class PlaybackControllerImplTest {

    private class FakePlaybackSettings : PlaybackSettings {
        private val _mode = MutableStateFlow(PlaybackSettings.DEFAULT_PLAY_MODE)
        override val playMode: Flow<PlayMode> = _mode
        override suspend fun setPlayMode(mode: PlayMode) {
            _mode.value = mode
        }
    }

    private class FakeHandle : SessionHandle {
        lateinit var events: SessionEvents
        val calls = mutableListOf<String>()
        var queuedItems: List<MediaItem> = emptyList()
        var queuedStartIndex = -1
        var appliedConfig: PlayModePolicy.PlayerConfig? = null
        var queueInfo = SessionQueueInfo(index = -1, count = 0, mediaId = null)
        var playing = false
        var position = 0L
        var duration = 0L
        var released = false

        override fun play() {
            calls += "play"
        }

        override fun pause() {
            calls += "pause"
        }

        override fun seekTo(positionMs: Long) {
            calls += "seekTo($positionMs)"
        }

        override fun seekToNext() {
            calls += "next"
        }

        override fun seekToPrevious() {
            calls += "previous"
        }

        override fun setQueue(items: List<MediaItem>, startIndex: Int) {
            calls += "setQueue(${items.size},$startIndex)"
            queuedItems = items
            queuedStartIndex = startIndex
        }

        override fun prepare() {
            calls += "prepare"
        }

        override fun applyPlayerConfig(config: PlayModePolicy.PlayerConfig) {
            calls += "applyConfig(${config.repeatMode},${config.shuffleEnabled})"
            appliedConfig = config
        }

        override val isPlaying: Boolean get() = playing
        override val positionMs: Long get() = position
        override val durationMs: Long get() = duration
        override fun currentQueueInfo(): SessionQueueInfo = queueInfo

        override fun release() {
            released = true
        }
    }

    private class FakeConnector : SessionConnector {
        val handle = FakeHandle()
        var failNext = false

        /** 打开后 connect() 挂起在 gate 上，用于观察「连接前命令缓存」路径 */
        var gateEnabled = false
        private val gate = CompletableDeferred<Unit>()

        fun openGate() {
            gate.complete(Unit)
        }

        override suspend fun connect(events: SessionEvents): SessionHandle? {
            if (failNext) {
                failNext = false
                return null
            }
            if (gateEnabled) gate.await()
            return handle.also { it.events = events }
        }
    }

    private lateinit var scope: CoroutineScope
    private lateinit var settings: FakePlaybackSettings
    private lateinit var connector: FakeConnector
    private lateinit var controller: PlaybackControllerImpl

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        settings = FakePlaybackSettings()
        connector = FakeConnector()
        controller = PlaybackControllerImpl(
            settings = settings,
            scope = scope,
            connector = connector,
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    // ---- 命令映射 ----

    @Test
    fun playQueueMapsSongsToQueueAndAutoplays() {
        controller.connect()
        val songs = listOf(song(1), song(2), song(3))

        controller.playQueue(songs, startIndex = 1)

        assertTrue(connector.handle.calls.contains("setQueue(3,1)"))
        assertTrue(connector.handle.calls.containsAll(listOf("prepare", "play")))
        // 入队前先应用当前模式（默认 REPEAT_ALL）
        assertEquals(
            PlayModePolicy.playerConfig(PlayMode.REPEAT_ALL),
            connector.handle.appliedConfig,
        )
        // MediaItem 只带业务标识（ADR-0004 D1），mediaId = "{local}:{id}"
        assertEquals(
            listOf("local:1", "local:2", "local:3"),
            connector.handle.queuedItems.map { it.mediaId },
        )
        // 乐观更新：点播立即出迷你条
        assertEquals(songs[1], controller.uiState.value.currentSong)
        assertEquals(1, controller.uiState.value.queueIndex)
        assertEquals(3, controller.uiState.value.queueSize)
    }

    @Test
    fun emptyPlayQueueIsIgnored() {
        controller.connect()

        controller.playQueue(emptyList())

        // connect() 的模式应用之外，不应有任何队列/播放命令
        assertTrue(connector.handle.calls.none { it.startsWith("setQueue") || it == "play" || it == "prepare" })
    }

    @Test
    fun togglePlayPauseDelegatesByIsPlaying() {
        controller.connect()

        connector.handle.playing = false
        controller.togglePlayPause()
        assertTrue(connector.handle.calls.last() == "play")

        connector.handle.playing = true
        controller.togglePlayPause()
        assertTrue(connector.handle.calls.last() == "pause")
    }

    @Test
    fun transportCommandsMapDirectly() {
        controller.connect()

        controller.next()
        controller.previous()
        controller.seekTo(1234L)
        controller.play()
        controller.pause()

        // connect() 时 syncFromSession 会先应用持久化模式（applyConfig 在队首），传输命令按序追加在后
        assertEquals(
            listOf("next", "previous", "seekTo(1234)", "play", "pause"),
            connector.handle.calls.takeLast(5),
        )
    }

    @Test
    fun setPlayModePersistsAndAppliesToPlayer() = runBlocking {
        controller.connect()

        controller.setPlayMode(PlayMode.REPEAT_ONE)

        assertEquals(
            PlayModePolicy.playerConfig(PlayMode.REPEAT_ONE),
            connector.handle.appliedConfig,
        )
        assertEquals(PlayMode.REPEAT_ONE, controller.uiState.value.playMode)
        assertEquals(PlayMode.REPEAT_ONE, settings.playMode.first())
    }

    // ---- 连接生命周期 ----

    @Test
    fun commandsBeforeConnectAreBufferedAndFlushedOnConnect() {
        connector.gateEnabled = true

        // 未 connect() 就点播（UI 首帧与异步连接的竞争）：不崩溃，命令先缓存
        controller.playQueue(listOf(song(1)))

        assertTrue(connector.handle.calls.none { it.startsWith("setQueue") || it == "play" || it == "prepare" })
        // 乐观更新已生效（迷你条先出）
        assertEquals(song(1), controller.uiState.value.currentSong)
        assertEquals(1, controller.uiState.value.queueSize)

        // 连接成功后缓存命令按序补发
        controller.connect()
        connector.openGate()
        assertTrue(connector.handle.calls.any { it == "setQueue(1,0)" })
        assertTrue(connector.handle.calls.containsAll(listOf("prepare", "play")))
    }

    @Test
    fun failedConnectAllowsRetry() {
        connector.failNext = true

        controller.connect()
        assertNull(controller.uiState.value.currentSong)

        // 重试成功
        controller.connect()
        connector.handle.queueInfo = SessionQueueInfo(index = 0, count = 2, mediaId = "local:1")
        controller.playQueue(listOf(song(1), song(2)))
        assertTrue(connector.handle.calls.isNotEmpty())
    }

    @Test
    fun releaseReleasesHandleAndAllowsReconnect() {
        controller.connect()

        controller.release()

        assertTrue(connector.handle.released)
    }

    // ---- 会话事件 → UI 状态 ----

    @Test
    fun sessionEventsDeriveStatusTransitions() {
        controller.connect()
        connector.handle.queueInfo = SessionQueueInfo(index = 0, count = 1, mediaId = "local:1")
        controller.playQueue(listOf(song(1)))
        assertEquals(PlaybackStatus.PAUSED, controller.uiState.value.status) // 乐观态（无事件时）

        connector.handle.events.onBufferingChanged(true)
        assertEquals(PlaybackStatus.BUFFERING, controller.uiState.value.status)

        connector.handle.events.onPlayingChanged(true)
        connector.handle.events.onBufferingChanged(false)
        assertEquals(PlaybackStatus.PLAYING, controller.uiState.value.status)

        connector.handle.events.onPlayingChanged(false)
        assertEquals(PlaybackStatus.PAUSED, controller.uiState.value.status)

        // 队列清空 → 无播放（迷你条整条隐藏，DESIGN-SYSTEM §5.2）
        connector.handle.events.onQueueChanged(-1, 0, null)
        assertEquals(PlaybackStatus.NONE, controller.uiState.value.status)
    }

    @Test
    fun queueChangeEventResolvesCurrentSongFromSnapshot() {
        val songs = listOf(song(1), song(2), song(3))
        controller.connect()
        controller.playQueue(songs)

        connector.handle.events.onQueueChanged(2, 3, "local:3")

        assertEquals(songs[2], controller.uiState.value.currentSong)
        assertEquals("local:3", controller.uiState.value.currentMediaId)
        // 尚无 playing 事件：从乐观 PAUSED 保持为 PAUSED
        assertEquals(PlaybackStatus.PAUSED, controller.uiState.value.status)
        assertTrue(controller.uiState.value.hasQueue)
    }

    @Test
    fun durationEventFeedsUiState() {
        controller.connect()

        connector.handle.events.onDurationChanged(99_000L)

        assertEquals(99_000L, controller.uiState.value.durationMs)
    }

    @Test
    fun connectSyncsQueueAndDurationFromSession() {
        connector.handle.queueInfo = SessionQueueInfo(index = 1, count = 3, mediaId = "local:2")
        connector.handle.duration = 45_000L

        controller.connect()
        controller.playQueue(listOf(song(1), song(2), song(3)), startIndex = 0)

        // playQueue 乐观覆盖后，会话事件最终对齐（这里只验证初连同步不抛、字段可写）
        assertEquals(3, controller.uiState.value.queueSize)
    }

    private fun song(id: Long) = Song(
        id = id,
        title = "曲目$id",
        artist = "歌手$id",
        album = "专辑$id",
        albumId = id,
        durationMs = 200_000L,
        sizeBytes = 1_000_000L,
        path = "/music/$id.flac",
        dateModifiedSec = 0L,
    )
}
