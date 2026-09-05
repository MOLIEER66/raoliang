package com.echomusic.app.core.designsystem.palette

import com.materialkolor.hct.Hct
import com.materialkolor.utils.ColorUtils
import kotlin.math.min

/**
 * Echo Palette 取色流水线（DESIGN-SYSTEM §1.1 五步中的 2–4 步，纯 JVM 可测）：
 *
 *   像素（已下采样 128px）→ K-means 聚色（k=6，≤10 轮，CIELab 空间）
 *   → 过滤 L* < 12 与 L* > 92（纯黑纯白不作种子）
 *   → 打分 S = 0.5·彩度 + 0.3·覆盖率 + 0.2·亮度带权重（L* 40–70 加权最高）
 *   → 最高分为 seed；次高且色相差 > 30° 的簇为 glow Hue
 *   → [EchoPaletteMapper.fromSeed] tonal 展开（§1.2）。
 *
 * 护栏出口（§1.3）：全灰度（seed 彩度 < 8）或无可用簇 → 返回 null，
 * 由调用方回退品牌回声青基准板（[EchoPaletteMapper.brandBaseline]）；
 * 无封面文件 → [EchoPaletteMapper.hashFallback]。
 *
 * 输入约定：ARGB 像素（alpha 忽略，透明像素按丢弃处理）。
 */
object EchoPaletteExtractor {

    /** §1.1 第 2 步：K-means 簇数 */
    const val CLUSTER_COUNT = 6

    /** §1.1 第 2 步：迭代上限 */
    const val MAX_ITERATIONS = 10

    /** §1.1 第 2 步：纯黑纯白不作种子（L* 过滤带） */
    const val TONE_FILTER_MIN = 12.0
    const val TONE_FILTER_MAX = 92.0

    /** §1.3 护栏一：灰度封面（seed 彩度 < 8）→ 回声青基准板 */
    const val GRAYSCALE_CHROMA_FLOOR = 8.0

    /** §1.1 第 4 步：glow 候选与 seed 的最小色相差 */
    const val GLOW_MIN_HUE_DISTANCE = 30.0

    /** 打分用的彩度归一上限（CAM16 彩度高频带 ≈ 80，封面上极少超出） */
    private const val CHROMA_NORM = 80.0

    /** 亮度带权重中心（L* 40–70 加权最高，避开焦黑与过曝） */
    private const val BAND_CENTER_LOW = 40.0
    private const val BAND_CENTER_HIGH = 70.0

    /**
     * 流水线入口：封面像素 → [EchoPaletteSpec]（深浅两套）。
     * 无可用结果（空图/全灰度/全黑白）返回 null，调用方按 §1.3 兜底。
     */
    fun extract(pixels: IntArray, clusterCount: Int = CLUSTER_COUNT): EchoPaletteSpec? {
        val clusters = kMeans(pixels, clusterCount)
        val eligible = clusters.filter { it.hct.tone in TONE_FILTER_MIN..TONE_FILTER_MAX }
        if (eligible.isEmpty()) return null

        val scored = eligible
            .map { ScoredCluster(it, score(it, clusters.totalPopulation())) }
            .sortedByDescending { it.score }
        val seed = scored.first().cluster
        if (seed.hct.chroma < GRAYSCALE_CHROMA_FLOOR) return null

        val glowCluster = scored.drop(1)
            .firstOrNull { hueDistance(it.cluster.hct.hue, seed.hct.hue) > GLOW_MIN_HUE_DISTANCE }
            ?.cluster
        val glowHue = glowCluster?.hct?.hue ?: seed.hct.hue
        return EchoPaletteMapper.fromSeed(seed.hct, glowHue)
    }

    // ---- §1.1 第 3 步：打分 ----

    /** S = 0.5·彩度 + 0.3·覆盖率 + 0.2·亮度带权重 */
    internal fun score(cluster: Cluster, totalPopulation: Int): Double {
        val chromaTerm = (cluster.hct.chroma / CHROMA_NORM).coerceIn(0.0, 1.0)
        val coverageTerm = if (totalPopulation <= 0) 0.0 else cluster.population.toDouble() / totalPopulation
        return 0.5 * chromaTerm + 0.3 * coverageTerm + 0.2 * luminanceBandWeight(cluster.hct.tone)
    }

    /** 亮度带权重：L* 40–70 内为 1，带外线性衰减到 0（12/92 处归零） */
    internal fun luminanceBandWeight(lstar: Double): Double = when {
        lstar in BAND_CENTER_LOW..BAND_CENTER_HIGH -> 1.0
        lstar < BAND_CENTER_LOW -> ((lstar - TONE_FILTER_MIN) / (BAND_CENTER_LOW - TONE_FILTER_MIN)).coerceIn(0.0, 1.0)
        else -> ((TONE_FILTER_MAX - lstar) / (TONE_FILTER_MAX - BAND_CENTER_HIGH)).coerceIn(0.0, 1.0)
    }

