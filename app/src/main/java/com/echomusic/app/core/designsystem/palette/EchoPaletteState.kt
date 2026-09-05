package com.echomusic.app.core.designsystem.palette

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.echomusic.app.core.designsystem.theme.EchoMotion

/**
 * 取色切换的 Compose 状态（§6.3 theme 档 800ms crossfade；BREAKDOWN T9 允许降 600ms，
 * 本实现走全档 800ms）。spec 变化（切歌/明暗切换）时对两套 [EchoPaletteSet] 逐 token lerp，
 * 波前扫过处颜色连续过渡——「回声扩散」S3 的背景底座。
 */
@Composable
fun rememberEchoPalette(spec: EchoPaletteSpec?, isDark: Boolean): EchoPalette {
    val target = remember(spec, isDark) {
        (spec ?: EchoPaletteMapper.brandBaseline()).toEchoPalette(isDark)
    }
    var shown by remember { mutableStateOf(target) }
    var from by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(target) {
        if (target != shown) {
            from = shown
            shown = target
            progress.snapTo(0f)
            progress.animateTo(1f, tween(EchoMotion.THEME_MS, easing = LinearEasing))
        }
    }

    return if (progress.value >= 1f) target else lerpEchoPalette(from, target, progress.value)
}
