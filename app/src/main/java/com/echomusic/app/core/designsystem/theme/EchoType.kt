package com.echomusic.app.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 字阶表（DESIGN-SYSTEM §3.2，非 M3 默认 57/32/28 的自定义字阶）：
 * 44/30/27/20/16/14/15/13/12/11/10 全部按设计稿行高与字距锁定；
 * 负字距只给大标题（30sp+）。
 */
val EchoTypography = Typography(
    // 歌单详情·歌单名（沉浸头图，最多 2 行）
    displayLarge = TextStyle(
        fontSize = 44.sp, fontWeight = FontWeight(700), lineHeight = 52.sp, letterSpacing = (-0.5).sp,
    ),
    // 页面大标题：音乐库 / 搜索前态 / 设置
    headlineLarge = TextStyle(
        fontSize = 30.sp, fontWeight = FontWeight(750), lineHeight = 38.sp, letterSpacing = (-0.4).sp,
    ),
    // 正在播放·歌名（单行省略 / 超限刊头转横排）
    headlineMedium = TextStyle(
        fontSize = 27.sp, fontWeight = FontWeight(720), lineHeight = 34.sp, letterSpacing = (-0.3).sp,
    ),
    // 区块标题、弹窗标题、歌词「纯音乐」态
    titleLarge = TextStyle(
        fontSize = 20.sp, fontWeight = FontWeight(600), lineHeight = 28.sp, letterSpacing = (-0.2).sp,
    ),
    // 卡片标题、空状态标题
    titleMedium = TextStyle(
        fontSize = 16.sp, fontWeight = FontWeight(600), lineHeight = 24.sp,
    ),
    // 歌曲行标题、列表主文字、搜索词
    titleSmall = TextStyle(
        fontSize = 14.sp, fontWeight = FontWeight(600), lineHeight = 20.sp,
    ),
    // 设置正文、说明文字
    bodyLarge = TextStyle(
        fontSize = 15.sp, fontWeight = FontWeight(400), lineHeight = 22.sp,
    ),
    // 副标题（歌手 · 专辑）、次要正文、错误细节
    bodyMedium = TextStyle(
        fontSize = 13.sp, fontWeight = FontWeight(400), lineHeight = 18.sp,
    ),
    // 时长、统计行、搜索热词、列表副标题
    labelLarge = TextStyle(
        fontSize = 12.sp, fontWeight = FontWeight(500), lineHeight = 16.sp, letterSpacing = 0.2.sp,
    ),
    // 底部导航标签、迷你条副标题、页眉小标
    labelMedium = TextStyle(
        fontSize = 11.sp, fontWeight = FontWeight(500), lineHeight = 14.sp, letterSpacing = 0.3.sp,
    ),
    // 音源徽章、角标
    labelSmall = TextStyle(
        fontSize = 10.sp, fontWeight = FontWeight(600), lineHeight = 12.sp, letterSpacing = 0.4.sp,
    ),
)

/**
 * 等宽数字（DESIGN-SYSTEM §3.1：全部时长/计数启用 tabular-nums——数字跳动时不抖动，
 * 「播放器感」最重要的隐形细节）。所有时间/计数文本必须经本扩展。
 */
fun TextStyle.tabularNums(): TextStyle = copy(fontFeatureSettings = "tnum")
