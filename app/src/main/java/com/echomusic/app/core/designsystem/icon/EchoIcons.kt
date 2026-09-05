package com.echomusic.app.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 自绘图标库（DESIGN-SYSTEM §4.4 / §8.2）：18 枚内联 SVG 的 Compose 移植——
 * 24dp 栅格、2dp 圆头描边（round cap / round join）、视觉重心统一 2dp 内缩；
 * 填充态仅用于「选中 / 进行中」语义（播放、暂停、已收藏）；禁描边与填充风格混用。
 * 路径数据逐字节来自 design/mockups.html 的 <symbol> 定义（1px = 1dp）。
 */
object EchoIcons {

    private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(block).build()

    /** 填充路径（语义填充专用：播放/暂停/已收藏/上下曲三角） */
    private fun ImageVector.Builder.fillPath(block: PathBuilder.() -> Unit) = path(
        fill = SolidColor(Color.Black),
        fillAlpha = 1f,
        stroke = null,
        pathFillType = PathFillType.NonZero,
        pathBuilder = block,
    )

    /** 2dp 圆头描边路径（默认图标风格） */
    private fun ImageVector.Builder.strokePath(width: Float = 2f, block: PathBuilder.() -> Unit) = path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeAlpha = 1f,
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathFillType = PathFillType.NonZero,
        pathBuilder = block,
    )

    /** 实心圆点（More 三点等）：两段半圆拼圆 */
    private fun ImageVector.Builder.dot(cx: Float, cy: Float, r: Float) = fillPath {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, false, true, cx + r, cy)
        arcTo(r, r, 0f, false, true, cx - r, cy)
        close()
    }

    /** 描边圆（Search/Gear/Lib 的音符轮） */
    private fun ImageVector.Builder.strokeCircle(cx: Float, cy: Float, r: Float, width: Float = 2f) = strokePath(width) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, false, true, cx + r, cy)
        arcTo(r, r, 0f, false, true, cx - r, cy)
        close()
    }

    /** 圆角矩形（Pause 双柱 / Prev-Next 边柱） */
    private fun PathBuilder.roundedRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
        moveTo(x + r, y)
        lineTo(x + w - r, y)
        arcTo(r, r, 0f, false, true, x + w, y + r)
        lineTo(x + w, y + h - r)
        arcTo(r, r, 0f, false, true, x + w - r, y + h)
        lineTo(x + r, y + h)
        arcTo(r, r, 0f, false, true, x, y + h - r)
        lineTo(x, y + r)
        arcTo(r, r, 0f, false, true, x + r, y)
        close()
    }

    // ---- 播放/暂停（§4.4 语义填充）----

    val Play: ImageVector = icon("EchoPlay") {
        fillPath {
            moveTo(8.2f, 5.6f)
            lineTo(8.2f, 18.4f)
            curveToRelative(0f, 1f, 1.1f, 1.6f, 2f, 1.1f)
            lineToRelative(10.1f, -6.4f)
            curveToRelative(0.8f, -0.5f, 0.8f, -1.7f, 0f, -2.2f)
            lineTo(10.2f, 4.5f)
            curveToRelative(-0.9f, -0.5f, -2f, 0.1f, -2f, 1.1f)
            close()
        }
    }

    val Pause: ImageVector = icon("EchoPause") {
        fillPath { roundedRect(6.4f, 4.8f, 3.8f, 14.4f, 1.7f) }
        fillPath { roundedRect(13.8f, 4.8f, 3.8f, 14.4f, 1.7f) }
    }

    // ---- 上下曲（34dp 使用，填充三角 + 边柱）----

    val SkipPrevious: ImageVector = icon("EchoSkipPrevious") {
        fillPath { roundedRect(5.6f, 5.2f, 2.6f, 13.6f, 1.3f) }
        fillPath {
            moveTo(19f, 7f)
            lineTo(19f, 17f)
            curveToRelative(0f, 0.9f, -1f, 1.4f, -1.7f, 0.9f)
            lineToRelative(-7.2f, -5f)
            arcToRelative(1.1f, 1.1f, 0f, false, true, 0f, -1.8f)
            lineToRelative(7.2f, -5f)
            curveToRelative(0.7f, -0.5f, 1.7f, 0f, 1.7f, 0.9f)
            close()
        }
    }

    val SkipNext: ImageVector = icon("EchoSkipNext") {
        fillPath { roundedRect(15.8f, 5.2f, 2.6f, 13.6f, 1.3f) }
        fillPath {
            moveTo(5f, 7f)
            lineTo(5f, 17f)
            curveToRelative(0f, 0.9f, 1f, 1.4f, 1.7f, 0.9f)
            lineToRelative(7.2f, -5f)
            arcToRelative(1.1f, 1.1f, 0f, false, false, 0f, -1.8f)
            lineToRelative(-7.2f, -5f)
            curveTo(6f, 5.6f, 5f, 6.1f, 5f, 7f)
            close()
        }
    }

    // ---- 播放模式（§4.4：单曲循环=填充语义角标 1）----

    val Shuffle: ImageVector = icon("EchoShuffle") {
        strokePath {
            moveTo(3f, 7f)
            horizontalLineTo(6.4f)
            curveToRelative(1.1f, 0f, 2.1f, 0.5f, 2.7f, 1.4f)
            lineToRelative(5.8f, 7.2f)
            curveToRelative(0.6f, 0.9f, 1.6f, 1.4f, 2.7f, 1.4f)
            horizontalLineTo(20f)
        }
        strokePath {
            moveTo(3f, 17f)
            horizontalLineTo(6.4f)
            curveToRelative(1.1f, 0f, 2.1f, -0.5f, 2.7f, -1.4f)
            lineToRelative(1f, -1.3f)
        }
        strokePath {
            moveTo(13.9f, 9.7f)
            lineToRelative(1f, -1.3f)
            curveToRelative(0.6f, -0.9f, 1.6f, -1.4f, 2.7f, -1.4f)
            horizontalLineTo(20f)
        }
        strokePath {
            moveTo(17.7f, 4.7f)
            lineTo(20f, 7f)
            lineTo(17.7f, 9.3f)
        }
        strokePath {
            moveTo(17.7f, 14.7f)
            lineTo(20f, 17f)
            lineTo(17.7f, 19.3f)
        }
    }

    /** 循环环体（Repeat / RepeatOne 共用） */
    private fun ImageVector.Builder.loopBody() {
        strokePath {
            moveTo(17.6f, 3.6f)
            lineToRelative(3f, 3f)
            lineToRelative(-3f, 3f)
        }
        strokePath {
            moveTo(20.6f, 6.6f)
            horizontalLineTo(8f)
            arcToRelative(4.6f, 4.6f, 0f, false, false, -4.6f, 4.6f)
            verticalLineToRelative(0.6f)
        }
        strokePath {
            moveTo(6.4f, 20.4f)
            lineToRelative(-3f, -3f)
            lineToRelative(3f, -3f)
        }
        strokePath {
            moveTo(3.4f, 17.4f)
            horizontalLineTo(16f)
            arcToRelative(4.6f, 4.6f, 0f, false, false, 4.6f, -4.6f)
            verticalLineToRelative(-0.6f)
        }
    }

    val Repeat: ImageVector = icon("EchoRepeat") { loopBody() }

    val RepeatOne: ImageVector = icon("EchoRepeatOne") {
        loopBody()
        // 角标「1」（i-repeat1 的 1.8 宽笔画）
        strokePath(width = 1.8f) {
            moveTo(10.9f, 10.6f)
            lineToRelative(1.3f, -0.9f)
            verticalLineToRelative(4.8f)
        }
    }

    // ---- 收藏（§4.4：已收藏=填充心形；M1 未收藏态用描边心形）----

    private fun PathBuilder.heartPath() {
        moveTo(12f, 20.3f)
        curveTo(8.7f, 18f, 4.3f, 14.7f, 3.1f, 11.4f)
        curveTo(2f, 8.5f, 4f, 5.6f, 7f, 5.6f)
        curveToRelative(2f, 0f, 3.7f, 1.2f, 5f, 3.1f)
        curveToRelative(1.3f, -1.9f, 3f, -3.1f, 5f, -3.1f)
        curveToRelative(3f, 0f, 5f, 2.9f, 3.9f, 5.8f)
        curveToRelative(-1.2f, 3.3f, -5.6f, 6.6f, -8.9f, 8.9f)
        close()
    }

    val HeartFill: ImageVector = icon("EchoHeartFill") { fillPath { heartPath() } }

    val HeartOutline: ImageVector = icon("EchoHeartOutline") { strokePath { heartPath() } }

    // ---- 通用 ----

    val More: ImageVector = icon("EchoMore") {
        dot(5.5f, 12f, 1.7f)
        dot(12f, 12f, 1.7f)
        dot(18.5f, 12f, 1.7f)
    }

    val Search: ImageVector = icon("EchoSearch") {
        strokeCircle(11f, 11f, 6.3f)
        strokePath {
            moveTo(15.9f, 15.9f)
            lineToRelative(4.3f, 4.3f)
        }
    }

    /** 音乐库（双音符） */
    val Library: ImageVector = icon("EchoLibrary") {
        strokePath {
            moveTo(9.5f, 17.5f)
            verticalLineTo(6.3f)
            curveToRelative(0f, -0.5f, 0.35f, -0.9f, 0.84f, -0.98f)
            lineToRelative(8f, -1.33f)
            curveToRelative(0.6f, -0.1f, 1.16f, 0.37f, 1.16f, 0.98f)
            verticalLineTo(15.5f)
        }
        strokeCircle(7f, 17.5f, 2.6f)
        strokeCircle(17f, 15.5f, 2.6f)
    }

    /** 歌单（列表 + 加号） */
    val Playlist: ImageVector = icon("EchoPlaylist") {
        strokePath {
            moveTo(4f, 6.5f)
            horizontalLineTo(15f)
        }
        strokePath {
            moveTo(4f, 11.5f)
            horizontalLineTo(15f)
        }
        strokePath {
            moveTo(4f, 16.5f)
            horizontalLineTo(11f)
        }
        strokePath {
            moveTo(18f, 13f)
            verticalLineTo(20f)
        }
        strokePath {
            moveTo(14.5f, 16.5f)
            horizontalLineTo(21.5f)
        }
    }

    /** 设置（圆心 + 八向齿） */
    val Settings: ImageVector = icon("EchoSettings") {
        strokeCircle(12f, 12f, 3.1f)
        strokePath { moveTo(12f, 3.2f); verticalLineTo(5.5f) }
        strokePath { moveTo(12f, 18.5f); verticalLineTo(20.8f) }
        strokePath { moveTo(20.8f, 12f); horizontalLineTo(18.5f) }
        strokePath { moveTo(5.5f, 12f); horizontalLineTo(3.2f) }
        strokePath { moveTo(18.2f, 5.8f); lineToRelative(-1.6f, 1.6f) }
        strokePath { moveTo(7.4f, 16.6f); lineToRelative(-1.6f, 1.6f) }
        strokePath { moveTo(18.2f, 18.2f); lineToRelative(-1.6f, -1.6f) }
        strokePath { moveTo(7.4f, 7.4f); lineTo(5.8f, 5.8f) }
    }

    val ChevronDown: ImageVector = icon("EchoChevronDown") {
        strokePath {
            moveTo(6.5f, 9.5f)
            lineToRelative(5.5f, 5.5f)
            lineToRelative(5.5f, -5.5f)
        }
    }

    /** 队列（列表 + 实心小三角播放位） */
    val Queue: ImageVector = icon("EchoQueue") {
        strokePath {
            moveTo(4f, 6.5f)
            horizontalLineTo(15f)
        }
        strokePath {
            moveTo(4f, 11.5f)
            horizontalLineTo(15f)
        }
        strokePath {
            moveTo(4f, 16.5f)
            horizontalLineTo(10f)
        }
        fillPath {
            moveTo(14.5f, 12.9f)
            lineToRelative(5.4f, 3.1f)
            lineToRelative(-5.4f, 3.1f)
            close()
        }
    }

    val Download: ImageVector = icon("EchoDownload") {
        strokePath {
            moveTo(12f, 4.5f)
            verticalLineTo(14f)
        }
        strokePath {
            moveTo(8f, 10.5f)
            lineToRelative(4f, 4f)
            lineToRelative(4f, -4f)
        }
        strokePath {
            moveTo(5f, 19.5f)
            horizontalLineTo(19f)
        }
    }

    val Sort: ImageVector = icon("EchoSort") {
        strokePath {
            moveTo(8f, 5.5f)
            verticalLineTo(18.5f)
        }
        strokePath {
            moveTo(5f, 8f)
            lineToRelative(3f, -3f)
            lineToRelative(3f, 3f)
        }
        strokePath {
            moveTo(16f, 18.5f)
            verticalLineTo(5.5f)
        }
        strokePath {
            moveTo(13f, 16f)
            lineToRelative(3f, 3f)
            lineToRelative(3f, -3f)
        }
    }

    val Clear: ImageVector = icon("EchoClear") {
        strokePath {
            moveTo(6.5f, 6.5f)
            lineToRelative(11f, 11f)
        }
        strokePath {
            moveTo(17.5f, 6.5f)
            lineToRelative(-11f, 11f)
        }
    }
}
