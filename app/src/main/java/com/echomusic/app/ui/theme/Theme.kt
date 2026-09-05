package com.echomusic.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = EchoPrimary,
    onPrimary = EchoOnPrimary,
    primaryContainer = EchoPrimaryContainer,
    onPrimaryContainer = EchoOnPrimaryContainer,
    secondary = EchoSecondary,
    onSecondary = EchoOnSecondary,
    secondaryContainer = EchoSecondaryContainer,
    onSecondaryContainer = EchoOnSecondaryContainer,
    background = EchoBackground,
    onBackground = EchoOnBackground,
    surface = EchoSurface,
    onSurface = EchoOnSurface,
)

private val DarkColors = darkColorScheme(
    primary = EchoPrimaryDark,
    onPrimary = EchoOnPrimaryDark,
    primaryContainer = EchoPrimaryContainerDark,
    onPrimaryContainer = EchoOnPrimaryContainerDark,
    secondary = EchoSecondaryDark,
    onSecondary = EchoOnSecondaryDark,
    secondaryContainer = EchoSecondaryContainerDark,
    onSecondaryContainer = EchoOnSecondaryContainerDark,
    background = EchoBackgroundDark,
    onBackground = EchoOnBackgroundDark,
    surface = EchoSurfaceDark,
    onSurface = EchoOnSurfaceDark,
)

/**
 * 应用主题：浅色/深色跟随系统。
 * dynamicColor 思路示例：Android 12+ 优先用系统动态取色（壁纸生成配色），
 * 不支持或需要品牌色一致性时回落到上面的 Echo 配色。
 */
@Composable
fun EchoMusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = EchoTypography,
        content = content,
    )
}
