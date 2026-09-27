package com.echomusic.app.core.source

import kotlinx.serialization.json.JsonElement

/**
 * 音源脚本领域模型（ADR-0004 D3：core.source 纯 JVM，全量可单测）。
 */

/** 头注释解析产物（P0-13；解析器与容错规则见 core.source.protocol 的 T4 实现）。 */
data class LxScriptMeta(
    val name: String,
    val description: String?,
    val version: String?,
    val author: String?,
    val homepage: String?,
    /** @updateUrl：脚本自更新地址（P0-14 updateAlert 的下载源，域名需审计）。 */
    val updateUrl: String?,
    /** 原始头注释块（脚本详情页展示用）。 */
    val rawHeader: String?,
)

/** inited 握手中 sources 表的单项（官方结构 + 生态扩展容错）。 */
data class LxSourceInfo(
    /** 平台 id（sources 表的 key：kw/kg/tx/wy/mg/...，生态可能扩展未知平台）。 */
    val id: String,
    /** 源名（官方"目前非必须"，标本直接填平台 id）。 */
    val name: String,
    /** 官方固定值 music；歌单源 songlist 为桌面版扩展（P1-9，标本未用）。 */
    val type: String,
    /** 能力声明：App 必须按它分发（协议 §4：生态已全开三 action，文档未跟上）。 */
    val actions: Set<String>,
    /** 音质档位表（未知值由解析层映射/隐藏，不崩）。 */
    val qualitys: List<String>,
) {
    fun supports(action: String): Boolean = action in actions

    /** 该平台可用的音质档位（对 [LxProtocol.Quality] 已知值的有序映射，未知隐藏）。 */
    val knownQualitys: List<String>
        get() = qualitys.filter { q ->
            q in setOf(
                LxProtocol.Quality.Q_128K,
                LxProtocol.Quality.Q_320K,
                LxProtocol.Quality.Q_FLAC,
                LxProtocol.Quality.Q_FLAC_24BIT,
                LxProtocol.Quality.Q_HIRES,
            )
        }
}

/** inited 握手结果（协议 §4：status/sources + 未知字段忽略——openDevTools 等）。 */
sealed interface LxInitResult {
    /** status:true 就绪。 */
    data class Ready(
        val sources: Map<String, LxSourceInfo>,
        val openDevTools: Boolean?, // 桌面版语义，移动版宿主忽略（协议 §4 分歧 3）
    ) : LxInitResult

    /** status:false / 缺省 / 握手超时——宿主应禁用该源并展示可读错误。 */
    data class Failed(val reason: String) : LxInitResult
}

/** request 事件派发结果（musicUrl resolve 的 URL / lyric 的 JSON 串 / reject 文案）。 */
sealed interface LxResult {
    /** resolve 的原始数据（musicUrl 为字符串、lyric 为对象——按 action 语义消费）。 */
    data class Success(val data: JsonElement?) : LxResult

    /** 脚本 reject 的原始文案原样透出（ADR-0002："错误提示可读"）。 */
    data class Failure(val message: String) : LxResult

    /** 宿主侧超时/取消/isolate 崩溃（降级触发条件之一，P0-16）。 */
    data class Error(val kind: Kind, val message: String) : LxResult {
        enum class Kind { TIMEOUT, RUNTIME_DEAD, PROTOCOL }
    }
}

/** 脚本运行时事件（脚本主动推给宿主的旁路信息，非请求-响应主线）。 */
sealed interface LxScriptEvent {
    /** P0-11：console 捕获（log/warn/error，1024 截断由胶水完成）。 */
    data class Log(val level: String, val message: String) : LxScriptEvent

    /** P0-14：updateAlert（≤1 次，App 弹更新确认，确认后经用户手势下载 updateUrl 重导入）。 */
    data class UpdateAlert(
        val name: String?,
        val version: String?,
        val message: String?,
        val updateUrl: String?,
    ) : LxScriptEvent
}
