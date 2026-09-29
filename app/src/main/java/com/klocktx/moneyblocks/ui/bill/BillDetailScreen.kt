package com.klocktx.moneyblocks.ui.bill

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klocktx.moneyblocks.core.Cents
import com.klocktx.moneyblocks.core.formatTime
import com.klocktx.moneyblocks.core.millisToLocalDate
import com.klocktx.moneyblocks.core.relativeDayLabel
import com.klocktx.moneyblocks.core.toSignedYuanString
import com.klocktx.moneyblocks.core.toSignedYuanWithSymbol
import com.klocktx.moneyblocks.domain.model.Bill
import com.klocktx.moneyblocks.domain.model.BillPalette
import com.klocktx.moneyblocks.domain.model.Entry
import com.klocktx.moneyblocks.ui.LocalToast
import com.klocktx.moneyblocks.ui.appViewModel
import com.klocktx.moneyblocks.ui.common.AppToast
import com.klocktx.moneyblocks.ui.common.BillIconBadge
import com.klocktx.moneyblocks.ui.common.ItemRow
import com.klocktx.moneyblocks.ui.common.Panels
import com.klocktx.moneyblocks.ui.keypad.RecordTarget
import com.klocktx.moneyblocks.ui.keypad.asRecordTarget
import com.klocktx.moneyblocks.ui.theme.Badge
import com.klocktx.moneyblocks.ui.theme.IconSize
import com.klocktx.moneyblocks.ui.theme.MoneyDisplay
import com.klocktx.moneyblocks.ui.theme.Sheet
import com.klocktx.moneyblocks.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillDetailScreen(
    billId: Long,
    onBack: () -> Unit,
    /** 三种入口共用同一个弹层：空白记一笔 / 编辑这条 / 按这条再记一笔。 */
    onOpenSheet: (RecordTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = appViewModel(key = "detail-$billId") { BillDetailViewModel(it.billRepository, it.entryRepository, billId) }
    val bill by viewModel.bill.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val allTimeTotal by viewModel.allTimeTotal.collectAsStateWithLifecycle()
    val monthTotal by viewModel.monthTotal.collectAsStateWithLifecycle()
    val showToast = LocalToast.current
    val days = remember(entries) { groupByDay(entries) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        // 外层 Scaffold 已经把状态栏高度算进 padding，内层再算一次会出现双倍顶部空白
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        bill?.let {
                            BillIconBadge(iconKey = it.iconKey, palette = it.palette, size = Badge.md, iconSize = IconSize.sm)
                            Spacer(Modifier.width(Spacing.sm))
                        }
                        Text(bill?.name ?: "", style = MaterialTheme.typography.titleMedium)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                // 外层 Scaffold 已经让内容从状态栏下方开始，这里再吃一次 insets 就是双倍空白
                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = Spacing.listBottom,
        ) {
            item(key = "summary") {
                SummaryHeader(
                    allTimeTotal = allTimeTotal,
                    monthTotal = monthTotal,
                    entryCount = entries.size,
                    income = bill?.isIncome == true,
                    onRecord = { bill?.let { onOpenSheet(it.asRecordTarget()) } },
                )
            }

            if (entries.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "还没有条目",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
                    )
                }
            }

            days.forEach { day ->
                item(key = "day-${day.label}") { DayHeader(day, income = bill?.isIncome == true) }
                items(day.entries, key = { "entry-${it.id}" }) { entry ->
                    EntryRow(
                        entry = entry,
                        bill = bill,
                        onClick = { bill?.let { onOpenSheet(it.asRecordTarget(entry)) } },
                        onDuplicate = {
                            bill?.let { onOpenSheet(it.asRecordTarget().copy(prefill = entry)) }
                        },
                        onDelete = {
                            viewModel.delete(entry)
                            showToast(
                                AppToast(
                                    message = "已删除 ${entry.amount.toSignedYuanWithSymbol(bill?.isIncome == true)}",
                                    actionLabel = "撤销",
                                    onAction = { viewModel.restore(entry) },
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryHeader(
    allTimeTotal: Cents,
    monthTotal: Cents,
    entryCount: Int,
    income: Boolean,
    onRecord: () -> Unit,
) {
    Column(Modifier.padding(Spacing.screen)) {
        Text(
            text = if (income) "累计收入" else "累计支出",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(allTimeTotal.toSignedYuanWithSymbol(income), style = MoneyDisplay)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = "本月 ${monthTotal.toSignedYuanString(income)} · 共 $entryCount 笔",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.lg))
        Button(
            onClick = onRecord,
            modifier = Modifier
                .fillMaxWidth()
                .height(Sheet.strip),
            shape = Panels.buttonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.width(IconSize.md))
            Spacer(Modifier.width(Spacing.sm))
            Text(if (income) "记一笔收入" else "记一笔", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun DayHeader(day: EntryDay, income: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = day.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = day.total.toSignedYuanString(income),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EntryRow(
    entry: Entry,
    bill: Bill?,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    ItemRow(
        title = entry.note.ifEmpty { bill?.name ?: "无备注" },
        // 自动生成的那笔标出来，免得用户在明细里看到陌生记录却想不起是规则记的。
        // 只加两个字，不新增控件、不改行高。
        subtitle = relativeDayLabel(millisToLocalDate(entry.occurredAt)) +
            " ${formatTime(entry.occurredAt)}" +
            if (entry.autoDay != null) " · 自动" else "",
        amount = entry.amount.toSignedYuanWithSymbol(bill?.isIncome == true),
        iconKey = bill?.iconKey ?: "category",
        palette = bill?.palette ?: BillPalette.SLATE,
        onClick = onClick,
        onLongClick = onDuplicate,
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        trailingAction = {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "删除这笔",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.md),
                )
            }
        },
    )
}
