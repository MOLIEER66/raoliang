package com.echomusic.app.core.source.protocol

import com.echomusic.app.core.source.LxResult
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * 歌词解析（T4 · 协议 §5.2 出参 + P0-6）。
 *
 * 洛雪 lyric 出参对象：`{ lyric, tlyric, rlyric, lxlyric }`
 * - 官方文档拼写含 typo（`lryic/tlryic`），生态脚本实际用 `lyric/tlyric`——
 *   读取侧宽容：两种拼写都认（P1-11）；
 * - `lyric`：标准 LRC（`[mm:ss.xx]行文本`）；
 * - `tlyric`：翻译（LRC，行时间对齐主歌词）；
 * - `rlyric`：罗马音（M2 不展示，透传保留）；
 * - `lxlyric`：洛雪逐字格式 `[mm:ss.xx]<startMs,durMs>字<startMs,durMs>字`；
 * - 失败时脚本 resolve 空对象/never reject（协议 §5.2：歌词失败不阻断播放）。
 */
object LxLyrics {

    /** 一行歌词（纯 JVM 模型，UI 层直接消费）。 */
    data class Line(
        val timeMs: Long,
        val text: String,
        /** 逐字时间（lxlyric 独有；LRC 为空表）。 */
        val words: List<Word> = emptyList(),
    )

    /** 逐字片段（lxlyric 的 `<startMs,durMs>字`）。 */
    data class Word(val startMs: Long, val durMs: Long, val text: String)

    /** lyric action 出参的规范化产物（缺失流给 null）。 */
    data class Parsed(
        val main: List<Line>,
        val translation: List<Line>?,
        val romanization: String?,
        /** 原始 lxlyric 串（M4 卡拉OK渲染的直通数据，R6 风险条目）。 */
        val rawLxlyric: String?,
    ) {
        val hasLyrics: Boolean get() = main.isNotEmpty()
    }

    // ---------- 出参对象 → Parsed ----------

    /** LxResult.Success.data（lyric action 的 resolve 对象）→ [Parsed]。 */
    fun fromResult(result: LxResult): Parsed? = when (result) {
        is LxResult.Success -> fromJson(result.data)
        else -> null
    }

    fun fromJson(data: JsonElement?): Parsed? {
        val obj = (data as? JsonObject) ?: return null
        fun field(vararg names: String): String? =
            names.firstNotNullOfOrNull { n ->
                (obj[n] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }
            }
        return Parsed(
            main = parseLrc(field("lyric", "lryic")),
            translation = field("tlyric", "tlryic")?.let { parseLrc(it) }?.takeIf { it.isNotEmpty() },
            romanization = field("rlyric"),
            rawLxlyric = field("lxlyric"),
        )
    }

    // ---------- LRC 行解析 ----------

    private val LRC_TIME = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val LX_WORD = Regex("""<(\d+),(\d+)>([^<]*)""")

    /**
     * 解析 LRC / lxlyric 文本为行列表。
     * lxlyric 行（带 `<start,dur>` 逐字段）自动产出 [Line.words]；
     * 多时间标签行（`[00:12.00][00:45.00]副歌`）拆为多行；
     * 无时间标签的行（元数据 `[ti:标题]`、纯文本）跳过。
     */
    fun parseLrc(text: String?): List<Line> {
        if (text.isNullOrEmpty()) return emptyList()
        val out = mutableListOf<Line>()
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach

            val times = LRC_TIME.findAll(line).toList()
            if (times.isEmpty()) return@forEach // [ti:]/[ar:] 等元数据或杂行

            val content = line.substring(times.last().range.last + 1).trim()
            if (content.isEmpty() && times.size == 1) return@forEach // 空行跳过（单标签）

            val words = LX_WORD.findAll(content).mapNotNull { m ->
                Word(m.groupValues[1].toLong(), m.groupValues[2].toLong(), m.groupValues[3])
            }.filter { it.text.isNotEmpty() }.toList()

            val plain = if (words.isEmpty()) content else words.joinToString("") { it.text }

            times.forEach { m ->
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val fracRaw = m.groupValues[3]
                // 位数宽容：2 位=厘秒、3 位=毫秒、1 位=百毫秒
                val fracMs = when (fracRaw.length) {
                    0 -> 0L
                    1 -> fracRaw.toLong() * 100
                    2 -> fracRaw.toLong() * 10
                    else -> fracRaw.take(3).toLong()
                }
                out += Line(timeMs = min * 60_000 + sec * 1_000 + fracMs, text = plain, words = words)
            }
        }
        return out.sortedBy { it.timeMs }
    }
}
