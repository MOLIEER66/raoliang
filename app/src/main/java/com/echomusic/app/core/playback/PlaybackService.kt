package com.echomusic.app.core.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.echomusic.app.MainActivity
import com.echomusic.app.R
import com.echomusic.app.core.data.repository.LibraryRepository
import com.echomusic.app.core.model.SongSource
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * 播放引擎服务（BREAKDOWN T5 / ADR-0004 D1 方案 A）：
 * `MediaSessionService` + `ExoPlayer` + `DefaultMediaNotificationProvider`。
 *
 * 三个系统交互开关全部托管给 Media3（ADR-0004 D1 的核心取舍）：
 *  - `setAudioAttributes(_, handleAudioFocus = true)`：被其他应用抢占 → 暂停，抢占结束 → 按系统
 *    信号恢复；音频焦点丢失期间不自动抢回（P0-12）；
 *  - `setHandleAudioBecomingNoisy(true)`：拔耳机/断蓝牙自动暂停（P0-7）；
 *  - `setWakeMode(WAKE_MODE_LOCAL)`：播放期间持锁，后台不被 CPU 休眠打断（P1-19）。
 *
 * 通知用官方默认实现（MediaStyle，自动带封面/歌名/播放控制，通道名取 strings 资源）；
 * 13+ 无通知权限时服务不死、仅通知不显示（P0-5 的厂商差异兜底见 M1-BREAKDOWN §4）。
 *
 * 队列来源：UI 经 MediaController 下发（见 [PlaybackController.playQueue]），
 * session callback（[resolveLocalMediaItems]）把 mediaId 解析成本地 content URI 才真正可播。
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private val libraryRepository: LibraryRepository by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val playCountTracker = PlayCountTracker()
    private val playCountListener = PlayCountListener()
    private val sessionCallback = SessionCallback()

    /** onAddMediaItems 的异步解析挂起期间的 future 引用，销毁时统一置异常，避免控制端永久挂起 */
    private val pendingResolutions = mutableSetOf<SettableFuture<List<MediaItem>>>()

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AUDIO_ATTRIBUTES, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(playCountListener)
        mediaSession = MediaSession.Builder(this, player)
            // 通知栏点击回到 App（P0-5）；MainActivity 本身 UI 波次才接线，此处仅 PendingIntent 引用
            .setSessionActivity(sessionActivityIntent())
            // Media3 1.11：onAddMediaItems 位于 MediaSession.Callback（不再是 service 的方法）
            .setCallback(sessionCallback)
            .build()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(NOTIFICATION_CHANNEL_ID)
                .setChannelName(R.string.media_notification_channel_name)
                .build(),
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /**
     * 把控制端下发的 MediaItem（只带 mediaId 业务标识，ADR-0004 D1）解析成可播放项。
     * M1 仅本地：mediaId → 库内曲目 → MediaStore content URI（`Song.localUri`）+ 通知封面。
     *
     * M2 锚点（ADR-0004 D1 锁定的换源接口）：在线曲目（ONLINE）将来**不在这里拼 URL**，
     * 而是给 ExoPlayer 换 ResolvingDataSource 工厂，在 `open()` 时经
     * `SourceManager.resolveMusicUrl(sourceId, songId)` 解析（可注入 Referer/UA 头、
     * 多源降级重试）；本回调届时对 ONLINE 放行原始 item 即可，session/通知/队列零改动。
     * P2 的下载缓存（SimpleCache + CacheDataSource）也插在同一位置。
     */
    private inner class SessionCallback : MediaSession.Callback {

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> {
            val future = SettableFuture.create<List<MediaItem>>()
            pendingResolutions += future
            future.addListener({ pendingResolutions -= future }, Executor { it.run() })
            serviceScope.launch {
                try {
                    future.set(resolveLocalMediaItems(mediaItems))
                } catch (t: Throwable) {
                    future.setException(t)
                }
            }
            return future
        }
    }

    private suspend fun resolveLocalMediaItems(requested: List<MediaItem>): List<MediaItem> {
        val hasLocalRequest = requested.any { PlaybackMediaId.parse(it.mediaId)?.source == SongSource.LOCAL }
        if (!hasLocalRequest) return emptyList()
        // 一次全量查询按 id 索引（千行级成本可忽略），避免逐条 DAO 往返
        val libraryById = libraryRepository.observeLibrary().first().associateBy { it.id }
        return requested.mapNotNull { item ->
            val decoded = PlaybackMediaId.parse(item.mediaId) ?: return@mapNotNull null
            if (decoded.source != SongSource.LOCAL) return@mapNotNull null // M2 锚点：在线流走 ResolvingDataSource
            val song = libraryById[decoded.songId] ?: return@mapNotNull null
            val playbackUri = song.localUri ?: return@mapNotNull null
            MediaItem.Builder()
                .setMediaId(item.mediaId)
                .setUri(playbackUri)
                .setMediaMetadata(
                    // 控制端已带文字元数据；通知封面在此补（MediaStore 专辑封面 URI）
                    item.mediaMetadata.buildUpon()
                        .setArtworkUri(PlaybackMediaId.albumArtworkUri(song.albumId).toUri())
                        .build(),
                )
                .build()
        }
    }

    /**
     * 最近任务划掉 App（P0-11）：播放中保持（通知仍在、可从通知恢复控制），
     * 空闲/已播完时停服务。与官方后台播放指南样例一致。
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null ||
            !player.playWhenReady ||
            player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED
        ) {
            pauseAllPlayersAndStopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        pendingResolutions.forEach { it.setException(IllegalStateException("PlaybackService 已销毁")) }
        pendingResolutions.clear()
        mediaSession?.run {
            player.removeListener(playCountListener)
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    /**
     * 播放统计回写（ADR-0004 D2：「最近播放/最常播放」数据源）。
     * 判定逻辑抽成纯类 [PlayCountTracker]（JVM 单测），本监听只负责把返回的 id 落库。
     */
    private inner class PlayCountListener : Player.Listener {

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // 切歌：上一首已开播 → 落库（含 REPEAT_ALL 队尾回绕的 AUTO 过渡）
            playCountTracker.onItemStarted(mediaItem?.mediaId)?.let(::recordPlayedAsync)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            // 队列自然播完（REPEAT_OFF 到队尾，无过渡事件）：当前一首落库
            if (playbackState == Player.STATE_ENDED) {
                playCountTracker.onQueueEnded()?.let(::recordPlayedAsync)
            }
        }
    }

    private fun recordPlayedAsync(songId: Long) {
        serviceScope.launch {
            libraryRepository.recordPlayed(songId, atEpochMs = System.currentTimeMillis())
        }
    }

    private fun sessionActivityIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "media_playback"

        private val AUDIO_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
    }
}
