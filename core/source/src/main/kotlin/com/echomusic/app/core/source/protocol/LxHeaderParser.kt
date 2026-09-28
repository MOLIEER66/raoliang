package com.echomusic.app.core.source.protocol

import com.echomusic.app.core.source.LxScriptMeta

/**
 * 洛雪脚本头注释解析（T4 · P0-13）。
 *
 * 生态标本（HYWmusic_free_v1.0.0.js L1-11）的文件头是一个块注释，
 * 内部每行形如 `@tag 值`（@name / @version / @author / @description /
 * @homepage / @license / @updateUrl），本解析器抽取其中协议相关字段。
 *
 * 容错姿态（协议研究 §7 P0-13）：字段缺失给 null 不拒收；长度超限**截断**不拒收
 * （@name ≤24、@description ≤36 是 UI 展示约束，不是脚本合规红线）；
 * 混淆脚本可能没有头注释——返回 null 让调用方回退用文件名。
 */
object LxHeaderParser {

    const val MAX_NAME_LEN = 24
    const val MAX_DESCRIPTION_LEN = 36

    private val HEADER_BLOCK = Regex("""(?s)/\*+(.*?)\*/""")
    private val TAG = Regex("""(?m)^\s*\*?\s*@(\w+)\s*(.*)$""")

    /**
     * 解析脚本头注释。非脚本（无块注释/无任何 @tag）返回 null。
     */
    fun parse(scriptCode: String): LxScriptMeta? {
        val block = HEADER_BLOCK.find(scriptCode)?.groupValues?.get(1) ?: return null
        val tags = TAG.findAll(block).associate { m ->
            m.groupValues[1] to m.groupValues[2].trim()
        }
        if (tags.isEmpty()) return null

        val name = tags["name"]?.take(MAX_NAME_LEN)?.takeIf { it.isNotEmpty() }
        return LxScriptMeta(
            name = name ?: return null,
            description = tags["description"]?.take(MAX_DESCRIPTION_LEN)?.takeIf { it.isNotEmpty() },
            version = tags["version"]?.takeIf { it.isNotEmpty() },
            author = tags["author"]?.takeIf { it.isNotEmpty() },
            homepage = tags["homepage"]?.takeIf { it.isNotEmpty() },
            updateUrl = tags["updateUrl"]?.takeIf { it.isNotEmpty() },
            rawHeader = block.trim().take(2048),
        )
    }
}
