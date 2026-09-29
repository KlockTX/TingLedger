package com.klocktx.moneyblocks.domain

import com.klocktx.moneyblocks.core.cents
import com.klocktx.moneyblocks.core.dayRange
import com.klocktx.moneyblocks.core.toYuanString
import com.klocktx.moneyblocks.core.today
import com.klocktx.moneyblocks.domain.model.Balance
import com.klocktx.moneyblocks.domain.model.Bill
import com.klocktx.moneyblocks.domain.model.BillKind
import com.klocktx.moneyblocks.domain.model.BillPalette
import com.klocktx.moneyblocks.domain.model.BillSummary
import com.klocktx.moneyblocks.domain.model.DayPoint
import com.klocktx.moneyblocks.domain.model.Entry
import com.klocktx.moneyblocks.domain.model.OverviewStat
import com.klocktx.moneyblocks.domain.model.SearchHit
import com.klocktx.moneyblocks.domain.model.amountLabel
import com.klocktx.moneyblocks.domain.model.kindTotals
import com.klocktx.moneyblocks.domain.usecase.BuildStatsUseCase
import com.klocktx.moneyblocks.ui.bill.groupByDay
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsLogicTest {

    private fun summary(
        name: String,
        periodCents: Long,
        kind: BillKind = BillKind.EXPENSE,
    ) = BillSummary(
        bill = Bill(
            id = name.hashCode().toLong(),
            name = name,
            iconKey = "restaurant",
            palette = BillPalette.GREEN,
            kind = kind,
        ),
        periodTotal = periodCents.cents,
        periodCount = 1,
        allTimeTotal = periodCents.cents,
        allTimeCount = 1,
        lastEntryAt = null,
    )

    @Test
    fun shareSlicesExcludeIncomeBills() {
        // 工资进「钱花在哪了」的分会让每个支出占比凭空变小
        val slices = BuildStatsUseCase.shareSlices(
            listOf(summary("吃饭", 300), summary("水电", 100), summary("工资", 400, BillKind.INCOME)),
        )
        assertEquals(listOf("吃饭", "水电"), slices.map { it.bill.name })
        assertEquals(1.0, slices.sumOf { it.share }, 0.0001)
        assertEquals(0.75, slices.first().share, 0.0001)
    }

    @Test
    fun balanceSubtractsExpenseFromIncomeAndKeepsSign() {
        // 收支都在「分」这一层：700 分 - 300 分 = 4.00 元
        assertEquals("4.00", Balance(expense = 300L.cents, income = 700L.cents).net.toYuanString())
        val overspent = Balance(expense = 900L.cents, income = 700L.cents).net
        assertEquals("-2.00", overspent.toYuanString())
        assertTrue(overspent.isNegative())
    }

    @Test
    fun kindTotalsReportBothSidesSeparately() {
        fun hit(amount: Long, name: String, kind: BillKind) = SearchHit(
            entry = Entry(id = 1, billId = 1, amount = amount.cents, note = "", occurredAt = 0L),
            bill = Bill(id = 1, name = name, iconKey = "restaurant", palette = BillPalette.GREEN, kind = kind),
        )
        val mixed = listOf(
            hit(3800, "吃饭", BillKind.EXPENSE),
            hit(120000, "工资", BillKind.INCOME),
        ).kindTotals()
        // 混着收支时不能给一个两边相减的假合计
        assertEquals("支出 38.00 · 收入 +1,200.00", mixed.amountLabel())

        val expenseOnly = listOf(hit(3800, "吃饭", BillKind.EXPENSE)).kindTotals()
        assertEquals("¥38.00", expenseOnly.amountLabel())
    }

    @Test
    fun shareSlicesSortsDescendingAndSumsToOne() {
        val slices = BuildStatsUseCase.shareSlices(
            listOf(summary("吃饭", 300), summary("打车", 100), summary("水电", 600)),
        )
        assertEquals(listOf("水电", "吃饭", "打车"), slices.map { it.bill.name })
        assertEquals(1.0, slices.sumOf { it.share }, 0.0001)
        assertEquals(0.6, slices.first().share, 0.0001)
    }

    @Test
    fun shareSlicesHandlesEmptyMonth() {
        val slices = BuildStatsUseCase.shareSlices(listOf(summary("吃饭", 0)))
        // 区间总额为 0 时占比必须是 0 而不是 NaN
        assertEquals(0.0, slices.single().share, 0.0001)
        assertTrue(slices.single().share.isNaN().not())
    }

    @Test
    fun periodAndAllTimeTotalsAreSeparate() {
        val summaries = listOf(summary("吃饭", 300), summary("打车", 100))
        assertEquals(400L, BuildStatsUseCase.periodTotalOf(summaries).value)
        assertEquals(400L, BuildStatsUseCase.allTimeTotalOf(summaries).value)
    }

    @Test
    fun summaryShareOfUsesGivenTotal() {
        val row = summary("吃饭", 250)
        assertEquals(0.25, row.shareOf(1000L.cents), 0.0001)
        assertEquals(0.0, row.shareOf(0L.cents), 0.0001)
    }

    @Test
    fun monthOverMonthRatioIsNullWhenNoBaseline() {
        val stat = OverviewStat(
            monthTotal = 500L.cents,
            todayTotal = 0L.cents,
            elapsedDays = 10,
            dailyAverage = 50L.cents,
            prevMonthSameSpanTotal = 0L.cents,
        )
        assertNull(stat.monthOverMonthRatio)
    }

    @Test
    fun monthOverMonthRatioComparesAgainstSameSpan() {
        val stat = OverviewStat(
            monthTotal = 1500L.cents,
            todayTotal = 0L.cents,
            elapsedDays = 10,
            dailyAverage = 150L.cents,
            prevMonthSameSpanTotal = 1000L.cents,
        )
        assertEquals(0.5, stat.monthOverMonthRatio!!, 0.0001)
    }

    @Test
    fun entriesGroupByDayNewestFirst() {
        // 用相对今天的日期，否则断言会随运行日期漂移（昨天 / 具体月日）
        val now = today()
        fun entryOn(date: LocalDate, amount: Long, tail: Int) = Entry(
            id = amount,
            billId = 1,
            amount = amount.cents,
            occurredAt = dayRange(date).start + tail,
        )
        val days = groupByDay(
            listOf(
                entryOn(now.minusDays(1), 100, 0),
                entryOn(now, 200, 0),
                entryOn(now, 50, 1),
            ),
        )
        assertEquals(2, days.size)
        assertEquals("今天", days.first().label)
        assertEquals(250L, days.first().total.value)
        assertEquals(2, days.first().entries.size)
        assertEquals("昨天", days[1].label)
    }

    @Test
    fun dailyAverageUsesElapsedDaysNotWholeMonth() {
        // 当月已过 3 天则按 3 算，否则月初看日均会假性偏低
        assertEquals(300L, BuildStatsUseCase.dailyAverage(900L.cents, 3).value)
        assertEquals(0L, BuildStatsUseCase.dailyAverage(900L.cents, 0).value)
    }

    @Test
    fun monthDigestAveragesPerEntryNotPerDay() {
        val days = listOf(
            DayPoint(LocalDate.of(2026, 9, 1), 100L.cents),
            DayPoint(LocalDate.of(2026, 9, 8), 900L.cents),
        )
        val digest = BuildStatsUseCase.monthDigest(days, entryCount = 4, total = 1_000L.cents)
        // 4 笔共 10.00 → 平均 2.50，而不是按天除出来的 5.00
        assertEquals("2.50", digest.average.toYuanString())
        assertEquals("9.00", digest.busiestDay?.total?.toYuanString())
        assertEquals(8, digest.busiestDay?.date?.dayOfMonth)
    }

    @Test
    fun monthDigestOnEmptyMonthHasNoBusiestDay() {
        val days = List(3) { DayPoint(LocalDate.of(2026, 9, it + 1), 0L.cents) }
        val digest = BuildStatsUseCase.monthDigest(days, entryCount = 0, total = 0L.cents)
        // 全是 0 的日子不能当「最贵一天」报出来
        assertNull(digest.busiestDay)
        assertEquals("0.00", digest.average.toYuanString())
    }

    @Test
    fun paletteFallsBackForUnknownKey() {
        assertEquals(BillPalette.AMBER, BillPalette.fromKey("amber"))
        assertEquals(BillPalette.GREEN, BillPalette.fromKey("不认识的颜色"))
    }
    /** 「今天还没记」的三个条件，缺一不可。 */
    @Test
    fun todayNudgeNeedsAnEmptyTodayAfterABusyMonth() {
        val day = 20_500L
        assertTrue(
            BuildStatsUseCase.shouldNudgeToday(
                todayCount = 0, monthCount = 5, viewingCurrentMonth = true, todayDay = day,
            ),
        )
        // 今天记过了就别催
        assertTrue(
            !BuildStatsUseCase.shouldNudgeToday(
                todayCount = 1, monthCount = 5, viewingCurrentMonth = true, todayDay = day,
            ),
        )
        // 本月一笔都没记：新装机第一个月，催了只会烦
        assertTrue(
            !BuildStatsUseCase.shouldNudgeToday(
                todayCount = 0, monthCount = 0, viewingCurrentMonth = true, todayDay = day,
            ),
        )
        // 翻到历史月：那个月早过完了
        assertTrue(
            !BuildStatsUseCase.shouldNudgeToday(
                todayCount = 0, monthCount = 5, viewingCurrentMonth = false, todayDay = day,
            ),
        )
    }

    /** 0 元的一笔也算记过——判据是笔数不是金额。 */
    @Test
    fun zeroAmountEntryStillCountsAsRecorded() {
        assertTrue(
            !BuildStatsUseCase.shouldNudgeToday(
                todayCount = 1, monthCount = 1, viewingCurrentMonth = true, todayDay = 20_500L,
            ),
        )
    }

    /** 当天点过一次就不再提；跨了天自动重新生效（存的是日子，不是布尔）。 */
    @Test
    fun nudgeStaysQuietForTheRestOfThatDay() {
        assertTrue(
            !BuildStatsUseCase.shouldNudgeToday(
                todayCount = 0, monthCount = 7, viewingCurrentMonth = true,
                todayDay = 20_500L, nudgeSeenDay = 20_500L,
            ),
        )
        assertTrue(
            BuildStatsUseCase.shouldNudgeToday(
                todayCount = 0, monthCount = 7, viewingCurrentMonth = true,
                todayDay = 20_500L, nudgeSeenDay = 20_499L,
            ),
        )
    }
}
