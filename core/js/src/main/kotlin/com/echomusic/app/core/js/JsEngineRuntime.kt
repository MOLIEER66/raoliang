package com.echomusic.app.core.js

import android.content.Context
import androidx.javascriptengine.IsolateStartupParameters
import androidx.javascriptengine.JavaScriptIsolate
import androidx.javascriptengine.JavaScriptSandbox
import androidx.javascriptengine.Message
import androidx.javascriptengine.MessagePort
import androidx.javascriptengine.MessagePortClient
import androidx.javascriptengine.TerminationInfo
import com.echomusic.app.core.source.LxBridge
import com.echomusic.app.core.source.LxInitResult
import com.echomusic.app.core.source.LxProtocol
import com.echomusic.app.core.source.LxResult
import com.echomusic.app.core.source.LxRuntime
import com.echomusic.app.core.source.LxScriptEvent
import com.echomusic.app.core.source.LxScriptMeta
import com.echomusic.app.core.source.wire.LxWire
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * JS Engine 沙箱运行时（ADR-0003 主选实现）：每实例一个 isolate，脚本与宿主的
 * 全部通信走单条 MessagePort（`lx-bridge`）+ JSON 文本（编解码见 [LxWire]）。
 *
 * 生命周期：[load]（身份注入 → prelude → 端口绑定 → 用户脚本 → 等 inited）→
 * [dispatchRequest]×N → [close]（幂等，联动取消在途 HTTP）。
 *
 * 沙箱保证（P0-15，ADR-0003 §3）：
 * - 脚本运行在 WebView Provider 的独立沙箱进程，OOM/崩溃只杀 isolate（addOnTerminatedCallback）；
 * - 无文件系统/进程/原生网络能力，HTTP 一律经 [bridge] 代发；
 * - isolate 崩溃 → [isAlive]=false，在途/后续 dispatch 返回 [LxResult.Error]（RUNTIME_DEAD），
 *   触发 SourceManager 的多源降级（P0-16）。
 */
