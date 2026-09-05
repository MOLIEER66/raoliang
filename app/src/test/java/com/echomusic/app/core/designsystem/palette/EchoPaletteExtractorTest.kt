package com.echomusic.app.core.designsystem.palette

import com.materialkolor.hct.Hct
import com.materialkolor.utils.ColorUtils.argbFromLstar
import com.materialkolor.utils.ColorUtils.lstarFromArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Echo Palette 取色流水线纯 JVM 单测（BREAKDOWN §3.1：五步流水线各步独立断言——
 * 聚类数、L* 过滤、打分序、seed/glow 选定、护栏兜底）。
 *
 * 像素输入 = ARGB IntArray 的「替身位图」；色样用 HCT 构造保证色相/彩度/明度可预测。
 */
class EchoPaletteExtractorTest {

    // ---- 色样（HCT 构造，色相/彩度/明度可预测）----

    private val indigo = Hct.from(265.0, 40.0, 25.0).toInt() or (-0x1000000)
    private val champagneGold = Hct.from(75.0, 55.0, 65.0).toInt() or (-0x1000000)
    private val vividRed = Hct.from(20.0, 90.0, 55.0).toInt() or (-0x1000000)
    private val midGray = argbFromLstar(50.0) or (-0x1000000)
    private val pureBlack = -0x1000000
    private val pureWhite = -0x1

    private fun fill(argb: Int, count: Int): IntArray = IntArray(count) { argb }

    // ---- §1.1 第 2 步：K-means 聚色 ----

    @Test
    fun `kMeans 双色块聚成两簇且覆盖率正确`() {
        // 80% 靛蓝 + 20% 金：两个相距极远的色块必然分进两簇
        val pixels = fill(indigo, 800) + fill(champagneGold, 200)

        val clusters = EchoPaletteExtractor.kMeans(pixels, clusterCount = 6)

        assertEquals(2, clusters.size)
        assertEquals(0.8, clusters[0].coverage, 0.01) // 按覆盖像素数降序
        assertEquals(0.2, clusters[1].coverage, 0.01)
        // 代表色落回各自色相（K-means 质心不偏色相）
        assertEquals(265.0, clusters[0].hct.hue, 6.0)
        assertEquals(75.0, clusters[1].hct.hue, 6.0)
    }

    @Test
    fun `kMeans 簇数不超过去重色数`() {
        val clusters = EchoPaletteExtractor.kMeans(fill(midGray, 10), clusterCount = 6)
        assertEquals(1, clusters.size)
    }

    @Test
    fun `kMeans 空输入返回空表`() {
        assertTrue(EchoPaletteExtractor.kMeans(IntArray(0)).isEmpty())
    }

    @Test
    fun `kMeans 确定性：同输入两次聚类结果一致`() {
        val pixels = fill(indigo, 500) + fill(champagneGold, 300) + fill(vividRed, 200)
        val first = EchoPaletteExtractor.kMeans(pixels)
        val second = EchoPaletteExtractor.kMeans(pixels)
        assertEquals(first.map { it.argb }, second.map { it.argb })
        assertEquals(first.map { it.population }, second.map { it.population })
    }

    // ---- §1.1 第 2 步：L* 过滤（纯黑纯白不作种子）----

    @Test
    fun `纯黑白图像无可用簇返回null`() {
        val pixels = fill(pureBlack, 500) + fill(pureWhite, 500)
        assertNull(EchoPaletteExtractor.extract(pixels))
    }

    @Test
    fun `黑白底上的小色块仍能选出种子`() {
        // 90% 白（被 L* 过滤）+ 10% 鲜红：唯一合格簇当选 seed
        val pixels = fill(pureWhite, 900) + fill(vividRed, 100)
        val spec = EchoPaletteExtractor.extract(pixels)
        assertEquals(20.0, spec?.seedHue ?: 0.0, 15.0)
    }

    // ---- §1.3 护栏一：灰度封面（C < 8）→ 回声青基准板 ----

