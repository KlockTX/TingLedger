package com.klocktx.moneyblocks.ui.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klocktx.moneyblocks.core.formatTime
import com.klocktx.moneyblocks.core.millisToLocalDate
import com.klocktx.moneyblocks.core.relativeDayLabel
import com.klocktx.moneyblocks.core.toSignedYuanString
import com.klocktx.moneyblocks.domain.model.AmountFloor
import com.klocktx.moneyblocks.domain.model.Bill
import com.klocktx.moneyblocks.domain.model.BillKind
import com.klocktx.moneyblocks.domain.model.DateScope
import com.klocktx.moneyblocks.domain.model.SearchFilter
import com.klocktx.moneyblocks.domain.model.amountLabel
import com.klocktx.moneyblocks.domain.model.kindTotals
import com.klocktx.moneyblocks.ui.appViewModel
import com.klocktx.moneyblocks.ui.common.Chip
import com.klocktx.moneyblocks.ui.common.ItemRow
import com.klocktx.moneyblocks.ui.common.Panels
import com.klocktx.moneyblocks.ui.theme.Col
import com.klocktx.moneyblocks.ui.theme.FloatOffset
import com.klocktx.moneyblocks.ui.theme.IconSize
import com.klocktx.moneyblocks.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenBill: (Bill) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = appViewModel { SearchViewModel(it.entryRepository) }
    val query by viewModel.queryText.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val truncated by viewModel.truncated.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::onQueryChange,
                        placeholder = { Text("搜备注、账单名或金额，留空则只看筛选结果") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(IconSize.md))
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = viewModel::clear) {
                                    Icon(Icons.Default.Close, contentDescription = "清空")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        shape = Panels.innerShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
            )
        },
    ) { padding ->
        val grouped = remember(results) {
            results.groupBy { relativeDayLabel(millisToLocalDate(it.entry.occurredAt)) }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
                contentPadding = PaddingValues(bottom = FloatOffset.aboveFab),
        ) {
            item(key = "filter") { FilterPanel(filter, viewModel::setFilter) }
            if (results.isEmpty()) {
                item(key = "hint") {
                    Hint(
                        title = if (query.isBlank() && filter.isPristine) "可以搜什么" else "没找到相关记录",
                        body = when {
                            query.isBlank() && filter.isPristine ->
                                "备注里的字、账单名，或者直接输入金额「38.50」精确匹配某一笔。" +
                                    "也可以留空，只用下面的筛选看某个范围内的全部记录。"
                            query.isBlank() -> "没有同时满足这些条件的记录，把某一行改回「全部/不限」。"
                            !filter.isPristine -> "换个关键词，或者把筛选放宽一些。"
                            else -> "换个关键词，或者少输入几个字。"
                        },
                    )
                }
            } else {
                item(key = "count") {
                    Text(
                        text = if (truncated) {
                            "已有 ${results.size} 笔，结果较多，请缩小范围"
                        } else {
                            "共 ${results.size} 笔 · ${results.kindTotals().amountLabel()}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    )
                }
                grouped.forEach { (label, hits) ->
                    item(key = "day-$label") {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                horizontal = Spacing.lg,
                                vertical = Spacing.xs,
                            ),
                        )
                    }
                    items(hits, key = { "hit-${it.entry.id}" }) { hit ->
                        ItemRow(
                            title = hit.entry.note.ifEmpty { hit.bill.name },
                            subtitle = "${hit.bill.name} · ${formatTime(hit.entry.occurredAt)}",
                            amount = hit.entry.amount.toSignedYuanString(hit.bill.isIncome),
                            iconKey = hit.bill.iconKey,
                            palette = hit.bill.palette,
                            onClick = { onOpenBill(hit.bill) },
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 筛选面板：方向 / 金额下限 / 时间范围。
 *
 * 三行都是"点一下立刻换结果"的单选，所以共用一套 Chip。每行横向可滚——
 * 窄屏上多一个「≥1000」不该把别的字挤掉。
 */
@Composable
private fun FilterPanel(filter: SearchFilter, onChange: (SearchFilter) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        FilterChips(
            title = "方向",
            labels = listOf("全部", "支出", "收入"),
            selected = when (filter.kind) {
                null -> 0
                BillKind.EXPENSE -> 1
                BillKind.INCOME -> 2
            },
        ) { index ->
            onChange(
                filter.copy(
                    kind = when (index) {
                        1 -> BillKind.EXPENSE
                        2 -> BillKind.INCOME
                        else -> null
                    },
                ),
            )
        }
        FilterChips(
            title = "金额",
            labels = AmountFloor.entries.map { it.label },
            selected = AmountFloor.entries.indexOf(filter.floor),
        ) { index ->
            onChange(filter.copy(floor = AmountFloor.entries[index]))
        }
        FilterChips(
            title = "时间",
            labels = DateScope.entries.map { it.label },
            selected = DateScope.entries.indexOf(filter.scope),
        ) { index ->
            onChange(filter.copy(scope = DateScope.entries[index]))
        }
    }
}

@Composable
private fun FilterChips(
    title: String,
    labels: List<String>,
    selected: Int,
    onPick: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(Col.filterLabel),
        )
        Row(
            Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            labels.forEachIndexed { index, label ->
                Chip(text = label, selected = index == selected, onClick = { onPick(index) })
            }
        }
    }
}

@Composable
private fun Hint(title: String, body: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
