package com.echomusic.app.core.source.wire

import com.echomusic.app.core.source.LxBridge
import com.echomusic.app.core.source.LxInitResult
import com.echomusic.app.core.source.LxProtocol
import com.echomusic.app.core.source.LxScriptEvent
import com.echomusic.app.core.source.LxSourceInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * 沙箱信道编解码（T2）：Kotlin 宿主 ↔ lx-prelude.js 之间全部消息的 JSON 协议。
 *
 * 设计原则（协议研究 §8"7 处分歧"的产物）：
 * - **宽容解码**：未知字段一律忽略（openDevTools 等桌面版语义照收不炸）；
 * - **宁多勿少**：musicInfo 等业务数据以 JsonElement 透传，宿主不解构不裁剪（§5.4）；
 * - **双签名兜底**：httpResult 的 body 同时放 resp.body（JSON 语义）与独立字段（字符串），
 *   胶水侧保证 `resp.body === body`（§6 callback 三参与两参两种写法都兼容）；
 * - **callId 关联**：request 派发与 http 代发共用单调计数器，超时/取消靠它定位。
 *
 * 消息一览（`t` = type 判别字段）：
 * ```
 * JS → Kotlin   {t:'inited', status, sources, ...未知字段}
 *               {t:'resp', callId, ok, data?, message?}        // request handler Promise 结果
 *               {t:'http', callId, url, options?}               // lx.request() 代发
 *               {t:'updateAlert', name, version, message, updateUrl}
 *               {t:'log', level, message}                      // console 捕获（1024 已截断）
 * Kotlin → JS   {t:'evt', callId, source, action, info}        // request 事件派发
 *               {t:'httpResult', callId, err?, resp?}          // 代发结果回灌
 * ```
 */
object LxWire {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // ---------- JS → Kotlin ----------

    /** 信道入站消息（胶水 postMessage 的每一条）。 */
    sealed interface Incoming {
        data class Inited(
            val status: Boolean?,
            val sources: Map<String, LxSourceInfo>,
            val openDevTools: Boolean?,
        ) : Incoming

        data class Resp(
            val callId: Long,
            val ok: Boolean,
            val data: JsonElement?,
            val message: String?,
        ) : Incoming

        data class Http(
            val callId: Long,
            val request: LxBridge.Request,
        ) : Incoming

        data class UpdateAlert(
            val name: String?,
            val version: String?,
            val message: String?,
            val updateUrl: String?,
        ) : Incoming

        data class Log(val level: String, val message: String) : Incoming

        /** 脚本 setTimeout（P0-10：宿主侧计时，timerFired 回灌）。 */
        data class Timer(val timerId: Long, val ms: Long) : Incoming

        /** 脚本 clearTimeout。 */
        data class TimerClear(val timerId: Long) : Incoming

        /** 脚本侧取消在途 HTTP（lx.request 返回的 cancel()，P1-4）。 */
        data class HttpCancel(val callId: Long) : Incoming

        /** 生态自定义事件（M2 不消费，前向兼容）。 */
        data class RawEvent(val name: String?, val datas: JsonElement?) : Incoming
    }

    /** 解码一条 JS → Kotlin 消息；无法识别/损坏的消息返回 null（宽容姿态，不炸信道）。 */
    fun decodeIncoming(raw: String): Incoming? {
        val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        return when (obj["t"]?.jsonPrimitive?.contentOrNull) {
            "inited" -> decodeInited(obj)
            "resp" -> decodeResp(obj)
            "http" -> decodeHttp(obj)
            "updateAlert" -> decodeUpdateAlert(obj)
            "log" -> decodeLog(obj)
            "setTimer" -> obj["timerId"]?.jsonPrimitive?.longOrNull?.let {
                Incoming.Timer(it, obj["ms"]?.jsonPrimitive?.longOrNull ?: 0L)
            }
            "clearTimer" -> obj["timerId"]?.jsonPrimitive?.longOrNull?.let { Incoming.TimerClear(it) }
            "httpCancel" -> obj["callId"]?.jsonPrimitive?.longOrNull?.let { Incoming.HttpCancel(it) }
            "rawEvent" -> Incoming.RawEvent(
                obj["name"]?.jsonPrimitive?.contentOrNull,
                obj["datas"] ?: JsonNull,
            )
            else -> null
        }
    }

