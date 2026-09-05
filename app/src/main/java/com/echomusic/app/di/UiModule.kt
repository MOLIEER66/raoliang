package com.echomusic.app.di

import com.echomusic.app.feature.library.LibraryViewModel
import com.echomusic.app.feature.player.PlayerViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * UI 层 Koin module 表（ADR-0004 D3：app 包 = 装配 + 导航）。
 * ViewModel 经 koin-androidx-compose 的 koinViewModel() 取用。
 */
val uiModule = module {
    viewModel { LibraryViewModel(get(), get()) }
    viewModel { PlayerViewModel(get(), get(), get()) }
}
