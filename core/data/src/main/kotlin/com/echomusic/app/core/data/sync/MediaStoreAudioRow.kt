package com.echomusic.app.core.data.sync

import com.echomusic.app.core.model.Song

/**
 * MediaStore.Audio.Media 一行的纯数据投影（无 Context/ContentResolver 依赖）。
 *
 * 由 `MediaStoreSource`（Context 薄封装）从 Cursor 读出后填入；字段归一与 Song 映射
 * 在本层完成，可整层纯 JVM 单测（BREAKDOWN §3.1「MediaStore 行 → Song 字段映射」）。
 *
 * @property dateModifiedSec MediaStore `DATE_MODIFIED`（秒，TEXT 列由调用方转 Long），
 *                          增量同步的比对键（ADR-0004 D2）
 */
data class MediaStoreAudioRow(
    val id: Long,
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumId: Long,
    val durationMs: Long,
    val sizeBytes: Long,
    val path: String?,
    val dateModifiedSec: Long,
    val year: Int? = null,
    val mimeType: String? = null,
)

/**
 * 行 → 领域模型映射。空值/异常值归一规则集中在此（库页与播放页只见干净模型）：
 *  - 空标题/歌手/专辑归一为「未知标题 / 未知歌手 / 未知专辑」；
 *  - 负时长归一为 0；
 *  - year == 0、空白 mimeType 归一为 null。
 */
fun MediaStoreAudioRow.toSong(): Song = Song(
    id = id,
    source = com.echomusic.app.core.model.SongSource.LOCAL,
    title = title?.takeIf { it.isNotBlank() } ?: UNKNOWN_TITLE,
    artist = artist?.takeIf { it.isNotBlank() } ?: UNKNOWN_ARTIST,
    album = album?.takeIf { it.isNotBlank() } ?: UNKNOWN_ALBUM,
    albumId = albumId,
    durationMs = durationMs.coerceAtLeast(0L),
    sizeBytes = sizeBytes,
    path = path,
    dateModifiedSec = dateModifiedSec,
    year = year?.takeIf { it != 0 },
    mimeType = mimeType?.takeIf { it.isNotBlank() },
)

const val UNKNOWN_TITLE = "未知标题"
const val UNKNOWN_ARTIST = "未知歌手"
const val UNKNOWN_ALBUM = "未知专辑"
