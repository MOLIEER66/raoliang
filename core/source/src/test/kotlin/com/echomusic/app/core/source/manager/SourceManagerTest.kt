package com.echomusic.app.core.source.manager

import com.echomusic.app.core.source.LxInitResult
import com.echomusic.app.core.source.LxResult
import com.echomusic.app.core.source.LxRuntime
import com.echomusic.app.core.source.LxScriptEvent
import com.echomusic.app.core.source.LxScriptMeta
import com.echomusic.app.core.source.LxSourceInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SourceManager + FallbackPolicy 单测（T5 · BREAKDOWN §3.1）。
 * FakeLxRuntime 模拟：握手成功/失败、dispatch 按脚本注入的行为序列、崩溃态。
 */
class SourceManagerTest {

    // ------------------------------------------------------------------ Fake 运行时

    /** 可编程假体：每次 dispatch 按 script 指令出结果。 */
    private class FakeLxRuntime(
        val name: String,
        private val caps: Map<String, LxSourceInfo>,
        private val script: MutableList<(String, String) -> LxResult>,
    ) : LxRuntime {
        override val isAlive = true
        override val events: Flow<LxScriptEvent> = emptyFlow()

        override suspend fun load(
            scriptCode: String,
            scriptInfo: LxScriptMeta?,
            handlerTimeoutMs: Long,
        ): LxInitResult = LxInitResult.Ready(caps, openDevTools = false)

        override suspend fun dispatchRequest(source: String, action: String, info: kotlinx.serialization.json.JsonElement): LxResult =
            if (script.isEmpty()) LxResult.Error(LxResult.Error.Kind.PROTOCOL, "脚本指令耗尽") else script.removeAt(0)(source, action)

        override fun close() {}
    }

    private fun caps(vararg platforms: String): Map<String, LxSourceInfo> =
        platforms.associateWith {
            LxSourceInfo(
                id = it, name = it, type = "music",
                actions = setOf("musicUrl", "lyric", "pic"),
                qualitys = listOf("128k", "320k", "flac"),
            )
        }

    private val okUrl = { _: String, _: String -> LxResult.Success(JsonPrimitive("https://cdn.example/song.flac")) }

    private fun reject(msg: String) = { _: String, _: String -> LxResult.Failure(msg) }

    private fun timeout() = { _: String, _: String -> LxResult.Error(LxResult.Error.Kind.TIMEOUT, "12s") }

    // ------------------------------------------------------------------ 降级策略

    @Test
    fun `降级 - 首源 reject 文案透出后切次源成功`() = runTest {
        val r = FallbackPolicy.resolve(
            listOf("s1" to "源一", "s2" to "源二"),
        ) { if (it == "s1") reject("鉴权失败")(it, "x") else okUrl(it, "x") }
        assertTrue(r is FallbackPolicy.Resolution.Success)
    }

    @Test
    fun `降级 - 全挂时逐源报告`() = runTest {
        val r = FallbackPolicy.resolve(
            listOf("s1" to "源一", "s2" to "源二"),
        ) { if (it == "s1") reject("鉴权失败")(it, "x") else timeout()(it, "x") }
        assertTrue(r is FallbackPolicy.Resolution.AllFailed)
        val s = FallbackPolicy.summarize(r as FallbackPolicy.Resolution.AllFailed)
        assertTrue(s.contains("源一：鉴权失败"))
        assertTrue(s.contains("源二：响应超时"))
    }

    @Test
    fun `降级 - 空源列表直接全挂`() = runTest {
        val r = FallbackPolicy.resolve(emptyList()) { okUrl("", "") }
        assertTrue((r as FallbackPolicy.Resolution.AllFailed).attempts.isEmpty())
    }

    // ------------------------------------------------------------------ SourceManager

    private fun script(name: String, vararg platforms: String) =
        """/**
         * @name $name
         * @version v1.0.0
         */"""

    @Test
    fun `导入 - 头注释解析到握手全链`() = runTest {
        val store = LxScriptStore.Fake()
        val runtimes = mutableMapOf<String, FakeLxRuntime>()
        val mgr = SourceManager(
            runtimeFactory = { code, meta ->
                FakeLxRuntime(meta!!.name, caps("kw", "tx"), mutableListOf(okUrl)).also { runtimes[meta.name] = it }
            },
            scriptStore = store,
        )
        val r = mgr.import(script("源一"))
        assertTrue(r is SourceManager.ImportResult.Ready)
        assertEquals(1, mgr.sources.value.size)
        assertTrue(mgr.caps.value["源一"]!!.platforms.containsKey("kw"))
    }

