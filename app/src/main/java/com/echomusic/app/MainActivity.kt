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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.echomusic.app.ui.theme.EchoTheme

/**
 * M0 空壳入口：显示应用名、版本号、里程碑状态和占位文案。
 * 音乐库 UI（SCREENS §1）在 M1 UI 波次落地。
 *
 * T0 门禁的数据库冒烟已随 T2 正式数据层移除（id=1 幂等写入与状态行依赖的 DAO 回路）；
 * Koin 解析链改由 EchoApplication 装配的真实依赖承载，Coil AsyncImage 冒烟保留至 UI 波次。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge：内容延伸进状态栏/导航栏（Android 15+ 强制，PRD §7）
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            EchoTheme {
                EchoHome()
            }
        }
    }
}

@Composable
fun EchoHome() {
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
    EchoTheme {
        EchoHome()
    }
}
