package com.echomusic.app.core.source

/**
 * 洛雪（LX）自定义音源协议常量（ADR-0002 / 协议剖析 lx-script-protocol.md）。
 *
 * 协议事实标准的"移动版"方言：宿主向脚本注入唯一全局对象 `globalThis.lx`，
 * 脚本顶层注册 `on(request)` 并主动发送 `inited` 握手，此后宿主按脚本
 * 声明的 `actions` 分发业务请求，脚本经宿主 `request()` 网络桥完成所有 HTTP。
 *
 * 本文件只放"协议本身"的常量；信道编解码见 [wire.LxWire]。
 */
object LxProtocol {

    /** 脚本可见的运行环境标识（生态事实标准：桌面 desktop / 移动 mobile）。 */
    const val ENV = "mobile"

    /**
     * 宿主实现声明的协议版本（UA 注入用 `lx-music-mobile/{version}`）。
     * 生态事实：UA 前缀 `lx-music-*` 是部分公益后端的准入校验依据。
     */
    const val VERSION = "2.0.0"

    /** 事件名常量（与官方文档、生态脚本解构引用的字段一一对应）。 */
    object Events {
        const val INITED = "inited"
        const val REQUEST = "request"
        const val UPDATE_ALERT = "updateAlert"
    }

    /** request 事件可派发的 action 集合（官方定义三件套）。 */
    object Actions {
        const val MUSIC_URL = "musicUrl"
        const val LYRIC = "lyric"
        const val PIC = "pic"
        val ALL = setOf(MUSIC_URL, LYRIC, PIC)
    }

    /** 官方音质档位枚举（qualitys 声明的已知值；未知值"能映射就映射，不能就隐藏"）。 */
    object Quality {
        const val Q_128K = "128k"
        const val Q_320K = "320k"
        const val Q_FLAC = "flac"
        const val Q_FLAC_24BIT = "flac24bit"
        const val Q_HIRES = "hires" // 生态扩展档（协议 §4），非官方枚举
    }

    /** sources 声明的 type 固定值（官方文档："目前固定值需为 music"）。 */
    const val SOURCE_TYPE_MUSIC = "music"

    /** App 对 handler 处理的整体超时（协议研究 §6：官方未固化，App 层兜底，可配置）。 */
    const val DEFAULT_HANDLER_TIMEOUT_MS = 12_000L
}
