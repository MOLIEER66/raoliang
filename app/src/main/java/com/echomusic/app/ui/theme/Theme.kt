package com.echomusic.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.echomusic.app.core.designsystem.palette.EchoPaletteSpec
import com.echomusic.app.core.designsystem.palette.LocalEchoPalette
import com.echomusic.app.core.designsystem.palette.rememberEchoPalette
import com.echomusic.app.core.designsystem.theme.EchoTypography

/**
 * 应用主题（DESIGN-SYSTEM §2.2 / §7）：
 *  - 未播放：品牌回声青基准板（§2.1，深浅跟随系统）；
 *  - 播放中：外部传入播放曲目的 [EchoPaletteSpec]，整 App（含迷你条与导航）切换到
 *    封面 tonal 配色，切换走 800ms crossfade（[rememberEchoPalette]）；
 *  - 取色源是「歌曲」而非壁纸——Android 12 的系统 dynamicColor 不采用（DESIGN-SYSTEM §8.1
 *    对 Material You 的排查结论）。
 */
@Composable
fun EchoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    paletteSpec: EchoPaletteSpec? = null,
    content: @Composable () -> Unit,
) {
    val palette = rememberEchoPalette(paletteSpec, darkTheme)
    CompositionLocalProvider(LocalEchoPalette provides palette) {
        MaterialTheme(
            colorScheme = palette.colorScheme,
            typography = EchoTypography,
            content = content,
        )
    }
}
