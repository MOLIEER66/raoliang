package com.echomusic.app.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * 固定功能色（DESIGN-SYSTEM §2.1 品牌基准色板 / §2.3 语义色与音源徽章点色）。
 *
 * 这些色不随封面取色漂移：语义色永不参与动态映射（§1.3 护栏三），
 * 品牌色只在「未播放」与取色兜底时出场。动态部分见 core.designsystem.palette。
 */
object EchoColors {

    // ---- §2.1 品牌基准 · 深色（优先）----
    val PrimaryDark = Color(0xFF4FE3C1)          // 回声青
    val OnPrimaryDark = Color(0xFF052A21)
    val PrimaryContainerDark = Color(0xFF0F463A)
    val OnPrimaryContainerDark = Color(0xFFB7F5E6)

    // ---- §2.1 品牌基准 · 浅色（对照）----
    val PrimaryLight = Color(0xFF0A7F69)
    val OnPrimaryLight = Color(0xFFFFFFFF)
    val PrimaryContainerLight = Color(0xFFD3F1E7)
    val OnPrimaryContainerLight = Color(0xFF00473A)

    // ---- §2.3 语义色（不随封面变）----
    val ErrorDark = Color(0xFFFF8A80)
    val OnErrorDark = Color(0xFF2B0B08)
    val ErrorLight = Color(0xFFB3261E)
    val OnErrorLight = Color(0xFFFFFFFF)

    val SuccessDark = Color(0xFF6FE0A8)
    val SuccessLight = Color(0xFF1B7F4D)

    val WarningDark = Color(0xFFFFC46B)
    val WarningLight = Color(0xFF9A6A00)

    // ---- §2.3 音源徽章圆点色（口径全 App 一致）----
    val SourceLxPublic = Color(0xFF4FE3C1)       // 洛雪·公益版
    val SourceLxSelfHosted = Color(0xFF9D8CFF)   // 洛雪·自建
    val SourceLocal = Color(0xFF8A939B)          // 本地
    val SourceInvalid = Color(0xFFFF8A80)        // 失效

    /** 回声青的色相（165°）下的浅色泛光，用于声标/分隔线 glow（§5.6/§6.3） */
    val GlowStatic = Color(0x4D4FE3C1)
}
