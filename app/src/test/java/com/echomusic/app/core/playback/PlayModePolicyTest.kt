package com.echomusic.app.core.playback

import androidx.media3.common.Player
import com.echomusic.app.core.model.PlayMode
import org.junit.Assert.assertEquals
import org.junit.Test

/** 播放模式状态机（ADR-0004 D1 映射 + T10 三态循环切换）的纯 JVM 用例。 */
class PlayModePolicyTest {

    @Test
    fun repeatAllMapsToLoopWithoutShuffle() {
        val config = PlayModePolicy.playerConfig(PlayMode.REPEAT_ALL)

        assertEquals(Player.REPEAT_MODE_ALL, config.repeatMode)
        assertEquals(false, config.shuffleEnabled)
    }

    @Test
    fun repeatOneMapsToRepeatOneWithoutShuffle() {
        val config = PlayModePolicy.playerConfig(PlayMode.REPEAT_ONE)

        assertEquals(Player.REPEAT_MODE_ONE, config.repeatMode)
        assertEquals(false, config.shuffleEnabled)
    }

    @Test
    fun shuffleKeepsLoopAndEnablesShuffleMode() {
        // 随机 = setShuffleModeEnabled(true) + REPEAT_ALL（整轮随机完回绕，ADR-0004 D1）
        val config = PlayModePolicy.playerConfig(PlayMode.SHUFFLE)

        assertEquals(Player.REPEAT_MODE_ALL, config.repeatMode)
        assertEquals(true, config.shuffleEnabled)
    }

    @Test
    fun threeStateCycleLoops() {
        assertEquals(
            PlayMode.REPEAT_ONE,
            PlayModePolicy.next(PlayMode.REPEAT_ALL),
        )
        assertEquals(
            PlayMode.SHUFFLE,
            PlayModePolicy.next(PlayMode.REPEAT_ONE),
        )
        assertEquals(
            PlayMode.REPEAT_ALL,
            PlayModePolicy.next(PlayMode.SHUFFLE),
        )
    }
}
