package com.echomusic.app.core.data.db

import androidx.room3.Database
import androidx.room3.RoomDatabase

/**
 * 绕梁数据库 v1（T2 正式表结构，取代 T0 冒烟版）。
 *
 * 表：songs（曲目缓存 + 播放统计冗余字段）、albums（封面缓存键 + 取色缓存预留位）、
 * sync_meta（lastSyncAt 增量游标，与 songs 同事务写入）。ADR-0004 D2/D4。
 *
 * Schema JSON 导出目录 app/schemas（build.gradle.kts 的 KSP room.schemaLocation 配置），
 * 首个 1.json 已提交；M3 歌单表等演进走 Room3 migration，勿再破坏性重建。
 */
@Database(
    entities = [SongEntity::class, AlbumEntity::class, SyncMetaEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class EchoDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao

    abstract fun albumDao(): AlbumDao

    abstract fun syncMetaDao(): SyncMetaDao

    companion object {
        const val NAME = "raoliang.db"
    }
}
