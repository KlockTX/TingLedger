package com.klocktx.moneyblocks.domain.model

import com.klocktx.moneyblocks.core.TimeRange
import com.klocktx.moneyblocks.core.monthRange
import com.klocktx.moneyblocks.core.startMillis
import java.time.LocalDate

/** 金额下限。做成枚举而不是自由输入：四个档覆盖"找大笔支出"的实际问法，
 *  而且不会出现"用户输了个 0.001 然后奇怪为什么没结果"。 */
enum class AmountFloor(val label: String, val cents: Long) {
    ANY("不限", 0L),
    Y100("≥100", 10_000L),
    Y500("≥500", 50_000L),
    Y1000("≥1000", 100_000L),
}

/** 时间范围。"近 7 天"含今天，"本月/上月"按自然月——和首页、分析页的口径一致。 */
enum class DateScope(val label: String) {
    ALL("全部"),
    WEEK("近7天"),
    MONTH("本月"),
    LAST_MONTH("上月"),
}

/**
 * 搜索的附加筛选条件。三个维度都为空时等价于"不筛"。
 *
 * 放 domain 层：`rangeFor` 是纯日期算术，最容易写错（月初、月末、跨月、跨年），
 * 必须能脱离 Android 直接单测。
 */
data class SearchFilter(
    val kind: BillKind? = null,
    val floor: AmountFloor = AmountFloor.ANY,
    val scope: DateScope = DateScope.ALL,
) {
    /** 一个筛选都没开。用它决定"空关键词要不要查库"。 */
    val isPristine: Boolean get() = kind == null && floor == AmountFloor.ANY && scope == DateScope.ALL

    /** 该范围内的归属时间；[DateScope.ALL] 返回 null 表示不限时间。 */
    fun rangeFor(today: LocalDate): TimeRange? = when (scope) {
        DateScope.ALL -> null
        DateScope.WEEK -> TimeRange(
            start = today.minusDays(6).startMillis(),
            endExclusive = today.plusDays(1).startMillis(),
        )
        DateScope.MONTH -> monthRange(today.year, today.monthValue)
        DateScope.LAST_MONTH -> {
            val first = today.minusMonths(1).withDayOfMonth(1)
            TimeRange(first.startMillis(), first.plusMonths(1).startMillis())
        }
    }
}
