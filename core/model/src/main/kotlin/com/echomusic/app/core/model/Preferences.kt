package com.echomusic.app.core.model

/**
 * 播放模式（SCREENS §2 操作行三态；T10 的 NextPicker 按此枚举做队列决策）。
 */
enum class PlayMode {
    /** 列表循环：队尾点下一首回队首，队首点上一首回队尾 */
    REPEAT_ALL,

    /** 单曲循环（角标 1） */
    REPEAT_ONE,

    /** 随机 */
    SHUFFLE,
}

/**
 * 主题模式（SCREENS §6 设置页；设计系统深色优先，SYSTEM 跟随系统）。
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * 音乐库统计行（SCREENS §1：「1,247 首 · 本地 1,240 · 洛雪 7」）。
 */
data class LibraryStats(
    val total: Int,
    val bySource: Map<SongSource, Int> = emptyMap(),
) {
    val localCount: Int get() = bySource[SongSource.LOCAL] ?: 0
    val onlineCount: Int get() = bySource[SongSource.ONLINE] ?: 0
}
