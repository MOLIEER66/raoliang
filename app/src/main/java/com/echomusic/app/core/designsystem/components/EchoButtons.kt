package com.echomusic.app.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echomusic.app.core.designsystem.theme.EchoRadius

/**
 * 按钮（DESIGN-SYSTEM §5.4）：主按钮 48 / 次按钮 tonal 40 / 药丸工具钮 38 / 幽灵按钮 40。
 * 按压 10% stateLayer；禁用容器与内容均 38%；加载态文字换 16dp 环形进度。
 * 不用 M3 Button：字阶（15/13sp、600/660 字重）与高度是设计稿自定义口径。
 */

/** 主按钮：高 48，full 圆角，primary 底 onPrimary 15/600（§5.4） */
@Composable
fun EchoPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val stateLayer by animateFloatAsState(if (pressed) 0.10f else 0f, label = "primaryStateLayer")
    val contentAlpha = if (enabled) 1f else 0.38f
    Box(
        modifier = modifier
            .height(48.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha), RoundedCornerShape(EchoRadius.full))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = stateLayer), RoundedCornerShape(EchoRadius.full)),
        )
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(
                text = text,
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight(600), lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/** 次按钮 tonal：高 40，primaryContainer 底 onPrimaryContainer 13/600，图标 18（§5.4） */
@Composable
fun EchoTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .height(40.dp)
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (enabled) 1f else 0.38f),
                RoundedCornerShape(EchoRadius.full),
            )
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Icon(it, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight(600), lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** 药丸工具钮：高 38，primary 12% 透明底（动态取色下随 primary），primary 13/660，图标 17（§5.4） */
@Composable
fun EchoPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val stateLayer by animateFloatAsState(if (pressed) 0.08f else 0f, label = "pillStateLayer")
    Box(
        modifier = modifier
            .height(38.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(EchoRadius.full))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = stateLayer), RoundedCornerShape(EchoRadius.full)),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Icon(it, contentDescription = null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight(660), lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 幽灵按钮：高 40，无底，primary 13/600，图标 18（§5.4） */
@Composable
fun EchoGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val stateLayer by animateFloatAsState(if (pressed) 0.08f else 0f, label = "ghostStateLayer")
    Box(
        modifier = modifier
            .height(40.dp)
            .background(Color.Transparent, RoundedCornerShape(EchoRadius.full))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = stateLayer), RoundedCornerShape(EchoRadius.full)),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Icon(it, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight(600), lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
