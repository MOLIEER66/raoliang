package com.echomusic.app.core.source.manager

import com.echomusic.app.core.source.LxResult
import kotlinx.serialization.json.JsonElement

/**
 * 多源降级策略（T5 · P0-16，ADR-0002"单源失败自动降级"）。
 *
 * 纯函数：给定按序启用的源列表与单源执行器，依次尝试直到成功。
 * 触发降级的失败族：
 * - [LxResult.Failure]：脚本 reject（文案原样透出——可读错误，但同源再试无意义，切下一个源）；
 * - [LxResult.Error]：超时 / isolate 崩溃 / 协议错（宿主侧故障，切源）。
 *
 * 返回：首个成功源的结果；全挂时返回聚合报告（每源的失败原因，UI 展示"逐源尝试"明细）。
 */
object FallbackPolicy {

    /** 单源执行器（SourceManager 传入：runtime 存活检查 + dispatchRequest）。 */
    fun interface Attempt {
        suspend fun run(): LxResult
    }

    /** 降级结果：命中源 + 其成功数据；或全挂明细。 */
    sealed interface Resolution {
        data class Success(val sourceIndex: Int, val data: JsonElement?) : Resolution
        data class AllFailed(val attempts: List<AttemptReport>) : Resolution
    }

    /** 单源尝试报告（name = 源名，result = 失败结果）。 */
    data class AttemptReport(val source: String, val result: LxResult)

    /**
     * 依次尝试 [sources]，返回首个成功者。
     *
     * @param sources 按用户排序的启用源（id → 显示名）
     * @param attempt 源 id → 执行器（调用方保证 key 覆盖）
     */
    suspend fun resolve(
        sources: List<Pair<String, String>>,
        attempt: suspend (sourceId: String) -> LxResult,
    ): Resolution {
        val reports = mutableListOf<AttemptReport>()
        sources.forEach { (id, name) ->
            when (val r = attempt(id)) {
                is LxResult.Success -> return Resolution.Success(
                    sourceIndex = reports.size,
                    data = r.data,
                )
                is LxResult.Failure -> reports += AttemptReport(name, r)
                is LxResult.Error -> reports += AttemptReport(name, r)
            }
        }
        return Resolution.AllFailed(reports)
    }

    /** 全挂报告 → 用户可读文案（"源A：鉴权失败；源B：请求过速"——reject 原文透出）。 */
    fun summarize(failed: Resolution.AllFailed): String =
        failed.attempts.joinToString("；") { (name, r) ->
            "$name：" + when (r) {
                is LxResult.Failure -> r.message
                is LxResult.Error -> when (r.kind) {
                    LxResult.Error.Kind.TIMEOUT -> "响应超时"
                    LxResult.Error.Kind.RUNTIME_DEAD -> "脚本已停止（可能崩溃）"
                    LxResult.Error.Kind.PROTOCOL -> r.message
                }
                is LxResult.Success -> "" // unreachable
            }
        }
}
