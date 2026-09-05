package com.echomusic.app.core.designsystem.palette

import com.materialkolor.hct.Hct
import com.materialkolor.utils.ColorUtils
import kotlin.math.abs
import kotlin.math.min

/**
 * HCT tonal 展开映射器（DESIGN-SYSTEM §1.2 数值表 → [EchoPaletteSet] 深浅两套）。
 *
 * 映射全部经 `Hct.from(hue, chroma, tone)` 精确落 tone 值（表内 T 值硬编码对照，
 * BREAKDOWN T9 验收项）；gamut 之外由 HCT 求解器自动收彩度。
 *
 * §1.2 表外的推导项（表只给到语义主色，M3 ColorScheme 还需要三个槽位）：
 *  - outline / outlineVariant：同色相中性（C=4）tone 28/20（深）· 60/86（浅）——
 *    深色模式下 outlineVariant（分隔线/进度轨道）比 outline 更贴近背景，符合 §4.3 发丝线层级；
 *  - primaryContainer / onPrimaryContainer：container tone 30/90（深）· 90/15（浅），
 *    彩度 clamp 在 seed 的 24–48 / 20–44——与 §2.1 品牌基准板里 primaryContainer
 *    （#0F463A≈T30、#D3F1E7≈T90）的 tone 档位对齐；
 *  - onPrimaryContainer 的对比度同样过 §1.3 硬门槛。
 *
 * 护栏（§1.3）：映射后正文对比 < 4.5:1、图形 < 3:1 时沿 tone 轴 ±6 步进调整。
 */
object EchoPaletteMapper {

    /** §1.3 对比硬门槛：正文 ≥ 4.5:1、大字号与图形 ≥ 3:1 */
    private const val TEXT_CONTRAST = 4.5
    private const val GRAPHIC_CONTRAST = 3.0

    /** glow/coverShadow 的 alpha 中值（§1.2 区间：深 30–35% 与 40–55%，浅 35–50% 与 20–25%） */
    private const val GLOW_ALPHA_DARK = 0.32f
    private const val GLOW_ALPHA_LIGHT = 0.42f
    private const val COVER_SHADOW_ALPHA_DARK = 0.50f
    private const val COVER_SHADOW_ALPHA_LIGHT = 0.22f

    private const val SCRIM_DARK = 0x99000000.toInt()

    /** 表外推导：中性槽位用的彩度上限（§1.3「正文只由彩度 ≤ 4 的中性灰阶构成」） */
    private const val NEUTRAL_CHROMA = 4.0

    /** seed → 深浅两套 tonal 映射。glowHue 缺省 = seedHue（单色相光晕，§1.1 第 4 步无合格次高分簇时） */
    fun fromSeed(seed: Hct, glowHue: Double = seed.hue): EchoPaletteSpec =
        EchoPaletteSpec(
            dark = darkSet(seed.hue, seed.chroma, glowHue),
            light = lightSet(seed.hue, seed.chroma, glowHue),
            seedHue = seed.hue,
            seedChroma = seed.chroma,
            glowHue = glowHue,
            source = EchoPaletteSpec.Source.DYNAMIC,
        )

    /**
     * 品牌回声青基准板（§1.3 护栏一：灰度封面 C < 8 回退；也是「未播放」的静止态）。
     * 直接取 §2.1 品牌基准色板逐项硬编码（设计稿定稿值，不走 tonal 生成）。
     */
    fun brandBaseline(): EchoPaletteSpec {
        val seed = Hct.fromInt(0xFF4FE3C1.toInt())
        return EchoPaletteSpec(
            dark = EchoPaletteSet(
                background = 0xFF0E1013.toInt(),
                surface = 0xFF15181C.toInt(),
                surfaceContainer = 0xFF1D2126.toInt(),
                outline = 0xFF272C32.toInt(),
                outlineVariant = 0xFF20242A.toInt(),
                onSurface = 0xFFF2F4F3.toInt(),
                onSurfaceVariant = 0xFF9AA3A8.toInt(),
                primary = 0xFF4FE3C1.toInt(),
                onPrimary = 0xFF052A21.toInt(),
                primaryContainer = 0xFF0F463A.toInt(),
                onPrimaryContainer = 0xFFB7F5E6.toInt(),
                glow = withAlpha(Hct.from(seed.hue, seed.chroma * 0.8, 28.0).toInt(), GLOW_ALPHA_DARK),
                coverShadow = withAlpha(Hct.from(seed.hue, seed.chroma * 0.8, 12.0).toInt(), COVER_SHADOW_ALPHA_DARK),
                scrim = SCRIM_DARK,
            ),
            light = EchoPaletteSet(
                background = 0xFFF5F6F5.toInt(),
                surface = 0xFFFFFFFF.toInt(),
                surfaceContainer = 0xFFECEEEC.toInt(),
                outline = 0xFFE1E5E3.toInt(),
                outlineVariant = 0xFFEBEEEC.toInt(),
                onSurface = 0xFF191C1B.toInt(),
                onSurfaceVariant = 0xFF5D6663.toInt(),
                primary = 0xFF0A7F69.toInt(),
                onPrimary = 0xFFFFFFFF.toInt(),
                primaryContainer = 0xFFD3F1E7.toInt(),
                onPrimaryContainer = 0xFF00473A.toInt(),
                glow = withAlpha(Hct.from(seed.hue, seed.chroma * 0.8, 28.0).toInt(), GLOW_ALPHA_LIGHT),
                coverShadow = withAlpha(Hct.from(seed.hue, seed.chroma * 0.8, 60.0).toInt(), COVER_SHADOW_ALPHA_LIGHT),
                scrim = SCRIM_DARK,
            ),
            seedHue = seed.hue,
            seedChroma = seed.chroma,
            glowHue = seed.hue,
            source = EchoPaletteSpec.Source.BRAND_BASELINE,
        )
    }

