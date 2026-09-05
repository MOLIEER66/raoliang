package com.echomusic.app.core.data.di

import androidx.room3.Room
import com.echomusic.app.core.data.db.AlbumDao
import com.echomusic.app.core.data.db.EchoDatabase
import com.echomusic.app.core.data.db.SongDao
import com.echomusic.app.core.data.db.SyncMetaDao
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * data 层 Koin module 表（ADR-0004 D6 回退落点 = Koin 4.1.1）。
 * D3：M2 拆 module 时本表随 core.data 包整体迁移。
 * 同步器 / Repository / 设置 / 取色相关绑定随 T3/T9 各任务增量注册于本文件。
 */
val dataModule = module {
    single<EchoDatabase> {
        Room.databaseBuilder(
            androidContext(),
            EchoDatabase::class.java,
            EchoDatabase.NAME,
        )
            // v1 为 T2 重建的正式 schema（T0 冒烟库结构不同）：开发机上的旧库直接丢弃重建。
            // 首个 1.json 已导出提交，M3 起的演进必须走 migration，不再破坏性重建。
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }
    single<SongDao> { get<EchoDatabase>().songDao() }
    single<AlbumDao> { get<EchoDatabase>().albumDao() }
    single<SyncMetaDao> { get<EchoDatabase>().syncMetaDao() }
}
