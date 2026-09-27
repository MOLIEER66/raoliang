package com.echomusic.app.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import com.echomusic.app.core.model.Song

/**
 * Song → 队列 MediaItem（ADR-0004 D1：MediaItem 只承载「业务标识 + 元数据」）。
 *
 * 真实播放 URI 不在此处拼接——由 `PlaybackService` 的 session callback 按库内曲目解析
 * MediaStore content URI，并在那里补通知栏封面（artworkUri 是 Uri 类型，属 Android 侧，
 * helper 见 [PlaybackMediaId.albumArtworkUri]）；M2 在线流将来在 DataSource 层换
 * ResolvingDataSource（换源唯一改动点）。
 * 这里只用 mediaId 与字符串元数据字段，JVM 单测无 Android 运行时依赖。
 */
@OptIn(UnstableApi::class)
object QueueMediaItemFactory {

    fun create(song: Song): MediaItem = MediaItem.Builder()
        .setMediaId(PlaybackMediaId.of(song))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .build(),
        )
        .build()

    fun createAll(songs: List<Song>): List<MediaItem> = songs.map(::create)
}
