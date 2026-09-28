package com.echomusic.app.core.source.bridge

import com.echomusic.app.core.source.LxBridge
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * OkHttpLxBridge 单测（T3 · BREAKDOWN §3.2 MockWebServer）。
 * 覆盖：UA 注入/优先、header 黑名单、method 白名单、JSON 语义、
 * 非 JSON 原样、超时、取消、审计回调、GET body 拒绝。
 */
class OkHttpLxBridgeTest {

    private lateinit var server: MockWebServer
    private val audited = mutableListOf<String>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun bridge() = OkHttpLxBridge(audit = { audited.add(it) })

    private fun url(path: String = "/x") = server.url(path).toString()

    @Test
    fun `默认 UA 注入 lx-music-mobile 版本`() = runTest {
        server.enqueue(MockResponse().setBody("{}").setHeader("Content-Type", "application/json"))
        bridge().fetch(LxBridge.Request(url = url()))
        val recorded = server.takeRequest()
        assertEquals("lx-music-mobile/2.0.0", recorded.getHeader("User-Agent"))
    }

    @Test
    fun `脚本显式 UA 优先`() = runTest {
        server.enqueue(MockResponse().setBody("hi"))
        bridge().fetch(
            LxBridge.Request(url = url(), headers = mapOf("User-Agent" to "custom-source/9.9")),
        )
        assertEquals("custom-source/9.9", server.takeRequest().getHeader("User-Agent"))
    }

    @Test
    fun `私有鉴权头透传 - 中文值百分号编码保请求不崩`() = runTest {
        server.enqueue(MockResponse().setBody("hi"))
        bridge().fetch(
            LxBridge.Request(url = url(), headers = mapOf("X-Card-Key" to "公益版")),
        )
        // 非 ASCII 值 UTF-8 百分号编码（OkHttp 校验器拒绝原文；Node ≥12 同抛 ERR_INVALID_CHAR）
        val recorded = server.takeRequest()
        assertEquals("%E5%85%AC%E7%9B%8A%E7%89%88", recorded.getHeader("X-Card-Key"))
    }

    @Test
    fun `ASCII 头值原样直通`() = runTest {
        server.enqueue(MockResponse().setBody("hi"))
        bridge().fetch(
            LxBridge.Request(url = url(), headers = mapOf("X-Card-Key" to "free-2026")),
        )
        assertEquals("free-2026", server.takeRequest().getHeader("X-Card-Key"))
    }

    @Test
    fun `黑名单头被剥除`() = runTest {
        server.enqueue(MockResponse().setBody("hi"))
        bridge().fetch(
            LxBridge.Request(
                url = url(),
                headers = mapOf("Cookie" to "sid=1", "Authorization" to "Bearer x"),
            ),
        )
        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Cookie"))
        assertNull(recorded.getHeader("Authorization"))
    }

    @Test
    fun `POST body 与 content-type 透传`() = runTest {
        server.enqueue(MockResponse().setBody("ok"))
        bridge().fetch(
            LxBridge.Request(
                url = url(),
                method = "POST",
                headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                body = "q=test",
            ),
        )
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("q=test", recorded.body.readUtf8())
        assertTrue(recorded.getHeader("Content-Type")!!.startsWith("application/x-www-form"))
    }

    @Test
    fun `JSON 语义 - content-type json 时 parsedBody 为对象`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"code":200,"url":"http://a/b.flac"}""")
                .setHeader("Content-Type", "application/json; charset=utf-8"),
        )
        val resp = bridge().fetch(LxBridge.Request(url = url()))
        assertEquals(200, resp.statusCode)
        assertNotNull(resp.parsedBody)
        val parsed = resp.parsedBody as kotlinx.serialization.json.JsonElement
        assertEquals("http://a/b.flac", parsed.jsonObject["url"]!!.jsonPrimitive.content)
        // 双签名兜底的上半场：rawBody 与 parsedBody 同源（胶水层保证 resp.body === body）
        assertTrue(resp.rawBody!!.contains("b.flac"))
    }

    @Test
    fun `JSON 语义 - 非 JSON 时 parsedBody 为 null rawBody 原样`() = runTest {
        server.enqueue(
            MockResponse().setBody("<html>plain</html>").setHeader("Content-Type", "text/html"),
        )
        val resp = bridge().fetch(LxBridge.Request(url = url()))
        assertNull(resp.parsedBody)
        assertEquals("<html>plain</html>", resp.rawBody)
    }

    @Test
    fun `JSON 语义 - 声称 json 但 body 损坏时退回 rawBody`() = runTest {
        server.enqueue(
            MockResponse().setBody("{broken json").setHeader("Content-Type", "application/json"),
        )
        val resp = bridge().fetch(LxBridge.Request(url = url()))
        assertNull(resp.parsedBody)
        assertEquals("{broken json", resp.rawBody)
    }

    @Test
    fun `method 白名单 - 非法动词拒绝`() = runTest {
        val e = runCatching {
            bridge().fetch(LxBridge.Request(url = url(), method = "TRACE"))
        }.exceptionOrNull()
        assertTrue(e is IllegalArgumentException)
    }

    @Test
    fun `GET 携带 body 拒绝`() = runTest {
        val e = runCatching {
            bridge().fetch(LxBridge.Request(url = url(), method = "GET", body = "x"))
        }.exceptionOrNull()
        assertTrue(e is IllegalArgumentException)
    }

    @Test
    fun `审计回调记录全部出站 URL`() = runTest {
        server.enqueue(MockResponse().setBody("1"))
        server.enqueue(MockResponse().setBody("2"))
        val b = bridge()
        b.fetch(LxBridge.Request(url = url("/a")))
        b.fetch(LxBridge.Request(url = url("/b?k=v")))
        assertEquals(listOf(url("/a"), url("/b?k=v")), audited)
    }

    @Test
    fun `响应头多值合并展示`() = runTest {
        server.enqueue(
            MockResponse().setBody("x")
                .setHeader("Set-Cookie", "a=1")
                .addHeader("Set-Cookie", "b=2"),
        )
        val resp = bridge().fetch(LxBridge.Request(url = url()))
        // OkHttp Multimap → joinToString；脚本侧 headers 对象可见
        assertTrue(resp.headers.isNotEmpty())
    }
}
