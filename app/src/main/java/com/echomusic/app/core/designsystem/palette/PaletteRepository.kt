package com.echomusic.app.core.designsystem.palette

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import coil3.SingletonImageLoader
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.echomusic.app.core.model.Song
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 取色位图供给（DESIGN-SYSTEM §7：封面向下采样 128px → K-means/HCT 打分，
 * K-means 跑在 Default 调度器，结果缓存按专辑）。
 *
 * 位图解码复用 Coil 管线（与显示封面同源：[AlbumCoverRef] → [AlbumArtFetcher]），
 * 取色请求固定 128px，缓存键与显示封面隔离（`{albumId}#palette`），两条消费链互不挤占。
 * Coil 3.5 无逐请求 hardware 开关，这里统一把位图压回 ARGB_8888 软件位图
 * （[ensureSoftwareBitmap]，硬件位图不可读像素）。
 *
 * 返回 null = 走 §1.3 护栏（灰度封面/无可用簇 → 品牌回声青基准板）；
 * 无封面文件则不会进入本类——调用方对取色失败用 hash 渐变兜底。
 */
class PaletteRepository(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    private val imageLoader: ImageLoader by lazy { SingletonImageLoader.get(context) }
    private val cache = LruCache<String, EchoPaletteSpec>(CACHE_SIZE)

    /** 当前播放曲目的配色；灰度封面等护栏场景返回 null（→ 回声青基准板） */
    suspend fun paletteFor(song: Song?): EchoPaletteSpec? {
        song ?: return null
        cache.get(paletteKey(song))?.let { return it }

        val bitmap = loadCoverBitmap(song) ?: return null
        val spec = withContext(ioDispatcher) {
            val software = ensureSoftwareBitmap(bitmap)
            val pixels = IntArray(software.width * software.height)
            software.getPixels(pixels, 0, software.width, 0, 0, software.width, software.height)
            EchoPaletteExtractor.extract(pixels)
        }
        spec?.let { cache.put(paletteKey(song), it) }
        return spec
    }

    /**
     * §1.3 兜底链一步到位（播放页/主题接线用）：
     * 封面取色 → 无封面（位图拿不到）→ 「歌手+曲名」hash 选 8 组回声渐变之一（同曲同色）；
     * 有封面但全灰度（C < 8）/无可用簇 → 品牌回声青基准板。结果同样按专辑缓存。
     */
    suspend fun paletteWithFallback(song: Song): EchoPaletteSpec {
        cache.get(paletteKey(song))?.let { return it }
        val bitmap = loadCoverBitmap(song)
        val spec = if (bitmap == null) {
            EchoPaletteMapper.hashFallback(song.songKey)
        } else {
            withContext(ioDispatcher) {
                val software = ensureSoftwareBitmap(bitmap)
                val pixels = IntArray(software.width * software.height)
                software.getPixels(pixels, 0, software.width, 0, 0, software.width, software.height)
                EchoPaletteExtractor.extract(pixels)
            } ?: EchoPaletteMapper.brandBaseline()
        }
        cache.put(paletteKey(song), spec)
        return spec
    }

    private suspend fun loadCoverBitmap(song: Song): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(AlbumCoverRef(albumId = song.albumId, filePath = song.path))
            .size(EXTRACT_SIZE_PX)
            .memoryCacheKey(paletteKey(song))
            .build()
        val result = imageLoader.execute(request)
        val image = (result as? SuccessResult)?.image ?: return null
        return (image as? BitmapDrawable)?.bitmap
    }

    /** 硬件位图不可 getPixels：经 Canvas 落回 ARGB_8888 软件位图 */
    private fun ensureSoftwareBitmap(source: Bitmap): Bitmap {
        if (source.config != Bitmap.Config.HARDWARE) return source
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(output).drawBitmap(source, 0f, 0f, null)
        return output
    }

    private fun paletteKey(song: Song): String = "${song.coverCacheKey}#palette"

    private companion object {
        /** §1.1 第 1 步：下采样 128×128 */
        const val EXTRACT_SIZE_PX = 128
        const val CACHE_SIZE = 48
    }
}
