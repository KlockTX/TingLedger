package com.klocktx.moneyblocks.domain.usecase

import com.klocktx.moneyblocks.core.Cents
import com.klocktx.moneyblocks.core.cents
import com.klocktx.moneyblocks.domain.model.DayPoint
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 消费日历的格子。**不引任何 Android 类型**，所以对齐、分档这些最容易画错的东西
 * 能跑在普通单测里——日历错了没人会去逐格核，只会觉得"好像哪不对"。
 */
data class HeatCell(
    /** null 表示月初前面的空格子（让 1 号落在它真正的星期列上）。 */
    val date: LocalDate?,
    val cents: Cents,
    /** 0 = 没花钱，1..[MonthHeatmap.LEVELS] 是深浅档。 */
    val level: Int,
) {
    val isEmptySlot: Boolean get() = date == null
}

object MonthHeatmap {

    /** 深浅分四档。再多就不是"扫一眼看出哪几天多"，而是查表了。 */
    const val LEVELS = 4

    /** 一列一星期，周一开头——和中文日历习惯一致（周一、周二……）。 */
    val weekColumns: List<DayOfWeek> = listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
        DayOfWeek.SUNDAY,
    )

    /** 星期几在第几列。`DayOfWeek.value` 是周一=1…周日=7。 */
    fun columnOf(day: DayOfWeek): Int = weekColumns.indexOf(day)

    /**
     * 某天落在第几档。用**当月最贵那天**当基准，而不是平均值：
     * 日历要回答的是"哪几天异常重"，和峰值比才看得出来；和平均比会把所有日子挤成中间色。
     */
    fun levelOf(cents: Long, peakCents: Long): Int {
        if (cents <= 0L || peakCents <= 0L) return 0
        val ratio = cents.toDouble() / peakCents.toDouble()
        return when {
            ratio > 0.75 -> 4
            ratio > 0.50 -> 3
            ratio > 0.25 -> 2
            else -> 1
        }
    }

    /**
     * 拼出这个月的格子：先是把 1 号推到正确列的前导空格，然后每天一格。
     *
     * 天数直接取 `monthFirst.lengthOfMonth()`，不靠 `days` 里有没有数据——
     * 一个月一天都没记的时候，日历照样要摆满整月，否则它会缩成几格，看着像坏了。
     */
    fun cells(monthFirst: LocalDate, days: List<DayPoint>): List<HeatCell> {
        val monthLast = monthFirst.withDayOfMonth(monthFirst.lengthOfMonth())
        val byDate = days
            .filter { !it.date.isBefore(monthFirst) && !it.date.isAfter(monthLast) }
            .associate { it.date to it.total }
        val peak = (byDate.values + 0L.cents).maxByOrNull { it.value } ?: 0L.cents
        val lead = columnOf(monthFirst.dayOfWeek)
        val out = ArrayList<HeatCell>(lead + monthFirst.lengthOfMonth())
        repeat(lead) { out += HeatCell(null, 0L.cents, 0) }
        for (day in 1..monthFirst.lengthOfMonth()) {
            val date = monthFirst.withDayOfMonth(day)
            val cents = byDate[date] ?: 0L.cents
            out += HeatCell(date, cents, levelOf(cents.value, peak.value))
        }
        return out
    }
}

/**
 * 连续记账天数。
 *
 * 口径是**人性化**的：今天还没记不算断。一个人晚上十点打开 App，
 * 看到"连续 12 天"比看到"连续 11 天，昨天那格断了"更可能继续记下去；
 * 而真要惩罚"还没记"，首页那条「今天还没记」已经在提醒了，这里不该再吓一次。
 */
object Streak {

    /** 从今天往回数，连续有多少天记过账（今天没记不算断，往前找）。 */
    fun days(active: Set<LocalDate>, today: LocalDate): Int {
        if (active.isEmpty()) return 0
        var cursor = if (active.contains(today)) today else today.minusDays(1)
        if (!active.contains(cursor)) return 0
        var count = 0
        while (active.contains(cursor)) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }
}
