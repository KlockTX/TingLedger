package com.klocktx.moneyblocks.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klocktx.moneyblocks.domain.model.SearchFilter
import com.klocktx.moneyblocks.domain.model.SearchHit
import com.klocktx.moneyblocks.domain.repo.EntryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(private val entryRepository: EntryRepository) : ViewModel() {

    private val query = MutableStateFlow("")
    val queryText: StateFlow<String> = query

    /** 方向 / 金额下限 / 时间范围。三个维度一起进 SQL 筛，不再取回后在内存里过滤。 */
    private val _filter = MutableStateFlow(SearchFilter())
    val filter: StateFlow<SearchFilter> = _filter

    /**
     * 输入停 220ms 再查：中文联想输入会连续改字符串，逐字符查库既没必要也会让结果抖动。
     * 筛选改动走同一条流——它和关键词一样都会改变结果集。
     */
    private val searched: StateFlow<List<SearchHit>> = combine(query, _filter) { keyword, filter ->
        keyword to filter
    }
        .debounce(220)
        .distinctUntilChanged()
        .flatMapLatest { (keyword, filter) ->
            flow { emit(entryRepository.search(keyword, filter)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val results: StateFlow<List<SearchHit>> = searched

    /** 结果被上限截断了。这时候"共 N 笔"只是"至少 N 笔"，界面必须说实话。 */
    val truncated: StateFlow<Boolean> = searched
        .map { it.size >= EntryRepository.SEARCH_LIMIT }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun setFilter(value: SearchFilter) {
        _filter.value = value
    }

    fun clear() {
        query.value = ""
        _filter.value = SearchFilter()
    }
}
