package com.echomusic.app.core.designsystem.di

import com.echomusic.app.core.designsystem.palette.PaletteRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * designsystem 层 Koin module 表（ADR-0004 D3：M2 拆 module 时随包整体迁移）。
 * 取色器（PaletteRepository）依赖 SingletonImageLoader（EchoApplication 装配 Coil 组件）。
 */
val designSystemModule = module {
    single { PaletteRepository(androidContext()) }
}
