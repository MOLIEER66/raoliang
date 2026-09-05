package com.echomusic.app.core.designsystem.palette

/**
 * Echo Palette 规格数据（DESIGN-SYSTEM §1.2 映射的产物，纯 Kotlin 零 Android 依赖）。
 *
 * 取色器（[EchoPaletteExtractor]）与映射器（[EchoPaletteMapper]）产出的最终形态：
 * 深浅两套色值 + seed/glow 色相元数据。色值统一用 ARGB Int 承载（glow/coverShadow
 * 的透明度已烘焙进 ARGB），Compose 侧到 `Color`/`ColorScheme` 的换算在
 * [EchoPalette] 完成——拆开这层是为了让 §1.2 数值表（T 值硬编码）可被纯 JVM 单测断言。
 *
 * @property dark 深色映射（设计系统深色优先）
 * @property light 浅色映射（同一首歌两种模式色相一致、明度相反，§1.2）
 * @property seedHue 种子色相（HCT Hue，0–360）
 * @property seedChroma 种子彩度（HCT Chroma）
 * @property glowHue 光晕辅助色相（与 seed 色相差 > 30° 的次高分簇；无合格簇时等于 seedHue）
 * @property source 配色来源（护栏兜底的判别依据，§1.3）
 */
data class EchoPaletteSpec(
    val dark: EchoPaletteSet,
    val light: EchoPaletteSet,
    val seedHue: Double,
    val seedChroma: Double,
    val glowHue: Double,
    val source: Source,
) {
    /** 深浅两套之一 */
    fun set(isDark: Boolean): EchoPaletteSet = if (isDark) dark else light

    /** 配色来源（§1.3 护栏三态） */
    enum class Source {
        /** 从封面 K-means/HCT 流水线取色 */
        DYNAMIC,

        /** 灰度封面或无可用簇 → 品牌回声青基准板 */
        BRAND_BASELINE,

        /** 无封面 → 「歌手+曲名」hash 选定的内置回声渐变（同曲同色） */
        HASHED_FALLBACK,
    }
}

/**
 * 单一明暗模式下的一整套语义色值（DESIGN-SYSTEM §1.2 数值表逐项对应；
 * outline/outlineVariant/primaryContainer 三项为表外推导，推导依据见 [EchoPaletteMapper]）。
 * glow/coverShadow 的 alpha 已按 §1.2 规定烘焙进 ARGB。
 */
data class EchoPaletteSet(
    val background: Int,
    val surface: Int,
    val surfaceContainer: Int,
    val outline: Int,
    val outlineVariant: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    /** 沉浸背景光晕（radial，alpha 已烘焙：深色 32% / 浅色 42%，§1.2 30–50% 区间中值） */
    val glow: Int,
    /** 封面投影（光晕色加深，alpha：深色 50% / 浅色 22%，§1.2 40–55% / 20–25% 区间中值） */
    val coverShadow: Int,
    /** 弹层遮罩 #000000 60%（§2.1） */
    val scrim: Int,
)
