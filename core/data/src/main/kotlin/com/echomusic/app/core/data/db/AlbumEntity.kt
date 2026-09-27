package com.echomusic.app.core.data.db

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * 专辑缓存表（BREAKDOWN T2：封面缓存键）。
 * 同步管道在 songs 落库后整体重建（见 [AlbumDao.rebuild]），M1 不做增量维护。
 *
 * @property artworkKey 封面缓存键（Coil memory-cache key），与 ADR-0004 D5 的
 *                      `album:{albumId}` 口径一致
 * @property seedArgb   预留：取色结果缓存（seed 色 ARGB）——BREAKDOWN §4「取色缓存按
 *                      albumId 落 Room（预留字段已留）」，M1 不写入
 * @property glowHue    预留：取色结果缓存（glow 色相），M1 不写入
 */
@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val artist: String,
    val songCount: Int,
    val artworkKey: String,
    val seedArgb: Long? = null,
    val glowHue: Float? = null,
) {
    companion object {
        fun artworkKeyOf(albumId: Long): String = "album:$albumId"
    }
}