    @Test
    fun `全灰度封面返回null由调用方兜底品牌色`() {
        val pixels = fill(midGray, 1000)
        assertNull(EchoPaletteExtractor.extract(pixels))
    }

    // ---- §1.1 第 3 步：打分序 ----

    @Test
    fun `打分序-彩度权重压过覆盖率`() {
        // 低彩度高覆盖 vs 高彩度低覆盖：S=0.5·彩度 的设计权重让后者当 seed
        val mutedIndigo = Hct.from(265.0, 12.0, 30.0).toInt() or (-0x1000000)
        val pixels = fill(mutedIndigo, 700) + fill(champagneGold, 300)

        val spec = EchoPaletteExtractor.extract(pixels)

        assertEquals(75.0, spec?.seedHue ?: -1.0, 15.0)
        assertEquals(265.0, spec?.glowHue ?: -1.0, 15.0)
    }

    @Test
    fun `打分序-同彩度同亮度带下覆盖率决定胜负`() {
        // 两簇彩度 50 / 明度带内（权重同为 1.0）：覆盖率 70% 的簇当 seed
        val skyBlue = Hct.from(205.0, 50.0, 55.0).toInt() or (-0x1000000)
        val gold = Hct.from(75.0, 50.0, 55.0).toInt() or (-0x1000000)
        val pixels = fill(skyBlue, 700) + fill(gold, 300)
        val spec = EchoPaletteExtractor.extract(pixels)
        assertEquals(205.0, spec?.seedHue ?: -1.0, 15.0)
    }

    @Test
    fun `打分-三元合成公式逐项可复算`() {
        // S = 0.5·彩度 + 0.3·覆盖率 + 0.2·亮度带权重（DESIGN-SYSTEM §1.1 第 3 步）
        // 彩度项取簇的实际 HCT 彩度（Hct.from 求解器可能收 gamut），只验证权重合成
        val mid = Hct.from(30.0, 60.0, 55.0).toInt() or (-0x1000000)
        val cluster = EchoPaletteExtractor.Cluster(
            argb = mid, population = 600, coverage = 0.6, hct = Hct.fromInt(mid),
        )
        val score = EchoPaletteExtractor.score(cluster, totalPopulation = 1000)
        val chromaTerm = 0.5 * (cluster.hct.chroma / 80.0).coerceIn(0.0, 1.0)
        val coverageTerm = 0.3 * 0.6
        val bandTerm = 0.2 * 1.0 // L*55 落在 40–70 亮度带
        assertEquals(chromaTerm + coverageTerm + bandTerm, score, 1e-9)
    }

    @Test
    fun `亮度带权重函数-40至70为1带外线性衰减`() {
        assertEquals(1.0, EchoPaletteExtractor.luminanceBandWeight(40.0), 1e-9)
        assertEquals(1.0, EchoPaletteExtractor.luminanceBandWeight(70.0), 1e-9)
        assertEquals(1.0, EchoPaletteExtractor.luminanceBandWeight(55.0), 1e-9)
        assertEquals(0.5, EchoPaletteExtractor.luminanceBandWeight(26.0), 0.01) // (26-12)/28
        assertEquals(0.0, EchoPaletteExtractor.luminanceBandWeight(12.0), 1e-9)
        assertEquals(0.0, EchoPaletteExtractor.luminanceBandWeight(92.0), 1e-9)
    }

    @Test
    fun `色相角距-跨360度取最短弧`() {
        assertEquals(30.0, EchoPaletteExtractor.hueDistance(350.0, 20.0), 1e-9)
        assertEquals(0.0, EchoPaletteExtractor.hueDistance(10.0, 370.0), 1e-9)
        assertEquals(180.0, EchoPaletteExtractor.hueDistance(0.0, 180.0), 1e-9)
    }

    // ---- §1.1 第 4 步：seed / glow 选定 ----

