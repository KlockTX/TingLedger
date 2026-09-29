package com.klocktx.moneyblocks.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.klocktx.moneyblocks.domain.model.BillPalette
import com.klocktx.moneyblocks.ui.theme.Alpha
import com.klocktx.moneyblocks.ui.theme.Badge
import com.klocktx.moneyblocks.ui.theme.Bar
import com.klocktx.moneyblocks.ui.theme.IconSize
import com.klocktx.moneyblocks.ui.theme.Radius
import com.klocktx.moneyblocks.ui.theme.Rows
import com.klocktx.moneyblocks.ui.theme.Spacing

/**
 * 界面的容器层。之前描边卡片在六七个文件里各写一遍、圆角和描边透明度还不完全一致，
 * 这是「不够整」的根因，所以统一收口到这里。
 *
 * 这里**不再出现字面量**：圆角来自 [Radius]，透明度来自 [Alpha]。
 * 名字保留（`shape` / `innerShape` / `dialogShape`…）是因为有二十多处调用方，
 * 改调用方名字不是这次重构的目的，让它们指向同一个来源才是。
 */
object Panels {
    /** 页面里的卡片。 */
    val shape = Radius.Card

    /** 卡片内部的输入框、次级面板。 */
    val innerShape = Radius.Field

    /** 标签、色块这类小东西。 */
    val cornerSm = Radius.Chip

    /** 弹窗：盖在页面之上，圆角比卡片再大一档才好分层。 */
    val dialogShape = Radius.Overlay

    /** 主操作按钮和金额展示区这类「稍重一层」的元素。和输入框同一档。 */
    val buttonShape = Radius.Field

    /** 面板描边的透明度。 */
    const val outlineAlpha = Alpha.Outline

    /** 卡片内分隔线的透明度。 */
    const val dividerAlpha = Alpha.Divider
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValuesAll,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    shadowElevation: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = Panels.shape
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    Surface(
        modifier = modifier.then(clickableModifier),
        shape = shape,
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = shadowElevation,
        border = BorderStroke(Bar.hairline, MaterialTheme.colorScheme.outlineVariant.copy(alpha = Panels.outlineAlpha)),
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

private val PaddingValuesAll = PaddingValues(Spacing.md)

/** 列表式面板：内边距交给每一行自己管，容器只留圆角与描边。 */
val NoPadding = PaddingValues(0.dp)

/** Panel 内部的行分隔线，默认左侧留白对齐图标列。 */
@Composable
fun PanelDivider(indent: Dp = 0.dp, modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(start = indent),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = Panels.dividerAlpha),
    )
}

/**
 * 账单/条目列表里的一行。统一了图标徽章、主副标题、右侧金额和可选箭头的排布，
 * 详情页、搜索页、按日查看都复用它，避免三处各写一份而样式慢慢跑偏。
 */
@Composable
fun ItemRow(
    title: String,
    subtitle: String,
    amount: String,
    iconKey: String,
    palette: BillPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    trailingAction: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BillIconBadge(iconKey = iconKey, palette = palette, size = Badge.md, iconSize = IconSize.sm)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Spacing.hairline))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = amount,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        trailingAction?.invoke()
        if (onLongClick != null) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize.md),
            )
        }
    }
}

/** 设置类的一行：图标 + 标题 + 摘要 + 右侧值或箭头。外观/预算/导出共用。 */
@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingIcon(icon)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(Spacing.hairline))
            Text(
                text = summary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (value != null) {
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(IconSize.md),
        )
    }
}

@Composable
private fun SettingIcon(icon: ImageVector) {
    Box(
        Modifier
            .size(Badge.md)
            .clip(Radius.Field)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(IconSize.sm),
        )
    }
}

/** 可筛选的小胶囊：日期快捷、月份等场景共用。 */
@Composable
fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    leadingIcon: ImageVector? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = Panels.cornerSm,
        color = if (selected) accent.copy(alpha = Alpha.SelectedTint) else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingIcon?.let {
                Icon(it, contentDescription = null, modifier = Modifier.size(IconSize.sm))
                Spacer(Modifier.width(Spacing.xs))
            }
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * 网格里的一格可选项：图标选择、月份选择共用同一套选中态，
 * 免得两处各自维护「淡底 + 描边」的判断。
 */
@Composable
fun PickerCell(
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .padding(Spacing.xs)
            .height(Rows.grid)
            .clip(Panels.innerShape)
            .background(if (selected) accent.copy(alpha = Alpha.PressedTint) else MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                width = if (selected) Bar.hairline else 0.dp,
                color = if (selected) accent.copy(alpha = Alpha.SelectedBorder) else Color.Transparent,
                shape = Panels.innerShape,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** 选中角标：格子内容保持可见，只用小圆点表达「就是它」。需在 [PickerCell] 内使用。 */
@Composable
fun BoxScope.SelectedBadge(accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .align(Alignment.TopEnd)
            .padding(Spacing.xs)
            .size(Badge.dot)
            .clip(CircleShape)
            .background(accent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(Badge.dotGlyph),
        )
    }
}

/**
 * 少量互斥选项的分段选择器。主题和账单收支方向本是同一种控件，
 * 各写一遍就会各改一遍，所以放在这里。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentedPick(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selected,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = Alpha.SelectedTint),
                    activeContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
