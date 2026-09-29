package com.klocktx.moneyblocks.ui.bill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klocktx.moneyblocks.core.Cents
import com.klocktx.moneyblocks.core.cents
import com.klocktx.moneyblocks.core.millisToLocalDate
import com.klocktx.moneyblocks.core.relativeDayLabel
import com.klocktx.moneyblocks.core.today
import com.klocktx.moneyblocks.domain.repo.BillRepository
import com.klocktx.moneyblocks.domain.repo.EntryRepository
import com.klocktx.moneyblocks.domain.model.Bill
import com.klocktx.moneyblocks.domain.model.Entry
import com.klocktx.moneyblocks.domain.usecase.BuildStatsUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BillDetailViewModel(private val billRepository: BillRepository, private val entryRepository: EntryRepository, private val billId: Long) : ViewModel() {

    val bill: StateFlow<Bill?> = billRepository.observeBills()
        .map { list -> list.firstOrNull { it.id == billId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val entries: StateFlow<List<Entry>> = entryRepository.observeEntries(billId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 合计由已加载的条目求和：这个列表本就是该账单的全量，省一次聚合查询。 */
    val allTimeTotal: StateFlow<Cents> = entries
        .map { list -> list.sumOf { it.amount.value }.cents }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L.cents)

    val monthTotal: StateFlow<Cents> = entries
        .map { list ->
            val range = BuildStatsUseCase.rangeOf(today())
            list.filter { it.occurredAt in range.start until range.endExclusive }
                .sumOf { it.amount.value }.cents
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L.cents)

    fun delete(entry: Entry) {
        viewModelScope.launch { entryRepository.delete(entry.id) }
    }

    fun restore(entry: Entry) {
        viewModelScope.launch { entryRepository.add(entry.copy(id = 0)) }
    }
}

data class EntryDay(val label: String, val total: Cents, val entries: List<Entry>)

fun groupByDay(entries: List<Entry>): List<EntryDay> =
    entries
        .groupBy { millisToLocalDate(it.occurredAt) }
        .entries
        .sortedByDescending { it.key }
        .map { (date, items) ->
            EntryDay(
                label = relativeDayLabel(date),
                total = items.sumOf { it.amount.value }.cents,
                entries = items,
            )
        }
