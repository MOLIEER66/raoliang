package com.echomusic.app.core.data.di

import androidx.room3.Room
import com.echomusic.app.core.data.db.EchoDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * T0 门禁冒烟（ADR-0004 D6 回退落点）：data 层 Koin module 表的第一块拼图。
 * D3：M2 拆 module 时本表随 core.data 包整体迁移。
 */
val dataModule = module {
    single<EchoDatabase> {
        Room.databaseBuilder(
            androidContext(),
            EchoDatabase::class.java,
            EchoDatabase.NAME,
        ).build()
    }
    single { get<EchoDatabase>().songDao() }
}
