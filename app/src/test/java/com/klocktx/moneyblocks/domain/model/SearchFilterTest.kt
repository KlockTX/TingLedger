package com.klocktx.moneyblocks.domain.model

import com.klocktx.moneyblocks.core.contains
import com.klocktx.moneyblocks.core.millisToLocalDate
import com.klocktx.moneyblocks.core.zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 筛选的日期区间换算。
 *
 * 断言方式是"把毫秒换算回日期"而不是比数字：这样测的是**用户看到的日历边界**，
 * 换机器、换时区都不会假失败。期望日期本身就是这里要钉住的东西。
 */
class SearchFilterTest {

    private fun filterOf(scope: DateScope) = SearchFilter(scope = scope)

    private fun millisOf(at: LocalDateTime): Long = at.atZone(zone).toInstant().toEpochMilli()

    @Test
    fun allMeansNoTimeBound() {
        assertNull(filterOf(DateScope.ALL).rangeFor(LocalDate.of(2026, 9, 27)))
    }

    @Test
    fun weekEndsAtTheCloseOfTodayAndStartsSixDaysBack() {
        val today = LocalDate.of(2026, 9, 27)
        val range = filterOf(DateScope.WEEK).rangeFor(today) ?: throw IllegalStateException("近7天不该为 null")
        assertEquals(today.minusDays(6), millisToLocalDate(range.start))
        // 右开端点＝明天零点：今天的最后一笔仍在内，明天第一笔不在
        assertEquals(today.plusDays(1), millisToLocalDate(range.endExclusive))
        assertTrue("含今天", range.contains(millisOf(today.atTime(23, 59))))
        assertTrue("含起点当天零点", range.contains(range.start))
        assertFalse("第 8 天前不算", range.contains(millisOf(today.minusDays(7).atTime(12, 0))))
    }

    @Test
    fun weekCrossesIntoPreviousMonth() {
        val today = LocalDate.of(2026, 9, 3)
        val range = filterOf(DateScope.WEEK).rangeFor(today) ?: throw IllegalStateException("近7天不该为 null")
        assertEquals(LocalDate.of(2026, 8, 28), millisToLocalDate(range.start))
        assertEquals(LocalDate.of(2026, 9, 4), millisToLocalDate(range.endExclusive))
    }

    @Test
    fun monthIsTheCalendarMonthNotTheLast30Days() {
        val range = filterOf(DateScope.MONTH).rangeFor(LocalDate.of(2026, 9, 27))
            ?: throw IllegalStateException("本月不该为 null")
        assertEquals(LocalDate.of(2026, 9, 1), millisToLocalDate(range.start))
        assertEquals(LocalDate.of(2026, 10, 1), millisToLocalDate(range.endExclusive))
    }

    @Test
    fun monthOnThe31stStillCoversTheWholeMonth() {
        val range = filterOf(DateScope.MONTH).rangeFor(LocalDate.of(2026, 12, 31))
            ?: throw IllegalStateException("本月不该为 null")
        assertEquals(LocalDate.of(2026, 12, 1), millisToLocalDate(range.start))
        assertEquals("跨年到次年 1 月 1 日", LocalDate.of(2027, 1, 1), millisToLocalDate(range.endExclusive))
    }

    @Test
    fun lastMonthCrossesTheYearBoundary() {
        val range = filterOf(DateScope.LAST_MONTH).rangeFor(LocalDate.of(2026, 1, 15))
            ?: throw IllegalStateException("上月不该为 null")
        assertEquals(LocalDate.of(2025, 12, 1), millisToLocalDate(range.start))
        assertEquals(LocalDate.of(2026, 1, 1), millisToLocalDate(range.endExclusive))
    }

    @Test
    fun lastMonthOnMarch31IsFebruaryNotThreeWeeksAgo() {
        // minusMonths 会先把 3/31 夹到 2/28，所以必须显式取当月 1 号再算
        val range = filterOf(DateScope.LAST_MONTH).rangeFor(LocalDate.of(2026, 3, 31))
            ?: throw IllegalStateException("上月不该为 null")
        assertEquals(LocalDate.of(2026, 2, 1), millisToLocalDate(range.start))
        assertEquals(LocalDate.of(2026, 3, 1), millisToLocalDate(range.endExclusive))
    }

    @Test
    fun onlyAnUntouchedFilterMeansNoQuery() {
        assertTrue(SearchFilter().isPristine)
        assertFalse("空关键词 + 任意一个筛选，就是一次真实的查询", SearchFilter(kind = BillKind.INCOME).isPristine)
        assertFalse(SearchFilter(floor = AmountFloor.Y500).isPristine)
        assertFalse(SearchFilter(scope = DateScope.MONTH).isPristine)
    }

    @Test
    fun amountFloorsAreYuanTimesOneHundred() {
        assertEquals(0L, AmountFloor.ANY.cents)
        assertEquals(10_000L, AmountFloor.Y100.cents)
        assertEquals(50_000L, AmountFloor.Y500.cents)
        assertEquals(100_000L, AmountFloor.Y1000.cents)
    }
}
