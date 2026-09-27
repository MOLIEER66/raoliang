package com.echomusic.app.core.data.sync

import com.echomusic.app.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BREAKDOWN T3 / §3.1：syncDiff 纯 JVM 单测——
 * 新增/修改/删除/重复 `_id`/时间戳相等不误判（五类验收用例）+ 统计字段保留。
 */
class SyncDiffTest {

    private fun song(
        id: Long,
        dateModifiedSec: Long = 1_700_000_000L,
        title: String = "曲目$id",
        lastPlayedAtMs: Long? = null,
        playCount: Int = 0,
    ) = Song(
        id = id,
        title = title,
        artist = "歌手$id",
        album = "专辑$id",
        albumId = id * 10,
        durationMs = 200_000L,
        sizeBytes = 1_000_000L,
        path = "/music/$id.flac",
        dateModifiedSec = dateModifiedSec,
        lastPlayedAtMs = lastPlayedAtMs,
        playCount = playCount,
    )

    @Test
    fun firstSyncTreatsEverythingAsUpsert() {
        val result = syncDiff(current = emptyList(), incoming = listOf(song(1), song(2)), seenIds = setOf(1L, 2L))

        assertEquals(listOf(1L, 2L), result.toUpsert.map { it.id }.sorted())
        assertTrue(result.toDeleteIds.isEmpty())
        assertTrue(result.hasChanges)
    }

    @Test
    fun modifiedRowIsUpsertedNewerTimestampWins() {
        val current = listOf(song(1, dateModifiedSec = 100, title = "旧标题"))
        val incoming = listOf(song(1, dateModifiedSec = 200, title = "新标题"))

        val result = syncDiff(current, incoming, seenIds = setOf(1L))

        assertEquals(listOf(1L), result.toUpsert.map { it.id })
        assertEquals("新标题", result.toUpsert.single().title)
    }

    @Test
    fun equalTimestampIsNotMisjudged() {
        // 时间戳相等（重复同步/边界重取）→ 不写，幂等
        val current = listOf(song(1, dateModifiedSec = 100, playCount = 3))
        val incoming = listOf(song(1, dateModifiedSec = 100, title = "同时间戳"))

        val result = syncDiff(current, incoming, seenIds = setOf(1L))

        assertTrue(result.toUpsert.isEmpty())
        assertTrue(result.toDeleteIds.isEmpty())
    }

    @Test
    fun olderTimestampIsIgnored() {
        val current = listOf(song(1, dateModifiedSec = 200))
        val incoming = listOf(song(1, dateModifiedSec = 100))

        val result = syncDiff(current, incoming, seenIds = setOf(1L))

        assertTrue(result.toUpsert.isEmpty())
    }

    @Test
    fun deletedRowIsDiffOfSeenIds() {
        // 本轮 MediaStore 只剩 id=2；现库 LOCAL 的 1、3 都该删
        val current = listOf(song(1), song(2), song(3))
        val incoming = listOf(song(2))

        val result = syncDiff(current, incoming, seenIds = setOf(2L))

        assertTrue(result.toUpsert.isEmpty())
        assertEquals(setOf(1L, 3L), result.toDeleteIds.toSet())
    }

    @Test
    fun onlineRowsNeverDeleted() {
        // M2 预留：ONLINE 行不受 MediaStore 差集影响
        val online = song(9).copy(source = com.echomusic.app.core.model.SongSource.ONLINE)
        val current = listOf(song(1), online)
        val incoming = emptyList<Song>()

        val result = syncDiff(current, incoming, seenIds = emptySet())

        assertEquals(listOf(1L), result.toDeleteIds)
    }

    @Test
    fun duplicateIncomingIdsKeepNewest() {
        val incoming = listOf(
            song(1, dateModifiedSec = 100, title = "旧"),
            song(1, dateModifiedSec = 300, title = "新"),
            song(1, dateModifiedSec = 200, title = "中"),
        )

        val result = syncDiff(current = emptyList(), incoming, seenIds = setOf(1L))

        assertEquals(1, result.toUpsert.size)
        assertEquals("新", result.toUpsert.single().title)
        assertEquals(300L, result.toUpsert.single().dateModifiedSec)
    }

    @Test
    fun upsertPreservesPlayStats() {
        // 重新扫描不丢「最近/最常播放」（ADR-0004 D2 的数据面理由）
        val current = listOf(song(1, dateModifiedSec = 100, lastPlayedAtMs = 999L, playCount = 7))
        val incoming = listOf(song(1, dateModifiedSec = 200))

        val result = syncDiff(current, incoming, seenIds = setOf(1L))

        val upserted = result.toUpsert.single()
        assertEquals(999L, upserted.lastPlayedAtMs)
        assertEquals(7, upserted.playCount)
    }

    @Test
    fun noChangesYieldsEmptyDiff() {
        val current = listOf(song(1), song(2))
        val incoming = listOf(song(1), song(2))

        val result = syncDiff(current, incoming, seenIds = setOf(1L, 2L))

        assertTrue(!result.hasChanges)
        assertTrue(result.toUpsert.isEmpty())
        assertTrue(result.toDeleteIds.isEmpty())
    }
}
