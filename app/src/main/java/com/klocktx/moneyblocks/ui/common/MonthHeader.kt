package com.klocktx.moneyblocks.ui.common

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.klocktx.moneyblocks.core.today
import com.klocktx.moneyblocks.ui.theme.Alpha
import com.klocktx.moneyblocks.ui.theme.IconSize
import com.klocktx.moneyblocks.ui.theme.Radius
import com.klocktx.moneyblocks.ui.theme.Rows
import com.klocktx.moneyblocks.ui.theme.Spacing
import java.time.LocalDate
import java.time.YearMonth

/**
 * 首页与分析页共用的顶栏。
 *
 * 左右各留一个 48dp 的固定槽，中间是月份切换器。两个页面必须走这里，
 * 不要各自摆一行——之前首页左边塞搜索按钮、分析页整行只有月份胶囊，
 * 切 tab 时那颗胶囊会横跳几十 dp，很晃眼。槽位空着也照样占位。
 */
@Composable
fun ScreenTopBar(
    monthFirst: LocalDate,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Rows.header)
            .testTag("top-bar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(Rows.minTouch), contentAlignment = Alignment.Center) { leading() }
        MonthHeader(
            monthFirst = monthFirst,
            canGoNext = canGoNext,
            onPrevious = onPrevious,
            onNext = onNext,
            onPick = onPick,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.size(Rows.minTouch), contentAlignment = Alignment.Center) { trailing() }
    }
}

/**
 * 页眉月份切换器：左右箭头 + 居中的月份胶囊，点胶囊打开月份选择面板。
 * 首页和分析页共用，两处保持一致。
 */
@Composable
fun MonthHeader(
    monthFirst: LocalDate,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val isCurrentMonth = YearMonth.from(monthFirst) == YearMonth.from(today())

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        ArrowButton(Icons.Default.ChevronLeft, "上个月", onClick = onPrevious)
        Spacer(Modifier.width(Spacing.sm))

        Surface(
            onClick = { showPicker = true },
            shape = Radius.Pill,
            color = if (isCurrentMonth) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.primary.copy(alpha = Alpha.SelectedTint),
            contentColor = if (isCurrentMonth) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f, fill = false),
        ) {
            // 换月时文字交叉淡入，而不是硬切换
            Crossfade(targetState = monthLabel(monthFirst, isCurrentMonth), label = "monthText") { text ->
                Row(
                    Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(text, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(Spacing.xs))
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = "选择月份",
                        modifier = Modifier.size(IconSize.md),
                    )
                }
            }
        }

        Spacer(Modifier.width(Spacing.sm))
        ArrowButton(Icons.Default.ChevronRight, "下个月", enabled = canGoNext, onClick = onNext)
    }

    if (showPicker) {
        MonthPickerDialog(
            current = monthFirst,
            onDismiss = { showPicker = false },
            onPick = { picked ->
                onPick(picked)
                showPicker = false
            },
        )
    }
}

@Composable
private fun ArrowButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(Rows.monthPill)
            .clip(Radius.Pill)
            .background(
                if (enabled) MaterialTheme.colorScheme.surfaceContainerLow
                else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = Alpha.Disabled),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Alpha.Muted),
            modifier = Modifier.size(IconSize.lg),
        )
    }
}

private fun monthLabel(first: LocalDate, isCurrentMonth: Boolean): String {
    val now = today()
    return when {
        isCurrentMonth -> "本月"
        first.year == now.year -> "${first.monthValue}月"
        else -> "${first.year}年${first.monthValue}月"
    }
}

/** 月份选择面板：先选年，再点月。 */
@Composable
private fun MonthPickerDialog(
    current: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    var shownYear by remember(current) { mutableStateOf(current.year) }
    val thisYear = today().year

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = Panels.dialogShape,
        title = { Text("选择月份", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArrowButton(Icons.Default.ChevronLeft, "上一年") { shownYear -= 1 }
                    Text(
                        text = "${shownYear}年",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    // 不允许翻到未来年份
                    ArrowButton(
                        Icons.Default.ChevronRight,
                        "下一年",
                        enabled = shownYear < thisYear,
                    ) { shownYear += 1 }
                }
                Spacer(Modifier.height(Spacing.md))
                (1..12).chunked(4).forEach { rowMonths ->
                    Row(Modifier.fillMaxWidth()) {
                        rowMonths.forEach { month ->
                            val candidate = LocalDate.of(shownYear, month, 1)
                            val selected = YearMonth.from(candidate) == YearMonth.from(current)
                            val future = candidate.isAfter(today().withDayOfMonth(1))
                            PickerCell(
                                selected = selected,
                                accent = MaterialTheme.colorScheme.primary,
                                enabled = !future,
                                onClick = { onPick(candidate) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = "${month}月",
                                    style = if (selected) MaterialTheme.typography.titleMedium
                                    else MaterialTheme.typography.bodyMedium,
                                    color = when {
                                        future -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Alpha.Muted)
                                        selected -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(today().withDayOfMonth(1)) }) { Text("回到本月") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}
