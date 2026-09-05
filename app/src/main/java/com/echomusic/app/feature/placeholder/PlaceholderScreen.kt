package com.echomusic.app.feature.placeholder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.echomusic.app.BuildConfig
import com.echomusic.app.R
import com.echomusic.app.core.designsystem.components.EchoEmptyState
import com.echomusic.app.core.designsystem.components.EchoRippleArtwork
import com.echomusic.app.core.designsystem.theme.EchoSpacing

/**
 * 占位屏（BREAKDOWN §0 折衷：底部导航 4 项全部可见，搜索/歌单/设置显示空态屏，
 * 既忠实视觉稿又避免导航返工）。文案遵守 §5.8：给原因，不做假按钮。
 */
@Composable
fun PlaceholderScreen(kind: PlaceholderKind, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val title: String
        val body: String
        when (kind) {
            PlaceholderKind.SEARCH -> {
                title = stringResource(R.string.placeholder_search_title)
                body = stringResource(R.string.placeholder_search_body)
            }
            PlaceholderKind.PLAYLIST -> {
                title = stringResource(R.string.placeholder_playlist_title)
                body = stringResource(R.string.placeholder_playlist_body)
            }
            PlaceholderKind.SETTINGS -> {
                title = stringResource(R.string.placeholder_settings_title)
                body = stringResource(R.string.placeholder_settings_body, "v" + BuildConfig.VERSION_NAME)
            }
        }
        EchoEmptyState(
            title = title,
            body = body,
            artwork = { EchoRippleArtwork(size = EchoSpacing.s48) },
        )
    }
}

enum class PlaceholderKind { SEARCH, PLAYLIST, SETTINGS }
