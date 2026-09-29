package com.klocktx.moneyblocks.domain.usecase

import com.klocktx.moneyblocks.core.cents
import com.klocktx.moneyblocks.domain.model.DayPoint
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeatmapTest {

    /** pairs 的右边一律按**分**写，和库里的口径一致。 */
    private fun month(first: LocalDate, vararg pairs: Pair<Int, Long>): List<DayPoint> =
        pairs.map { (day, cents) -> DayPoint(first.withDayOfMonth(day), cents.cents) }

    @Test
    fun weekColumnsRunMondayToSunday() {
        assertEquals(0, MonthHeatmap.columnOf(DayOfWeek.MONDAY))
        assertEquals(5, MonthHeatmap.columnOf(DayOfWeek.SATURDAY))
        assertEquals(6, MonthHeatmap.columnOf(DayOfWeek.SUNDAY))
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), MonthHeatmap.weekColumns.map { MonthHeatmap.columnOf(it) })
    }

    /**
     * 逐月核对对齐：连查 60 个月，7 种起始星期都会碰到，
     * 比手写「某月周一开头」的例子可靠——日历错一格没人会去逐格核。
     */
    @Test
    fun everyMonthAlignsFirstDayToItsWeekdayColumn() {
        var first = LocalDate.of(2022, 1, 1)
        val seenStartColumns = mutableSetOf<Int>()
        repeat(60) {
            val lead = MonthHeatmap.columnOf(first.dayOfWeek)
            seenStartColumns += lead
            val cells = MonthHeatmap.cells(first, month(first, 1 to 10_000L, 15 to 5_000L))
            assertEquals(lead + first.lengthOfMonth(), cells.size)
            repeat(lead) { i -> assertTrue("slot $i of $first should be blank", cells[i].isEmptySlot) }
            assertEquals(first, cells[lead].date)
            for (day in 1..first.lengthOfMonth()) {
                val date = cells[lead + day - 1].date
                assertEquals(first.withDayOfMonth(day), date)
                assertEquals((lead + day - 1) % 7, MonthHeatmap.columnOf(date!!.dayOfWeek))
            }
            first = first.plusMonths(1)
        }
        assertEquals(setOf(0, 1, 2, 3, 4, 5, 6), seenStartColumns)
    }

    @Test
    fun leapFebruaryHas29CellsAndPlainOneHas28() {
        val leap = LocalDate.of(2024, 2, 1)
        val plain = LocalDate.of(2023, 2, 1)
        assertEquals(29, MonthHeatmap.cells(leap, emptyList()).count { it.date != null })
        assertEquals(28, MonthHeatmap.cells(plain, emptyList()).count { it.date != null })
        // 闰格里 2 月 29 日确实占一格，而不是被截断
        val cells = MonthHeatmap.cells(leap, month(leap, 29 to 9_900L))
        assertEquals(9_900L.cents, cells.last { it.date == LocalDate.of(2024, 2, 29) }.cents)
    }

    @Test
    fun emptyMonthStillFillsTheWholeGrid() {
        val first = LocalDate.of(2025, 9, 1)
        val cells = MonthHeatmap.cells(first, emptyList())
        assertEquals(MonthHeatmap.columnOf(first.dayOfWeek) + 30, cells.size)
        assertTrue(cells.filter { it.date != null }.all { it.cents.value == 0L })
        assertTrue(cells.all { it.level == 0 })
    }

    @Test
    fun levelsAreComparedAgainstThePeakDay() {
        val first = LocalDate.of(2025, 6, 1)
        val cells = MonthHeatmap.cells(
            first,
            month(first, 1 to 1_000L, 2 to 800L, 3 to 100L, 4 to 0L),
        ).associateBy { it.date }
        assertEquals(4, cells[LocalDate.of(2025, 6, 1)]!!.level)
        // ¥8.00 / ¥10.00 = 0.8 → 最重档
        assertEquals(4, cells[LocalDate.of(2025, 6, 2)]!!.level)
        // ¥1.00 / ¥10.00 = 0.1 → 最轻档，但不是 0
        assertEquals(1, cells[LocalDate.of(2025, 6, 3)]!!.level)
        assertEquals(0, cells[LocalDate.of(2025, 6, 4)]!!.level)
    }

    @Test
    fun blankDaysOfAQuietMonthStayLevelZero() {
        val first = LocalDate.of(2025, 11, 1)
        val cells = MonthHeatmap.cells(first, month(first, 10 to 50_000L))
        assertEquals(0, cells.first { it.date == LocalDate.of(2025, 11, 11) }.level)
        assertEquals(4, cells.first { it.date == LocalDate.of(2025, 11, 10) }.level)
    }

    /** 混进别处的日子既不能多占格子，也不能把整月颜色压淡。 */
    @Test
    fun pointsOutsideTheMonthAreIgnored() {
        val first = LocalDate.of(2025, 9, 1)
        val cells = MonthHeatmap.cells(
            first,
            listOf(
                DayPoint(LocalDate.of(2025, 8, 20), 9_999_900L.cents),
                DayPoint(LocalDate.of(2025, 10, 3), 9_999_900L.cents),
                DayPoint(first, 100_000L.cents),
            ),
        )
        assertEquals(MonthHeatmap.columnOf(first.dayOfWeek) + 30, cells.size)
        assertEquals(4, cells.first { it.date == first }.level)
        assertTrue(cells.filter { it.date != null && it.date != first }.all { it.level == 0 })
    }

    @Test
    fun levelBucketsBreakAtQuarterPoints() {
        assertEquals(4, MonthHeatmap.levelOf(751L, 1_000L))
        // 压线归轻一档：0.75 整不算"超过 0.75"
        assertEquals(3, MonthHeatmap.levelOf(750L, 1_000L))
        assertEquals(3, MonthHeatmap.levelOf(501L, 1_000L))
        assertEquals(2, MonthHeatmap.levelOf(500L, 1_000L))
        assertEquals(2, MonthHeatmap.levelOf(251L, 1_000L))
        assertEquals(1, MonthHeatmap.levelOf(250L, 1_000L))
        assertEquals(1, MonthHeatmap.levelOf(1L, 1_000L))
    }

    @Test
    fun levelIsZeroWithoutSpendOrWithoutPeak() {
        assertEquals(0, MonthHeatmap.levelOf(0L, 1_000L))
        assertEquals(0, MonthHeatmap.levelOf(-5L, 1_000L))
        // 整月一分钱没花时不能出现"最贵那天满格"
        assertEquals(0, MonthHeatmap.levelOf(500L, 0L))
        assertEquals(0, MonthHeatmap.levelOf(0L, 0L))
    }

    @Test
    fun leadingSlotsAreBlankAndTheFirstDayFollowsThem() {
        val first = LocalDate.of(2025, 6, 1) // 周日 → 前导 6 格
        val lead = MonthHeatmap.columnOf(first.dayOfWeek)
        assertEquals(6, lead)
        val cells = MonthHeatmap.cells(first, emptyList())
        repeat(lead) { assertTrue("slot $it", cells[it].isEmptySlot) }
        assertNull(cells[lead - 1].date)
        assertEquals(first, cells[lead].date)
    }

    @Test
    fun streakCountsTodayWhenItIsRecorded() {
        val today = LocalDate.of(2025, 9, 27)
        assertEquals(1, Streak.days(setOf(today), today))
        assertEquals(3, Streak.days(setOf(today, today.minusDays(1), today.minusDays(2)), today))
    }

    @Test
    fun streakDoesNotBreakJustBecauseTodayIsNotRecordedYet() {
        val today = LocalDate.of(2025, 9, 27)
        val yesterday = today.minusDays(1)
        assertEquals(2, Streak.days(setOf(yesterday, yesterday.minusDays(1)), today))
        assertEquals(0, Streak.days(setOf(today.minusDays(2)), today))
    }

    @Test
    fun streakStopsAtTheFirstGap() {
        val today = LocalDate.of(2025, 9, 27)
        val days = setOf(today, today.minusDays(1), today.minusDays(3), today.minusDays(4))
        assertEquals(2, Streak.days(days, today))
    }

    @Test
    fun streakCrossesMonthAndYearBoundaries() {
        val firstOfSeptember = LocalDate.of(2025, 9, 1)
        assertEquals(
            "9/1 往前要接着数 8/31、8/30",
            3,
            Streak.days(
                setOf(firstOfSeptember, LocalDate.of(2025, 8, 31), LocalDate.of(2025, 8, 30)),
                firstOfSeptember,
            ),
        )
        assertEquals(
            "元旦要能接着数到去年 12 月",
            3,
            Streak.days(
                setOf(LocalDate.of(2024, 1, 1), LocalDate.of(2023, 12, 31), LocalDate.of(2023, 12, 30)),
                LocalDate.of(2024, 1, 1),
            ),
        )
    }

    @Test
    fun streakIsZeroWhenNothingWasEverRecorded() {
        assertEquals(0, Streak.days(emptySet(), LocalDate.of(2025, 9, 27)))
    }
}
