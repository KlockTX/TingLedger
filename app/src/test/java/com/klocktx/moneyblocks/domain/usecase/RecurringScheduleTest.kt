package com.klocktx.moneyblocks.domain.usecase

import com.klocktx.moneyblocks.core.cents
import com.klocktx.moneyblocks.domain.model.Bill
import com.klocktx.moneyblocks.domain.model.BillKind
import com.klocktx.moneyblocks.domain.model.BillPalette
import com.klocktx.moneyblocks.domain.model.BillRepeat
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 周期排期的纯单测。
 *
 * 这些日期规则一旦错，错法是"账本里凭空多/少一笔"，界面上完全正常，
 * 装机的人要翻很久才发现。所以宁可把每条边界都写成断言。
 */
class RecurringScheduleTest {

    private fun day(text: String): Long = LocalDate.parse(text).toEpochDay()

    private fun days(list: List<Long>): List<String> =
        list.map { LocalDate.ofEpochDay(it).toString() }

    @Test
    fun monthlyCountsFromAnchorNotFromPreviousOccurrence() {
        val anchor = day("2026-01-31")
        // 若按"上一期 + 1 月"递推，2 月被夹成 28 日之后，3 月就永远停在 28 日了
        assertEquals(
            listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30"),
            (0L..3L).map { LocalDate.ofEpochDay(RepeatSchedule.occurrence(BillRepeat.MONTHLY, anchor, it)!!).toString() },
        )
    }

    @Test
    fun weeklyAlwaysLandsOnTheSameWeekday() {
        val anchor = day("2026-09-03")
        val dates = (0L..3L).map {
            LocalDate.ofEpochDay(RepeatSchedule.occurrence(BillRepeat.WEEKLY, anchor, it)!!).toString()
        }
        assertEquals(listOf("2026-09-03", "2026-09-10", "2026-09-17", "2026-09-24"), dates)
        dates.forEach { assertEquals("THURSDAY", LocalDate.parse(it).dayOfWeek.name) }
    }

    @Test
    fun noneNeverGenerates() {
        assertEquals(emptyList<Long>(), RepeatSchedule.pending(BillRepeat.NONE, day("2026-01-01"), 0L, day("2026-12-31")))
        assertNull(RepeatSchedule.occurrence(BillRepeat.NONE, day("2026-01-01"), 0L))
    }

    @Test
    fun doesNotBackfillBeforeTheAnchor() {
        // 9 月 10 日才设的规则，就算今天已经 25 日，也只该有 10 日那一笔
        assertEquals(
            listOf("2026-09-10"),
            days(RepeatSchedule.pending(BillRepeat.MONTHLY, day("2026-09-10"), 0L, day("2026-09-25"))),
        )
    }

    @Test
    fun includesTodayButNotTomorrow() {
        assertEquals(
            listOf("2026-09-10", "2026-09-17", "2026-09-24"),
            days(
                RepeatSchedule.pending(
                    BillRepeat.WEEKLY,
                    anchorDay = day("2026-09-10"),
                    cursorDay = 0L,
                    todayDay = day("2026-09-25"),
                ),
            ),
        )
        // 下一期是 10 月 1 日，今天才 9 月 30 日，就不该提前入账
        assertEquals(
            listOf("2026-09-10", "2026-09-17", "2026-09-24"),
            days(
                RepeatSchedule.pending(
                    BillRepeat.WEEKLY,
                    anchorDay = day("2026-09-10"),
                    cursorDay = 0L,
                    todayDay = day("2026-09-30"),
                ),
            ),
        )
        // 到 10 月 1 日那天，它就该被算进来
        assertEquals(
            "2026-10-01",
            days(
                RepeatSchedule.pending(
                    BillRepeat.WEEKLY,
                    anchorDay = day("2026-09-10"),
                    cursorDay = 0L,
                    todayDay = day("2026-10-01"),
                ),
            ).last(),
        )
    }

    @Test
    fun cursorSkipsPeriodsAlreadyGenerated() {
        // 生成到 8 月 10 日，今天 9 月 25 日：只补 9 月那一期
        assertEquals(
            listOf("2026-09-10"),
            days(
                RepeatSchedule.pending(
                    BillRepeat.MONTHLY,
                    anchorDay = day("2026-08-10"),
                    cursorDay = day("2026-08-10"),
                    todayDay = day("2026-09-25"),
                ),
            ),
        )
    }

