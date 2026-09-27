package com.echomusic.app.core.data.repository

import androidx.room3.withWriteTransaction
import com.echomusic.app.core.data.db.AlbumDao
import com.echomusic.app.core.data.db.EchoDatabase
import com.echomusic.app.core.data.db.SongDao
import com.echomusic.app.core.data.db.SyncMetaDao
import com.echomusic.app.core.data.db.songSourceFromKey
import com.echomusic.app.core.data.db.toEntity
import com.echomusic.app.core.data.db.toModel
import com.echomusic.app.core.data.db.toModels
import com.echomusic.app.core.data.sync.MediaStoreSource
import com.echomusic.app.core.data.sync.SyncState
import com.echomusic.app.core.data.sync.syncDiff
import com.echomusic.app.core.data.sync.toSong
import com.echomusic.app.core.model.LibraryStats
import com.echomusic.app.core.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 音乐库对外的唯一门面（BREAKDOWN T3 同步管道 + SCREENS §1 三个标签页的数据面）。
 *
 * 全部观察 API 返回冷 Flow（Room3 coroutines-only），同步进度经 [syncState] 热流广播。
 * 播放层（T5/T6 波次）经 [recordPlayed] 回写统计，驱动「最近/最常播放」两个标签页。
 */
interface LibraryRepository {

    /** 同步进度（StateFlow，UI 波次的扫描态直接订阅） */
    val syncState: StateFlow<SyncState>

    /**
     * 执行一次库同步：首次为全量（lastSyncAt=0），之后按 DATE_MODIFIED 增量。
     * 可重入安全（并发调用合并为一次）；失败经 [syncState] 报 FAILED，不抛给调用方。
     */
    suspend fun syncLibrary()

    /** 全部歌曲（按标题排序） */
    fun observeLibrary(): Flow<List<Song>>

    /** 最近播放（新→旧，只含播过的；SCREENS §1 标签页二） */
    fun observeRecentlyPlayed(limit: Int = DEFAULT_TAB_LIMIT): Flow<List<Song>>

    /** 最常播放（次数多→少，同次数按最近；SCREENS §1 标签页三） */
    fun observeMostPlayed(limit: Int = DEFAULT_TAB_LIMIT): Flow<List<Song>>

    /** 统计行（「N 首 · 本地 N · 洛雪 N」） */
    fun observeStats(): Flow<LibraryStats>

    /** 单曲观察（迷你条/播放页切歌时用） */
    fun observeSong(id: Long): Flow<Song?>

    /** 播放回写：计数 +1、最近时间戳更新（播放层经此调用，勿直接写 DAO） */
    suspend fun recordPlayed(songId: Long, atEpochMs: Long)

    companion object {
        const val DEFAULT_TAB_LIMIT = 500
    }
}

/**
 * [LibraryRepository] 的 Room + MediaStore 实现。
 *
 * 同步管道（syncLibrary）：
 *   1. 读 lastSyncAt 游标（sync_meta 表）；
 *   2. `MediaStoreSource.queryUpdatedSince` 拿增改批次 + `queryAllIds` 拿全量 id 清单；
 *   3. `syncDiff`（纯函数）算出增/改/删；
 *   4. 单个写事务内：分批 REPLACE 写入（批次间发进度）→ 删除差集 → 重建 albums →
 *      推进 lastSyncAt 游标。「歌曲 + 游标」原子一致，任何一步失败整体回滚。
 *
 * @param nowSec 扫描起始时间源（可注入以便 JVM 测试）
 */
class LibraryRepositoryImpl(
    private val db: EchoDatabase,
    private val songDao: SongDao,
    private val albumDao: AlbumDao,
    private val syncMetaDao: SyncMetaDao,
    private val mediaStoreSource: MediaStoreSource,
    private val nowSec: () -> Long = { System.currentTimeMillis() / 1000 },
    private val chunkSize: Int = UPSERT_CHUNK,
) : LibraryRepository {

    private val syncMutex = Mutex()
    private val _syncState = MutableStateFlow(SyncState.IDLE)
    override val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    override suspend fun syncLibrary() {
        syncMutex.withLock {
            try {
                _syncState.update { SyncState(phase = SyncState.Phase.SCANNING) }

                val scanStartedAtSec = nowSec()
                val lastSyncAtSec = syncMetaDao.getLastSyncAtSec()
                val incomingRows = mediaStoreSource.queryUpdatedSince(lastSyncAtSec)
                val seenIds = mediaStoreSource.queryAllIds().toSet()

                _syncState.update {
                    it.copy(phase = SyncState.Phase.SAVING, found = incomingRows.size)
                }

                val diff = syncDiff(
                    current = songDao.getAll().toModels(),
                    incoming = incomingRows.map { it.toSong() },
                    seenIds = seenIds,
                )

                db.withWriteTransaction {
                    diff.toUpsert.chunked(chunkSize).forEach { chunk ->
                        songDao.upsertAll(chunk.map { it.toEntity() })
                        _syncState.update { s ->
                            s.copy(upserted = s.upserted + chunk.size)
                        }
                    }
                    if (diff.toDeleteIds.isNotEmpty()) {
                        songDao.deleteByIds(diff.toDeleteIds)
                    }
                    albumDao.rebuild()
                    syncMetaDao.putLastSyncAtSec(scanStartedAtSec)
                }

                _syncState.update {
                    SyncState(
                        phase = SyncState.Phase.DONE,
                        found = incomingRows.size,
                        upserted = diff.toUpsert.size,
                        deleted = diff.toDeleteIds.size,
                    )
                }
            } catch (t: Throwable) {
                _syncState.update {
                    SyncState(
                        phase = SyncState.Phase.FAILED,
                        found = it.found,
                        upserted = it.upserted,
                        error = t.message ?: t.javaClass.simpleName,
                    )
                }
            }
        }
    }

    override fun observeLibrary(): Flow<List<Song>> =
        songDao.observeAll().map { it.toModels() }

    override fun observeRecentlyPlayed(limit: Int): Flow<List<Song>> =
        songDao.observeRecentlyPlayed(limit).map { it.toModels() }

    override fun observeMostPlayed(limit: Int): Flow<List<Song>> =
        songDao.observeMostPlayed(limit).map { it.toModels() }

    override fun observeStats(): Flow<LibraryStats> = combine(
        songDao.observeTotalCount(),
        songDao.observeCountBySource(),
    ) { total, bySource ->
        LibraryStats(
            total = total,
            bySource = bySource.associate { row ->
                songSourceFromKey(row.source) to row.count
            },
        )
    }

    override fun observeSong(id: Long): Flow<Song?> =
        songDao.observeById(id).map { it?.toModel() }

    override suspend fun recordPlayed(songId: Long, atEpochMs: Long) {
        songDao.recordPlayed(songId, atEpochMs)
    }

    private companion object {
        const val UPSERT_CHUNK = 200
    }
}
