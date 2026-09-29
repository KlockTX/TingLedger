package com.klocktx.moneyblocks.ui.analytics

import com.klocktx.moneyblocks.core.Cents
import com.klocktx.moneyblocks.core.TimeRange
import com.klocktx.moneyblocks.core.cents
import com.klocktx.moneyblocks.data.FakeSettings
import com.klocktx.moneyblocks.domain.model.Bill
import com.klocktx.moneyblocks.domain.model.BillKind
import com.klocktx.moneyblocks.domain.model.BillPalette
import com.klocktx.moneyblocks.domain.model.BillSummary
import com.klocktx.moneyblocks.domain.model.DayPoint
import com.klocktx.moneyblocks.domain.model.LargestEntry
import com.klocktx.moneyblocks.domain.model.MonthPoint
import com.klocktx.moneyblocks.domain.repo.BillRepository
import com.klocktx.moneyblocks.domain.repo.StatsRepository
import com.klocktx.moneyblocks.domain.usecase.BuildStatsUseCase
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 分析页每次数据变化打多少次库。
 *
 * 要防的是"看不见的重复"：`digest` 与 `monthDays` 各自跑一遍完全相同的
 * `dailySeries(本月, 支出)`。界面上没有任何症状，只是记账之后那一下刷新多扫一遍整月流水。
 *
 * 断言写的是**目标值**（一次触发只允许一次整月序列查询），
 * 并且每条都先确认"查询确实发生过"——计数为 0 的通过是假通过。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsQueryCountTest {

    private class CountingStats : StatsRepository {
        var totalCents: Cents = 250_000L.cents
        var days: List<DayPoint> = listOf(DayPoint(LocalDate.now().withDayOfMonth(1), 100L.cents))
        val dailySeriesRanges = ArrayList<TimeRange>()
        var monthlySeriesCalls = 0
        var largestCalls = 0
        var activeDaysCalls = 0

        override fun observeTotal(range: TimeRange, kind: BillKind): Flow<Cents> = flowOf(totalCents)

        override suspend fun total(range: TimeRange, kind: BillKind): Cents = totalCents

        override suspend fun dailySeries(range: TimeRange, kind: BillKind): List<DayPoint> {
            dailySeriesRanges += range
            return days
        }

        override suspend fun monthlySeries(months: Int, kind: BillKind): List<MonthPoint> {
            monthlySeriesCalls++
            return emptyList()
        }

        override suspend fun largestEntry(range: TimeRange, kind: BillKind): LargestEntry? {
            largestCalls++
            return null
        }

        override suspend fun activeDays(range: TimeRange): List<LocalDate> {
            activeDaysCalls++
            return emptyList()
        }
    }

    private class CountingBills : BillRepository {
        /** 换月要看得见"内容变了"：monthSummaries 是 stateIn 出来的 StateFlow，按相等去重。 */
        val bumps = MutableStateFlow(0)
        var summaryCalls = 0

        override fun observeBills(): Flow<List<Bill>> = flowOf(emptyList())

        override fun observeBillSummaries(range: TimeRange): Flow<List<BillSummary>> {
            summaryCalls++
            return bumps.map { n -> listOf(summaryOf(range.start + n)) }
        }

        override suspend fun find(id: Long): Bill? = null
        override suspend fun save(bill: Bill): Long = bill.id
        override suspend fun trash(id: Long) = Unit
        override suspend fun restore(id: Long) = Unit
        override suspend fun deleteForever(id: Long) = Unit
        override fun observeTrashed(): Flow<List<Bill>> = flowOf(emptyList())
        override suspend fun purgeTrash(cutoffMillis: Long): List<Long> = emptyList()
        override suspend fun setArchived(id: Long, archived: Boolean) = Unit
        override suspend fun reorder(orderedIds: List<Long>) = Unit
        override suspend fun maxSortOrder(): Int = 0
        override suspend fun recurring(): List<Bill> = emptyList()
        override suspend fun setRepeatCursor(id: Long, day: Long) = Unit
    }

    private lateinit var stats: CountingStats
    private lateinit var bills: CountingBills
    private lateinit var settings: FakeSettings

    /**
     * `viewModelScope` 用的是 `Dispatchers.Main.immediate`，而 `runTest` **不会**替换 Main——
     * 不 setMain 时 VM 里的协程一条都不跑，所有计数器停在 0，测试会以"没查询"的假象失败。
     * 调度器必须和 runTest 用同一个，否则推进不了 VM 里的那些流。
     */
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        stats = CountingStats()
        bills = CountingBills()
        settings = FakeSettings()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = AnalyticsViewModel(
        billRepository = bills,
        buildStats = BuildStatsUseCase(stats),
        settingsRepository = settings,
        statsRepository = stats,
    )

    /** 把界面上会订阅的流全都订上；漏订就会让计数假绿。 */
    private fun subscribe(scope: CoroutineScope, vm: AnalyticsViewModel) {
        listOf(
            vm.canGoNext, vm.monthFirstDay, vm.monthTotal, vm.todayTotal, vm.monthIncome,
            vm.prevMonthSameSpanTotal, vm.shareSlices, vm.billCaps, vm.pace,
            vm.digest, vm.monthDays, vm.heatCells, vm.streakDays,
            vm.tracksIncome, vm.trend, vm.monthlyComparison, vm.monthlyIncome, vm.largest,
        ).forEach { flow -> scope.launch { flow.collect { } } }
    }

    @Test
    fun openingTheScreenScansTheMonthExactlyOnce() = runTest(dispatcher) {
        val vm = newViewModel()
        subscribe(backgroundScope, vm)
        advanceUntilIdle()

        assertTrue(
            "一次都没查说明流没被订阅，这条通过没有意义",
            stats.dailySeriesRanges.isNotEmpty(),
        )
        val dupes = stats.dailySeriesRanges.groupBy { it }.filterValues { it.size > 1 }
        assertEquals(
            "同一条区间被查了不止一次：" + dupes.keys.map { it.start },
            0,
            dupes.size,
        )
        assertEquals("活跃日查询也只该一次", 1, stats.activeDaysCalls)
    }

    @Test
    fun oneNewEntryAddsExactlyOneMonthScan() = runTest(dispatcher) {
        val vm = newViewModel()
        subscribe(backgroundScope, vm)
        advanceUntilIdle()
        val before = stats.dailySeriesRanges.size
        val beforeActive = stats.activeDaysCalls
        assertTrue(before > 0)

        // 记一笔 → 账单汇总流重发
        bills.bumps.value = bills.bumps.value + 1
        advanceUntilIdle()

        assertEquals(
            "一次记账只该多一次整月序列查询（digest 应复用 monthDays 的结果）",
            1,
            stats.dailySeriesRanges.size - before,
        )
        assertEquals("连续天数需要一次活跃日查询", 1, stats.activeDaysCalls - beforeActive)
    }

    @Test
    fun capsChangeDoesNotRescanTheMonth() = runTest(dispatcher) {
        val vm = newViewModel()
        subscribe(backgroundScope, vm)
        advanceUntilIdle()
        val before = stats.dailySeriesRanges.size

        settings.setBillCap(1L, 500_00L.cents)
        advanceUntilIdle()

        assertEquals("改上限不该重扫整月流水", before, stats.dailySeriesRanges.size)
    }

    @Test
    fun switchingMonthScansTheNewMonthOnce() = runTest(dispatcher) {
        val vm = newViewModel()
        subscribe(backgroundScope, vm)
        advanceUntilIdle()
        val before = stats.dailySeriesRanges.size

        vm.previousMonth()
        advanceUntilIdle()

        assertEquals("翻月只取新那个月一次", 1, stats.dailySeriesRanges.size - before)
        assertTrue(
            "两个月的区间应当不同",
            stats.dailySeriesRanges.first() != stats.dailySeriesRanges.last(),
        )
    }
}
private fun summaryOf(mark: Long) = BillSummary(
    bill = Bill(id = 1L, name = "吃饭", iconKey = "restaurant", palette = BillPalette.GREEN),
    periodTotal = 100_00L.cents,
    periodCount = 1,
    allTimeTotal = 100_00L.cents,
    allTimeCount = 1,
    lastEntryAt = mark,
)

