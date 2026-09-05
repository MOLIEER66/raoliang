package com.echomusic.app.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp

/**
 * 动效档位与缓动曲线（DESIGN-SYSTEM §6.1/§6.2）+ 形状/间距令牌（§4.1/§4.2）。
 * 全部为设计稿锁定的唯一口径，组件内禁止出现表外 magic number。
 */
object EchoMotion {

    // ---- §6.1 时长档位 ----
    const val INSTANT_MS = 80     // 按压反馈
    const val FAST_MS = 150       // 交叉淡入
    const val BASE_MS = 250       // 控件出入场
    const val SLOW_MS = 400       // 共享元素
    const val EXPRESSIVE_MS = 600 // 调色、大幅位移
    const val THEME_MS = 800      // Echo Palette 切换

    // ---- §6.2 缓动曲线 ----
    /** emphasized cubic-bezier(0.2, 0, 0, 1)——进场、展开、共享转场默认 */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** standard cubic-bezier(0.4, 0, 0.2, 1)——一般属性 */
    val Standard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** decelerate cubic-bezier(0, 0, 0, 1)——手势释放 */
    val Decelerate = CubicBezierEasing(0f, 0f, 0f, 1f)

    /** spring（dampingRatio 0.85 / stiffness 380）——播放/暂停图标变形、导航声波（§6.2） */
    fun <T> echoSpring() = spring<T>(dampingRatio = 0.85f, stiffness = 380f)

    fun expressive() = tween<Float>(EXPRESSIVE_MS, easing = Emphasized)

    fun theme() = tween<Float>(THEME_MS, easing = LinearEasing)

    /** Reduce motion（§6.3d）：全部退化 */
    fun reduceMotion() = tween<Float>(FAST_MS, easing = LinearEasing)
}

/** §4.1 间距令牌（4dp 栅格） */
object EchoSpacing {
    val s4 = 4.dp
    val s8 = 8.dp
    val s12 = 12.dp
    val s16 = 16.dp
    val s20 = 20.dp
    val s24 = 24.dp
    val s32 = 32.dp
    val s40 = 40.dp
    val s48 = 48.dp
    val s64 = 64.dp

    /** 页面水平边距 */
    const val PAGE_HORIZONTAL = 20
    /** 沉浸屏水平边距 */
    const val IMMERSIVE_HORIZONTAL = 26
}

/** §4.2 圆角体系 */
object EchoRadius {
    val xs = 6.dp    // 进度端帽、小圆点
    val sm = 10.dp   // 44dp 列表封面、小缩略图
    val md = 14.dp   // 列表按压底、小组件
    val lg = 18.dp   // 迷你播放条、热词卡
    val xl = 24.dp   // 大封面（334/264dp）、设置分组卡
    val xxl = 28.dp  // 底部弹层顶角
    val full = 999.dp
}
