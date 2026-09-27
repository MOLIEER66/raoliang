package com.echomusic.app.core.model

/**
 * 曲目来源（ADR-0004 D2：songs 表用 source 统一建模）。
 * M1 全为 [LOCAL]；M2 在线曲目（洛雪音源）复用同一列表/队列模型。
 */
enum class SongSource {
    LOCAL,
    ONLINE,
}

/**
 * 曲目领域模型（source 无关，SCREENS §1/§2 音乐库与播放页所需字段全覆盖）。
 *
 * 纯 Kotlin、零 Android 依赖（D3 分包纪律：core.model 可整包 JVM 单测）。
 * `lastPlayedAtMs` / `playCount` 是「最近播放 / 最常播放」两个标签页的数据依据（SCREENS §1）。
 *
 * @property id            去重主键：本地曲目 = MediaStore `_id`（稳定、系统维护，ADR-0004 D2）；
 *                         M2 在线曲目用负数合成 id（-hash），避免主键迁移
 * @property source        曲目来源，M1 恒为 [SongSource.LOCAL]
 * @property title         标题
 * @property artist        歌手（MediaStore 空值归一为「未知歌手」）
 * @property album         专辑名（空值归一为「未知专辑」）
 * @property albumId       专辑 ID：封面缓存键（Coil key = "album:{albumId}"）、albums 表外键语义
 * @property durationMs    时长（列表行 tabular 数字、SCREENS §5.3）
 * @property sizeBytes     文件大小
 * @property path          文件路径（MediaStore `_data`；播放 URI 与封面提取的数据源）
 * @property dateModifiedSec   MediaStore `DATE_MODIFIED`（秒），增量同步的比对键（ADR-0004 D2）
 * @property year          年份（可缺）
 * @property mimeType      MIME 类型（可缺）
 * @property sourceSongId  M2 预留：音源侧曲目键 "{sourceId}:{songId}"（mediaId 同构，ADR-0004 D1）；
 *                         本地曲目恒为 null
 * @property lastPlayedAtMs 最近一次播放时间（epoch 毫秒；null = 从未播放）
 * @property playCount     累计播放次数
 */
data class Song(
    val id: Long,
    val source: SongSource = SongSource.LOCAL,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val sizeBytes: Long,
    val path: String?,
    val dateModifiedSec: Long,
    val year: Int? = null,
    val mimeType: String? = null,
    val sourceSongId: String? = null,
    val lastPlayedAtMs: Long? = null,
    val playCount: Int = 0,
) {

    /** 播放统计以外的「内容」视图：增量同步以此判定增改（时间戳/内容相等不误判）。 */
    val content: Song
        get() = copy(lastPlayedAtMs = null, playCount = 0)

    /**
     * 跨设备/跨库匹配歌词的辅助键（ADR-0004 D2：仅辅助，不做主键）。
     * songKey = title|artist|duration
     */
    val songKey: String get() = "$title|$artist|$durationMs"

    /** 播放用 MediaStore URI（仅本地曲目有效；播放层 T5 消费）。 */
    val localUri: String?
        get() = if (source == SongSource.LOCAL) {
            "content://media/external/audio/media/$id"
        } else {
            null
        }

    /** 封面缓存键（ADR-0004 D5：内存缓存键按 albumId 与 albums 表对齐）。 */
    val coverCacheKey: String get() = "album:$albumId"
}
