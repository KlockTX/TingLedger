package com.klocktx.moneyblocks.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.klocktx.moneyblocks.core.Cents
import com.klocktx.moneyblocks.core.toYuanString
import com.klocktx.moneyblocks.domain.model.BillPalette
import com.klocktx.moneyblocks.ui.theme.Alpha
import com.klocktx.moneyblocks.ui.theme.Badge
import com.klocktx.moneyblocks.ui.theme.Bar
import com.klocktx.moneyblocks.ui.theme.BillIcons
import com.klocktx.moneyblocks.ui.theme.IconSize
import com.klocktx.moneyblocks.ui.theme.MoneyBody
import com.klocktx.moneyblocks.ui.theme.MoneyCaption
import com.klocktx.moneyblocks.ui.theme.Radius
import com.klocktx.moneyblocks.ui.theme.Spacing
import com.klocktx.moneyblocks.ui.theme.tint
import com.klocktx.moneyblocks.ui.theme.toComposeColor

/** 图标 + 账单配色，统一成圆形浅底容器，界面里所有账单标识都走它。 */
@Composable
fun BillIconBadge(
    iconKey: String,
    palette: BillPalette,
    modifier: Modifier = Modifier,
    size: Dp = Badge.lg,
    iconSize: Dp = IconSize.lg,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(palette.tint(Alpha.SelectedTint)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = BillIcons.icon(iconKey),
            contentDescription = null,
            tint = palette.toComposeColor(),
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun AmountText(
    cents: Cents,
    modifier: Modifier = Modifier,
    style: TextStyle = MoneyBody,
    color: Color = MaterialTheme.colorScheme.onSurface,
    withSymbol: Boolean = false,
) {
    Text(
        text = if (withSymbol) "¥${cents.toYuanString()}" else cents.toYuanString(),
        modifier = modifier,
        style = style,
        color = color,
    )
}

/** 细分段条：账单卡片与占比列表共用同一视觉语言。 */
@Composable
fun TrackBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = Bar.track,
    trackColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = Alpha.Track),
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(Radius.pillOf(height))
            .background(trackColor),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(Radius.pillOf(height))
                .background(color),
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

@Composable
fun MetricColumn(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xxs))
        Text(text = value, style = MoneyCaption, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** 指标列之间的竖线。首页和分析页的概览区用的是同一种分隔。 */
@Composable
fun MetricDivider() {
    Spacer(
        Modifier
            .height(Bar.metricRule)
            .width(Bar.hairline)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = Alpha.Track)),
    )
}

/**
 * 「双横」拖动把手。
 *
 * 没直接用 Material 的 drag_handle，那是三条横线，语义上更像"菜单/更多"；
 * 两条短圆横在系统列表里就是"按住我挪位置"。自己画也不用引一个手写 ImageVector。
 * 读屏拿不到形状，所以语义标签显式给上。
 */
@Composable
fun ReorderHandle(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Column(
        modifier = modifier.semantics { contentDescription = "拖动排序" },
        verticalArrangement = Arrangement.spacedBy(Bar.legendGap, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        repeat(2) {
            Box(
                Modifier
                    .width(Bar.legendRule)
                    .height(Bar.thin)
                    .clip(Radius.Hairline)
                    .background(color),
            )
        }
    }
}
