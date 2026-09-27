package com.echomusic.app.core.data.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 本地音频读取权限的运行时判断（ADR-0004 D2 / BREAKDOWN T3）。
 *
 * 只做「授/不授」两态判断（音频无部分授权态）；权限弹窗与
 * 授予/拒绝/永久拒绝三分支的路由是 UI 波次（T4）的事，那里用 Robolectric 测。
 */
object AudioPermissions {

    /** 请求与 manifest 检查统一入口：13+ 用细分权限，26–32 用旧权限 */
    val manifestPermission: String
        get() = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    /** 音频读取权限当前是否已授予 */
    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, manifestPermission) ==
            PackageManager.PERMISSION_GRANTED
}
