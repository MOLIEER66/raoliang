package com.echomusic.app.core.playback

import com.echomusic.app.core.model.Song
import com.echomusic.app.core.model.SongSource

/**
 * mediaId 编解码（ADR-0004 D1 锁定的会话契约：MediaItem 只承载「业务标识」，
 * 格式 `"{sourceId}:{songId}"`，真实播放 URL 不进 mediaId）。
 *
 * M1 本地源固定前缀 [LOCAL_SOURCE]；M2 在线曲目（洛雪）mediaId 用其音源 sourceId，
 * 与 `Song.sourceSongId` 同构。编码侧见 [PlaybackMediaId.of]，解析侧见 [parse]。
 *
 * 纯 Kotlin、零 Android 依赖（D3 分包纪律：core.playback 的纯逻辑可 JVM 单测）。
 */
object PlaybackMediaId {

    const val LOCAL_SOURCE = "local"
    private const val SEPARATOR = ":"

    /** 解码结果：曲目来源 + 数据库主键 */
    data class Decoded(val source: SongSource, val songId: Long)

    /** Song → mediaId：M2 在线曲目优先用音源侧同构键（Song.sourceSongId），本地用 "{local}:{id}" */
    fun of(song: Song): String = song.sourceSongId ?: encodeLocal(song.id)

    /** 本地曲目 → mediaId */
    fun encodeLocal(songId: Long): String = "$LOCAL_SOURCE:$songId"

    /** mediaId → [Decoded]；无法识别（空、无分隔符、id 非数字）返回 null */
    fun parse(mediaId: String?): Decoded? {
        if (mediaId.isNullOrEmpty()) return null
        val separatorIndex = mediaId.indexOf(SEPARATOR)
        if (separatorIndex <= 0) return null
        val songId = mediaId.substring(separatorIndex + SEPARATOR.length).toLongOrNull() ?: return null
        val source = when (val sourceId = mediaId.substring(0, separatorIndex)) {
            LOCAL_SOURCE -> SongSource.LOCAL
            // M2：任何非本地前缀都按在线源对待（解析点在 DataSource 层，见 PlaybackService 的锚点注释）
            else -> SongSource.ONLINE
        }
        return Decoded(source, songId)
    }

    /**
     * 通知栏/锁屏展示用封面 URI：MediaStore 专辑封面（服务端经 `Uri.parse` 注入 MediaMetadata）。
     * UI 内的封面显示走 Coil AlbumArtFetcher 读内嵌封面（ADR-0004 D5），与通知互不影响。
     */
    fun albumArtworkUri(albumId: Long): String = "content://media/external/audio/albumart/$albumId"
}
