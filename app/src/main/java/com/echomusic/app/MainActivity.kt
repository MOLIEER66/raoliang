package com.echomusic.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import coil3.compose.AsyncImage
import com.echomusic.app.core.data.db.SongDao
import com.echomusic.app.core.data.db.SongEntity
import com.echomusic.app.ui.theme.EchoMusicTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * M0 空壳入口：显示应用名、版本号、里程碑状态和占位文案。
 * 播放能力（M1）与音源系统（M2）后续接入。
 *
 * T0 门禁冒烟（ADR-0004 D4/D5/D6）：
 *  - Koin `by inject()`：DI 解析链实测（D6 回退落点）；
 *  - songDao insert→query 一轮：Room3 运行时实测，结果写进状态行；
 *  - AsyncImage：Coil 3 渲染管线实测（本地资源，免网络）。
 * 以上冒烟代码随 T2/T4 数据层落地时移除。
 */
class MainActivity : ComponentActivity() {

    private val songDao: SongDao by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge：内容延伸进状态栏/导航栏（Android 15+ 强制，PRD §7）
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 冒烟：走真库 insert→query 一轮（id 固定 1，REPLACE 幂等）
        var smokeStatus by mutableStateOf("DI ⏳ Room ⏳")
        lifecycleScope.launch {
            songDao.insertAll(listOf(SongEntity(id = 1, title = "T0 冒烟曲目")))
            val loaded = songDao.observeAll().first()
            smokeStatus = "DI ✓ Room ✓（${loaded.size} 行）"
        }

        setContent {
            EchoMusicTheme {
                EchoHome(smokeStatus = smokeStatus)
            }
        }
    }
}

@Composable
fun EchoHome(smokeStatus: String) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "v" + BuildConfig.VERSION_NAME,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            // T0 门禁状态行：Hilt 注入 + Room3 读写回路的结果
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = smokeStatus,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            // T0 冒烟：Coil AsyncImage（本地资源模型，免网络）
            Spacer(modifier = Modifier.height(8.dp))
            AsyncImage(
                model = "android.resource://${BuildConfig.APPLICATION_ID}/${R.drawable.ic_launcher_foreground}",
                contentDescription = "Coil 冒烟图",
                modifier = Modifier.height(48.dp),
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.m0_status),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.m0_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true, name = "M0 主页")
@Composable
fun EchoHomePreview() {
    EchoMusicTheme {
        EchoHome(smokeStatus = "DI ✓ Room ✓（预览）")
    }
}
