package com.echomusic.app.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.echomusic.app.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Song → MediaItem 映射（ADR-0004 D1：MediaItem 只承载业务标识 + 元数据，URI 由服务端解析）。
 * 只用字符串字段，纯 JVM 可跑。
 */
@OptIn(UnstableApi::class)
class QueueMediaItemFactoryTest {

    @Test
    fun localSongMapsMediaIdAndNotificationMetadata() {
        val item = QueueMediaItemFactory.create(song(7L, sourceSongId = null))

        assertEquals("local:7", item.mediaId)
        assertEquals("曲目7", item.mediaMetadata.title)
        assertEquals("歌手7", item.mediaMetadata.artist)
        assertEquals("专辑7", item.mediaMetadata.albumTitle)
        // 通知栏封面 URI 由服务端按此 helper 注入（artworkUri 为 Uri 类型，属 Android 侧）
        assertEquals("content://media/external/audio/albumart/7", PlaybackMediaId.albumArtworkUri(7L))
        // 关键契约：入队 item 不带播放 URI，解析权在 PlaybackService 的 session callback
        assertEquals(null, item.localConfiguration)
    }

    @Test
    fun onlineSongKeepsSourceSideMediaId() {
        val item = QueueMediaItemFactory.create(song(-99L, sourceSongId = "lxpublic:abc"))

        assertEquals("lxpublic:abc", item.mediaId)
    }

    @Test
    fun createAllPreservesOrder() {
        val items = QueueMediaItemFactory.createAll(listOf(song(1, null), song(2, null), song(3, null)))

        assertEquals(listOf("local:1", "local:2", "local:3"), items.map { it.mediaId })
    }

    private fun song(id: Long, sourceSongId: String?) = Song(
        id = id,
        title = "曲目$id",
        artist = "歌手$id",
        album = "专辑$id",
        albumId = id,
        durationMs = 200_000L,
        sizeBytes = 1_000_000L,
        path = "/music/$id.flac",
        dateModifiedSec = 0L,
        sourceSongId = sourceSongId,
    )
}
