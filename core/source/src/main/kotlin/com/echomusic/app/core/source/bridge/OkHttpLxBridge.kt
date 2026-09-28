package com.echomusic.app.core.source.bridge

import com.echomusic.app.core.source.LxBridge
import com.echomusic.app.core.source.LxProtocol
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request as OkRequest
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response as OkResponse
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OkHttp 网络桥实现（T3 · ADR-0002 安全边界的执行层）。
 *
 * 安全姿态（协议剖析 §6 的 App 裁决）：
 * - **UA 注入**：脚本未显式设 UA 时注入 `lx-music-mobile/{version}`（生态准入事实标准，P0-9）；
 * - **header 黑名单制**：Host/Cookie 等由宿主/传输层管辖的头拒绝透传——透传制 + 黑名单
 *   而非白名单枚举（生态脚本带各种私有鉴权头如 X-Card-Key，白名单枚举死得早）；
 * - **method 白名单**：GET/POST/HEAD/PUT/DELETE 之外的动词拒绝（不给脚本任意动词面）；
 * - **明文 http**：放行但经 [audit] 全量记录（M2 简化：不按域拦截，SCREENS §6 详情页可审计）；
 * - **JSON 语义**（needle 行为）：content-type 含 json 时解析为对象回灌 parsedBody，
 *   否则 parsedBody 为 null、rawBody 原样——两头写法的脚本都能活（双签名兜底的上半场）。
 *
 * 纯 JVM（OkHttp 平台无关），MockWebServer 全量单测。
 *
 * @param client 共享 OkHttpClient（可与 Coil 共用连接池；单次超时按请求覆盖）
 * @param audit 域名审计回调（每条出站 URL 记录；落盘与展示归 T5 的仓库层）
 */
class OkHttpLxBridge(
    private val client: OkHttpClient = defaultClient(),
    private val audit: (url: String) -> Unit = {},
) : LxBridge {

    override suspend fun fetch(request: LxBridge.Request): LxBridge.Response {
        audit(request.url)
        val okRequest = request.toOkHttp()

        val okResponse = suspendCancellableCoroutine { cont ->
            val call = client.newBuilder()
                .callTimeout(request.timeoutMs ?: DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build()
                .newCall(okRequest)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: OkResponse) {
                    if (cont.isActive) cont.resume(response)
                }
            })
        }

        return okResponse.use { resp ->
            val raw = resp.body?.string()
            LxBridge.Response(
                statusCode = resp.code,
                headers = resp.headers.toMultimap()
                    .mapValues { (_, v) -> v.joinToString(", ") },
                rawBody = raw,
                parsedBody = raw
                    ?.takeIf { resp.header("content-type")?.contains("json", ignoreCase = true) == true }
                    ?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() },
            )
        }
    }

    private fun LxBridge.Request.toOkHttp(): OkRequest {
        val method = method.uppercase()
        require(method in ALLOWED_METHODS) { "method not allowed: $method" }

        val headers = buildMap {
            this@toOkHttp.headers.forEach { (k, v) ->
                if (k.lowercase() !in BLOCKED_HEADERS) put(k, v)
            }
            // UA 注入（P0-9：脚本显式 UA 优先——上面已透传未被黑名单的 UA）
            if (none { it.key.lowercase() == "user-agent" }) {
                put("User-Agent", "lx-music-mobile/${LxProtocol.VERSION}")
            }
        }

        val body = if (method == "GET" || method == "HEAD") {
            if (this@toOkHttp.body != null) {
                throw IllegalArgumentException("GET/HEAD must not carry body")
            }
            null
        } else {
            val ct = headers.entries.firstOrNull { it.key.lowercase() == "content-type" }?.value
            this@toOkHttp.body?.toRequestBody(ct?.toMediaTypeOrNull())
        }

        return OkRequest.Builder()
            .url(url)
            .method(method, body)
            .apply {
                headers.forEach { (k, v) ->
                    // 生态实测（T3）：标本脚本的 X-Card-Key: 公益版 含中文，OkHttp 校验器
                    // 拒绝非 ISO-8859-1 值（Node ≥12 同样抛 ERR_INVALID_CHAR）。桥层容错：
                    // 非 ASCII 值按 UTF-8 百分号编码后发出，保请求不崩；后端若严格比对
                    // 原文，脚本 reject 文案原样透出（可读错误，ADR-0002 降级语义）。
                    header(k, sanitizeHeaderValue(v))
                }
            }
            .build()
    }

    /** 非 ASCII 的 header 值 → UTF-8 百分号编码（ASCII 直通）。 */
    private fun sanitizeHeaderValue(value: String): String {
        if (value.all { it.code in 0x20..0x7E || it.code == '\t'.code }) return value
        val sb = StringBuilder()
        value.toByteArray(Charsets.UTF_8).forEach { b ->
            val c = b.toInt() and 0xFF
            if (c in 0x20..0x7E) sb.append(c.toChar()) else sb.append('%').append("%02X".format(c))
        }
        return sb.toString()
    }

    companion object {
        /** 脚本未设 timeout 时的单次 HTTP 默认超时（独立于 handler 整体超时）。 */
        const val DEFAULT_TIMEOUT_MS = 10_000L

        /** method 白名单（needle 等价面；拒绝 CONNECT/TRACE 等动词滥用）。 */
        private val ALLOWED_METHODS = setOf("GET", "POST", "HEAD", "PUT", "DELETE")

        /**
         * header 黑名单（协议 §6：透传制 + 黑名单）：
         * - Host/Content-Length：传输层语义，脚本伪造即破坏连接；
         * - Cookie：M2 阶段统一禁（脚本鉴权应经自有 header，如标本的 X-Card-Key）；
         * - Authorization：OAuth 凭据面，脚本无权触碰；
         * - Connection/Proxy-*：连接管理面。
         */
        private val BLOCKED_HEADERS = setOf(
            "host", "content-length", "cookie", "authorization",
            "connection", "proxy-connection", "proxy-authorization",
        )

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
