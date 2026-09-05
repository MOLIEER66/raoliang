package com.echomusic.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.echomusic.app.core.designsystem.palette.AlbumCoverRef
import com.echomusic.app.core.designsystem.palette.EchoPaletteMapper
import com.echomusic.app.core.designsystem.theme.EchoRadius
import com.echomusic.app.core.model.Song

/**
 * 封面统一入口（ADR-0004 D5：显示走 Coil AsyncImage；ADR-0004 D2：封面必有圆角，
 * 全 App 无直角封面）。加载中/失败以「该曲 hash 回声渐变」垫底（§1.3 护栏二：
 * 同一首歌永远同一渐变，确定性取色）。
 *
 * @param shape 圆角：列表缩略图 r10（§4.2）、播放页大图 r26（SCREENS §2）
 */
@Composable
fun SongCover(
    song: Song?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(EchoRadius.sm),
    contentDescription: String? = null,
) {
    val fallbackBrush = rememberHashFallbackBrush(song)
    Box(modifier.clip(shape).background(fallbackBrush)) {
        if (song != null) {
            val context = LocalContext.current
            val request = remember(song.albumId, song.path) {
                ImageRequest.Builder(context)
                    .data(AlbumCoverRef(albumId = song.albumId, filePath = song.path))
                    .memoryCacheKey(song.coverCacheKey)
                    .crossfade(150)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/** 「歌手+曲名」hash → 8 组回声渐变之一（§1.3），对角线双色 */
@Composable
private fun rememberHashFallbackBrush(song: Song?): Brush {
    val isDark = isSystemInDarkTheme()
    val defaultColor = MaterialTheme.colorScheme.surfaceContainer
    return remember(song?.songKey, isDark) {
        if (song == null) {
            Brush.linearGradient(listOf(defaultColor, defaultColor))
        } else {
            val set = EchoPaletteMapper.hashFallback(song.songKey).set(isDark)
            Brush.linearGradient(
                listOf(Color(set.primary), Color(set.glow)),
            )
        }
    }
}
