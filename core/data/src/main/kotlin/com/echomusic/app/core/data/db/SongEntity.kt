package com.echomusic.app.core.data.db

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * 曲目缓存表（ADR-0004 D2/D4：MediaStore 查询 + Room 缓存 + 增量同步；`source` 字段 M2 复用）。
 *
 * 播放统计（[lastPlayedAtMs] / [playCount]）以冗余字段并入本表，而不是独立 play_history /
 * play_counts 表——这是 BREAKDOWN T2 允许的「二选一」：M1 的「最近播放 / 最常播放」两个标签页
 * 只需要 per-song 的最近时间与计数，冗余字段让排序查询零 join、且增改同步时由 syncDiff
 * 原地保留统计（见 core.data.sync.SyncDiff）。历史流水（逐次播放记录）若 M3+ 需要，届时以
 * 迁移追加 play_history 表，不影响本表。
 *
 * @property id 去重主键 = MediaStore `_id`（ADR-0004 D2：稳定、系统维护）。
 *              M2 在线曲目用负数合成 id，不再迁移主键结构。
 */
@Entity(
    tableName = "songs",
    indices = [
        Index("albumId"),        // albums 重建 / 封面键关联
        Index("lastPlayedAtMs"), // 最近播放排序
        Index("playCount"),      // 最常播放排序
        Index("source"),         // 统计行按来源计数（SCREENS §1）
    ],
)
data class SongEntity(
    @PrimaryKey val id: Long,
    val source: String = SOURCE_LOCAL,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val sizeBytes: Long,
    val path: String?,
    val dateModifiedSec: Long,
    val year: Int? = null,
    val mimeType: String? = null,
    /** M2 预留：音源侧曲目键 "{sourceId}:{songId}"，本地曲目恒为 null */
    val onlineKey: String? = null,
    val lastPlayedAtMs: Long? = null,
    val playCount: Int = 0,
) {
    companion object {
        const val SOURCE_LOCAL = "LOCAL"
        const val SOURCE_ONLINE = "ONLINE"
    }
}
