package com.klocktx.moneyblocks.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klocktx.moneyblocks.core.Cents
import com.klocktx.moneyblocks.core.TimeRange
import com.klocktx.moneyblocks.core.cents
import com.klocktx.moneyblocks.core.dayRange
import com.klocktx.moneyblocks.core.endExclusiveMillis
import com.klocktx.moneyblocks.core.startMillis
import com.klocktx.moneyblocks.core.today
import com.klocktx.moneyblocks.domain.repo.BillRepository
import com.klocktx.moneyblocks.domain.repo.SettingsRepository
import com.klocktx.moneyblocks.domain.repo.StatsRepository
import com.klocktx.moneyblocks.domain.model.Balance
import com.klocktx.moneyblocks.domain.model.BillKind
import com.klocktx.moneyblocks.domain.model.BillSummary
import com.klocktx.moneyblocks.domain.model.DayPoint
import com.klocktx.moneyblocks.domain.model.LargestEntry
import com.klocktx.moneyblocks.domain.model.MonthDigest
import com.klocktx.moneyblocks.domain.model.MonthPoint
import com.klocktx.moneyblocks.domain.model.ShareSlice
import com.klocktx.moneyblocks.domain.usecase.Pace
import com.klocktx.moneyblocks.domain.usecase.BuildStatsUseCase
import com.klocktx.moneyblocks.domain.usecase.HeatCell
import com.klocktx.moneyblocks.domain.usecase.MonthHeatmap
import com.klocktx.moneyblocks.domain.usecase.Streak
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModel(
    private val billRepository: BillRepository,
    private val buildStats: BuildStatsUseCase,
    private val settingsRepository: SettingsRepository,
    private val statsRepository: StatsRepository,
) : ViewModel() {

    private val monthFirst = MutableStateFlow(today().withDayOfMonth(1))
    val monthFirstDay: StateFlow<LocalDate> = monthFirst

    val canGoNext: StateFlow<Boolean> = monthFirst
        .map { it.isBefore(today().withDayOfMonth(1)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val monthTotal: StateFlow<Cents> = monthFirst
        .flatMapLatest {
            statsRepository.observeTotal(BuildStatsUseCase.rangeOf(it), BillKind.EXPENSE)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L.cents)

    val todayTotal: StateFlow<Cents> = statsRepository
        .observeTotal(dayRange(today()), BillKind.EXPENSE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L.cents)

    /** 本月收入。分析页其余口径全是支出，只有这一条来自收入侧。 */
    val monthIncome: StateFlow<Cents> = monthFirst
        .flatMapLatest { statsRepository.observeTotal(BuildStatsUseCase.rangeOf(it), BillKind.INCOME) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L.cents)

    /** 上月同期总额与今天的写入无关，切月时算一次即可。 */
    val prevMonthSameSpanTotal: StateFlow<Cents> = monthFirst
        .flatMapLatest { first ->
            flow {
                emit(buildStats.prevMonthSameSpanTotal(first, BuildStatsUseCase.elapsedDays(first)))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L.cents)

    /** 本月各账单的汇总。占比和「有没有收入」都从这一份派生，不必查两次库。 */
    private val monthSummaries: StateFlow<List<BillSummary>?> = monthFirst
        .flatMapLatest { first ->
            billRepository.observeBillSummaries(BuildStatsUseCase.rangeOf(first))
        }
        // 整页有五条派生流共用这个根：WhileSubscribed 在订阅到齐前重启它，
        // 下游每一条都会带着自己那趟查询重跑一遍。
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * 各账单的月度上限。
     *
     * 只在这里当输入用：上限会和占比合成一份 `List<ShareSlice>` 再交给界面——
     * 直接把 `Map` 传进组合会让用到它的那一段每次重组（`Map` 不是稳定类型）。
     */
    val billCaps: StateFlow<Map<Long, Cents>> = settingsRepository.billCaps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val shareSlices: StateFlow<List<ShareSlice>> = combine(monthSummaries.filterNotNull(), billCaps) { rows, caps ->
        BuildStatsUseCase.shareSlices(rows, caps)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 总预算的节奏。三个输入都是现成的（看哪个月、那月已花、预算），
     * 所以直接合成一个 [Pace] 交出去——界面上那几句话的口径必须完全一致，
     * 不能一处按"已过 12 天"算、另一处按"12 月 31 天"算。
     */
    val pace: StateFlow<Pace> = combine(
        combine(monthFirst, monthTotal) { first, spent -> first to spent },
        settingsRepository.monthlyBudget,
    ) { (first, spent), budget ->
        // 只有正在看本月时才用"今天"当已过天数；翻到往月就是整月，
        // 否则一句"按当前速度"会拿一个月初的口径去说一个月的话。
        val today = today()
        val asOf = if (first == today.withDayOfMonth(1)) today else first.withDayOfMonth(first.lengthOfMonth())
        Pace.of(first, spent, budget, asOf)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Pace(0L.cents, 0L.cents, 0, 30))

    /**
     * 本月的逐日支出序列。日历、`digest` 的「最贵一天」都以它为口径。
     *
     * 以前 `digest` 自己又查了一遍**完全相同**的区间：每次刷新多扫一遍整月流水，
     * 还会让两个数字在不同时刻更新，看着像对不上。`AnalyticsQueryCountTest` 就是钉这个的。
     */
    val monthDays: StateFlow<List<DayPoint>> = monthSummaries
        .filterNotNull()
        .flatMapLatest {
            flow {
                emit(
                    statsRepository.dailySeries(
                        BuildStatsUseCase.rangeOf(monthFirst.value),
                        BillKind.EXPENSE,
                    ),
                )
            }
        }
        // Eagerly：这条是整页共用的口径（日历、节奏、热力格都从它派生）。
        // 用 WhileSubscribed 时，订阅陆续到齐之前上游会被拆掉再重启，
        // 结果同一条整月区间被扫两遍——AnalyticsQueryCountTest 就是量这个的。
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 本月节奏：笔数与总额来自汇总流，最贵一天复用上面那条逐日序列，不再多查一次库。 */
    val digest: StateFlow<MonthDigest> = combine(monthSummaries.filterNotNull(), monthDays) { rows, days ->
        val expense = rows.filter { it.bill.kind == BillKind.EXPENSE }
        BuildStatsUseCase.monthDigest(
            days = days,
            entryCount = expense.sumOf { it.periodCount },
            total = expense.sumOf { it.periodTotal.value }.cents,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthDigest(0, 0L.cents, null))

    /** 消费日历的格子。月份和逐日序列任一个变了都要重排，所以 combine 而不是读快照。 */
    val heatCells: StateFlow<List<HeatCell>> = combine(monthFirst, monthDays) { first, days ->
        MonthHeatmap.cells(first, days)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 连续记账天数。用月汇总当"数据变了"的信号去重查，
     * 因为这条要跟着每一笔记账动，而它本身跨月，不能挂在所选月份上。
     */
    val streakDays: StateFlow<Int> = monthSummaries
        .filterNotNull()
        .flatMapLatest {
            flow {
                val from = today().minusDays(STREAK_LOOKBACK_DAYS - 1L)
                emit(
                    statsRepository.activeDays(
                        TimeRange(from.startMillis(), today().endExclusiveMillis()),
                    ),
                )
            }
        }
        .map { days -> Streak.days(days.toSet(), today()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 没有收入账单时，分析页不必摆一行 0.00 的收入。 */
    val tracksIncome: StateFlow<Boolean> = monthSummaries
        .filterNotNull()
        .map { rows -> rows.any { it.bill.isIncome } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val trend: StateFlow<List<DayPoint>> = flow { emit(buildStats.trend(TREND_DAYS)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 近 12 个自然月的支出对比，用于月度趋势柱状图。 */
    val monthlyComparison: StateFlow<List<MonthPoint>> =
        flow { emit(statsRepository.monthlySeries(MONTHS_COMPARED, BillKind.EXPENSE)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 收入侧的同一条序列。只在有收入账单时才参与绘图，否则柱子里多一列 0 是噪音。 */
    val monthlyIncome: StateFlow<List<MonthPoint>> =
        flow { emit(statsRepository.monthlySeries(MONTHS_COMPARED, BillKind.INCOME)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val largest: StateFlow<LargestEntry?> = monthFirst
        .flatMapLatest { first ->
            flow { emit(buildStats.largest(BuildStatsUseCase.rangeOf(first))) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * 日均。放在 VM 里而不是界面上：以前这是 `viewModel.dailyAverage()`，
     * 每次重组都重算，而且读的是 `StateFlow.value`（不受跟踪，理论上能读到旧值）。
     */
    val dailyAverage: StateFlow<Cents> = combine(monthFirst, monthTotal) { first, total ->
        BuildStatsUseCase.dailyAverage(total, BuildStatsUseCase.elapsedDays(first))
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L.cents)

    /** 净额（收入 - 支出）。同上，界面上只做展示。 */
    val balance: StateFlow<Balance> = combine(monthTotal, monthIncome) { expense, income ->
        Balance(expense = expense, income = income)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Balance(0L.cents, 0L.cents))

    fun previousMonth() {
        monthFirst.value = monthFirst.value.minusMonths(1)
    }

    fun nextMonth() {
        if (canGoNext.value) monthFirst.value = monthFirst.value.plusMonths(1)
    }

    /** 月份面板直接跳月，未来月份夹回本月。 */
    fun goToMonth(first: LocalDate) {
        val firstOfMonth = first.withDayOfMonth(1)
        val thisMonth = today().withDayOfMonth(1)
        monthFirst.value = if (firstOfMonth.isAfter(thisMonth)) thisMonth else firstOfMonth
    }

    companion object {
        const val TREND_DAYS = 30
        const val MONTHS_COMPARED = 12

        /**
         * 连续天数最多往前看 400 天。
         * 再长就没必要了：能连 400 天的人不会因为截断而少记一笔，
         * 而这个上限保证这条查询不会随着使用年限无限长大。
         */
        const val STREAK_LOOKBACK_DAYS = 400L
    }
}