    /**
     * 8 组内置「回声渐变」种子（§1.3 护栏二：无封面文件 → 按「歌手+曲名」hash 确定性选一组，
     * 同一首歌永远同一配色）。色相分布跨 360°（每组间距 ≥ 30°），彩度取中高饱和带，
     * 键色 tone 60（打分亮度带中心）。命名取声学意象，与「绕梁」气质一致。
     */
    private val hashedSeeds: List<SeedGradient> = listOf(
        SeedGradient("回声青", hue = 165.0, chroma = 60.0),
        SeedGradient("靛夜", hue = 265.0, chroma = 48.0),
        SeedGradient("天青", hue = 205.0, chroma = 44.0),
        SeedGradient("香槟金", hue = 75.0, chroma = 42.0),
        SeedGradient("琥珀", hue = 45.0, chroma = 52.0),
        SeedGradient("绯红", hue = 12.0, chroma = 48.0),
        SeedGradient("玫紫", hue = 330.0, chroma = 46.0),
        SeedGradient("苔青", hue = 120.0, chroma = 40.0),
    )

    /** 无封面兜底：hash(key) → 8 组渐变之一（确定性；同 key 恒同色，不是随机彩虹） */
    fun hashFallback(key: String): EchoPaletteSpec {
        val hash = key.fold(0) { acc, c -> (acc * 31 + c.code) and 0x7FFFFFFF }
        val seed = hashedSeeds[abs(hash) % hashedSeeds.size]
        val glowHue = (seed.hue + 40.0) % 360.0
        return fromSeed(Hct.from(seed.hue, seed.chroma, 60.0), glowHue)
            .copy(source = EchoPaletteSpec.Source.HASHED_FALLBACK)
    }

    // ---- §1.2 映射表 ----

    private fun darkSet(hue: Double, chroma: Double, glowHue: Double): EchoPaletteSet {
        val glowC = chromaAtHue(glowHue, chroma)
        val background = Hct.from(hue, min(chroma, 10.0), 8.0).toInt()
        val surface = Hct.from(hue, min(chroma, 8.0), 12.0).toInt()
        val surfaceContainer = Hct.from(hue, min(chroma, 8.0), 16.0).toInt()
        val primary = Hct.from(hue, chroma.coerceIn(45.0, 88.0), 80.0).toInt()
        val primaryContainer = Hct.from(hue, chroma.coerceIn(24.0, 48.0), 30.0).toInt()
        return EchoPaletteSet(
            background = background,
            surface = surface,
            surfaceContainer = surfaceContainer,
            outline = Hct.from(hue, NEUTRAL_CHROMA, 28.0).toInt(),
            outlineVariant = Hct.from(hue, NEUTRAL_CHROMA, 20.0).toInt(),
            onSurface = ensureTextContrast(Hct.from(hue, NEUTRAL_CHROMA, 94.0).toInt(), background, darkenIfLight = false),
            onSurfaceVariant = ensureTextContrast(Hct.from(hue, NEUTRAL_CHROMA, 66.0).toInt(), surfaceContainer, darkenIfLight = false),
            primary = ensureGraphicContrast(primary, background),
            onPrimary = ensureTextContrast(Hct.from(hue, min(chroma, 10.0), 12.0).toInt(), primary, darkenIfLight = true),
            primaryContainer = primaryContainer,
            onPrimaryContainer = ensureTextContrast(Hct.from(hue, chroma.coerceIn(10.0, 36.0), 90.0).toInt(), primaryContainer, darkenIfLight = false),
            glow = withAlpha(Hct.from(glowHue, glowC * 0.8, 28.0).toInt(), GLOW_ALPHA_DARK),
            coverShadow = withAlpha(Hct.from(glowHue, glowC * 0.8, 12.0).toInt(), COVER_SHADOW_ALPHA_DARK),
            scrim = SCRIM_DARK,
        )
    }

