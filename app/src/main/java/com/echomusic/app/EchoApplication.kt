package com.echomusic.app

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.echomusic.app.core.data.di.dataModule
import com.echomusic.app.core.designsystem.di.designSystemModule
import com.echomusic.app.core.designsystem.palette.AlbumArtFetcher
import com.echomusic.app.core.designsystem.palette.AlbumCoverRef
import com.echomusic.app.core.playback.di.playbackModule
import com.echomusic.app.di.uiModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * T0 门禁冒烟（ADR-0004 D6 回退落点）：Koin 应用入口。
 * 门禁实测结论：Hilt 2.60.1 Gradle 插件要求 AGP 9+，与锁定的 AGP 8.13.2 冲突，
 * 按 D6 既定回退线切换 Koin 4.1.1（详见 libs.versions.toml 注释）。
 * T5/T6 起追加 playbackModule（PlaybackService 依赖 + PlaybackController 桥）；
 * T9 起追加 designSystemModule（取色器）并装配 Coil 内嵌封面 Fetcher。
 */
class EchoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Coil 单例（ADR-0004 D5）：注册内嵌封面 Fetcher（AlbumCoverRef → ID3/APIC 字节流）。
        // setSafe：进程内只允许设置一次，重复进入不覆盖。
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components { add(AlbumArtFetcher.Factory(context), AlbumCoverRef::class) }
                .build()
        }
        startKoin {
            androidContext(this@EchoApplication)
            modules(dataModule, playbackModule, designSystemModule, uiModule)
        }
    }
}
