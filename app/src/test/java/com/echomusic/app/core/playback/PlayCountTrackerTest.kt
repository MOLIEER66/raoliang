package com.echomusic.app.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * 播放统计回写条件（BREAKDOWN T5：recordPlayed 只对 LOCAL 源计数，切歌/自然播完两个时点）。
 * [PlayCountTracker] 返回待回写的曲目 id（null = 本次不落库），纯 JVM 断言。
 */
class PlayCountTrackerTest {

    private lateinit var tracker: PlayCountTracker

    @Before
    fun setUp() {
        tracker = PlayCountTracker()
    }

    @Test
    fun switchingSongsRecordsPreviousLocalSong() {
        tracker.onItemStarted("local:1")

        assertEquals(1L, tracker.onItemStarted("local:2"))
    }

    @Test
    fun naturalQueueEndRecordsCurrentSong() {
        tracker.onItemStarted("local:5")

        assertEquals(5L, tracker.onQueueEnded())
    }

    @Test
    fun onlineSongsAreNeverCounted() {
        tracker.onItemStarted("lxpublic:abc")

        // 在线曲切到本地曲：上一首（在线）不计数
        assertNull(tracker.onItemStarted("local:3"))

        // 本地曲自然播完：正常计数
        assertEquals(3L, tracker.onQueueEnded())
    }

    @Test
    fun transitionToSameItemDoesNotDoubleCount() {
        tracker.onItemStarted("local:9")

        assertNull(tracker.onItemStarted("local:9"))
    }

    @Test
    fun restartAfterEndThenEndCountsAsSecondPlay() {
        tracker.onItemStarted("local:4")
        assertEquals(4L, tracker.onQueueEnded())

        // 播完后用户按播放重播同一首（无 transition 事件），再次自然播完 = 第二次播放，再计一次
        assertEquals(4L, tracker.onQueueEnded())
    }

    @Test
    fun firstItemStartIsNotCounted() {
        assertNull(tracker.onItemStarted("local:1"))
    }

    @Test
    fun nullOrMalformedMediaIdsAreSafe() {
        assertNull(tracker.onItemStarted(null))

        // 上一首为 null（无法识别），切歌不计数；当前也不计入后续统计
        assertNull(tracker.onItemStarted("garbage"))
        assertNull(tracker.onQueueEnded())
    }

    @Test
    fun emptyTrackerEndsSilently() {
        assertNull(tracker.onQueueEnded())
    }
}
