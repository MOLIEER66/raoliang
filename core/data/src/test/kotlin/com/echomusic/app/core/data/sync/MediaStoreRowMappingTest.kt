package com.echomusic.app.core.data.sync

import com.echomusic.app.core.model.SongSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * BREAKDOWN §3.1「模型映射」：MediaStore 行 → Song 字段映射（纯 JVM，无 Cursor）。
 * 归一规则：空标题/歌手/专辑 → 未知；负时长 → 0；year=0 与空白 MIME → null。
 */
class MediaStoreRowMappingTest {

    private fun row(
        title: String? = "晴天",
        artist: String? = "周杰伦",
        album: String? = "叶惠美",
        year: Int? = 2003,
        mimeType: String? = "audio/flac",
        durationMs: Long = 269_000L,
    ) = MediaStoreAudioRow(
        id = 42L,
        title = title,
        artist = artist,
        album = album,
        albumId = 7L,
        durationMs = durationMs,
        sizeBytes = 31_457_280L,
        path = "/storage/emulated/0/Music/晴天.flac",
        dateModifiedSec = 1_724_000_000L,
        year = year,
        mimeType = mimeType,
    )

    @Test
    fun fullRowMapsEveryField() {
        val song = row().toSong()

        assertEquals(42L, song.id)
        assertEquals(SongSource.LOCAL, song.source)
        assertEquals("晴天", song.title)
        assertEquals("周杰伦", song.artist)
        assertEquals("叶惠美", song.album)
        assertEquals(7L, song.albumId)
        assertEquals(269_000L, song.durationMs)
        assertEquals(31_457_280L, song.sizeBytes)
        assertEquals("/storage/emulated/0/Music/晴天.flac", song.path)
        assertEquals(1_724_000_000L, song.dateModifiedSec)
        assertEquals(2003, song.year)
        assertEquals("audio/flac", song.mimeType)
        // 本地曲目统计字段恒零值，M2 预留键恒空
        assertNull(song.lastPlayedAtMs)
        assertEquals(0, song.playCount)
        assertNull(song.sourceSongId)
        // 播放层要用的派生属性
        assertEquals("content://media/external/audio/media/42", song.localUri)
        assertEquals("album:7", song.coverCacheKey)
    }

    @Test
    fun blankValuesNormalizeToUnknown() {
        val song = row(title = "  ", artist = null, album = "").toSong()

        assertEquals(UNKNOWN_TITLE, song.title)
        assertEquals(UNKNOWN_ARTIST, song.artist)
        assertEquals(UNKNOWN_ALBUM, song.album)
    }

    @Test
    fun sentinelValuesNormalizeToNullAndZero() {
        val song = row(year = 0, mimeType = "", durationMs = -5).toSong()

        assertNull(song.year)
        assertNull(song.mimeType)
        assertEquals(0L, song.durationMs)
    }

    @Test
    fun songKeyIsTitleArtistDuration() {
        assertEquals("晴天|周杰伦|269000", row().toSong().songKey)
    }
}
