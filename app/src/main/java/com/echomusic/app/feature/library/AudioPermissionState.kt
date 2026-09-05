package com.echomusic.app.feature.library

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.echomusic.app.core.data.permissions.AudioPermissions

/**
 * 音频读取权限的 Compose 状态（BREAKDOWN T4）：走 `ActivityResultContracts.RequestPermission`
 * （不引入 accompanist），授予/拒绝/永久拒绝三分支：
 *  - 授予 → [granted] = true；
 *  - 拒绝（可再问）→ [granted] = false，[permanentlyDenied] = false，按钮 = 去授权；
 *  - 永久拒绝 → 按钮 = 去系统设置（[openAppSettings]）。
 * 从系统设置页返回时经 Lifecycle ON_RESUME 刷新授权态（预测性返回兼容，
 * 返回路由全程走 OnBackPressedDispatcher 系 API）。
 */
class AudioPermissionState internal constructor(private val context: Context) {

    var granted: Boolean by mutableStateOf(AudioPermissions.isGranted(context))
        internal set

    var permanentlyDenied: Boolean by mutableStateOf(false)
        internal set

    internal var launchRequest: ((String) -> Unit)? = null

    /** 发起系统权限弹窗（文案由系统渲染；应用内解释文案见库页未授权态） */
    fun request() {
        if (!granted) launchRequest?.invoke(AudioPermissions.manifestPermission)
    }

    /** 永久拒绝后的出路：跳应用详情页授权 */
    fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** ON_RESUME 刷新（设置页授权返回 / 弹窗关闭后兜底） */
    fun refresh() {
        granted = AudioPermissions.isGranted(context)
    }

    internal fun onRequestResult(result: Boolean) {
        granted = result
        if (result) {
            permanentlyDenied = false
        } else {
            permanentlyDenied = (context as? Activity)?.let { activity ->
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, AudioPermissions.manifestPermission)
            } ?: false
        }
    }
}

@Composable
fun rememberAudioPermissionState(): AudioPermissionState {
    val context = LocalContext.current
    val state = remember { AudioPermissionState(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        state.onRequestResult(result)
    }
    SideEffect { state.launchRequest = { permission -> launcher.launch(permission) } }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return state
}
