package com.echomusic.app.core.playback

import com.echomusic.app.core.model.SongSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** mediaId 会话契约（ADR-0004 D1："{sourceId}:{songId}"）编解码的纯 JVM 用例。 */
class PlaybackMediaIdTest {

    @Test
    fun encodeLocalProducesSourcePrefixedId() {
        assertEquals("local:42", PlaybackMediaId.encodeLocal(42L))
    }

    @Test
    fun ofPrefersSourceSongIdForOnlineSongs() {
        val local = song(id = 7L, sourceSongId = null)
        assertEquals("local:7", PlaybackMediaId.of(local))

        val online = song(id = -99L, sourceSongId = "lxpublic:abc123")
        assertEquals("lxpublic:abc123", PlaybackMediaId.of(online))
    }

    @Test
    fun parseDecodesLocalId() {
        val decoded = PlaybackMediaId.parse("local:123")
        assertEquals(SongSource.LOCAL, decoded!!.source)
        assertEquals(123L, decoded.songId)
    }

    @Test
    fun parseTreatsUnknownPrefixAsOnlineSource() {
        val decoded = PlaybackMediaId.parse("lxpublic:88")
        assertEquals(SongSource.ONLINE, decoded!!.source)
        assertEquals(88L, decoded.songId)
    }

    @Test
    fun parseRejectsMalformedIds() {
        assertNull(PlaybackMediaId.parse(null))
        assertNull(PlaybackMediaId.parse(""))
        assertNull(PlaybackMediaId.parse("noseparator"))
        assertNull(PlaybackMediaId.parse(":123"))
        assertNull(PlaybackMediaId.parse("local:"))
        assertNull(PlaybackMediaId.parse("local:notanumber"))
        assertNull(PlaybackMediaId.parse("local:12.5"))
    }

    private fun song(id: Long, sourceSongId: String?) = com.echomusic.app.core.model.Song(
        id = id,
        title = "曲目$id",
        artist = "歌手",
        album = "专辑",
        albumId = 1L,
        durationMs = 100_000L,
        sizeBytes = 1L,
        path = "/music/$id.flac",
        dateModifiedSec = 0L,
        sourceSongId = sourceSongId,
    )
}