    private fun decodeInited(obj: JsonObject): Incoming.Inited {
        val sources = mutableMapOf<String, LxSourceInfo>()
        val sourcesObj = obj["sources"] as? JsonObject
        if (sourcesObj != null) {
            for ((id, v) in sourcesObj) {
                val sv = v as? JsonObject ?: continue
                sources[id] = LxSourceInfo(
                    id = id,
                    name = sv["name"]?.jsonPrimitive?.contentOrNull ?: id,
                    type = sv["type"]?.jsonPrimitive?.contentOrNull
                        ?: LxProtocol.SOURCE_TYPE_MUSIC,
                    actions = (sv["actions"] as? JsonArray)
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                        ?.toSet()
                        ?: emptySet(),
                    qualitys = (sv["qualitys"] as? JsonArray)
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                        ?: emptyList(),
                )
            }
        }
        return Incoming.Inited(
            status = obj["status"]?.jsonPrimitive?.booleanOrNull,
            sources = sources,
            openDevTools = obj["openDevTools"]?.jsonPrimitive?.booleanOrNull, // 桌面版语义，忽略
        )
    }

    private fun decodeResp(obj: JsonObject): Incoming.Resp? {
        val callId = obj["callId"]?.jsonPrimitive?.longOrNull ?: return null
        val ok = obj["ok"]?.jsonPrimitive?.booleanOrNull ?: return null
        val message = obj["message"]?.jsonPrimitive?.contentOrNull
            ?: (obj["data"] as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
        return Incoming.Resp(
            callId = callId,
            ok = ok,
            data = if (ok) obj["data"] else null,
            message = message,
        )
    }

    private fun decodeHttp(obj: JsonObject): Incoming.Http? {
        val callId = obj["callId"]?.jsonPrimitive?.longOrNull ?: return null
        val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: return null
        val options = obj["options"] as? JsonObject
        val headers = mutableMapOf<String, String>()
        (options?.get("headers") as? JsonObject)?.forEach { (k, v) ->
            v.jsonPrimitive.contentOrNull?.let { headers[k] = it }
        }
        val method = options?.get("method")?.jsonPrimitive?.contentOrNull?.uppercase() ?: "GET"
        return Incoming.Http(
            callId = callId,
            request = LxBridge.Request(
                url = url,
                method = method,
                headers = headers,
                body = options?.get("body")?.jsonPrimitive?.contentOrNull,
                timeoutMs = options?.get("timeout")?.jsonPrimitive?.let {
                    it.longOrNull ?: it.contentOrNull?.toLongOrNull()
                },
            ),
        )
    }

    private fun decodeUpdateAlert(obj: JsonObject) = Incoming.UpdateAlert(
        name = obj["name"]?.jsonPrimitive?.contentOrNull,
        version = obj["version"]?.jsonPrimitive?.contentOrNull,
        message = obj["message"]?.jsonPrimitive?.contentOrNull,
        updateUrl = obj["updateUrl"]?.jsonPrimitive?.contentOrNull,
    )

    private fun decodeLog(obj: JsonObject): Incoming.Log? {
        val level = obj["level"]?.jsonPrimitive?.contentOrNull ?: return null
        val message = obj["message"]?.jsonPrimitive?.contentOrNull ?: return null
        return Incoming.Log(level, message)
    }

    // ---------- Kotlin → JS ----------

    /** request 事件派发（info 为已编好的业务 JSON，宿主不解构）。 */
    fun encodeEvent(callId: Long, source: String, action: String, info: JsonElement): String =
        buildJsonObject {
            put("t", "evt")
            put("callId", callId)
            put("source", source)
            put("action", action)
            put("info", info)
        }.toString()

    /**
     * 代发结果回灌。err 非空即失败（脚本 callback 第一参）；
     * 成功时 resp.body 为 JSON 语义值（对象/数组/标量）——胶水保证 `resp.body === body`。
     *
     * @param rawBody 原始 body 字符串（content-type 非 JSON 或解析失败时回灌它）
     * @param parsedBody 桥层 JSON 语义解析产物（对象时优先于 rawBody 回灌）
     */
    fun encodeHttpResult(
        callId: Long,
        err: String?,
        statusCode: Int? = null,
        headers: Map<String, String> = emptyMap(),
        rawBody: String? = null,
        parsedBody: Any? = null,
    ): String = buildJsonObject {
        put("t", "httpResult")
        put("callId", callId)
        if (err != null) put("err", err)
        if (statusCode != null) {
            put("resp", buildJsonObject {
                put("statusCode", statusCode)
                put("headers", buildJsonObject {
                    headers.forEach { (k, v) -> put(k, v) }
                })
                val body: JsonElement = when {
                    parsedBody != null -> valueToJson(parsedBody)
                    rawBody != null -> JsonPrimitive(rawBody)
                    else -> JsonNull
                }
                put("body", body)
            })
        }
    }.toString()

    /** 宿主计时器到点回灌（P0-10）。 */
    fun encodeTimerFired(timerId: Long): String =
        buildJsonObject {
            put("t", "timerFired")
            put("timerId", timerId)
        }.toString()

    // ---------- Kotlin 值 ↔ JsonElement ----------

    /** Kotlin 业务值（Map/List/String/Number/Boolean/null）→ JsonElement。 */
    fun valueToJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Int -> JsonPrimitive(value)
        is Long -> JsonPrimitive(value)
        is Double -> JsonPrimitive(value)
        is Float -> JsonPrimitive(value)
        is Map<*, *> -> buildJsonObject { value.forEach { (k, v) -> put(k.toString(), valueToJson(v)) } }
        is Iterable<*> -> JsonArray(value.map { valueToJson(it) })
        else -> JsonPrimitive(value.toString())
    }