    @Test
    fun `glow取次高且色相差大于30度的簇`() {
        val pixels = fill(indigo, 600) + fill(champagneGold, 400)
        val spec = EchoPaletteExtractor.extract(pixels)!!
        assertTrue(abs(EchoPaletteExtractor.hueDistance(spec.glowHue, spec.seedHue)) > 30.0)
        // 金色高彩度 + 亮度带内（打分 0.66 > 靛蓝 0.52）当 seed，靛蓝当 glow
        assertEquals(75.0, spec.seedHue, 10.0)
        assertEquals(265.0, spec.glowHue, 10.0)
    }

    @Test
    fun `单色相图像glow回退为seed色相`() {
        val spec = EchoPaletteExtractor.extract(fill(vividRed, 100))!!
        assertEquals(spec.seedHue, spec.glowHue, 1e-9)
    }

    @Test
    fun `流水线确定性-同封面同配色`() {
        val pixels = fill(indigo, 600) + fill(champagneGold, 300) + fill(vividRed, 100)
        val a = EchoPaletteExtractor.extract(pixels)
        val b = EchoPaletteExtractor.extract(pixels)
        assertEquals(a, b)
    }

    @Test
    fun `透明像素按不透明处理不参与聚色`() {
        // 全透明白（alpha 被忽略后成纯白）→ 被 L* 过滤，与全红图等价
        val transparentWhite = 0x00FFFFFF
        val pixels = fill(transparentWhite, 500) + fill(vividRed, 500)
        val spec = EchoPaletteExtractor.extract(pixels)
        assertEquals(EchoPaletteExtractor.extract(fill(vividRed, 1000)), spec)
    }

    // ---- §1.2 映射表：T 值硬编码对照 ----

    @Test
    fun `深色映射-tone值符合设计表`() {
        val spec = EchoPaletteMapper.fromSeed(Hct.from(265.0, 48.0, 50.0))
        val d = spec.dark
        assertEquals(8.0, lstarFromArgb(d.background), 0.5)
        assertEquals(12.0, lstarFromArgb(d.surface), 0.5)
        assertEquals(16.0, lstarFromArgb(d.surfaceContainer), 0.5)
        assertEquals(80.0, lstarFromArgb(d.primary), 0.5)
        assertEquals(94.0, lstarFromArgb(d.onSurface), 0.5)
        assertEquals(66.0, lstarFromArgb(d.onSurfaceVariant), 0.5)
    }

    @Test
    fun `浅色映射-tone值符合设计表`() {
        val spec = EchoPaletteMapper.fromSeed(Hct.from(265.0, 48.0, 50.0))
        val l = spec.light
        assertEquals(97.0, lstarFromArgb(l.background), 0.5)
        assertEquals(99.0, lstarFromArgb(l.surface), 0.5)
        assertEquals(94.0, lstarFromArgb(l.surfaceContainer), 0.5)
        assertEquals(38.0, lstarFromArgb(l.primary), 0.5)
        assertEquals(13.0, lstarFromArgb(l.onSurface), 0.5)
        assertEquals(42.0, lstarFromArgb(l.onSurfaceVariant), 0.5)
    }

    @Test
    fun `深色primary彩度clamp在45至88`() {
        // clamp 边界用青色相（165°，T80 处 gamut 最宽）验证；gamut 收彩由 HCT 求解器兜底
        val low = EchoPaletteMapper.fromSeed(Hct.from(165.0, 10.0, 50.0)).dark.primary
        val high = EchoPaletteMapper.fromSeed(Hct.from(165.0, 110.0, 50.0)).dark.primary
        // HCT 求解器在 tone 80 处的数值精度 ~0.1 彩度：clamp(45) 的实测可能落在 44.9x
        assertTrue("低彩度 seed 的 primary 彩度应 ≈45（实测 ${Hct.fromInt(low).chroma}）", Hct.fromInt(low).chroma >= 44.5)
        assertTrue("高彩度 seed 的 primary 彩度应 ≤88（实测 ${Hct.fromInt(high).chroma}）", Hct.fromInt(high).chroma <= 88.0)
    }

