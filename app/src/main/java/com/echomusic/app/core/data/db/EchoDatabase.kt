package com.echomusic.app.core.data.db

import androidx.room3.Database
import androidx.room3.RoomDatabase

/**
 * T0 冒烟数据库：只挂最小 songs 表，验证「KSP(Room3) 生成 → 编译 → JVM 运行」整条链路。
 * exportSchema 暂关（未配置导出目录会告警）；T2 落正式表结构时开启并配置 schemas 导出目录。
 */
@Database(entities = [SongEntity::class], version = 1, exportSchema = false)
abstract class EchoDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao

    companion object {
        const val NAME = "raoliang.db"
    }
}
