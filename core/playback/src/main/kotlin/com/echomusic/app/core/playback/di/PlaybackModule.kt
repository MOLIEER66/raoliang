package com.echomusic.app.core.playback.di

import com.echomusic.app.core.data.settings.PlaybackPreferences
import com.echomusic.app.core.data.settings.PlaybackSettings
import com.echomusic.app.core.playback.MediaSessionConnector
import com.echomusic.app.core.playback.PlaybackController
import com.echomusic.app.core.playback.PlaybackControllerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * playback 层 Koin module 表（BREAKDOWN T5/T6 / ADR-0004 D6）。
 * D3：M2 拆 module 时本表随 core.playback 包整体迁移；
 * 服务本体由系统创建（不经 Koin），依赖经 `inject()` 从容器取。
 *
 * UI 波次取用：`getKoin().get<PlaybackController>()` / `koinInject<PlaybackController>()`（默认名）。
 */
val playbackModule = module {

    single<PlaybackSettings> { PlaybackPreferences(androidContext()) }

    single<PlaybackController> {
        PlaybackControllerImpl(
            settings = get(),
            // media3 要求主线程命令（MediaControllerBridge 内的构建/查询），会话事件也主线程回调
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            connector = MediaSessionConnector(androidContext()),
        )
    }
}