    @Test
    fun spansYearsInOrder() {
        val pending = RepeatSchedule.pending(
            BillRepeat.MONTHLY,
            anchorDay = day("2025-01-15"),
            cursorDay = 0L,
            todayDay = day("2026-09-25"),
        )
        assertEquals(21, pending.size)
        assertEquals("2025-01-15", LocalDate.ofEpochDay(pending.first()).toString())
        assertEquals("2026-09-15", LocalDate.ofEpochDay(pending.last()).toString())
        assertEquals(pending, pending.sorted())
    }

    @Test
    fun runawayAnchorIsCapped() {
        // 手工修库把首次日期改到几十年前，也不该让一次启动变成几万次插入。
        // 用周频是因为月频要跨 100 年才够触发上限，测起来不直观。
        val pending = RepeatSchedule.pending(BillRepeat.WEEKLY, day("1971-01-04"), 0L, day("2026-09-25"))
        assertEquals(RepeatSchedule.MAX_PER_PASS, pending.size)
        // 上限截断的是"往后还有多少期"，不是把开头砍掉：首期仍然是首次入账那天
        assertEquals(day("1971-01-04"), pending.first())
    }

    @Test
    fun unsetAnchorGeneratesNothing() {
        // epochDay <= 0 在这套库里表示"没设过"，不能被当成 1970 年以前真的有一条规则
        assertEquals(emptyList<Long>(), RepeatSchedule.pending(BillRepeat.MONTHLY, 0L, 0L, day("2026-09-25")))
        assertEquals(emptyList<Long>(), RepeatSchedule.pending(BillRepeat.MONTHLY, -25_567L, 0L, day("2026-09-25")))
    }

    @Test
    fun nextPointsAtTheUpcomingOccurrence() {
        assertEquals(
            day("2026-10-10"),
            RepeatSchedule.next(BillRepeat.MONTHLY, day("2026-09-10"), day("2026-09-10"), day("2026-09-25")),
        )
        // 今天就是期次，那"下一次"就是今天
        assertEquals(
            day("2026-09-25"),
            RepeatSchedule.next(BillRepeat.MONTHLY, day("2026-08-25"), day("2026-08-25"), day("2026-09-25")),
        )
        assertNull(RepeatSchedule.next(BillRepeat.NONE, day("2026-09-25"), 0L, day("2026-09-25")))
    }

    @Test
    fun yuanParsingRejectsWhatItCannotRepresentExactly() {
        assertEquals(120_000L, yuanTextToCents("1200"))
        assertEquals(1_250L, yuanTextToCents("12.5"))
        assertEquals(1_205L, yuanTextToCents("12.05"))
        assertEquals(0L, yuanTextToCents("0"))
        assertNull(yuanTextToCents(""))
        assertNull(yuanTextToCents("abc"))
        assertNull(yuanTextToCents("-5"))
        // 第三位小数无法用「分」表示，宁可拒绝也不悄悄四舍五入
        assertNull(yuanTextToCents("12.345"))
        assertNull(yuanTextToCents("1.2.3"))
    }

    @Test
    fun yuanRoundTripKeepsCents() {
        listOf(0L, 5L, 50L, 100L, 1_205L, 120_000L, 9_999_999L).forEach { cents ->
            assertEquals(cents, yuanTextToCents(centsToYuanText(cents)))
        }
    }

    @Test
    fun describeOnlyForCompleteRules() {
        val base = Bill(id = 1, name = "房租", iconKey = "home", palette = BillPalette.GREEN, kind = BillKind.EXPENSE)
        assertNull(describeRepeat(base))
        assertNull(describeRepeat(base.copy(repeat = BillRepeat.MONTHLY, repeatAnchorDay = day("2026-09-10"))))
        assertEquals(
            "每月 ¥1,200.00",
            describeRepeat(
                base.copy(
                    repeat = BillRepeat.MONTHLY,
                    repeatAmount = 120_000L.cents,
                    repeatAnchorDay = day("2026-09-10"),
                ),
            ),
        )
    }

    @Test
    fun repeatGapNamesTheMissingPiece() {
        assertEquals(RepeatGap.NONE_SET, RepeatGap.of(BillRepeat.NONE, 0L, 0L))
        assertEquals(RepeatGap.NO_AMOUNT, RepeatGap.of(BillRepeat.MONTHLY, 0L, day("2026-09-10")))
        assertEquals(RepeatGap.NO_ANCHOR, RepeatGap.of(BillRepeat.MONTHLY, 100L, 0L))
        assertEquals(RepeatGap.NONE_SET, RepeatGap.of(BillRepeat.MONTHLY, 100L, day("2026-09-10")))
    }
}