    @Test
    fun `导入 - 无头注释拒绝`() = runTest {
        val mgr = SourceManager({ _, _ -> null }, LxScriptStore.Fake())
        val r = mgr.import("var a = 1")
        assertTrue(r is SourceManager.ImportResult.Rejected)
    }

    @Test
    fun `导入 - 同名重复`() = runTest {
        val store = LxScriptStore.Fake()
        val mgr = SourceManager({ _, meta -> FakeLxRuntime(meta!!.name, caps("kw"), mutableListOf(okUrl)) }, store)
        mgr.import(script("源一"))
        val r2 = mgr.import(script("源一"))
        assertTrue(r2 is SourceManager.ImportResult.Duplicate)
    }

    @Test
    fun `业务 - 首选源挂次源顶上`() = runTest {
        val store = LxScriptStore.Fake()
        val mgr = SourceManager(
            { _, meta ->
                FakeLxRuntime(
                    meta!!.name, caps("kw"),
                    mutableListOf(
                        if (meta.name == "源一") reject("请求过速") else okUrl,
                    ),
                )
            },
            store,
        )
        mgr.import(script("源一"))
        mgr.import(script("源二"))
        val out = mgr.resolveMusicUrl(JsonPrimitive("{}"), platform = "kw", quality = "320k")
        assertTrue(out.isSuccess)
        assertEquals("源二", out.viaSource)
    }

    @Test
    fun `业务 - 平台能力过滤（mg 只有源二支持）`() = runTest {
        val mgr = SourceManager({ _, meta -> FakeLxRuntime(meta!!.name, caps("kw"), mutableListOf(okUrl)) }, LxScriptStore.Fake())
        mgr.import(script("唯一源"))
        val out = mgr.resolveMusicUrl(JsonPrimitive("{}"), platform = "mg", quality = null)
        assertFalse(out.isSuccess)
        assertTrue(out.failureSummary!!.contains("mg"))
    }

    @Test
    fun `业务 - 全挂给出可读汇总`() = runTest {
        val store = LxScriptStore.Fake()
        val mgr = SourceManager(
            { _, meta -> FakeLxRuntime(meta!!.name, caps("kw"), mutableListOf(reject("服务器错误"))) },
            store,
        )
        mgr.import(script("源一"))
        val out = mgr.resolveMusicUrl(JsonPrimitive("{}"), platform = "kw", quality = null)
        assertFalse(out.isSuccess)
        assertTrue(out.failureSummary!!.contains("源一：服务器错误"))
    }

    @Test
    fun `删除 - 干净移除`() = runTest {
        val store = LxScriptStore.Fake()
        val mgr = SourceManager({ _, meta -> FakeLxRuntime(meta!!.name, caps("kw"), mutableListOf(okUrl)) }, store)
        mgr.import(script("源一"))
        mgr.delete("源一")
        assertTrue(mgr.sources.value.isEmpty())
        assertTrue(store.entries.isEmpty())
    }

    @Test
    fun `停用 - 不参与降级`() = runTest {
        val store = LxScriptStore.Fake()
        val mgr = SourceManager(
            { _, meta -> FakeLxRuntime(meta!!.name, caps("kw"), mutableListOf(okUrl)) },
            store,
        )
        mgr.import(script("源一"))
        mgr.setEnabled("源一", false)
        val out = mgr.resolveMusicUrl(JsonPrimitive("{}"), platform = "kw", quality = null)
        assertFalse(out.isSuccess)
    }

    @Test
    fun `装载 - loadAll 恢复已存脚本并握手`() = runTest {
        val store = LxScriptStore.Fake()
        // 预置一条已存脚本（模拟上次导入）
        val code = script("存量源")
        store.written["存量源"] = code
        store.upsert(LxScriptMeta("存量源", null, "v1", null, null, null, null), "存量源.js", 0)
        val mgr = SourceManager({ _, meta -> FakeLxRuntime(meta!!.name, caps("kw"), mutableListOf(okUrl)) }, store)
        mgr.loadAll()
        assertEquals(1, mgr.sources.value.size)
        assertEquals(1, mgr.caps.value.size)
    }
}