class JsEngineRuntime private constructor(
    private val sandbox: JavaScriptSandbox,
    private val bridge: LxBridge,
) : LxRuntime {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 端口线程：MessagePortClient 回调 + evaluate Future 监听全走它（单线程保证消息顺序）。 */
    private val portExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "lx-port").apply { isDaemon = true }
    }

    /** 计时器：ScheduledExecutor 并发调度（Thread.sleep 串行方案会让长计时饿死后面的）。 */
    private val timerExecutor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "lx-timer").apply { isDaemon = true }
        }

    private var isolate: JavaScriptIsolate? = null
    private var port: MessagePort? = null
    private var nextCallId = 1L

    /** 业务 callId → 等待脚本 handler 结果的挂起点。 */
    private val pendingResponses = HashMap<Long, CompletableDeferred<LxResult>>()

    /** 脚本 HTTP callId → 在途 Job（isolate 销毁/脚本取消时联动 cancel，P1-4）。 */
    private val pendingHttp = HashMap<Long, kotlinx.coroutines.Job>()

    /** 脚本 setTimeout 的计时任务句柄。 */
    private val pendingTimers = HashMap<Long, java.util.concurrent.ScheduledFuture<*>>()

    private val _events = MutableSharedFlow<LxScriptEvent>(extraBufferCapacity = 128)
    override val events: SharedFlow<LxScriptEvent> = _events.asSharedFlow()

    private val _isAlive = MutableStateFlow(true)
    override val isAlive: Boolean get() = _isAlive.value

    @Volatile
    private var handlerTimeoutMs: Long = LxProtocol.DEFAULT_HANDLER_TIMEOUT_MS

    // ------------------------------------------------------------------ load

    override suspend fun load(
        scriptCode: String,
        scriptInfo: LxScriptMeta?,
        handlerTimeoutMs: Long,
    ): LxInitResult {
        this.handlerTimeoutMs = handlerTimeoutMs
        val iso = sandbox.createIsolate(
            IsolateStartupParameters().apply {
                // 对抗"内存巨兽"脚本（ADR-0003 §8）：上限 64MB，超出即杀 isolate（TerminationInfo 可观测）
                setMaxHeapSizeBytes(MAX_HEAP_BYTES)
            },
        ).also { isolate = it }
        iso.addOnTerminatedCallback(portExecutor, ::onIsolateTerminated)

        // 1) 端口先行注册（JS 侧 getNamedPort 按名匹配；消息全走 portExecutor 单线程）
        val inited = CompletableDeferred<LxInitResult>()
        port = iso.createMessageChannel(
            PORT_NAME,
            portExecutor,
            MessagePortClient { message -> onScriptMessage(message, inited) },
        )

        // 2) 宿主身份（同步赋值，prelude 读取 lx.version / currentScriptInfo）
        iso.evaluateAsync(HOST_IDENTITY_JS(scriptInfo)).await()

        // 3) prelude（同步定义 lx 骨架 + __lxHostBind；资源经 classloader 读 assets）
        iso.evaluateAsync(readAssetPrelude()).await()

        // 4) 端口绑定（async：android.getNamedPort → __lxHostBind → flush pending）
        iso.evaluateAsync(PORT_BIND_JS).await()

        // 5) 用户脚本（顶层注册 handler + send inited；握手消息进胶水 pending 队列，端口绑好后 flush）
        iso.evaluateAsync(scriptCode).await()

        // 6) 等握手（协议 §4：status:false/超时 → 禁用该源并展示可读错误）
        return try {
            withTimeout(INIT_TIMEOUT_MS) { inited.await() }
        } catch (e: Exception) {
            close()
            LxInitResult.Failed("脚本初始化超时（${INIT_TIMEOUT_MS}ms）：${e.message ?: e::class.simpleName}")
        }
    }

    // ------------------------------------------------------------------ dispatch

    override suspend fun dispatchRequest(
        source: String,
        action: String,
        info: JsonElement,
    ): LxResult {
        val live = port ?: return LxResult.Error(LxResult.Error.Kind.RUNTIME_DEAD, "尚未 load 或已关闭")
        if (!isAlive) return LxResult.Error(LxResult.Error.Kind.RUNTIME_DEAD, "isolate 已终止")
        val callId = synchronized(this) { nextCallId++ }
        val deferred = CompletableDeferred<LxResult>()
        synchronized(pendingResponses) { pendingResponses[callId] = deferred }
        try {
            live.postMessage(Message.createStringMessage(LxWire.encodeEvent(callId, source, action, info)))
        } catch (e: Exception) {
            synchronized(pendingResponses) { pendingResponses.remove(callId) }
            return LxResult.Error(LxResult.Error.Kind.RUNTIME_DEAD, "端口已关闭：${e.message}")
        }
        return try {
            withTimeout(handlerTimeoutMs) { deferred.await() }
        } catch (e: Exception) {
            synchronized(pendingResponses) { pendingResponses.remove(callId) }
            LxResult.Error(LxResult.Error.Kind.TIMEOUT, "脚本处理超时（${handlerTimeoutMs}ms）")
        }
    }

    // ------------------------------------------------------------------ 消息入口（port 线程）

    private fun onScriptMessage(message: Message, inited: CompletableDeferred<LxInitResult>) {
        if (message.type != Message.TYPE_STRING) return
        when (val incoming = LxWire.decodeIncoming(message.string) ?: return) {
            is LxWire.Incoming.Inited ->
                inited.complete(LxWire.initedToResult(incoming))
            is LxWire.Incoming.Resp -> {
                val d = synchronized(pendingResponses) { pendingResponses.remove(incoming.callId) }
                d?.complete(LxWire.respToResult(incoming))
            }
            is LxWire.Incoming.Http -> onScriptHttp(incoming)
            is LxWire.Incoming.HttpCancel -> synchronized(pendingHttp) {
                pendingHttp.remove(incoming.callId)
            }?.cancel()
            is LxWire.Incoming.UpdateAlert,
            is LxWire.Incoming.Log,
            -> LxWire.incomingToEvent(incoming)?.let(_events::tryEmit)
            is LxWire.Incoming.Timer -> scheduleTimer(incoming)
            is LxWire.Incoming.TimerClear -> synchronized(pendingTimers) {
                pendingTimers.remove(incoming.timerId)
            }?.cancel(false)
            is LxWire.Incoming.RawEvent -> Unit // 生态自定义事件 M2 不消费（前向兼容）
        }
    }

    /** 脚本 HTTP 一律宿主代发（P0-8/P0-15）；结果回灌或 err 回灌，callId 关联。 */
    private fun onScriptHttp(incoming: LxWire.Incoming.Http) {
        val callId = incoming.callId
        val job = scope.launch {
            val wire = try {
                val resp = bridge.fetch(incoming.request)
                LxWire.encodeHttpResult(
                    callId = callId,
                    err = null,
                    statusCode = resp.statusCode,
                    headers = resp.headers,
                    rawBody = resp.rawBody,
                    parsedBody = resp.parsedBody,
                )
            } catch (e: Exception) {
                LxWire.encodeHttpResult(
                    callId = callId,
                    err = e.message ?: "网络错误（${e::class.simpleName}）",
                )
            }
            postToScript(wire)
        }
        synchronized(pendingHttp) { pendingHttp[callId] = job }
        job.invokeOnCompletion { synchronized(pendingHttp) { if (pendingHttp[callId] === job) pendingHttp.remove(callId) } }
    }

    // ------------------------------------------------------------------ 计时器（P0-10）

    private fun scheduleTimer(msg: LxWire.Incoming.Timer) {
        val future = timerExecutor.schedule({
            synchronized(pendingTimers) { pendingTimers.remove(msg.timerId) }
            postToScript(LxWire.encodeTimerFired(msg.timerId))
        }, msg.ms, TimeUnit.MILLISECONDS)
        synchronized(pendingTimers) { pendingTimers[msg.timerId] = future }
    }

    // ------------------------------------------------------------------ 终止与关闭

    /** isolate 终止（含宿主主动 close）：在途业务快速失败 + 联动取消，幂等。 */
    private fun onIsolateTerminated(info: TerminationInfo) {
        if (!_isAlive.compareAndSet(expect = true, update = false)) return
        failAllPending("${info.statusString}: ${info.message}")
    }

    /** 宿主主动关闭的快速失败路径（TerminationInfo 构造器包私有，不能伪造实例）。 */
    private fun failAllPending(reason: String) {
        synchronized(pendingResponses) {
            pendingResponses.values.forEach {
                it.complete(LxResult.Error(LxResult.Error.Kind.RUNTIME_DEAD, reason))
            }
            pendingResponses.clear()
        }
        synchronized(pendingHttp) { pendingHttp.values.forEach { it.cancel() }; pendingHttp.clear() }
        synchronized(pendingTimers) { pendingTimers.values.forEach { it.cancel(false) }; pendingTimers.clear() }
    }

    override fun close() {
        failAllPending("closed by host")
        runCatching { port?.close() }
        runCatching { isolate?.close() }
        port = null
        isolate = null
        scope.cancel()
        timerExecutor.shutdownNow()
        portExecutor.shutdown()
    }

    // ------------------------------------------------------------------ 内部工具

    private fun postToScript(json: String) {
        runCatching { port?.postMessage(Message.createStringMessage(json)) }
    }

    private fun readAssetPrelude(): String =
        javaClass.classLoader!!.getResourceAsStream("lx-prelude.js")!!
            .bufferedReader().readText()

    private suspend fun ListenableFuture<Void>.awaitVoid() {
        suspendCancellableCoroutine { cont ->
            addListener({ runCatching { get() }.fold({ cont.resume(Unit) }, { cont.resume(Unit) }) }, portExecutor)
            cont.invokeOnCancellation { cancel(false) }
        }
    }

    private suspend fun ListenableFuture<String>.await(): String =
        suspendCancellableCoroutine { cont ->
            addListener({
                runCatching { get() }.fold({ cont.resume(it) }, { cont.resume("") })
            }, portExecutor)
            cont.invokeOnCancellation { cancel(false) }
        }

    private fun JavaScriptIsolate.evaluateAsync(code: String): ListenableFuture<String> =
        evaluateJavaScriptAsync(code)

    companion object {
        private const val PORT_NAME = "lx-bridge"
        private const val MAX_HEAP_BYTES = 64L * 1024 * 1024
        private const val INIT_TIMEOUT_MS = 8_000L

        /** 沙箱可用性探测（ADR-0003 R1：WebView 版本不足/被禁用时给可读降级，不崩）。 */
        fun isSupported(): Boolean = JavaScriptSandbox.isSupported()

        /**
         * 创建运行时（连接沙箱进程）。失败（系统 WebView 不可用等）返回 null，
         * 调用方降级提示；不在此处抛异常（ADR-0003 §6 回退线）。
         */
        suspend fun create(context: Context, bridge: LxBridge): JsEngineRuntime? {
            if (!JavaScriptSandbox.isSupported()) return null
            return runCatching {
                val sandbox = suspendCancellableCoroutine<JavaScriptSandbox> { cont ->
                    val future = JavaScriptSandbox.createConnectedInstanceAsync(context)
                    future.addListener({
                        runCatching { future.get() }
                            .fold({ cont.resume(it) }, { cont.resumeWith(Result.failure(it)) })
                    }, portFallbackExecutor)
                    cont.invokeOnCancellation { future.cancel(false) }
                }
                JsEngineRuntime(sandbox, bridge)
            }.getOrNull()
        }

        private val portFallbackExecutor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "lx-sandbox-init").apply { isDaemon = true }
        }

        private fun HOST_IDENTITY_JS(scriptInfo: LxScriptMeta?): String {
            val info: JsonObject = scriptInfo?.let { m ->
                buildJsonObject {
                    put("name", m.name)
                    m.version?.let { put("version", it) }
                    m.description?.let { put("description", it) }
                    m.author?.let { put("author", it) }
                    m.homepage?.let { put("homepage", it) }
                }
            } ?: JsonObject(emptyMap())
            return "globalThis.__lxHost={version:${jsonStr(LxProtocol.VERSION)},scriptInfo:$info};"
        }

        private const val PORT_BIND_JS =
            "android.getNamedPort('$PORT_NAME').then(function(p){globalThis.__lxHostBind(p);});"

        private fun jsonStr(s: String?): String =
            kotlinx.serialization.json.JsonPrimitive(s).toString()
    }
}
