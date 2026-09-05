package com.echomusic.app.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.echomusic.app.core.designsystem.theme.EchoRadius

/**
 * 音源徽章（DESIGN-SYSTEM §5.7）：高 17、圆角 full、水平 padding 7、1dp outline 描边、
 * labelSmall(10/600)、前置 4dp 圆点（色见 §2.3，口径全 App 一致）、无底色。
 * 本地文件在列表默认不显示徽章（降噪）；出现位置：播放页来源行、M2 搜索/在线列表行。
 */
@Composable
fun SourceBadge(
    label: String,
    dotColor: Color,
    modifier: Modifier = Modifier,
    invalid: Boolean = false,
) {
    val stroke = if (invalid) MaterialTheme.colorScheme.error.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline
    val textColor = if (invalid) MaterialTheme.colorScheme.error.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier
            .height(17.dp)
            .border(1.dp, stroke, RoundedCornerShape(EchoRadius.full))
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            Modifier
                .size(4.dp)
                .background(dotColor, RoundedCornerShape(EchoRadius.full)),
        )
        Spacer(Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = textColor)
    }
}
