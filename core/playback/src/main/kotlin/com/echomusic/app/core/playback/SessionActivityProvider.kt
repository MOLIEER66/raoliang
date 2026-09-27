package com.echomusic.app.core.playback

import android.content.Context
import android.content.Intent

/**
 * 宿主页面意图提供方（T1 拆线引入）。
 *
 * 拆线前 [PlaybackService] 直接引用 app 层的 MainActivity 构造通知栏回跳 PendingIntent；
 * 拆线后 :core:playback 不得反向依赖 app（ADR-0004 D3 单向依赖纪律），
 * 改由 app 装配层经 Koin 注入宿主页面意图——保持编译期安全：MainActivity 改名/搬家时编译器兜底，
 * 而非运行期字符串失联。
 */
fun interface SessionActivityProvider {
    fun create(context: Context): Intent
}