    // ---------- 出参整理（Resp → LxResult） ----------

    /** request handler 的 resolve 数据 → LxResult.Success（musicUrl 提取 http(s) 字符串）。 */
    fun respToResult(resp: Incoming.Resp): com.echomusic.app.core.source.LxResult {
        return if (resp.ok) {
            com.echomusic.app.core.source.LxResult.Success(resp.data)
        } else {
            // reject 文案原样透出（ADR-0002"错误提示可读"）；Error 对象的 message 字段已解码兼容
            com.echomusic.app.core.source.LxResult.Failure(resp.message ?: "未知错误（脚本 reject 无文案）")
        }
    }

    /** Inited 握手 → LxInitResult（status:false/缺省 = 失败，协议 §4）。 */
    fun initedToResult(inited: Incoming.Inited): LxInitResult {
        return if (inited.status == true) {
            LxInitResult.Ready(sources = inited.sources, openDevTools = inited.openDevTools)
        } else {
            LxInitResult.Failed(
                inited.status?.let { "脚本初始化失败（status:false）" } ?: "脚本未发送就绪握手（status 缺省）",
            )
        }
    }

    /** 旁路事件 → LxScriptEvent。 */
    fun incomingToEvent(incoming: Incoming): LxScriptEvent? = when (incoming) {
        is Incoming.Log -> LxScriptEvent.Log(incoming.level, incoming.message)
        is Incoming.UpdateAlert -> LxScriptEvent.UpdateAlert(
            incoming.name, incoming.version, incoming.message, incoming.updateUrl,
        )
        else -> null
    }

    // ---------- 业务入参编码（供 SourceManager 调 dispatch 前） ----------

    /** musicUrl 的 info：{type, musicInfo}——musicInfo 透传不裁剪（§5.4）。 */
    fun encodeMusicUrlInfo(quality: String?, musicInfo: JsonElement): JsonElement =
        buildJsonObject {
            if (quality != null) put("type", quality)
            put("musicInfo", musicInfo)
        }

    /** lyric/pic 的 info：{musicInfo}。 */
    fun encodeMusicInfo(musicInfo: JsonElement): JsonElement =
        buildJsonObject { put("musicInfo", musicInfo) }

    /** 便捷：从 Resp.data 提取 musicUrl 的 http(s) 字符串（协议 §5.1 出参）。 */
    fun extractUrl(data: JsonElement?): String? {
        val s = data?.jsonPrimitiveOrNull()?.contentOrNull ?: return null
        return s.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    private fun JsonElement.jsonPrimitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive
}
