package com.echomusic.app.core.source.wire

import com.echomusic.app.core.source.LxInitResult
import com.echomusic.app.core.source.LxResult
import com.echomusic.app.core.source.LxScriptEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LxWire 信道编解码单测（T2 · BREAKDOWN §3.1 纯函数层）。
 * 数据源：标本脚本 HYWmusic_free_v1.0.0.js 的真实消息形态 + 协议剖析 §8 的分歧样本。
 */
class LxWireTest {

    // ---------- 解码：握手 ----------

    @Test
    fun `解码 inited - 标本形态 status true 五平台`() {
        // 标本 L250-259 的 sources 结构（协议 §4）
        val raw = """
            {"t":"inited","status":true,"openDevTools":true,
             "sources":{
               "kw":{"name":"kw","type":"music","actions":["musicUrl","lyric","pic"],"qualitys":["128k","320k","flac","flac24bit","hires"]},
               "mg":{"name":"mg","type":"music","actions":["musicUrl"],"qualitys":["128k"]}
             }}
        """.trimIndent()
        val m = LxWire.decodeIncoming(raw) as LxWire.Incoming.Inited
        assertTrue(m.status == true)
        assertEquals(2, m.sources.size)
        val kw = m.sources.getValue("kw")
        assertEquals(setOf("musicUrl", "lyric", "pic"), kw.actions)
        assertEquals(listOf("128k", "320k", "flac", "flac24bit", "hires"), kw.qualitys)
        assertEquals("music", kw.type)
        // openDevTools 是桌面版语义字段，必须能收到但由上层忽略（协议 §4）
        assertEquals(true, m.openDevTools)
    }

    @Test
    fun `解码 inited - status 缺省视为失败`() {
        val m = LxWire.decodeIncoming("""{"t":"inited","sources":{}}""") as LxWire.Incoming.Inited
        assertNull(m.status)
        val r = LxWire.initedToResult(m)
        assertTrue(r is LxInitResult.Failed) // 缺省=初始化失败（协议 §4）
    }

    @Test
    fun `解码 inited - 未知平台与未知音质不炸`() {
        // 生态扩展（协议 §4：sources key 可扩展、qualitys 未知值容错）
        val m = LxWire.decodeIncoming(
            """{"t":"inited","status":true,"sources":{"xx":{"name":"新平台","type":"music","actions":["musicUrl"],"qualitys":["128k","999k","master"]}}}""",
        ) as LxWire.Incoming.Inited
        assertEquals(listOf("128k", "999k", "master"), m.sources.getValue("xx").qualitys) // 原样透传，映射归上层
    }

    @Test
    fun `解码 inited - actions 为字符串数组外的形态不炸`() {
        // 宽容：actions 缺省/类型异常 → 空能力表（上层禁用该平台，不崩信道）
        val m = LxWire.decodeIncoming(
            """{"t":"inited","status":true,"sources":{"kw":{"name":"kw"}}}""",
        ) as LxWire.Incoming.Inited
        assertTrue(m.sources.getValue("kw").actions.isEmpty())
    }

    // ---------- 解码：resp / http ----------

    @Test
    fun `解码 resp - ok 携带 data`() {
        val m = LxWire.decodeIncoming("""{"t":"resp","callId":7,"ok":true,"data":"http://a/x.flac"}""")!!
        val r = LxWire.respToResult(m as LxWire.Incoming.Resp)
        assertEquals("http://a/x.flac", LxWire.extractUrl((r as LxResult.Success).data))
    }

    @Test
    fun `解码 resp - reject 文案原样透出`() {
        // 协议 §5.1："鉴权失败"/"请求过速"等可读错误必须透传给 UI（ADR-0002）
        val m = LxWire.decodeIncoming("""{"t":"resp","callId":7,"ok":false,"message":"鉴权失败"}""")!!
        val r = LxWire.respToResult(m as LxWire.Incoming.Resp) as LxResult.Failure
        assertEquals("鉴权失败", r.message)
    }

    @Test
    fun `解码 http - 完整 options`() {
        val m = LxWire.decodeIncoming(
            """{"t":"http","callId":3,"url":"https://api.example.com/u",
                "options":{"method":"POST","headers":{"X-Card-Key":"公益版"},
                "body":"{\"q\":1}","timeout":5000}}""",
        ) as LxWire.Incoming.Http
        assertEquals(3, m.callId)
        assertEquals("https://api.example.com/u", m.request.url)
        assertEquals("POST", m.request.method)
        assertEquals("公益版", m.request.headers["X-Card-Key"]) // 发行版私有鉴权头透传（协议 §6 黑名单在桥层）
        assertEquals(5000L, m.request.timeoutMs)
    }

