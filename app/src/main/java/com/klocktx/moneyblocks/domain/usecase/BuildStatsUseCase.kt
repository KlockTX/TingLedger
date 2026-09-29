package com.klocktx.moneyblocks.domain.usecase

import com.klocktx.moneyblocks.core.Cents
import com.klocktx.moneyblocks.core.TimeRange
import com.klocktx.moneyblocks.core.cents
import com.klocktx.moneyblocks.core.monthRange
import com.klocktx.moneyblocks.core.today
import com.klocktx.moneyblocks.core.trailingDayRange
import com.klocktx.moneyblocks.domain.model.BillKind
import com.klocktx.moneyblocks.domain.model.BillSummary
import com.klocktx.moneyblocks.domain.model.DayPoint
import com.klocktx.moneyblocks.domain.model.LargestEntry
import com.klocktx.moneyblocks.domain.model.MonthDigest
import com.klocktx.moneyblocks.domain.model.ShareSlice
import com.klocktx.moneyblocks.domain.repo.StatsRepository
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class BuildStatsUseCase(private val stats: StatsRepository) {

    suspend fun trend(days: Int): List<DayPoint> = stats.dailySeries(trailingDayRange(days))

    suspend fun largest(range: TimeRange): LargestEntry? = stats.largestEntry(range)

    suspend fun prevMonthSameSpanTotal(monthFirst: LocalDate, elapsedDays: Int): Cents =
        stats.total(previousMonthSameSpan(monthFirst, elapsedDays))

    companion object {
        /** 与上月「相同已过天数」对齐比较，否则月初看环比必然是 -90%+ 的假信号。 */
        private fun previousMonthSameSpan(monthFirst: LocalDate, elapsedDays: Int): TimeRange {
            val prevFirst = monthFirst.minusMonths(1)
            val span = elapsedDays.coerceIn(0, prevFirst.lengthOfMonth())
            return TimeRange(
                prevFirst.atStartOfDayZoneMillis(),
                prevFirst.plusDays(span.toLong()).atStartOfDayZoneMillis(),
            )
        }

        fun rangeOf(first: LocalDate): TimeRange = monthRange(first.year, first.monthValue)

        /** 当月已过天数：本月按今天算，历史月按整月算，未来的月份算 0。 */
        fun elapsedDays(monthFirst: LocalDate): Int {
            val now = today()
            return when {
                YearMonth.from(now) == YearMonth.from(monthFirst) -> now.dayOfMonth
                now.isAfter(lastDayOf(monthRange(monthFirst.year, monthFirst.monthValue))) ->
                    monthFirst.lengthOfMonth()
                else -> 0
            }
        }

        /**
         * 要不要提醒「今天还没记」。
         *
         * 三个条件缺一不可：正在看本月（翻到历史月时提醒毫无意义）、
         * 本月确实记过（新装机第一个月不该被催）、今天还是 0 笔。
         * 用**笔数**而不是金额判断：有人今天记的是一笔 0 元的转账，那也算记过。
         * 点过一次就当天不再提（[nudgeSeenDay] == [todayDay]），免得变成追着人的横幅。
         */
        fun shouldNudgeToday(
            todayCount: Int,
            monthCount: Int,
            viewingCurrentMonth: Boolean,
            todayDay: Long,
            nudgeSeenDay: Long = 0L,
        ): Boolean = viewingCurrentMonth && todayCount == 0 && monthCount > 0 &&
            nudgeSeenDay != todayDay

        fun dailyAverage(monthTotal: Cents, elapsedDays: Int): Cents =
            if (elapsedDays <= 0) 0L.cents else (monthTotal.value / elapsedDays.toLong()).cents

        /**
         * 占比：区间总额为 0 时全部返回 0，避免除零产生 NaN。
         * 只看支出账单——把工资算进「钱花在哪了」的分会让每个支出占比都凭空变小。
         */
        fun shareSlices(
            summaries: List<BillSummary>,
            caps: Map<Long, Cents> = emptyMap(),
        ): List<ShareSlice> {
            val expense = summaries.filter { it.bill.kind == BillKind.EXPENSE }
            val total = expense.sumOf { it.periodTotal.value }
            return expense
                .map { row ->
                    ShareSlice(
                        bill = row.bill,
                        total = row.periodTotal,
                        share = if (total == 0L) 0.0
                        else row.periodTotal.value.toDouble() / total.toDouble(),
                        cap = caps[row.bill.id] ?: 0L.cents,
                    )
                }
                .sortedByDescending { it.total.value }
        }

        fun periodTotalOf(summaries: List<BillSummary>): Cents =
            summaries.sumOf { it.periodTotal.value }.cents

        /**
         * 本月节奏小结。平均单笔按「笔」算，和按「天」算的日均是两个问题，别混成一个数。
         * 最贵一天只在真有花费的日子中选，否则整月空着时会报出某个 0.00 的日子。
         */
        fun monthDigest(days: List<DayPoint>, entryCount: Int, total: Cents): MonthDigest = MonthDigest(
            entryCount = entryCount,
            average = if (entryCount <= 0) 0L.cents else (total.value / entryCount.toLong()).cents,
            busiestDay = days.filter { it.total.value > 0L }.maxByOrNull { it.total.value },
        )

        /** 累计口径单独算，避免和「本月」混在一个数字里造成误解。 */
        fun allTimeTotalOf(summaries: List<BillSummary>): Cents =
            summaries.sumOf { it.allTimeTotal.value }.cents

        private fun lastDayOf(range: TimeRange): LocalDate =
            Instant.ofEpochMilli(range.endExclusive - 1).atZone(ZoneId.systemDefault()).toLocalDate()

        private fun LocalDate.atStartOfDayZoneMillis(): Long =
            atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
