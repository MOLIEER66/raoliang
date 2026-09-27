package com.echomusic.app.core.source

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement

/**
 * 音源脚本运行时接口（ADR-0003 §4 架构落点 / ADR-0004 D3 接口位）。
 *
 * 生命周期：`load` → `dispatchRequest`×N → `close`。
 * 实现方（:core:js 的 JsEngineRuntime）保证：
 * - 每个脚本实例一个 isolate（多源并存、故障隔离，P0-15/16 地基）；
 * - `close` 幂等，且联动取消在途 [LxBridge] 请求；
 * - isolate 崩溃后 `isAlive == false`，后续调用返回 [LxResult.Error]，不抛异常。
 *
 * 纯 JVM 假体（FakeLxRuntime）驱动上层单测；真身依赖 Android 的
 * androidx.javascriptengine，隔离在 :core:js（ADR-0003 沙箱进程模型）。
 */
interface LxRuntime {

    /**
     * 注入宿主胶水（lx-prelude）+ 脚本代码，等待 `inited` 握手。
     *
     * @param scriptCode 完整脚本源码（含头注释，头注释解析归导入管线，这里不管）
     * @param scriptInfo 注入给脚本的 currentScriptInfo（P1-3 自省；无则为 null）
     * @param handlerTimeoutMs request 事件整体超时（协议研究 §6：App 层兜底）
     */
    suspend fun load(
        scriptCode: String,
        scriptInfo: LxScriptMeta? = null,
        handlerTimeoutMs: Long = LxProtocol.DEFAULT_HANDLER_TIMEOUT_MS,
    ): LxInitResult

    /**
     * 派发 request 事件（action ∈ musicUrl/lyric/pic），挂起等待 handler 的
     * Promise resolve/reject。未声明 action 的组合快速失败（协议 §5.5）。
     *
     * @param info 业务入参（musicUrl：{type, musicInfo}；lyric/pic：{musicInfo}），
     *   由调用方经 [com.echomusic.app.core.source.wire.LxWire.encodeMusicUrlInfo] 等编码，
     *   宿主不解构（§5.4"宁多勿少"）。
     */
    suspend fun dispatchRequest(
        source: String,
        action: String,
        info: JsonElement,
    ): LxResult

    /** 脚本旁路事件（console/updateAlert）冷流；close 后自然结束。 */
    val events: Flow<LxScriptEvent>

    /** isolate 是否存活（崩溃后所有 dispatch 返回 RUNTIME_DEAD，触发多源降级）。 */
    val isAlive: Boolean

    /** 销毁 isolate 与信道（幂等）。 */
    fun close()
}
