package com.echomusic.app.core.playback

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * media3 真身的隔离层（BREAKDOWN T6）：把 `MediaController` 的连接与 Player.Listener
 * 翻译成桥层的 [SessionConnector]/[SessionHandle]/[SessionEvents] 抽象，
 * 让 [PlaybackControllerImpl] 的命令映射/状态推导可纯 JVM 单测。
 *
 * 线程约束（media3 要求）：MediaController 的构建与命令必须在主线程——
 * Koin 里给本桥配 Main 调度器的 scope，由 [PlaybackControllerImpl.connect] 在主线程发起。
 */
@OptIn(UnstableApi::class)
internal class MediaSessionConnector(private val context: Context) : SessionConnector {

    override suspend fun connect(events: SessionEvents): SessionHandle? =
        suspendCancellableCoroutine { continuation ->
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            future.addListener(
                {
                    try {
                        val controller = future.get()
                        continuation.resume(MediaControllerSessionHandle(controller, events))
                    } catch (t: Throwable) {
                        // 连接失败（服务异常等）：桥层回到「无播放」，允许重试
                        continuation.resume(null)
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
            continuation.invokeOnCancellation { MediaController.releaseFuture(future) }
        }
}

/**
 * [SessionHandle] 的 MediaController 实现，同时兼任 Player.Listener → [SessionEvents] 翻译器。
 */
@OptIn(UnstableApi::class)
internal class MediaControllerSessionHandle(
    private val controller: MediaController,
    private val events: SessionEvents,
) : SessionHandle, Player.Listener {

    init {
        controller.addListener(this)
    }

    // ---- 命令面 ----

    override fun play() {
        controller.play()
    }

    override fun pause() {
        controller.pause()
    }

    override fun seekTo(positionMs: Long) {
        controller.seekTo(positionMs)
    }

    override fun seekToNext() {
        controller.seekToNextMediaItem()
    }

    override fun seekToPrevious() {
        controller.seekToPreviousMediaItem()
    }

    override fun setQueue(items: List<MediaItem>, startIndex: Int) {
        controller.setMediaItems(items, startIndex, C.TIME_UNSET)
    }

    override fun prepare() {
        controller.prepare()
    }

    override fun applyPlayerConfig(config: PlayModePolicy.PlayerConfig) {
        controller.repeatMode = config.repeatMode
        controller.shuffleModeEnabled = config.shuffleEnabled
    }

    // ---- 查询面 ----

    override val isPlaying: Boolean
        get() = controller.isPlaying

    override val positionMs: Long
        get() = controller.currentPosition

    override val durationMs: Long
        get() = controller.duration.takeIf { it != C.TIME_UNSET } ?: 0L

    override fun currentQueueInfo(): SessionQueueInfo {
        val count = controller.mediaItemCount
        if (count == 0) return SessionQueueInfo(index = -1, count = 0, mediaId = null)
        return SessionQueueInfo(
            index = controller.currentMediaItemIndex,
            count = count,
            mediaId = controller.currentMediaItem?.mediaId,
        )
    }

    override fun release() {
        controller.removeListener(this)
        controller.release()
    }

    // ---- Player.Listener → SessionEvents 翻译 ----

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        emitQueueChanged()
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        emitQueueChanged()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        events.onPlayingChanged(isPlaying)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        events.onBufferingChanged(isBuffering = playbackState == Player.STATE_BUFFERING)
        if (playbackState == Player.STATE_READY) {
            events.onDurationChanged(durationMs)
        }
    }

    private fun emitQueueChanged() {
        currentQueueInfo().let { events.onQueueChanged(it.index, it.count, it.mediaId) }
    }
}
