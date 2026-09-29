package com.klocktx.moneyblocks.domain.usecase

import com.klocktx.moneyblocks.core.cents
import java.time.LocalDate
import java.time.Month
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaceTest {

    private fun pace(spent: Long, budget: Long, elapsed: Int, days: Int = 30) =
        Pace(spent = spent.cents, budget = budget.cents, elapsedDays = elapsed, daysInMonth = days)

    @Test
    fun noBudgetMeansNoPaceLanguage() {
        val p = pace(spent = 500_00L, budget = 0L, elapsed = 10)
        assertFalse(p.isActive)
        assertEquals(PaceKind.INACTIVE, p.state)
        assertEquals(0L, p.allowance.value)
        assertEquals(0.0, p.consumed, 0.0)
        assertNull(p.overrunDay)
        assertFalse(p.willOverrun)
        assertFalse(p.isOverNow)
    }

    @Test
    fun daysLeftIncludesToday() {
        assertEquals(30, pace(0L, 1_000_00L, elapsed = 1).daysLeft)
        assertEquals(1, pace(0L, 1_000_00L, elapsed = 30).daysLeft)
        // 越界的输入不该长出负数天数
        assertEquals(0, pace(0L, 1_000_00L, elapsed = 31).daysLeft)
    }

    @Test
    fun allowanceIsTheRestSplitOverTheRestOfTheMonth() {
        // 30 天的月份，已过 10 天（含今天则还剩 21 天），预算 2100 元整
        val p = pace(spent = 0L, budget = 210_000L, elapsed = 10)
        assertEquals(21, p.daysLeft)
        assertEquals(10_000L, p.allowance.value)
    }

    @Test
    fun allowanceFloorsInsteadOfRoundingUp() {
        // 100 元剩 3 天：宁可少说 1 分，不能说"每天 33.34"结果三天后超
        val p = pace(spent = 0L, budget = 10_000L, elapsed = 28)
        assertEquals(3, p.daysLeft)
        assertEquals(3_333L, p.allowance.value)
    }

    @Test
    fun allowanceIsZeroOnceTheBudgetIsGone() {
        assertEquals(0L, pace(spent = 1_000_00L, budget = 1_000_00L, elapsed = 15).allowance.value)
        assertEquals(0L, pace(spent = 1_200_00L, budget = 1_000_00L, elapsed = 15).allowance.value)
    }

    @Test
    fun dailySoFarDividesByDaysActuallyElapsed() {
        assertEquals(200_00L, pace(spent = 600_00L, budget = 3_000_00L, elapsed = 3).dailySoFar.value)
        // 一天都没过也不能除零
        assertEquals(0L, pace(spent = 600_00L, budget = 3_000_00L, elapsed = 0).dailySoFar.value)
    }

    @Test
    fun projectionRefusesToSpeakInTheFirstWeek() {
        val early = pace(spent = 1_200_00L, budget = 3_000_00L, elapsed = 1, days = 31)
        assertFalse("第一天就报速度是骗人", early.projectionIsReliable)
        // 到了第 7 天就该开口——这条同时钉住阈值本身别被改松
        assertTrue(pace(0L, 3_000_00L, elapsed = 7).projectionIsReliable)
        assertTrue(pace(0L, 3_000_00L, elapsed = 6).not())
    }

    @Test
    fun projectedUsesTheMonthLengthNotTheDaysLeft() {
        val p = pace(spent = 1_000_00L, budget = 5_000_00L, elapsed = 10, days = 30)
        assertEquals(100_00L, p.dailySoFar.value)
        assertEquals(3_000_00L, p.projected.value)
        assertFalse(p.willOverrun)
    }

    @Test
    fun overrunDayIsTheFirstDayStrictlyOverBudget() {
        // 每天 100 元，预算 1000 元：第 10 天正好花到，第 11 天才算超
        val p = pace(spent = 500_00L, budget = 1_000_00L, elapsed = 5, days = 30)
        assertEquals(100_00L, p.dailySoFar.value)
        assertEquals(11, p.overrunDay)
        assertTrue(p.willOverrun)
        assertEquals(PaceKind.OVER_SOON, p.state)
    }

    @Test
    fun overrunDayIsNullWhenTheMonthEndsFirst() {
        // 每天 10 元、预算 5000 元：整月铺满也到不了
        val p = pace(spent = 30_00L, budget = 500_000L, elapsed = 3, days = 30)
        assertEquals(300_00L, p.projected.value)
        assertNull(p.overrunDay)
    }

    @Test
    fun exactlyOnBudgetIsNotOver() {
        val onCap = pace(spent = 1_000_00L, budget = 1_000_00L, elapsed = 15, days = 30)
        assertFalse("花到预算不等于超预算", onCap.isOverNow)
        assertEquals(0L, onCap.allowance.value)
        // 进度已经 100% 而时间只过半 → 后面每天都要超，属于会超
        assertTrue(onCap.willOverrun)
    }

    @Test
    fun statesSplitAtThePaceLine() {
        // 时间过半、钱只花了三成 → 宽裕
        assertEquals(PaceKind.EASY, pace(300_00L, 1_000_00L, elapsed = 15, days = 30).state)
        // 时间过半、钱也花了四成五（差距 5% ≤ 10%）→ 压线
        assertEquals(PaceKind.CLOSE, pace(450_00L, 1_000_00L, elapsed = 15, days = 30).state)
        // 时间过半、钱已经花了六成 → 按此速度会超
        assertEquals(PaceKind.OVER_SOON, pace(600_00L, 1_000_00L, elapsed = 15, days = 30).state)
        // 真超了
        assertEquals(PaceKind.OVER_NOW, pace(1_100_00L, 1_000_00L, elapsed = 15, days = 30).state)
    }

    @Test
    fun closeThresholdIsExactlyTenPercent() {
        // 差距正好 10%：算压线（阈值含边界，改这个数必须同时改测试）
        val atLine = pace(spent = 400_00L, budget = 1_000_00L, elapsed = 15, days = 30)
        assertEquals(0.10, atLine.elapsed - atLine.consumed, 1e-9)
        assertEquals(PaceKind.CLOSE, atLine.state)
        // 差 10.1% 就落到宽裕
        assertEquals(PaceKind.EASY, pace(399_00L, 1_000_00L, elapsed = 15, days = 30).state)
    }

    @Test
    fun lastDayOnBudgetCountsAsCloseNotEasy() {
        val p = pace(spent = 900_00L, budget = 1_000_00L, elapsed = 30, days = 30)
        assertEquals(1, p.daysLeft)
        assertEquals(PaceKind.CLOSE, p.state)
    }

    @Test
    fun februaryAndLeapFebruaryGetTheirOwnLength() {
        val plain = Pace.of(LocalDate.of(2023, Month.FEBRUARY, 1), 200_00L.cents, 1_000_00L.cents, LocalDate.of(2023, 2, 10))
        val leap = Pace.of(LocalDate.of(2024, Month.FEBRUARY, 1), 200_00L.cents, 1_000_00L.cents, LocalDate.of(2024, 2, 10))
        assertEquals(28, plain.daysInMonth)
        assertEquals(29, leap.daysInMonth)
        assertEquals(19, plain.daysLeft)
        assertEquals(20, leap.daysLeft)
    }

    @Test
    fun ofCurrentMonthCountsDaysUpToToday() {
        val first = LocalDate.of(2026, 9, 1)
        val p = Pace.of(first, 0L.cents, 3_000_00L.cents, LocalDate.of(2026, 9, 28))
        assertEquals(28, p.elapsedDays)
        assertEquals(30, p.daysInMonth)
        assertEquals(3, p.daysLeft)
    }

    @Test
    fun ofPastMonthIsJudgedOverTheWholeMonth() {
        // 看往月时没有"还剩几天"，整月都过完了
        val p = Pace.of(LocalDate.of(2026, 7, 1), 2_500_00L.cents, 3_000_00L.cents, LocalDate.of(2026, 9, 28))
        assertEquals(31, p.elapsedDays)
        assertEquals(1, p.daysLeft)
        assertFalse(p.isOverNow)
    }

    @Test
    fun ofFutureMonthHasElapsedZero() {
        val p = Pace.of(LocalDate.of(2026, 12, 1), 0L.cents, 3_000_00L.cents, LocalDate.of(2026, 9, 28))
        assertEquals(0, p.elapsedDays)
        assertFalse(p.projectionIsReliable)
        assertEquals(0L, p.dailySoFar.value)
    }

    private fun Pace.not() = !projectionIsReliable
}
