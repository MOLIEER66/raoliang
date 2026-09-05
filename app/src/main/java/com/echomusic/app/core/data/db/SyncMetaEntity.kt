package com.echomusic.app.core.data.db

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * 同步元数据表（ADR-0004 D2：记录每卷 lastSyncAt）。
 * 与 songs 落库同事务写入，保证「歌曲 + 增量游标」原子一致。
 *
 * 列名用 meta_key/meta_value 而非 key/value，避免 SQL 关键字转义。
 */
@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val metaKey: String,
    val metaValue: String,
) {
    companion object {
        /** 增量同步游标：MediaStore DATE_MODIFIED 秒级时间戳（单卷，M1 只有一卷） */
        const val KEY_LAST_SYNC_AT = "last_sync_at_sec"
    }
}
