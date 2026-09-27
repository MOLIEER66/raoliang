package com.echomusic.app.core.playback

import com.echomusic.app.core.model.SongSource

/**
 * 播放计数判定（纯 JVM，可单测）：按 BREAKDOWN T5 的回写契约，在「切歌」与「队列自然播完」
 * 两个时点把上一首/当前一首落库（经 LibraryRepository.recordPlayed，驱动「最近/最常播放」标签页）。
 *
 * 判定规则：
 *  - [onItemStarted]：新曲目成为当前曲目（入队首播/切歌）时，若上一首已「开播」且与当前不同，
 *    返回上一首待回写的本地曲目 id；
 *  - [onQueueEnded]：队列自然播完（STATE_ENDED，REPEAT_OFF 到队尾）时返回当前曲目 id；
 *  - 只对 LOCAL 源计数（mediaId 前缀判源），M2 在线曲目恒返回 null。
 *
 * 已知取舍（M1 接受）：REPEAT_MODE_ONE 逐次重播不触发 mediaItemTransition（Media3 行为），
 * 单曲循环的每次重播不重复计数；用户 2 秒内切歌也计一次播放。二者精度取舍记录在案，
 * 如需「有效收听时长」判定，M2 在本类加时长门槛即可（调用方已传时间戳，扩展点在 recordPlayed）。
 */
class PlayCountTracker {

    private var activeMediaId: String? = null

    /**
     * 新曲目成为当前曲目（入队首播 / 切歌）。
     * @return 待回写的本地曲目 id（= 上一首已开播曲目，且与当前不同、且为 LOCAL 源），否则 null
     */
    fun onItemStarted(mediaId: String?): Long? {
        val previous = activeMediaId
        activeMediaId = mediaId
        return previous?.takeIf { it != mediaId }?.localSongIdOrNull()
    }

    /**
     * 队列自然播完（STATE_ENDED）。
     * @return 当前曲目的本地曲目 id（仅 LOCAL），否则 null
     */
    fun onQueueEnded(): Long? = activeMediaId?.localSongIdOrNull()

    private fun String.localSongIdOrNull(): Long? =
        PlaybackMediaId.parse(this)
            ?.takeIf { it.source == SongSource.LOCAL }
            ?.songId
}
