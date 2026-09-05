package com.echomusic.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echomusic.app.core.designsystem.components.EchoBottomNav
import com.echomusic.app.core.designsystem.components.MiniPlayerBar
import com.echomusic.app.core.designsystem.components.NavItem
import com.echomusic.app.core.designsystem.components.rememberBottomNavHeight
import com.echomusic.app.core.designsystem.icon.EchoIcons
import com.echomusic.app.core.designsystem.theme.EchoMotion
import com.echomusic.app.core.designsystem.theme.EchoSpacing
import com.echomusic.app.core.playback.PlaybackStatus
import com.echomusic.app.feature.library.LibraryScreen
import com.echomusic.app.feature.placeholder.PlaceholderKind
import com.echomusic.app.feature.placeholder.PlaceholderScreen
import com.echomusic.app.feature.player.NowPlayingScreen
import com.echomusic.app.feature.player.PlayerViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * 应用导航壳（T7/T8/T10）：库 + 三个占位屏 + 迷你播放条 + 底部导航（S4 声波激活态）
 * + 正在播放页遮罩（迷你条↔播放页转场：滑入 400ms emphasized + 迷你条/导航下沉 24dp
 * 淡出 250ms，§6.3a；共享元素 morph M1 降级为普通转场，签名 morph 记入 M4）。
 * M1 自管理导航状态（无 navigation-compose：两层级页面用 saveable 状态足够，
 * 避免为两层结构引入重量依赖；若 M2 起多栈嵌套再引入并在 catalog 记录理由）。
 */
@Composable
fun EchoAppRoot(playerViewModel: PlayerViewModel = koinViewModel()) {
    val playerUi by playerViewModel.uiState.collectAsStateWithLifecycle()
    val positionMs by playerViewModel.positionMs.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var playerOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { playerViewModel.connect() }

    val navHeight = rememberBottomNavHeight()
    // 迷你条/导航的chrome层：播放页打开时下沉 24dp + 淡出 250ms（§6.3a）
    val chromeAlpha by animateFloatAsState(
        targetValue = if (playerOpen) 0f else 1f,
        animationSpec = tween(EchoMotion.BASE_MS),
        label = "chromeAlpha",
    )
    val chromeSink by animateDpAsState(
        targetValue = if (playerOpen) 24.dp else 0.dp,
        animationSpec = tween(EchoMotion.BASE_MS),
        label = "chromeSink",
    )
    val density = LocalDensity.current

    // 播放页打开时拦截返回（T10 升级为预测性返回跟手缩放）
    BackHandler(enabled = playerOpen) { playerOpen = false }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // 内容层（Tab 切换：内容 crossfade 150ms + 8dp 滑移，§6.3d）
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                val forward = targetState > initialState
                (fadeIn(tween(150)) + slideInHorizontally(tween(150)) { if (forward) it / 16 else -it / 16 })
                    .togetherWith(fadeOut(tween(150)))
            },
            modifier = Modifier.fillMaxSize(),
            label = "rootTab",
        ) { tab ->
            when (tab) {
                0 -> LibraryScreen(extraBottomPadding = if (playerUi.status != PlaybackStatus.NONE) 70.dp else 0.dp)
                1 -> PlaceholderScreen(PlaceholderKind.SEARCH)
                2 -> PlaceholderScreen(PlaceholderKind.PLAYLIST)
                else -> PlaceholderScreen(PlaceholderKind.SETTINGS)
            }
        }

        // 迷你播放条：无播放整条隐藏（§5.2），距底 = 导航高 + 8；随播放页打开下沉淡出
        if (playerUi.status != PlaybackStatus.NONE) {
            MiniPlayerBar(
                song = playerUi.song,
                progress = if (playerUi.durationMs > 0) positionMs.toFloat() / playerUi.durationMs else 0f,
                playing = playerUi.status == PlaybackStatus.PLAYING,
                buffering = playerUi.status == PlaybackStatus.BUFFERING,
                onTogglePlay = playerViewModel::togglePlayPause,
                onNext = playerViewModel::next,
                onOpen = { playerOpen = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = navHeight + EchoSpacing.s8)
                    .graphicsLayer {
                        alpha = chromeAlpha
                        translationY = with(density) { chromeSink.toPx() }
                    },
            )
        }

        // 底部导航（S4：激活项声波随播放律动）
        EchoBottomNav(
            items = listOf(
                NavItem("音乐库", EchoIcons.Library),
                NavItem("搜索", EchoIcons.Search),
                NavItem("歌单", EchoIcons.Playlist),
                NavItem("设置", EchoIcons.Settings),
            ),
            selected = selectedTab,
            onSelect = { selectedTab = it },
            playing = playerUi.status == PlaybackStatus.PLAYING,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    alpha = chromeAlpha
                    translationY = with(density) { chromeSink.toPx() }
                },
        )

        // 正在播放页：滑入 400ms emphasized + 淡入（§6.3a）
        AnimatedVisibility(
            visible = playerOpen,
            enter = slideInVertically(
                animationSpec = tween(EchoMotion.SLOW_MS, easing = EchoMotion.Emphasized),
            ) { it } + fadeIn(tween(EchoMotion.SLOW_MS)),
            exit = slideOutVertically(
                animationSpec = tween(EchoMotion.SLOW_MS, easing = EchoMotion.Emphasized),
            ) { it } + fadeOut(tween(EchoMotion.BASE_MS)),
            modifier = Modifier.fillMaxSize(),
        ) {
            NowPlayingScreen(onCollapse = { playerOpen = false })
        }
    }
}
