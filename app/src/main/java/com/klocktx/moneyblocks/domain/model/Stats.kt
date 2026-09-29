package com.klocktx.moneyblocks.domain.model

import com.klocktx.moneyblocks.core.Cents
import com.klocktx.moneyblocks.core.cents
import com.klocktx.moneyblocks.core.toYuanString
import com.klocktx.moneyblocks.core.toYuanWithSymbol
import java.time.LocalDate

data class BillSummary(
    val bill: Bill,
    val periodTotal: Cents,
    val periodCount: Int,
    val allTimeTotal: Cents,
    val allTimeCount: Int,
    val lastEntryAt: Long?,
) {
    /** 该账单在所选区间内的支出占比，区间总额为 0 时返回 0。 */
    fun shareOf(total: Cents): Double =
        if (total.value == 0L) 0.0 else periodTotal.value.toDouble() / total.value.toDouble()
}

data class DayPoint(val date: LocalDate, val total: Cents)

data class LargestEntry(
    val entry: Entry,
    val billName: String,
    val billIconKey: String,
    val billPalette: BillPalette,
    val billKind: BillKind,
)

data class OverviewStat(
    val monthTotal: Cents,
    val todayTotal: Cents,
    val elapsedDays: Int,
    val dailyAverage: Cents,
    /** 上月同期（相同「月内第几天」）总额，用于对齐比较。 */
    val prevMonthSameSpanTotal: Cents,
) {
    /** 环比变化比例；分母为 0 时返回 null，UI 显示「—」而不是 +Infinity%。 */
    val monthOverMonthRatio: Double?
        get() {
            val base = prevMonthSameSpanTotal.value
            if (base == 0L) return null
            return (monthTotal.value - base).toDouble() / base.toDouble()
        }
}

/**
 * 一个账单在本月的占比，连同它自己的月度上限。
 *
 * `cap` 放在这里而不是让界面去查 `Map<账单 id, 上限>`：`Map` 在 Compose 里是**不稳定类型**，
 * 传进组合会让用到它的那一段每次都被重组，明明数据没变。
 */
data class ShareSlice(
    val bill: Bill,
    val total: Cents,
    val share: Double,
    /** 0 表示没设上限。 */
    val cap: Cents = 0L.cents,
)

/** 一次搜索命中：条目加上它所属账单，界面直接可渲染。 */
data class SearchHit(val entry: Entry, val bill: Bill)

/**
 * 一组命中按方向拆开。收支混在一起时「合计」是个骗人的数字
 * （两边一减可能接近 0），所以只能分开报。
 */
data class KindTotals(val expense: Cents, val income: Cents)

fun List<SearchHit>.kindTotals(): KindTotals = KindTotals(
    expense = filterNot { it.bill.isIncome }.sumOf { it.entry.amount.value }.cents,
    income = filter { it.bill.isIncome }.sumOf { it.entry.amount.value }.cents,
)

/** 合计文案。只有一边时给一个数，两边都有时拆开写清楚。 */
fun KindTotals.amountLabel(): String = when {
    income.value == 0L -> expense.toYuanWithSymbol()
    expense.value == 0L -> "+${income.toYuanWithSymbol()}"
    else -> "支出 ${expense.toYuanString()} · 收入 +${income.toYuanString()}"
}

/** 月度对比图的一个柱子。 */
data class MonthPoint(val monthFirst: LocalDate, val total: Cents)

/** 本月节奏小结。 */
data class MonthDigest(
    val entryCount: Int,
    val average: Cents,
    /** 花钱最多的那一天；整月没有记录时为 null。 */
    val busiestDay: DayPoint?,
)

/**
 * 收支两侧各自汇总的结果。结余可以为负——这个负数是有意义的
 * （这个月花超了），和 Cents 里「金额恒为正」不冲突。
 */
data class Balance(val expense: Cents, val income: Cents) {
    val net: Cents get() = income - expense

    /** 一笔收入都没有时，界面上不必再摆「收入 0.00 / 结余 -xxx」。 */
    val hasIncome: Boolean get() = income.value > 0L
}
