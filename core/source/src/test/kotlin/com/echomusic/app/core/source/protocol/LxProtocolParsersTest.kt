package com.echomusic.app.core.source.protocol

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 协议纯函数单测（T4 · BREAKDOWN §3.1）。
 * 头注释数据 = 标本 HYWmusic_free_v1.0.0.js L1-11 原文；歌词 = 协议 §5.2 格式族谱。
 */
class LxHeaderParserTest {

    /** 标本 L1-11 头注释原文（updateUrl 打码为占位形态）。 */
    private val specimenHeader = """
        /**
         * @name HYWmusic_公益版
         * @version v1.0.0
         * @author Ryn
         * @description qq群：1094095648；965503129
         * @homepage https://github.com/Macrohard0001/HYWmusic_source
         * @license MIT
         * @updateUrl http://srv.example/api/releases?script=HYW&scriptType=free&releaseType=lx&version=v1.0.0
         *
         * 支持平台: kw、kg、tx、wy、mg
         * 生成时间: 2026-08-31T10:47:11.493Z
         */
    """.trimIndent()

    @Test
    fun `标本头注释全字段解析`() {
        val m = LxHeaderParser.parse(specimenHeader)!!
        assertEquals("HYWmusic_公益版", m.name)
        assertEquals("v1.0.0", m.version)
        assertEquals("Ryn", m.author)
        assertTrue(m.description!!.startsWith("qq群"))
        assertTrue(m.homepage!!.startsWith("https://github.com"))
        assertTrue(m.updateUrl!!.startsWith("http://srv.example"))
    }

    @Test
    fun `name 超 24 字截断不拒收`() {
        val long = "/**\n * @name ${"超长音源名称".repeat(10)}\n * @version 1.0\n */"
        val m = LxHeaderParser.parse(long)!!
        assertEquals(24, m.name.length)
    }

    @Test
    fun `description 超 36 字截断`() {
        val d = "/**\n * @name x\n * @description ${"描述".repeat(50)}\n */"
        assertEquals(36, LxHeaderParser.parse(d)!!.description!!.length)
    }

    @Test
    fun `无块注释返回 null`() {
        assertNull(LxHeaderParser.parse("var a = 1 // @name x"))
    }

    @Test
    fun `块注释无 tag 返回 null`() {
        assertNull(LxHeaderParser.parse("/* just a comment */"))
    }

    @Test
    fun `缺 name 返回 null（无主键可用）`() {
        assertNull(LxHeaderParser.parse("/**\n * @version 1.0\n */"))
    }

    @Test
    fun `可选字段缺失给 null 不拒收`() {
        val m = LxHeaderParser.parse("/**\n * @name 简源\n */")!!
        assertEquals("简源", m.name)
        assertNull(m.version)
        assertNull(m.updateUrl)
    }

    @Test
    fun `混行内注释的脚本整包解析（头在文件头）`() {
        val script = specimenHeader + "\n\n'use strict'\nconst x = 1\n"
        val m = LxHeaderParser.parse(script)!!
        assertEquals("HYWmusic_公益版", m.name)
    }
}

class LxLyricsTest {

    @Test
    fun `标准 LRC 行解析`() {
        val lrc = "[00:12.34]第一行\n[01:02.5]第二行\n[10:00.001]尾声"
        val lines = LxLyrics.parseLrc(lrc)
        assertEquals(3, lines.size)
        assertEquals(12_340, lines[0].timeMs)
        assertEquals(62_500, lines[1].timeMs)
        // .001 = 1ms（3 位毫秒精确解析）
        assertEquals(600_001, lines[2].timeMs)
        assertEquals("第一行", lines[0].text)
    }

    @Test
    fun `lxlyric 逐字行 - words 与拼合文本`() {
        val lx = "[00:01.00]<1000,500>绕<1500,800>梁"
        val lines = LxLyrics.parseLrc(lx)
        assertEquals(1, lines.size)
        val line = lines[0]
        assertEquals(1_000, line.timeMs)
        assertEquals("绕梁", line.text)
        assertEquals(2, line.words.size)
        assertEquals(Word(1_500, 800, "梁"), line.words[1])
    }

    @Test
    fun `lxlyric 时间标签秒数与逐字毫秒独立（协议格式不保证一致，各自解析）`() {
        val lx = "[00:10.00]<9800,200>近似<10100,300>对齐"
        val line = LxLyrics.parseLrc(lx).single()
        assertEquals(10_000, line.timeMs)
        assertEquals(9_800, line.words[0].startMs)
    }

    @Test
    fun `多时间标签行拆多行`() {
        val lines = LxLyrics.parseLrc("[00:01.00][00:30.00]副歌")
        assertEquals(2, lines.size)
        assertEquals(1_000, lines[0].timeMs)
        assertEquals(30_000, lines[1].timeMs)
    }

    @Test
    fun `元数据行与空行跳过`() {
        val lines = LxLyrics.parseLrc("[ti:标题]\n[ar:歌手]\n\n[00:01.00]正文\n[00:02.00]")
        assertEquals(1, lines.size)
    }

    @Test
    fun `行按时间排序（乱序输入）`() {
        val lines = LxLyrics.parseLrc("[00:30.00]后\n[00:05.00]先")
        assertEquals(5_000, lines[0].timeMs)
    }

    @Test
    fun `出参对象解析 - 生态拼写与官方 typo 双认`() {
        // 官方文档 typo 形态（P1-11）
        val typo = Json.parseToJsonElement(
            """{"lryic":"[00:01.00]词","tlryic":"[00:01.00]譯","rlyric":"[00:01.00]kotoba","lxlyric":"[00:01.00]<1000,200>詞"}""",
        )
        val p1 = LxLyrics.fromJson(typo)!!
        assertTrue(p1.hasLyrics)
        // romanization 保留原始 LRC 文本（rlyric，未解析——M2 不展示仅透传）
        assertEquals("[00:01.00]kotoba", p1.romanization)
        assertNotNull(p1.rawLxlyric)

        // 生态实际拼写
        val real = Json.parseToJsonElement(
            """{"lyric":"[00:01.00]词","tlyric":"[00:01.00]译"}""",
        )
        val p2 = LxLyrics.fromJson(real)!!
        assertTrue(p2.hasLyrics)
        assertEquals(1, p2.translation!!.size)
        assertNull(p2.romanization)
    }

    @Test
    fun `空对象与失败不阻断（协议 5_2 永不 reject）`() {
        val p = LxLyrics.fromJson(Json.parseToJsonElement("{}"))!!
        assertEquals(false, p.hasLyrics)
        assertNull(LxLyrics.fromResult(com.echomusic.app.core.source.LxResult.Failure("挂了")))
    }

    @Test
    fun `null 与非对象输入返回 null`() {
        assertNull(LxLyrics.fromJson(null))
        assertNull(LxLyrics.fromJson(Json.parseToJsonElement("\"str\"")))
    }

    @Test
    fun `毫秒位数宽容 - 1位2位3位`() {
        val lines = LxLyrics.parseLrc("[00:01.1]a\n[00:02.22]b\n[00:03.333]c")
        assertEquals(1_100, lines[0].timeMs)
        assertEquals(2_220, lines[1].timeMs)
        assertEquals(3_333, lines[2].timeMs)
    }
}

private fun Word(startMs: Long, durMs: Long, text: String) =
    LxLyrics.Word(startMs, durMs, text)
