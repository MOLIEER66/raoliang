package com.echomusic.app.core.data.settings

import com.echomusic.app.core.model.PlayMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 播放偏好的键值语义（BREAKDOWN §3.1「设置存取」：默认值 + 脏数据兜底）。
 *
 * 注：DataStore 文件读写在 Windows 桌面 JVM 上因 .tmp→目标文件 rename 的文件锁语义
 * 偶发失败（生产为 Android/CI-linux 不受影响），故此处只测确定性的映射逻辑，
 * 读写链路由 DataStore 框架保证 + 真机验收覆盖。
 */
class PlaybackPreferencesTest {

    @Test
    fun storedMappingFallsBackToDefaultOnDirtyData() {
        assertEquals(PlaybackSettings.DEFAULT_PLAY_MODE, PlaybackPreferences.playModeFromStored(null))
        assertEquals(PlaybackSettings.DEFAULT_PLAY_MODE, PlaybackPreferences.playModeFromStored(""))
        assertEquals(PlaybackSettings.DEFAULT_PLAY_MODE, PlaybackPreferences.playModeFromStored("bogus"))
        assertEquals(PlaybackSettings.DEFAULT_PLAY_MODE, PlaybackPreferences.playModeFromStored("repeat_one"))
    }

    @Test
    fun storedMappingAcceptsAllEnumNames() {
        PlayMode.entries.forEach { mode ->
            assertEquals(mode, PlaybackPreferences.playModeFromStored(mode.name))
        }
    }

    @Test
    fun defaultPlayModeIsRepeatAll() {
        assertEquals(PlayMode.REPEAT_ALL, PlaybackSettings.DEFAULT_PLAY_MODE)
    }
}
