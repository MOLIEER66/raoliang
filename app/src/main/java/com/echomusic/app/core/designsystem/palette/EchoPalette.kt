package com.echomusic.app.core.designsystem.palette

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.materialkolor.hct.Hct
import com.materialkolor.utils.ColorUtils
import kotlin.math.roundToInt

/**
 * Echo Palette 的 Compose 形态（DESIGN-SYSTEM §7：Material 3 `ColorScheme` 承载 +
 * `glow`/`coverShadow` 两个沉浸专用扩展 token）。
 *
 * 动态播放态由 [EchoPaletteSpec.toEchoPalette] 在运行时构建；未播放/兜底用
 * [brandBaselinePalette]。取色切换 crossfade（§6.3 theme 800ms）的数学底座是
 * [lerpEchoPalette]（逐 token lerp 后重建 ColorScheme），由 ui 层 `rememberEchoPalette`
 * 驱动动画进度。
 */
@Immutable
data class EchoPalette(
    /** 语义槽位（M3 ColorScheme） */
    val colorScheme: ColorScheme,
    /** 原始色值半区（crossfade lerp 的数据源） */
    val set: EchoPaletteSet,
    /** 光晕 1（主色系即 seed 色相，radial 左上，§2.4） */
    val glow: Color,
    /** 光晕 2（glow Hue，radial 右下，§2.4） */
    val glowAlt: Color,
    /** 封面投影（光晕色加深，§4.3 shadow-3，色相取自光晕） */
    val coverShadow: Color,
    /** 种子色（S3 回声扩散的波前描边色） */
    val seed: Color,
    /** 配色来源（护栏判别，§1.3） */
    val source: EchoPaletteSpec.Source,
    val isDark: Boolean,
)

/** 按明暗模式取 spec 对应半区并展开为 [EchoPalette] */
fun EchoPaletteSpec.toEchoPalette(isDark: Boolean): EchoPalette {
    val set = set(isDark)
    val glowAltArgb = set.glow // spec 的 glow 即 glow Hue 处光晕（§1.2 表）
    // 光晕 1：同 tone/alpha 换 seed 色相（§2.4 光晕 1 在左上·主色系）
    val glowArgb = (Hct.from(seedHue, glowChromaAt(seedHue, seedChroma), 28.0).toInt() and 0x00FFFFFF) or
        (glowAltArgb and -0x1000000)
    return EchoPalette(
        colorScheme = buildColorScheme(set, isDark),
        set = set,
        glow = Color(glowArgb),
        glowAlt = Color(glowAltArgb),
        coverShadow = Color(set.coverShadow),
        seed = Color(Hct.from(seedHue, seedChroma.coerceIn(24.0, 60.0), 60.0).toInt()),
        source = source,
        isDark = isDark,
    )
}

/** EchoPaletteSet → M3 ColorScheme（会话用到的槽位全量覆盖；未覆盖槽位落 M3 默认） */
fun buildColorScheme(set: EchoPaletteSet, isDark: Boolean): ColorScheme {
    val common: ColorScheme.() -> ColorScheme = {
        copy(
            primary = Color(set.primary),
            onPrimary = Color(set.onPrimary),
            primaryContainer = Color(set.primaryContainer),
            onPrimaryContainer = Color(set.onPrimaryContainer),
            background = Color(set.background),
            onBackground = Color(set.onSurface),
            surface = Color(set.surface),
            onSurface = Color(set.onSurface),
            surfaceVariant = Color(set.surfaceContainer),
            onSurfaceVariant = Color(set.onSurfaceVariant),
            surfaceContainer = Color(set.surfaceContainer),
            outline = Color(set.outline),
            outlineVariant = Color(set.outlineVariant),
            scrim = Color(set.scrim),
        )
    }
    return if (isDark) common(darkColorScheme()) else common(lightColorScheme())
}

