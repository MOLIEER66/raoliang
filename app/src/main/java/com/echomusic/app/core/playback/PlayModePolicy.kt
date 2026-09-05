package com.echomusic.app.core.playback

import androidx.media3.common.Player
import com.echomusic.app.core.model.PlayMode

/**
 * 播放模式状态机（BREAKDOWN T6 播放模式持久化 + T10 三态切换的数据面）。
 *
 * ADR-0004 D1 锁定的映射：循环模式 = `Player.REPEAT_MODE_*`，随机用 `setShuffleModeEnabled`。
 * 纯 JVM 可测（REPEAT_MODE_* 是编译期常量，无 Android 运行时调用）。
 */
object PlayModePolicy {

    /** ExoPlayer 侧的等价配置 */
    data class PlayerConfig(val repeatMode: Int, val shuffleEnabled: Boolean)

    /**
     * PlayMode → 播放器配置：
     *  - REPEAT_ALL：顺序循环（SCREENS §2 队尾回绕语义由播放器 REPEAT_MODE_ALL 承担）；
     *  - REPEAT_ONE：单曲循环（角标 1）；
     *  - SHUFFLE：随机（repeat 保持 ALL，整轮随机完回绕循环）。
     */
    fun playerConfig(mode: PlayMode): PlayerConfig = when (mode) {
        PlayMode.REPEAT_ALL -> PlayerConfig(Player.REPEAT_MODE_ALL, shuffleEnabled = false)
        PlayMode.REPEAT_ONE -> PlayerConfig(Player.REPEAT_MODE_ONE, shuffleEnabled = false)
        PlayMode.SHUFFLE -> PlayerConfig(Player.REPEAT_MODE_ALL, shuffleEnabled = true)
    }

    /** 三态循环切换顺序（T10 UI 切换按钮语义）：列表循环 → 单曲循环 → 随机 → 回到列表循环 */
    fun next(mode: PlayMode): PlayMode = when (mode) {
        PlayMode.REPEAT_ALL -> PlayMode.REPEAT_ONE
        PlayMode.REPEAT_ONE -> PlayMode.SHUFFLE
        PlayMode.SHUFFLE -> PlayMode.REPEAT_ALL
    }
}
