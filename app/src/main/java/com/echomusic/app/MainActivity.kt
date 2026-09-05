package com.echomusic.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.echomusic.app.feature.player.PlayerViewModel
import com.echomusic.app.ui.theme.EchoTheme
import org.koin.androidx.compose.koinViewModel

/**
 * 单 Activity 入口（Edge-to-edge 全程沉浸，PRD §7）。
 * EchoTheme 接 Echo Palette 动态部分：播放中整个 App（含列表与导航）切到封面 tonal
 * 配色（DESIGN-SYSTEM §2.2），切歌走 800ms crossfade；无播放落品牌回声青基准板。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge：内容延伸进状态栏/导航栏（Android 15+ 强制）
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val playerViewModel: PlayerViewModel = koinViewModel()
            val paletteSpec by playerViewModel.paletteSpec.collectAsStateWithLifecycle()
            EchoTheme(paletteSpec = paletteSpec) {
                EchoAppRoot(playerViewModel = playerViewModel)
            }
        }
    }
}
