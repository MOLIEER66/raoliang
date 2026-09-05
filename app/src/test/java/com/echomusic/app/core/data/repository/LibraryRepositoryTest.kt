package com.echomusic.app.core.data.repository

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.echomusic.app.core.data.db.EchoDatabase
import com.echomusic.app.core.data.db.EchoDatabase_Impl
import com.echomusic.app.core.data.sync.MediaStoreAudioRow
import com.echomusic.app.core.data.sync.MediaStoreSource
import com.echomusic.app.core.data.sync.SyncState
import com.echomusic.app.core.model.LibraryStats
import com.echomusic.app.core.model.SongSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 同步管道端到端（纯 JVM）：假 [MediaStoreSource] + 内存 Room（BundledSQLiteDriver）。
 * 覆盖：首次全量 → 增量（增/改/删）→ 幂等重扫 → 游标推进 → 进度可观测 → 统计保留。
 *
 * 时间轴约定：MediaStore DATE_MODIFIED 是秒级时间戳，fixture 用 100–600 的「小纪元」，
 * nowSec（扫描时刻）始终领先于已写入行的修改时间——与真实世界一致。
 */
class LibraryRepositoryTest {

    private class FakeMediaStoreSource : MediaStoreSource {
        var rows: List<MediaStoreAudioRow> = emptyList()
        var lastQuerySince: Long? = null
        var failNextQuery = false

        override suspend fun queryUpdatedSince(lastSyncAtSec: Long): List<MediaStoreAudioRow> {
            lastQuerySince = lastSyncAtSec
            if (failNextQuery) {
                failNextQuery = false
                throw IllegalStateException("模拟：媒体进程崩溃")
            }
            return rows.filter { it.dateModifiedSec >= lastSyncAtSec }
        }

        override suspend fun queryAllIds(): List<Long> = rows.map { it.id }
    }

    private lateinit var db: EchoDatabase
    private lateinit var source: FakeMediaStoreSource
    private lateinit var repo: LibraryRepositoryImpl
    private var nowSec = 400L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<EchoDatabase> { EchoDatabase_Impl() }
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        source = FakeMediaStoreSource()
        repo = LibraryRepositoryImpl(
            db = db,
            songDao = db.songDao(),
            albumDao = db.albumDao(),
            syncMetaDao = db.syncMetaDao(),
            mediaStoreSource = source,
            nowSec = { nowSec },
            chunkSize = 2, // 小批次：分批写入 + 进度递增路径可测
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun row(
        id: Long,
        dateModifiedSec: Long,
        title: String = "曲目$id",
        albumId: Long = 100,
    ) = MediaStoreAudioRow(
        id = id,
        title = title,
        artist = "歌手$id",
        album = "专辑$albumId",
        albumId = albumId,
        durationMs = 200_000L,
        sizeBytes = 1_000_000L,
        path = "/music/$id.flac",
        dateModifiedSec = dateModifiedSec,
    )

    @Test
    fun firstSyncLoadsEverythingAndAdvancesCursor() = runBlocking {
        source.rows = listOf(
            row(1, 100, albumId = 100),
            row(2, 200, albumId = 100),
            row(3, 300, albumId = 200),
        )

        repo.syncLibrary()

        val library = repo.observeLibrary().first()
        assertEquals(3, library.size)
        assertEquals(3, repo.syncState.value.found)
        assertEquals(SyncState.Phase.DONE, repo.syncState.value.phase)
        // 游标推进到扫描时刻（「下一次增量从游标起查」由 incrementalSync 用例覆盖）
        assertEquals(nowSec, db.syncMetaDao().getLastSyncAtSec())
        // 专辑表同步重建（两个专辑）
        assertEquals(2, db.albumDao().count())
    }

    @Test
    fun incrementalSyncHandlesAddModifyDelete() = runBlocking {
        source.rows = listOf(row(1, 100), row(2, 100))
        repo.syncLibrary()
        nowSec = 500L

        // 增：3（新）；改：1（时间戳变大）；删：2 从 MediaStore 消失
        source.rows = listOf(row(1, 450, title = "改名"), row(3, 460))
        repo.syncLibrary()

        val library = repo.observeLibrary().first().associateBy { it.id }
        assertEquals(setOf(1L, 3L), library.keys)
        assertEquals("改名", library[1L]!!.title)
        assertEquals(1, repo.syncState.value.deleted)
        assertEquals(2, repo.syncState.value.upserted)
        assertEquals(SyncState.Phase.DONE, repo.syncState.value.phase)
    }

    @Test
    fun rescanIsIdempotentAndKeepsPlayStats() = runBlocking {
        source.rows = listOf(row(1, 100))
        repo.syncLibrary()

        repo.recordPlayed(1L, atEpochMs = 5_555L)
        repo.recordPlayed(1L, atEpochMs = 6_666L)

        // MediaStore 无变化，重扫：零写入、零删除，统计保留
        nowSec = 500L
        repo.syncLibrary()

        val song = repo.observeLibrary().first().single()
        assertEquals(0, repo.syncState.value.upserted)
        assertEquals(2, song.playCount)
        assertEquals(6_666L, song.lastPlayedAtMs)

        // 最近/最常播放标签页（SCREENS §1）有数据
        assertEquals(1, repo.observeRecentlyPlayed().first().size)
        assertEquals(1, repo.observeMostPlayed().first().size)
    }

    @Test
    fun syncFailureReportsFailedState() = runBlocking {
        source.rows = listOf(row(1, 100))
        repo.syncLibrary()

        nowSec = 500L
        source.failNextQuery = true
        repo.syncLibrary()

        assertEquals(SyncState.Phase.FAILED, repo.syncState.value.phase)
        assertTrue(repo.syncState.value.error != null)
        // 失败不落半截数据
        assertEquals(1, repo.observeLibrary().first().size)
    }

    @Test
    fun statsFlowCountsBySource() = runBlocking {
        source.rows = listOf(row(1, 100), row(2, 200))
        repo.syncLibrary()

        val stats = repo.observeStats().first()
        assertEquals(
            LibraryStats(total = 2, bySource = mapOf(SongSource.LOCAL to 2)),
            stats,
        )
        assertEquals(2, stats.localCount)
        assertEquals(0, stats.onlineCount)
    }

    @Test
    fun observeSongEmitsNullForMissingId() = runBlocking {
        assertNull(repo.observeSong(123L).first())
    }
}