    @Test
    fun `解码 http - 缺省 method 默认 GET`() {
        val m = LxWire.decodeIncoming("""{"t":"http","callId":3,"url":"http://a"}""") as LxWire.Incoming.Http
        assertEquals("GET", m.request.method)
    }

    // ---------- 解码：旁路与控制 ----------

    @Test
    fun `解码 updateAlert 与 log`() {
        val u = LxWire.decodeIncoming(
            """{"t":"updateAlert","name":"HYW","version":"v1.1.0","message":"修复","updateUrl":"https://x/y.js"}""",
        )!!
        assertEquals(
            LxScriptEvent.UpdateAlert("HYW", "v1.1.0", "修复", "https://x/y.js"),
            LxWire.incomingToEvent(u),
        )
        val l = LxWire.decodeIncoming("""{"t":"log","level":"error","message":"boom"}""")!!
        assertEquals(LxScriptEvent.Log("error", "boom"), LxWire.incomingToEvent(l))
    }

    @Test
    fun `解码 timer 族与 httpCancel`() {
        assertTrue(LxWire.decodeIncoming("""{"t":"setTimer","timerId":9,"ms":150}""") is LxWire.Incoming.Timer)
        assertTrue(LxWire.decodeIncoming("""{"t":"clearTimer","timerId":9}""") is LxWire.Incoming.TimerClear)
        assertTrue(LxWire.decodeIncoming("""{"t":"httpCancel","callId":4}""") is LxWire.Incoming.HttpCancel)
        assertTrue(LxWire.decodeIncoming("""{"t":"rawEvent","name":"whatever","datas":null}""") is LxWire.Incoming.RawEvent)
    }

    @Test
    fun `解码 - 损坏消息返回 null 不炸信道`() {
        assertNull(LxWire.decodeIncoming("not json"))
        assertNull(LxWire.decodeIncoming("""{"t":"unknown-type"}"""))
        assertNull(LxWire.decodeIncoming(""))
    }

    // ---------- 编码 ----------

    @Test
    fun `编码 event - request 派发结构`() {
        val info = buildJsonObject { put("type", "320k") }
        val s = LxWire.encodeEvent(11, "kw", "musicUrl", info)
        val o = Json.parseToJsonElement(s).toString()
        assertTrue(o.contains("\"callId\":11"))
        assertTrue(o.contains("\"source\":\"kw\""))
        assertTrue(o.contains("\"action\":\"musicUrl\""))
        assertTrue(s.startsWith("{\"t\":\"evt\""))
    }

    @Test
    fun `编码 httpResult - JSON 语义对象体`() {
        val s = LxWire.encodeHttpResult(
            callId = 5, err = null, statusCode = 200,
            headers = mapOf("Content-Type" to "application/json"),
            rawBody = """{"code":200}""", parsedBody = mapOf("code" to 200),
        )
        val o = Json.parseToJsonElement(s).toString()
        assertTrue(o.contains("\"statusCode\":200"))
        assertTrue(o.contains("\"code\":200"))       // parsedBody 优先（对象形态）
        assertTrue(!o.contains("\\\"code\\\""))       // 不是字符串转义
    }

    @Test
    fun `编码 httpResult - 字符串体与错误`() {
        val s = LxWire.encodeHttpResult(callId = 6, err = null, statusCode = 404, rawBody = "<html/>")
        assertTrue(s.contains("<html/>"))
        val e = LxWire.encodeHttpResult(callId = 6, err = "network down")
        assertTrue(e.contains("network down"))
        assertTrue(!e.contains("resp"))
    }

    @Test
    fun `编码 timerFired`() {
        assertTrue(LxWire.encodeTimerFired(9).contains("\"timerId\":9"))
    }

    // ---------- 值转换与出参 ----------

    @Test
    fun `valueToJson - 全类型往返`() {
        val v = mapOf("i" to 1, "s" to "x", "b" to true, "l" to listOf(1L, 2.0), "n" to null)
        val j = LxWire.valueToJson(v)
        assertTrue(j.toString().contains("\"i\":1"))
        assertTrue(j.toString().contains("null"))
    }

    @Test
    fun `extractUrl - 只认 http 与 https`() {
        assertTrue(LxWire.extractUrl(Json.parseToJsonElement("\"https://a/b.flac\""))!!.startsWith("https"))
        assertNull(LxWire.extractUrl(Json.parseToJsonElement("\"ftp://a\"")))
        assertNull(LxWire.extractUrl(null))
        assertNull(LxWire.extractUrl(Json.parseToJsonElement("{\"obj\":1}")))
    }
}
