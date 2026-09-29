package com.klocktx.moneyblocks.domain.usecase

import com.klocktx.moneyblocks.domain.model.AmountFloor
import com.klocktx.moneyblocks.domain.model.BillKind
import com.klocktx.moneyblocks.domain.model.DateScope
import com.klocktx.moneyblocks.domain.model.SearchFilter
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchQueryTest {

    @Test
    fun wrapsKeywordInWildcards() {
        assertEquals("%午饭%", SearchQuery.like("午饭"))
    }

    @Test
    fun escapesWildcardCharacters() {
        // 用户搜 50% 时应该匹配字面量，而不是「50 后接任意内容」
        assertEquals("%50\\%%", SearchQuery.like("50%"))
        assertEquals("%a\\_b%", SearchQuery.like("a_b"))
    }

    @Test
    fun escapesEscapeCharacterItself() {
        assertEquals("%a\\\\b%", SearchQuery.like("a\\b"))
    }

    @Test
    fun numericKeywordBecomesExactCents() {
        assertEquals("3800", SearchQuery.exactCents("38"))
        assertEquals("3850", SearchQuery.exactCents("38.50"))
        assertEquals("5", SearchQuery.exactCents("0.05"))
    }

    @Test
    fun roundsHalfUpWhenFenOverflow() {
        assertEquals("1", SearchQuery.exactCents("0.005"))
    }

    @Test
    fun nonNumericKeywordReturnsEmpty() {
        assertEquals("", SearchQuery.exactCents("午饭"))
        assertEquals("", SearchQuery.exactCents(""))
        assertEquals("", SearchQuery.exactCents("  "))
    }

    @Test
    fun rejectsAbsurdAmounts() {
        assertEquals("", SearchQuery.exactCents("99999999999999999999"))
    }

    /**
     * 哨兵约定单独钉一次。DB 那侧的测试是"行为对"，这里是"约定没被改歪"——
     * 两边一起改才会同时通过，所以这条会在只动一处时先红。
     */
    @Test
    fun untouchedFilterBindsTheNoLimitSentinels() {
        val p = SearchQuery.params("", SearchFilter(), LocalDate.of(2026, 9, 27))
        assertEquals(0, p.hasText)
        assertEquals("", p.kind)
        assertEquals(0L, p.minCents)
        assertEquals(0L, p.start)
        assertEquals(Long.MAX_VALUE, p.endExclusive)
        assertEquals("", p.exactCents)
    }

    @Test
    fun aTypedKeywordTurnsTheTextBranchOn() {
        val p = SearchQuery.params("午饭", SearchFilter(kind = BillKind.EXPENSE), LocalDate.of(2026, 9, 27))
        assertEquals(1, p.hasText)
        assertEquals("%午饭%", p.notePattern)
        assertEquals("%午饭%", p.billPattern)
        assertEquals(BillKind.EXPENSE.key, p.kind)
        assertEquals("关键词是中文时精确金额分支必须关掉", "", p.exactCents)
    }

    @Test
    fun filtersLandOnTheirOwnParameters() {
        val today = LocalDate.of(2026, 9, 27)
        val floor = SearchQuery.params("", SearchFilter(floor = AmountFloor.Y500), today)
        assertEquals(50_000L, floor.minCents)
        assertEquals("金额筛选不该顺手把时间也限掉", Long.MAX_VALUE, floor.endExclusive)

        val scoped = SearchQuery.params("", SearchFilter(scope = DateScope.MONTH), today)
        assertEquals(0, scoped.hasText)
        assertEquals(today.withDayOfMonth(1).atZoneStart(), scoped.start)
        assertEquals(today.withDayOfMonth(1).plusMonths(1).atZoneStart(), scoped.endExclusive)
    }

    private fun java.time.LocalDate.atZoneStart(): Long =
        atStartOfDay(com.klocktx.moneyblocks.core.zone).toInstant().toEpochMilli()
}