    /** 色相环上的最小角距（0–180°） */
    internal fun hueDistance(a: Double, b: Double): Double {
        val diff = (a - b).mod(360.0)
        return min(diff, 360.0 - diff)
    }

    // ---- §1.1 第 2 步：K-means（CIELab 空间）----

    /**
     * K-means 聚色：先按「去重色 + 出现次数」收敛输入，初始中心取出现频次最高的 k 个
     * 去重色（确定性初始化——同一封面任何两次取色结果一致），在 CIELab 空间迭代
     * ≤ [maxIterations] 轮或至分配收敛。结果按覆盖像素数降序。
     */
    internal fun kMeans(
        pixels: IntArray,
        clusterCount: Int = CLUSTER_COUNT,
        maxIterations: Int = MAX_ITERATIONS,
    ): List<Cluster> {
        if (pixels.isEmpty()) return emptyList()

        // 去重：distinct ARGB → 出现次数（128px 图至多 16384 个 distinct，可承受）
        val colorCounts = HashMap<Int, Int>(min(pixels.size, 4096))
        for (p in pixels) {
            val opaque = p or (0xFF shl 24) // 忽略 alpha：封面不存在半透明语义
            colorCounts[opaque] = (colorCounts[opaque] ?: 0) + 1
        }
        val total = pixels.size.toDouble()
        val colors = colorCounts.keys.toIntArray()
        val counts = IntArray(colors.size) { colorCounts[colors[it]] ?: 0 }
        val labs = Array(colors.size) { ColorUtils.labFromArgb(colors[it]) }

        var k = clusterCount.coerceAtMost(colors.size)
        if (k <= 0) return emptyList()

        // 初始中心 = 频次 top-k 的去重色（稳定的确定性初始化）
        val order = colors.indices.sortedByDescending { counts[it] }
        var centroids = Array(k) { labs[order[it]].clone() }

        var assignments = IntArray(colors.size) { -1 }
        for (iteration in 0 until maxIterations) {
            var changed = false
            // E 步：按 Lab 欧氏距离分配
            for (i in colors.indices) {
                val lab = labs[i]
                var best = 0
                var bestDist = Double.MAX_VALUE
                for (c in 0 until k) {
                    val cent = centroids[c]
                    val dl = lab[0] - cent[0]
                    val da = lab[1] - cent[1]
                    val db = lab[2] - cent[2]
                    val dist = dl * dl + da * da + db * db
                    if (dist < bestDist) {
                        bestDist = dist
                        best = c
                    }
                }
                if (assignments[i] != best) {
                    assignments[i] = best
                    changed = true
                }
            }
            // M 步：加权均值为中心
            val sums = Array(k) { DoubleArray(3) }
            val weights = DoubleArray(k)
            for (i in colors.indices) {
                val c = assignments[i]
                val w = counts[i].toDouble()
                val lab = labs[i]
                sums[c][0] += lab[0] * w
                sums[c][1] += lab[1] * w
                sums[c][2] += lab[2] * w
                weights[c] += w
            }
            centroids = Array(k) { c ->
                if (weights[c] > 0.0) {
                    doubleArrayOf(sums[c][0] / weights[c], sums[c][1] / weights[c], sums[c][2] / weights[c])
                } else {
                    centroids[c] // 空簇保持原中心
                }
            }
            if (!changed) break
        }

        // 汇总簇：代表色 = 质心 Lab → ARGB；population = 分配到的像素数
        val population = IntArray(k)
        for (i in colors.indices) population[assignments[i]] += counts[i]
        return centroids.indices
            .filter { population[it] > 0 }
            .map { c ->
                val argb = ColorUtils.argbFromLab(centroids[c][0], centroids[c][1], centroids[c][2])
                Cluster(
                    argb = argb or (0xFF shl 24),
                    population = population[c],
                    coverage = population[c] / total,
                    hct = Hct.fromInt(argb),
                )
            }
            .sortedByDescending { it.population }
    }

    /** 聚色结果：质心代表色 + 覆盖像素数 + HCT 读数 */
    data class Cluster(
        val argb: Int,
        val population: Int,
        val coverage: Double,
        val hct: Hct,
    )

    private data class ScoredCluster(val cluster: Cluster, val score: Double)

    private fun List<Cluster>.totalPopulation(): Int = sumOf { it.population }
}
