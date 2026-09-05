package com.echomusic.app.core.designsystem.palette

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.echomusic.app.core.playback.PlaybackMediaId
import okio.Buffer
import okio.FileSystem

/**
 * 专辑封面加载模型（ADR-0004 D5：Coil 只管「显示」，内嵌封面走自定义 Fetcher）。
 *
 * `albumId` 是缓存语义键（与 Room `albums` 表 / `Song.coverCacheKey` 对齐），
 * `filePath` 供内嵌封面（ID3/APIC）提取；Coil 的 key 统一用 [cacheKey]，
 * 同一专辑跨曲目共享缓存。
 */
data class AlbumCoverRef(val albumId: Long, val filePath: String?) {
    val cacheKey: String get() = "album:$albumId"
}

/**
 * 内嵌封面 Fetcher（ADR-0004 D5）：`AlbumCoverRef` → 图片字节流。
 *
 * 提取优先级（DESIGN-SYSTEM §1.1 第 1 步「读取内嵌封面」）：
 *   1. `MediaMetadataRetriever` 读文件内嵌 artwork（ID3/APIC）；
 *   2. 失败/缺失 → MediaStore 专辑封面 URI 兜底；
 *   3. 两者皆空 → 抛错，由 AsyncImage 的占位层回落「hash 渐变」（§1.3 护栏二）。
 */
class AlbumArtFetcher(
    private val context: Context,
    private val ref: AlbumCoverRef,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val bytes = extractEmbeddedArtwork() ?: readAlbumArtStream()
            ?: throw IllegalArgumentException("专辑封面缺失 album=${ref.albumId}")
        val source = ImageSource(source = Buffer().write(bytes), fileSystem = FileSystem.SYSTEM)
        return SourceFetchResult(source = source, mimeType = null, dataSource = DataSource.DISK)
    }

    private fun extractEmbeddedArtwork(): ByteArray? {
        val path = ref.filePath ?: return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            retriever.embeddedPicture
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun readAlbumArtStream(): ByteArray? = runCatching {
        context.contentResolver.openInputStream(
            Uri.parse(PlaybackMediaId.albumArtworkUri(ref.albumId)),
        )?.use { it.readBytes() }
    }.getOrNull()

    /** Coil 组件注册入口（EchoApplication 的 SingletonImageLoader 工厂里 `add`） */
    class Factory(private val context: Context) : Fetcher.Factory<AlbumCoverRef> {
        override fun create(data: AlbumCoverRef, options: Options, imageLoader: ImageLoader): Fetcher =
            AlbumArtFetcher(context, data)
    }
}