/** 两套 palette 的逐 token 插值（取色切换 crossfade，§6.3b「波前扫过处背景 crossfade 到新色」） */
fun lerpEchoPalette(start: EchoPalette, stop: EchoPalette, fraction: Float): EchoPalette {
    val set = lerpSet(start.set, stop.set, fraction)
    return EchoPalette(
        colorScheme = buildColorScheme(set, stop.isDark),
        set = set,
        glow = lerpColor(start.glow, stop.glow, fraction),
        glowAlt = lerpColor(start.glowAlt, stop.glowAlt, fraction),
        coverShadow = lerpColor(start.coverShadow, stop.coverShadow, fraction),
        seed = lerpColor(start.seed, stop.seed, fraction),
        source = stop.source,
        isDark = stop.isDark,
    )
}

private fun lerpSet(start: EchoPaletteSet, stop: EchoPaletteSet, fraction: Float): EchoPaletteSet =
    EchoPaletteSet(
        background = lerpArgb(start.background, stop.background, fraction),
        surface = lerpArgb(start.surface, stop.surface, fraction),
        surfaceContainer = lerpArgb(start.surfaceContainer, stop.surfaceContainer, fraction),
        outline = lerpArgb(start.outline, stop.outline, fraction),
        outlineVariant = lerpArgb(start.outlineVariant, stop.outlineVariant, fraction),
        onSurface = lerpArgb(start.onSurface, stop.onSurface, fraction),
        onSurfaceVariant = lerpArgb(start.onSurfaceVariant, stop.onSurfaceVariant, fraction),
        primary = lerpArgb(start.primary, stop.primary, fraction),
        onPrimary = lerpArgb(start.onPrimary, stop.onPrimary, fraction),
        primaryContainer = lerpArgb(start.primaryContainer, stop.primaryContainer, fraction),
        onPrimaryContainer = lerpArgb(start.onPrimaryContainer, stop.onPrimaryContainer, fraction),
        glow = lerpArgb(start.glow, stop.glow, fraction),
        coverShadow = lerpArgb(start.coverShadow, stop.coverShadow, fraction),
        scrim = lerpArgb(start.scrim, stop.scrim, fraction),
    )

private fun lerpArgb(start: Int, stop: Int, fraction: Float): Int {
    fun channel(sa: Int, sb: Int): Int = (sa + ((sb - sa) * fraction).roundToInt()).coerceIn(0, 255)
    val a = channel((start ushr 24) and 0xFF, (stop ushr 24) and 0xFF)
    val r = channel((start ushr 16) and 0xFF, (stop ushr 16) and 0xFF)
    val g = channel((start ushr 8) and 0xFF, (stop ushr 8) and 0xFF)
    val b = channel(start and 0xFF, stop and 0xFF)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

private fun lerpColor(start: Color, stop: Color, fraction: Float): Color = Color(
    red = start.red + (stop.red - start.red) * fraction,
    green = start.green + (stop.green - start.green) * fraction,
    blue = start.blue + (stop.blue - start.blue) * fraction,
    alpha = start.alpha + (stop.alpha - start.alpha) * fraction,
)

private fun glowChromaAt(hue: Double, fallback: Double): Double =
    minOf(Hct.from(hue, fallback, 40.0).chroma, fallback)

/** 背景亮度 → 状态栏图标明暗（DESIGN-SYSTEM §7：状态栏图标明暗随 palette 亮度切换） */
fun EchoPaletteSet.isLightBackground(): Boolean = ColorUtils.lstarFromArgb(background) > 50.0

/** 未播放 / 兜底的 Compose 基准板 */
fun brandBaselinePalette(isDark: Boolean): EchoPalette =
    EchoPaletteMapper.brandBaseline().toEchoPalette(isDark)

val LocalEchoPalette = staticCompositionLocalOf<EchoPalette> {
    error("LocalEchoPalette 未提供：必须在 EchoTheme 内使用")
}
