package com.klocktx.moneyblocks.ui.day

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.klocktx.moneyblocks.core.formatDay
import com.klocktx.moneyblocks.core.formatTime
import com.klocktx.moneyblocks.core.formatWeekday
import com.klocktx.moneyblocks.core.millisToLocalDate
import com.klocktx.moneyblocks.core.toSignedYuanString
import com.klocktx.moneyblocks.domain.repo.EntryRepository
import com.klocktx.moneyblocks.domain.model.Bill
import com.klocktx.moneyblocks.domain.model.KindTotals
import com.klocktx.moneyblocks.domain.model.SearchHit
import com.klocktx.moneyblocks.domain.model.amountLabel
import com.klocktx.moneyblocks.domain.model.kindTotals
import com.klocktx.moneyblocks.ui.appViewModel
import com.klocktx.moneyblocks.ui.common.ItemRow
import com.klocktx.moneyblocks.ui.common.PanelDivider
import com.klocktx.moneyblocks.ui.theme.Col
import com.klocktx.moneyblocks.ui.theme.Sheet
import com.klocktx.moneyblocks.ui.theme.Spacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

class DayViewModel(
    entryRepository: EntryRepository,
    private val start: Long,
    private val endExclusive: Long,
) : ViewModel() {
    val hits: StateFlow<List<SearchHit>> = flow {
        emit(entryRepository.hitsBetween(start, endExclusive))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/**
 * 某一天的全部条目。入口有两处：首页「今日」数字、分析页近 30 天柱子。
 * 时间维度查账之前是缺的——只能按账单看，回不到「那天我到底花了什么」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaySheet(
    start: Long,
    endExclusive: Long,
    onDismiss: () -> Unit,
    onOpenBill: (Bill) -> Unit,
) {
    val viewModel = appViewModel(key = "day-$start") { DayViewModel(it.entryRepository, start, endExclusive) }
    val hits by viewModel.hits.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val day = millisToLocalDate(start)
    // 同一天可能既有工资又有菜钱，直接相加会得到一个谁都不代表的数
    val totals: KindTotals = hits.kindTotals()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.lg),
        ) {
            Text(
                text = "${formatDay(day)} ${formatWeekday(day)}",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "${hits.size} 笔 · ${totals.amountLabel()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.md))

            if (hits.isEmpty()) {
                Text(
                    text = "这天没有记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.xxl),
                )
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = Sheet.maxHeight)
                        .verticalScroll(rememberScrollState()),
                ) {
                    hits.forEachIndexed { index, hit ->
                        ItemRow(
                            title = hit.entry.note.ifEmpty { hit.bill.name },
                            subtitle = "${hit.bill.name} ${formatTime(hit.entry.occurredAt)}",
                            amount = hit.entry.amount.toSignedYuanString(hit.bill.isIncome),
                            iconKey = hit.bill.iconKey,
                            palette = hit.bill.palette,
                            onClick = { onOpenBill(hit.bill) },
                        )
                        if (index != hits.lastIndex) PanelDivider(indent = Col.rowIndent)
                    }
                }
            }
        }
    }
}
