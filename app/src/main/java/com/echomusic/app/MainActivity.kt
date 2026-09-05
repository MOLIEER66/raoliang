package com.echomusic.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.echomusic.app.feature.library.LibraryScreen
import com.echomusic.app.ui.theme.EchoTheme

/**
 * 单 Activity 入口（Edge-to-edge 全程沉浸，PRD §7）。
 * T0 的 Coil AsyncImage 冒烟随 UI 波次落地移除（正式封面链路 = SongCover/AlbumArtFetcher）。
 * 音乐库 UI（SCREENS §1）由 T4 波次接线；完整导航壳（迷你条/播放页/底部导航）在 T7/T10。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge：内容延伸进状态栏/导航栏（Android 15+ 强制）
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            EchoTheme {
                EchoAppRoot(
                    playerOpen = false,
                    onOpenPlayer = {},
                    onClosePlayer = {},
                )
            }
        }
    }
}