    @Test
    fun `同一首歌两种模式色相一致`() {
        val spec = EchoPaletteMapper.fromSeed(Hct.from(265.0, 48.0, 50.0))
        val darkHue = Hct.fromInt(spec.dark.primary).hue
        val lightHue = Hct.fromInt(spec.light.primary).hue
        // HCT 求解器色相数值精度 ~2°（Hct.from(265) 的实际 HCT 读数 ≈265.2）
        assertEquals(darkHue, lightHue, 4.0)
        assertEquals(265.0, spec.seedHue, 2.0)
    }

    // ---- §1.3 护栏二：对比度硬门槛 ----

    @Test
    fun `对比度护栏-正文至少4_5图形至少3_0`() {
        // 刻意用低彩度 seed（最接近灰度边界的合法场景）检验步进调整
        val spec = EchoPaletteMapper.fromSeed(Hct.from(200.0, 9.0, 50.0))
        for (set in listOf(spec.dark, spec.light)) {
            assertTrue(
                "onSurface 对 background ≥ 4.5（实测 ${EchoPaletteMapper.contrastRatio(set.onSurface, set.background)}）",
                EchoPaletteMapper.contrastRatio(set.onSurface, set.background) >= 4.5,
            )
            assertTrue(
                "onSurfaceVariant 对 surfaceContainer ≥ 4.5（迷你条场景）",
                EchoPaletteMapper.contrastRatio(set.onSurfaceVariant, set.surfaceContainer) >= 4.5,
            )
            assertTrue(
                "onPrimary 对 primary ≥ 4.5",
                EchoPaletteMapper.contrastRatio(set.onPrimary, set.primary) >= 4.5,
            )
            assertTrue(
                "primary 对 background ≥ 3.0（图形/大字号门槛）",
                EchoPaletteMapper.contrastRatio(set.primary, set.background) >= 3.0,
            )
        }
    }

    // ---- §1.3 护栏兜底 ----

    @Test
    fun `品牌基准板-逐项等于设计稿2_1硬编码`() {
        val baseline = EchoPaletteMapper.brandBaseline()
        assertEquals(EchoPaletteSpec.Source.BRAND_BASELINE, baseline.source)
        assertEquals(0xFF0E1013.toInt(), baseline.dark.background)
        assertEquals(0xFF15181C.toInt(), baseline.dark.surface)
        assertEquals(0xFF1D2126.toInt(), baseline.dark.surfaceContainer)
        assertEquals(0xFF4FE3C1.toInt(), baseline.dark.primary)
        assertEquals(0xFF052A21.toInt(), baseline.dark.onPrimary)
        assertEquals(0xFFF2F4F3.toInt(), baseline.dark.onSurface)
        assertEquals(0xFF9AA3A8.toInt(), baseline.dark.onSurfaceVariant)
        assertEquals(0xFFF5F6F5.toInt(), baseline.light.background)
        assertEquals(0xFF0A7F69.toInt(), baseline.light.primary)
        assertEquals(0xFF191C1B.toInt(), baseline.light.onSurface)
        assertEquals(0xFF5D6663.toInt(), baseline.light.onSurfaceVariant)
    }

    @Test
    fun `hash兜底-同键同色且命中8组渐变`() {
        val a = EchoPaletteMapper.hashFallback("周杰伦|夜曲|269000")
        val b = EchoPaletteMapper.hashFallback("周杰伦|夜曲|269000")
        assertEquals(a, b)
        assertEquals(EchoPaletteSpec.Source.HASHED_FALLBACK, a.source)
        // 任意两个键至少能命中不同的渐变组（16 个键全落同组在统计上不可能）
        val distinctSpecs = (0 until 16)
            .map { EchoPaletteMapper.hashFallback("song-key-$it") }
            .distinct()
        assertTrue("16 个键应至少命中 2 组不同的渐变（实际 ${distinctSpecs.size}）", distinctSpecs.size >= 2)
    }
}
