package com.echomusic.app.core.data.db

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * T0 冒烟用最小歌曲表。M1 正式表结构（albums / play_history / play_counts 与完整 songs 字段）在 T2 落地。
 * `source` 字段为 D2 决策预留：M1 全为 LOCAL，M2 在线曲目复用本表（ADR-0004 D2/D4）。
 */
@Entity(tableName = "songs")
data class SongEntity(
    // 去重主键 = MediaStore _id（ADR-0004 D2：稳定、系统维护）
    @PrimaryKey val id: Long,
    val title: String,
    val source: String = SOURCE_LOCAL,
) {
    companion object {
        const val SOURCE_LOCAL = "LOCAL"
        const val SOURCE_ONLINE = "ONLINE"
    }
}
