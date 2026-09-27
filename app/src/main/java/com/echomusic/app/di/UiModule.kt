package com.echomusic.app.di

import android.content.Intent
import com.echomusic.app.MainActivity
import com.echomusic.app.core.playback.SessionActivityProvider
import com.echomusic.app.feature.library.LibraryViewModel
import com.echomusic.app.feature.player.PlayerViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * UI 层 Koin module 表（ADR-0004 D3：app 包 = 装配 + 导航）。
 * ViewModel 经 koin-androidx-compose 的 koinViewModel() 取用。
 */
val uiModule = module {
    // T1 拆线：:core:playback 不许反向依赖 app，宿主页面意图在此注入（编译期安全）
    single<SessionActivityProvider> {
        SessionActivityProvider { context -> Intent(context, MainActivity::class.java) }
    }
    viewModel { LibraryViewModel(get(), get()) }
    viewModel { PlayerViewModel(get(), get(), get()) }
}