    private fun lightSet(hue: Double, chroma: Double, glowHue: Double): EchoPaletteSet {
        val glowC = chromaAtHue(glowHue, chroma)
        val background = Hct.from(hue, min(chroma, 8.0), 97.0).toInt()
        val surface = Hct.from(hue, min(chroma, 8.0), 99.0).toInt()
        val surfaceContainer = Hct.from(hue, min(chroma, 8.0), 94.0).toInt()
        val primary = Hct.from(hue, chroma.coerceIn(40.0, 72.0), 38.0).toInt()
        val primaryContainer = Hct.from(hue, chroma.coerceIn(20.0, 44.0), 90.0).toInt()
        return EchoPaletteSet(
            background = background,
            surface = surface,
            surfaceContainer = surfaceContainer,
            outline = Hct.from(hue, NEUTRAL_CHROMA, 60.0).toInt(),
            outlineVariant = Hct.from(hue, NEUTRAL_CHROMA, 86.0).toInt(),
            onSurface = ensureTextContrast(Hct.from(hue, NEUTRAL_CHROMA, 13.0).toInt(), background, darkenIfLight = true),
            onSurfaceVariant = ensureTextContrast(Hct.from(hue, NEUTRAL_CHROMA, 42.0).toInt(), surfaceContainer, darkenIfLight = true),
            primary = ensureGraphicContrast(primary, background),
            onPrimary = ensureTextContrast(Hct.from(hue, NEUTRAL_CHROMA, 100.0).toInt(), primary, darkenIfLight = false),
            primaryContainer = primaryContainer,
            onPrimaryContainer = ensureTextContrast(Hct.from(hue, chroma.coerceIn(14.0, 40.0), 15.0).toInt(), primaryContainer, darkenIfLight = true),
            glow = withAlpha(Hct.from(glowHue, glowC * 0.8, 28.0).toInt(), GLOW_ALPHA_LIGHT),
            coverShadow = withAlpha(Hct.from(glowHue, glowC * 0.8, 60.0).toInt(), COVER_SHADOW_ALPHA_LIGHT),
            scrim = SCRIM_DARK,
        )
    }

    /**
     * glow 色相处的可用彩度：HCT 求解器在该色相下可能达不到 seed 的彩度（黄/蓝区 gamut 收窄），
     * 先以「tone 40 处可达彩度」为上限，避免光晕饱和度失真。
     */
    private fun chromaAtHue(hue: Double, fallback: Double): Double {
        val probe = Hct.from(hue, fallback, 40.0)
        return min(probe.chroma, fallback)
    }

    // ---- §1.3 对比度护栏 ----

    private fun ensureTextContrast(argb: Int, against: Int, darkenIfLight: Boolean): Int =
        ensureContrast(argb, against, TEXT_CONTRAST, darkenIfLight)

    private fun ensureGraphicContrast(argb: Int, against: Int): Int {
        val backgroundLight = ColorUtils.lstarFromArgb(against) > 50.0
        return ensureContrast(argb, against, GRAPHIC_CONTRAST, darkenIfLight = backgroundLight)
    }

    /** 沿 tone 轴 ±6 步进调整直至满足对比硬门槛（§1.3），最多 10 步防死循环 */
    private fun ensureContrast(argb: Int, against: Int, minRatio: Double, darkenIfLight: Boolean): Int {
        var color = argb
        var tone = ColorUtils.lstarFromArgb(argb)
        var steps = 0
        while (contrastRatio(color, against) < minRatio && steps < 10) {
            tone = (tone + if (darkenIfLight) -6.0 else 6.0).coerceIn(0.0, 100.0)
            color = ColorUtils.argbFromLstar(tone)
            steps++
        }
        return color
    }

    /** WCAG 2.x 对比度（§1.3 口径：正文 ≥ 4.5:1、大字号与图形 ≥ 3:1） */
    fun contrastRatio(a: Int, b: Int): Double {
        val la = ColorUtils.calculateLuminance(a)
        val lb = ColorUtils.calculateLuminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun withAlpha(argb: Int, alpha: Float): Int =
        (argb and 0x00FFFFFF) or ((alpha * 255f).toInt().coerceIn(0, 255) shl 24)

    /** 内置回声渐变的种子定义 */
    private data class SeedGradient(val name: String, val hue: Double, val chroma: Double)
}
