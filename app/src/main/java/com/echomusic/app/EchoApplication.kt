package com.echomusic.app

import android.app.Application
import com.echomusic.app.core.data.di.dataModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * T0 门禁冒烟（ADR-0004 D6 回退落点）：Koin 应用入口。
 * 门禁实测结论：Hilt 2.60.1 Gradle 插件要求 AGP 9+，与锁定的 AGP 8.13.2 冲突，
 * 按 D6 既定回退线切换 Koin 4.1.1（详见 libs.versions.toml 注释）。
 */
class EchoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@EchoApplication)
            modules(dataModule)
        }
    }
}
