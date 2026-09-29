package com.klocktx.moneyblocks.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klocktx.moneyblocks.core.Cents
import com.klocktx.moneyblocks.core.TimeRange
import com.klocktx.moneyblocks.core.cents
import com.klocktx.moneyblocks.core.dayRange
import com.klocktx.moneyblocks.core.today
import com.klocktx.moneyblocks.domain.repo.BillRepository
import com.klocktx.moneyblocks.domain.repo.EntryRepository
import com.klocktx.moneyblocks.domain.usecase.ManageBillsUseCase
import com.klocktx.moneyblocks.domain.repo.SettingsRepository
import com.klocktx.moneyblocks.domain.repo.StatsRepository
import com.klocktx.moneyblocks.domain.model.Bill
import com.klocktx.moneyblocks.domain.model.BillKind
import com.klocktx.moneyblocks.domain.model.BillSummary
import com.klocktx.moneyblocks.domain.model.Entry
import com.klocktx.moneyblocks.domain.usecase.BuildStatsUseCase
import com.klocktx.moneyblocks.domain.usecase.Pace
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val billRepository: BillRepository, private val entryRepository: EntryRepository, private val manageBills: ManageBillsUseCase, private val settingsRepository: SettingsRepository, private val statsRepository: StatsRepository) : ViewModel() {

    private val monthFirst = MutableStateFlow(today().withDayOfMonth(1))

    private val rangeFlow = monthFirst.map { BuildStatsUseCase.rangeOf(it) }

    /**
     * 首次查询回来之前为 false。用来区分「真的没有账单」和「还没查到」，
     * 否则冷启动会先闪一下空状态，很难看。
     */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    val summaries: StateFlow<List<BillSummary>> = rangeFlow
        .flatMapLatest { billRepository.observeBillSummaries(it) }
        .onEach { _ready.value = true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 区间总额按全部条目算，含已归档账单，避免归档后首页数字凭空变小。 */
    val periodTotal: StateFlow<Cents> = rangeFlow
        .flatMapLatest { statsRepository.observeTotal(it, BillKind.EXPENSE) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L.cents)

    /** 本月收入。和支出分开取，否则工资会被算进「本月花了多少」。 */
    val periodIncome: StateFlow<Cents> = rangeFlow
        .flatMapLatest { statsRepository.observeTotal(it, BillKind.INCOME) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L.cents)

    val allTimeTotal: StateFlow<Cents> = statsRepository
        .observeTotal(TimeRange(0L, Long.MAX_VALUE), BillKind.EXPENSE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L.cents)

    val todayTotal: StateFlow<Cents> = statsRepository
        .observeTotal(dayRange(today()), BillKind.EXPENSE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L.cents)

    val recentEntries: StateFlow<List<Entry>> = entryRepository
        .observeRecent(RECENT_LIMIT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 月度预算，0 表示未设置。跨月不变，所以不随月份切换重新取。 */
    val monthlyBudget: StateFlow<Cents> = settingsRepository.monthlyBudget
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L.cents)

    /** 各账单的月度上限。编辑弹窗要把当前上限回填，不能让用户以为没设过。 */
    val billCaps: StateFlow<Map<Long, Cents>> = settingsRepository.billCaps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val monthFirstDay: StateFlow<LocalDate> = monthFirst

    /** 不允许翻到未来月份，翻了也只有空数据，只会让人怀疑 App 坏了。 */
    val canGoNext: StateFlow<Boolean> = monthFirst
        .map { it.isBefore(today().withDayOfMonth(1)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val elapsedDays: StateFlow<Int> = monthFirst.map { BuildStatsUseCase.elapsedDays(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 今天记了几笔（不分方向）。0 笔且本月记过，才值得轻轻提一句。 */
    val todayCount: StateFlow<Int> = entryRepository.observeCount(dayRange(today()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val monthCount: StateFlow<Int> = rangeFlow
        .flatMapLatest { entryRepository.observeCount(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 正在看本月。翻到历史月时不该催记录——那个月早过完了。 */
    val viewingCurrentMonth: StateFlow<Boolean> = monthFirst
        .map { it.withDayOfMonth(1) == today().withDayOfMonth(1) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** 今天这条提示被点掉的日子（epochDay）。点过一次就不再提。 */
    val nudgeSeenDay: StateFlow<Long> = settingsRepository.nudgeSeenDay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** 「今天还没记」的提示条件；规则本体在 BuildStatsUseCase，好单独测。 */
    fun shouldNudgeToday(): Boolean = BuildStatsUseCase.shouldNudgeToday(
        todayCount = todayCount.value,
        monthCount = monthCount.value,
        viewingCurrentMonth = viewingCurrentMonth.value,
        todayDay = today().toEpochDay(),
        nudgeSeenDay = nudgeSeenDay.value,
    )

    fun markNudgeSeen() {
        val day = today().toEpochDay()
        viewModelScope.launch { settingsRepository.setNudgeSeenDay(day) }
    }

    /**
     * 日均。以前是 `fun dailyAverage()` 在组合期调用：每次重组重算一遍，
     * 而且读的是 `StateFlow.value`（不受跟踪，理论上能读到旧值）。
     */
    val dailyAverage: StateFlow<Cents> = combine(periodTotal, elapsedDays) { total, days ->
        BuildStatsUseCase.dailyAverage(total, days)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L.cents)

    /** 本月变动 = 收入 - 支出。为负是有意义的（花超了），不做截断。 */
    val balance: StateFlow<Cents> = combine(periodIncome, periodTotal) { income, expense ->
        income - expense
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L.cents)

    /** 新建或编辑账单：界面交回一份改好的 Bill，id==0 就是新建。 */
    fun saveBill(bill: Bill, cap: Cents = 0L.cents) {
        viewModelScope.launch { manageBills.save(bill, cap) }
    }

    fun setArchived(bill: Bill, archived: Boolean) {
        viewModelScope.launch {
            if (archived) manageBills.archive(bill) else manageBills.restore(bill)
        }
    }

    /** 删除会连带带走名下所有条目，确认弹窗在 BillEditorDialog 里。 */
    fun deleteBill(bill: Bill) {
        viewModelScope.launch { manageBills.remove(bill) }
    }

    /** 拖动落定时整表写一次顺序，而不是每挪一格写一轮。 */
    fun commitOrder(orderedIds: List<Long>) {
        viewModelScope.launch { manageBills.commitOrder(orderedIds) }
    }

    fun previousMonth() {
        monthFirst.value = monthFirst.value.minusMonths(1)
    }

    fun nextMonth() {
        if (canGoNext.value) monthFirst.value = monthFirst.value.plusMonths(1)
    }

    fun backToCurrentMonth() {
        monthFirst.value = today().withDayOfMonth(1)
    }

    /** 月份面板直接跳月，未来月份夹回本月。 */
    fun goToMonth(first: LocalDate) {
        val firstOfMonth = first.withDayOfMonth(1)
        val thisMonth = today().withDayOfMonth(1)
        monthFirst.value = if (firstOfMonth.isAfter(thisMonth)) thisMonth else firstOfMonth
    }

    companion object {
        const val RECENT_LIMIT = 5
    }
}

/** 预算进度：把「花了多少」和「还剩多少」一次算清，避免除零判断散在界面里。 */
data class BudgetStatus(
    val spent: Cents,
    val budget: Cents,
    /** 这一段是哪个月。给了才谈节奏；不给就只报"花了/还剩"。 */
    val monthFirst: LocalDate? = null,
) {
    val fraction: Float
        get() = if (budget.value <= 0L) 0f
        else (spent.value.toDouble() / budget.value.toDouble()).toFloat().coerceIn(0f, 1f)

    val isOver: Boolean get() = budget.value > 0L && spent.value > budget.value

    /** 未设置预算时首页不显示这一行。 */
    val isActive: Boolean get() = budget.value > 0L

    val remaining: Cents get() = (budget.value - spent.value).cents

    /** 这个月还剩几天可花。没有月份信息就没有节奏可说。 */
    val pace: Pace? get() = monthFirst?.let { Pace.of(it, spent, budget, today()) }
}
