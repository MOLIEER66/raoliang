package com.echomusic.app.core.source

/**
 * 宿主网络桥（ADR-0002 安全边界）：脚本的所有 HTTP 一律经此代发。
 *
 * 实现方（T3 LxBridgeImpl，OkHttp）职责：
 * - 默认 UA `lx-music-mobile/{LxProtocol.VERSION}`，脚本显式 UA 优先（协议 §6）；
 * - header 透传制 + 黑名单制（封 Host/Cookie 等敏感头，禁白名单枚举——兼容性）；
 * - 响应 body 的 JSON 语义（needle 行为）：content-type 含 JSON → 解析为对象回灌，
 *   否则原样字符串（协议 §6：生态脚本普遍直接 `resp.body.code`）；
 * - 域名审计：全量 URL 记录（内存环形缓冲 + 落盘，SCREENS §6 脚本详情页数据源）；
 * - 明文 http 放行策略（按脚本域名，PRD §6 立场）。
 *
 * 接口保持纯 JVM（OkHttp 本身 API 级兼容 JVM），假体用 MockWebServer 覆盖。
 */
interface LxBridge {

    /** 代发一次 HTTP。失败（网络异常/超时）以 [LxHttpError] 抛出或返回 err——见 [Response]。 */
    suspend fun fetch(request: Request): Response

    /** 脚本侧 request 的可选取消句柄（P1-4：isolate 销毁时联动取消）。 */
    fun interface Cancellation {
        fun cancel()
    }

    /** 脚本 request() 的 options 子集（协议 §6：method/headers/body/timeout）。 */
    data class Request(
        val url: String,
        val method: String = "GET",
        /** 脚本显式 headers（黑名单头由实现层过滤，不在接口前置过滤）。 */
        val headers: Map<String, String> = emptyMap(),
        val body: String? = null,
        /** 单次 HTTP 超时（毫秒）；脚本不设则用实现层默认。 */
        val timeoutMs: Long? = null,
        /** 回灌时 body 按 content-type 解析 JSON 的开关（needle 行为，默认开）。 */
        val jsonSemantics: Boolean = true,
    )

    /** 回灌脚本的 resp 结构（协议 §6：{statusCode, headers, body}，body 双写保证 ===）。 */
    data class Response(
        val statusCode: Int,
        val headers: Map<String, String>,
        /** 未经 JSON 解析的原始 body 字符串（resp.body 与第三参恒等，双签名兜底）。 */
        val rawBody: String?,
        /**
         * JSON 语义下的解析产物（对象/数组/null）；非 JSON 或解析失败为 null。
         * 胶水据此决定回灌 `resp.body`（对象）还是 `body`（字符串）——两者恒同源。
         */
        val parsedBody: Any?,
    )

    /** 网络层错误（DNS/连接/超时等）——回灌脚本 callback 的第一参 err。 */
    class LxHttpError(message: String, cause: Throwable? = null) :
        RuntimeException(message, cause)
}
