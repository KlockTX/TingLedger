package com.klocktx.moneyblocks.domain.usecase

import com.klocktx.moneyblocks.core.Cents
import com.klocktx.moneyblocks.core.cents
import java.time.LocalDate

/**
 * 预算的**节奏**：不是"花了多少"，而是"照这样下去会怎样、每天还能花多少"。
 *
 * 放在 domain 层且全是整数运算，因为这几条判断最容易在边界上出错
 * （月初第一天、月末最后一天、二月、闰年、预算刚好花完），而它们在界面上都是
 * 一句看起来很有道理的话——说错了没人会发现。
 */
enum class PaceKind {
    /** 没设预算，一切都该闭嘴。 */
    INACTIVE,

    /** 落后进度超过 10%，宽裕。 */
    EASY,

    /** 基本贴着进度走。 */
    CLOSE,

    /** 按当前速度月末会超。 */
    OVER_SOON,

    /** 已经超了。 */
    OVER_NOW,
}

data class Pace(
    val spent: Cents,
    val budget: Cents,
    /** 本月**到今天为止**过了几天（含今天），1..daysInMonth。 */
    val elapsedDays: Int,
    val daysInMonth: Int,
) {
    val isActive: Boolean get() = budget.value > 0L

    /** 含今天还剩几天可花；月末那天是 1，不会为负。 */
    val daysLeft: Int get() = (daysInMonth - elapsedDays + 1).coerceAtLeast(0)

    val isOverNow: Boolean get() = isActive && spent.value > budget.value

    /** 已经花掉的份额。 */
    val consumed: Double
        get() = if (!isActive) 0.0 else spent.value.toDouble() / budget.value.toDouble()

    /** 已经过去的时间份额。两者一比就是"花得比时间快不快"。 */
    val elapsed: Double
        get() = if (daysInMonth <= 0) 0.0 else elapsedDays.toDouble() / daysInMonth.toDouble()

    /** 到目前为止平均每天花多少。一天都还没过（未来的月份）没有速度可言，给 0
     *  而不是"把已花的当成一天的量"——那会让预测直接放大到整月。 */
    val dailySoFar: Cents
        get() = if (elapsedDays <= 0) 0L.cents else (spent.value / elapsedDays).cents

    /**
     * 剩下的钱平摊到剩下的每一天。没预算、或已经超了，都是 0——
     * "还建议每天花 -50"不是一句可以显示的话。
     */
    val allowance: Cents
        get() {
            if (!isActive || daysLeft <= 0) return 0L.cents
            val left = budget.value - spent.value
            if (left <= 0L) return 0L.cents
            return (left / daysLeft).cents
        }

    /**
     * 天数太少时预测是噪音。月初第一天一笔房租就能把预测推到三倍，
     * 用户当时的判断不会是"这月危险了"，而是"这 App 在瞎说"。
     */
    val projectionIsReliable: Boolean get() = elapsedDays >= MIN_DAYS_FOR_PROJECTION

    /** 按当前平均速度铺满整月的预计支出。不可信时也照样算得出来，由界面决定说不说。 */
    val projected: Cents
        get() = (dailySoFar.value * daysInMonth.coerceAtLeast(1)).cents

    val willOverrun: Boolean get() = isActive && projected.value > budget.value

    /**
     * 按当前速度，本月第几天累计会**超过**预算；不会超则 null。
     * 正好花到预算不算超（和 [CapStatus] 同一口径）。
     */
    val overrunDay: Int?
        get() {
            val perDay = dailySoFar.value
            if (!isActive || perDay <= 0L) return null
            val day = (budget.value / perDay + 1).toInt()
            return if (day <= daysInMonth) day else null
        }

    val state: PaceKind
        get() = when {
            !isActive -> PaceKind.INACTIVE
            isOverNow -> PaceKind.OVER_NOW
            willOverrun -> PaceKind.OVER_SOON
            elapsed - consumed <= CLOSE_WITHIN -> PaceKind.CLOSE
            else -> PaceKind.EASY
        }

    companion object {
        /** 少于这么多天不谈预测。 */
        const val MIN_DAYS_FOR_PROJECTION = 7

        /** 落后进度不到这个数，算"压线"而不是"宽裕"。 */
        const val CLOSE_WITHIN = 0.10

        /** 按某个月算节奏。[today] 只在查看本月时等于真实今天；翻到往月时传该月最后一天。 */
        fun of(monthFirst: LocalDate, spent: Cents, budget: Cents, today: LocalDate): Pace {
            val daysInMonth = monthFirst.lengthOfMonth()
            val elapsed = if (monthFirst == today.withDayOfMonth(1)) {
                today.dayOfMonth
            } else if (monthFirst.isAfter(today)) {
                0
            } else {
                daysInMonth
            }
            return Pace(spent = spent, budget = budget, elapsedDays = elapsed, daysInMonth = daysInMonth)
        }
    }
}
