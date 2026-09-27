package com.echomusic.app.core.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.echomusic.app.core.data.db.AlbumEntity
import com.echomusic.app.core.data.db.EchoDatabase
import com.echomusic.app.core.data.db.EchoDatabase_Impl
import com.echomusic.app.core.data.db.SongEntity
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
 * BREAKDOWN T2 / §3.1：Room3 的 JVM target + `BundledSQLiteDriver` 内存库——
 * 纯 JVM 上跑 DAO，不需要模拟器/Robolectric。
 * 覆盖矩阵要求：CRUD、`play_counts` 聚合排序、Flow 发射。
 *
 * 注意：类路径上同时有 room3 的 android/jvm 变体，但两者都提供工厂 lambda 版
 * inMemoryDatabaseBuilder，此处显式传工厂，规避重载解析歧义。
 * 坑（T0 实测）：必须显式依赖 sqlite-bundled-jvm 构件——android 变体的驱动走
 * System.loadLibrary，纯 JVM 上必然 UnsatisfiedLinkError。
 */
class SongDaoTest {

    private lateinit var db: EchoDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<EchoDatabase> { EchoDatabase_Impl() }
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun song(
        id: Long,
        title: String = "曲目$id",
        albumId: Long = 100,
        lastPlayedAtMs: Long? = null,
        playCount: Int = 0,
    ) = SongEntity(
        id = id,
        title = title,
        artist = "歌手$id",
        album = "专辑$albumId",
        albumId = albumId,
        durationMs = 200_000L + id,
        sizeBytes = 1_000_000L,
        path = "/music/$id.flac",
        dateModifiedSec = 1_700_000_000L + id,
        lastPlayedAtMs = lastPlayedAtMs,
        playCount = playCount,
    )

    // ---- CRUD ----

    @Test
    fun insertThenQueryRoundtrip() = runBlocking {
        val dao = db.songDao()
        dao.insertAll(listOf(song(1, "阳关三叠"), song(2, "广陵散")))

        val all = dao.observeAll().first()
        assertEquals(2, all.size)
        assertEquals("阳关三叠", all.first { it.id == 1L }.title)
        // source 字段默认 LOCAL（D2 决策，M2 在线曲目复用该列）
        assertEquals(SongEntity.SOURCE_LOCAL, all[0].source)
        assertEquals(2, dao.count())
    }

    @Test
    fun replaceConflictKeepsSingleRow() = runBlocking {
        val dao = db.songDao()
        dao.insertAll(listOf(song(1, "初版")))
        dao.insertAll(listOf(song(1, "修订版")))

        assertEquals(1, dao.count())
        assertEquals("修订版", dao.observeAll().first().single().title)
    }

    @Test
    fun deleteByIdsRemovesOnlyTargetRows() = runBlocking {
        val dao = db.songDao()
        dao.insertAll(listOf(song(1), song(2), song(3)))

        dao.deleteByIds(listOf(1L, 3L))

        val remain = dao.observeAll().first()
        assertEquals(listOf(2L), remain.map { it.id })
    }

    // ---- 播放统计：recordPlayed + 聚合排序（SCREENS §1 三个标签页的数据面）----

    @Test
    fun recordPlayedIncrementsCountAndTimestamp() = runBlocking {
        val dao = db.songDao()
        dao.insertAll(listOf(song(1)))

        dao.recordPlayed(1L, atEpochMs = 1_724_000_000_000L)
        dao.recordPlayed(1L, atEpochMs = 1_724_000_100_000L)

        val row = dao.getById(1L)!!
        assertEquals(2, row.playCount)
        assertEquals(1_724_000_100_000L, row.lastPlayedAtMs)
    }

