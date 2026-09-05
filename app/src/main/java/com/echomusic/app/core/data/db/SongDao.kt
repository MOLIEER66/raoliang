package com.echomusic.app.core.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

/** 「N 首 · 本地 N · 洛雪 N」按来源计数的查询行（SCREENS §1 统计行）。 */
data class SourceCountRow(
    val source: String,
    val count: Int,
)

/**
 * 曲目表 DAO（BREAKDOWN T2）：查询全部返回 Flow（Room3 coroutines-only），写操作 suspend。
 */
@Dao
interface SongDao {

    // ---- 查询（音乐库三个标签页，SCREENS §1）----

    /** 全部歌曲 */
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun observeAll(): Flow<List<SongEntity>>

    /** 最近播放：只列播过的，新→旧 */
    @Query(
        "SELECT * FROM songs WHERE lastPlayedAtMs IS NOT NULL " +
            "ORDER BY lastPlayedAtMs DESC LIMIT :limit",
    )
    fun observeRecentlyPlayed(limit: Int): Flow<List<SongEntity>>

    /** 最常播放：次数多→少，同次数按最近播放靠前 */
    @Query(
        "SELECT * FROM songs WHERE playCount > 0 " +
            "ORDER BY playCount DESC, lastPlayedAtMs DESC LIMIT :limit",
    )
    fun observeMostPlayed(limit: Int): Flow<List<SongEntity>>

    /** 统计行：按来源分组计数 */
    @Query("SELECT source, COUNT(*) AS count FROM songs GROUP BY source")
    fun observeCountBySource(): Flow<List<SourceCountRow>>

    @Query("SELECT COUNT(*) FROM songs")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT * FROM songs WHERE id = :id")
    fun observeById(id: Long): Flow<SongEntity?>

    // ---- 同步管道用的一次性读取 ----

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getById(id: Long): SongEntity?

    @Query("SELECT * FROM songs")
    suspend fun getAll(): List<SongEntity>

    /** 库内现有 LOCAL 曲目 id 集合（删除检测的差集被减数，ADR-0004 D2） */
    @Query("SELECT id FROM songs WHERE source = 'LOCAL'")
    suspend fun getLocalIds(): List<Long>

    /** 某专辑下的本地曲目（封面提取器用它定位文件路径，ADR-0004 D5） */
    @Query("SELECT * FROM songs WHERE albumId = :albumId AND source = 'LOCAL' LIMIT 1")
    suspend fun getFirstLocalByAlbum(albumId: Long): SongEntity?

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun count(): Int

    // ---- 写入 ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<SongEntity>)

    /**
     * 全字段覆盖写（同步管道的唯一写入口，统计字段由 syncDiff 预先并入行内再写）。
     * 用 REPLACE 而非 @Upsert：BundledSQLiteDriver 的约束异常类型与 @Upsert 生成代码
     * 的 catch（SQLiteConstraintException）不匹配，冲突路径实测抛 SQLException（T2 实测）；
     * 本表无外键/自增，REPLACE（删除+重插）与 UPSERT 语义等价。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(songs: List<SongEntity>)

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM songs")
    suspend fun deleteAll()

    /** 播放计数 +1 并记录最近播放时间（播放层 T5/T6 经 Repository 调用） */
    @Query(
        "UPDATE songs SET playCount = playCount + 1, lastPlayedAtMs = :atEpochMs " +
            "WHERE id = :id",
    )
    suspend fun recordPlayed(id: Long, atEpochMs: Long)
}

/**
 * 专辑缓存 DAO。M1 全量重建（songs 表落库后执行 [rebuild]），不做增量维护——
 * 简单、幂等，规模（千行级）下成本可忽略。
 */
@Dao
interface AlbumDao {

    @Query("SELECT * FROM albums ORDER BY name ASC")
    fun observeAll(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums")
    suspend fun getAll(): List<AlbumEntity>

    @Query("SELECT COUNT(*) FROM albums")
    suspend fun count(): Int

    /** 从 songs 表重建 albums（含封面缓存键；取色缓存字段 M1 留空） */
    @Query("DELETE FROM albums")
    suspend fun clear()

    @Query(
        "INSERT INTO albums (id, name, artist, songCount, artworkKey, seedArgb, glowHue) " +
            "SELECT albumId, MAX(album), MAX(artist), COUNT(*), " +
            "'album:' || albumId, NULL, NULL " +
            "FROM songs WHERE source = 'LOCAL' GROUP BY albumId",
    )
    suspend fun insertFromSongs()

    @Transaction
    suspend fun rebuild() {
        clear()
        insertFromSongs()
    }
}

/** 同步元数据 DAO（lastSyncAt 游标，与 songs 写入同事务） */
@Dao
interface SyncMetaDao {

    @Query("SELECT metaValue FROM sync_meta WHERE metaKey = :key")
    suspend fun getValue(key: String): String?

    @Query("SELECT metaValue FROM sync_meta WHERE metaKey = :key")
    fun observeValue(key: String): Flow<String?>

    /** 全行覆盖写；REPLACE 语义说明见 [SongDao.upsertAll]（@Upsert 冲突路径与 Bundled 驱动不兼容） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: SyncMetaEntity)

    suspend fun getLastSyncAtSec(): Long = getValue(SyncMetaEntity.KEY_LAST_SYNC_AT)?.toLongOrNull() ?: 0L

    suspend fun putLastSyncAtSec(value: Long) {
        put(SyncMetaEntity(SyncMetaEntity.KEY_LAST_SYNC_AT, value.toString()))
    }
}