    @Test
    fun recentlyPlayedReturnsOnlyPlayedNewestFirst() = runBlocking {
        val dao = db.songDao()
        dao.insertAll(
            listOf(
                song(1, lastPlayedAtMs = 100L),
                song(2, lastPlayedAtMs = 300L),
                song(3, lastPlayedAtMs = 200L),
                song(4), // 从未播放，不应出现
            ),
        )

        val rows = dao.observeRecentlyPlayed(limit = 10).first()
        assertEquals(listOf(2L, 3L, 1L), rows.map { it.id })
    }

    @Test
    fun mostPlayedSortsByCountThenRecency() = runBlocking {
        val dao = db.songDao()
        dao.insertAll(
            listOf(
                song(1, playCount = 5, lastPlayedAtMs = 100L),
                song(2, playCount = 9, lastPlayedAtMs = 100L),
                // 同次数按最近播放靠前
                song(3, playCount = 5, lastPlayedAtMs = 400L),
                song(4, playCount = 0), // 零计数不进最常播放
            ),
        )

        val rows = dao.observeMostPlayed(limit = 10).first()
        assertEquals(listOf(2L, 3L, 1L), rows.map { it.id })
    }

    // ---- Flow 发射：写入触发观察者重新发射 ----

    @Test
    fun observeAllEmitsOnWrite() = runBlocking {
        val dao = db.songDao()
        assertEquals(0, dao.observeTotalCount().first())

        dao.insertAll(listOf(song(1)))
        assertEquals(1, dao.observeTotalCount().first())

        dao.deleteByIds(listOf(1L))
        assertEquals(0, dao.observeTotalCount().first())
    }

    @Test
    fun countBySourceGroupsForStatsRow() = runBlocking {
        val dao = db.songDao()
        dao.insertAll(listOf(song(1), song(2)))
        dao.insertAll(
            listOf(song(3).copy(source = SongEntity.SOURCE_ONLINE, id = 3)),
        )

        val rows = dao.observeCountBySource().first().associate { it.source to it.count }
        assertEquals(2, rows[SongEntity.SOURCE_LOCAL])
        assertEquals(1, rows[SongEntity.SOURCE_ONLINE])
    }

    // ---- 专辑重建 / 同步游标 ----

    @Test
    fun albumRebuildAggregatesFromSongs() = runBlocking {
        val dao = db.songDao()
        dao.insertAll(
            listOf(song(1, albumId = 100), song(2, albumId = 100), song(3, albumId = 200)),
        )

        db.albumDao().rebuild()

        val albums = db.albumDao().observeAll().first()
        assertEquals(2, albums.size)
        val album100 = albums.first { it.id == 100L }
        assertEquals(2, album100.songCount)
        assertEquals(AlbumEntity.artworkKeyOf(100L), album100.artworkKey)
        // 取色缓存字段 M1 不写入（预留位）
        assertNull(album100.seedArgb)
        assertNull(album100.glowHue)
    }

    @Test
    fun syncMetaRoundtripDefaultsToZero() = runBlocking {
        val dao = db.syncMetaDao()
        assertEquals(0L, dao.getLastSyncAtSec())

        dao.putLastSyncAtSec(1_724_000_000L)
        assertEquals(1_724_000_000L, dao.getLastSyncAtSec())
        // upsert 覆盖
        dao.putLastSyncAtSec(1_724_000_001L)
        assertEquals(1_724_000_001L, dao.getLastSyncAtSec())
    }

    @Test
    fun getFirstLocalByAlbumIgnoresOnlineRows() = runBlocking {
        val dao = db.songDao()
        dao.insertAll(listOf(song(1, albumId = 100)))
        dao.insertAll(
            listOf(
                song(2, albumId = 100).copy(
                    id = 2,
                    source = SongEntity.SOURCE_ONLINE,
                    path = null,
                ),
            ),
        )

        assertEquals(1L, dao.getFirstLocalByAlbum(100L)?.id)
        assertNull(dao.getFirstLocalByAlbum(999L))
        assertTrue(dao.getLocalIds().containsAll(listOf(1L)))
    }
}
