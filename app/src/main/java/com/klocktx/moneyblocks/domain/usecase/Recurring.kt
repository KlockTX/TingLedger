package com.klocktx.moneyblocks.domain.usecase

import com.klocktx.moneyblocks.core.startMillis
import com.klocktx.moneyblocks.core.today
import com.klocktx.moneyblocks.core.toYuanWithSymbol
import com.klocktx.moneyblocks.domain.model.Bill
import com.klocktx.moneyblocks.domain.model.BillRepeat
import com.klocktx.moneyblocks.domain.model.Entry
import com.klocktx.moneyblocks.domain.repo.BillRepository
import com.klocktx.moneyblocks.domain.repo.EntryRepository
import java.time.LocalDate

/**
 * 周期规则的排期计算。**不引任何 Android 类型**，所以整套日期规则能跑在普通单测里——
 * 月末夹日期、跨月、跨年这些恰恰是最容易口头保证、实际写错的东西。
 */
object RepeatSchedule {

    /** 单次最多生成多少期。防的是手工修库把首次日期写成 1970 年之类，把一次启动变成死循环。 */
    const val MAX_PER_PASS = 1_200

    /** 排期扫描的步数上限。生成条数受 [MAX_PER_PASS] 管，但"跳过旧期次"也得有边界。 */
    private const val MAX_STEPS = 6_000L

    /**
     * 第 n 期落在哪天（n 从 0 开始，0 就是首次入账那天）。
     *
     * 月度**始终从首次那天加 n 个月**，而不是"上一期加一个月"：`LocalDate.plusMonths`
     * 会把 1 月 31 日夹到 2 月 28 日，若接着从 2 月 28 日往后加，3 月就变成 28 日了——
     * 用户设的是"每月 31 号"，就该是 1/31、2/28、3/31。
     */
    fun occurrence(repeat: BillRepeat, anchorDay: Long, n: Long): Long? = when (repeat) {
        BillRepeat.NONE -> null
        BillRepeat.MONTHLY ->
            if (anchorDay <= 0L || n < 0L) null
            else LocalDate.ofEpochDay(anchorDay).plusMonths(n).toEpochDay()
        BillRepeat.WEEKLY ->
            if (anchorDay <= 0L || n < 0L) null
            else anchorDay + n * 7L
    }

    /**
     * 该补生成的日期列表（epochDay，升序）。
     *
     * 三条边界都是产品决定，不是实现细节：
     * - **不早于首次入账日**：用户挑了 9 月 1 日开始，就不该冒出 7、8 月两笔房租；
     * - **不晚于今天**：未来的期次留着到点再生成；
     * - **不重复已走过的期次**（游标之前）：删掉一笔自动条目就是真想删掉它。
     */
    fun pending(
        repeat: BillRepeat,
        anchorDay: Long,
        cursorDay: Long,
        todayDay: Long,
    ): List<Long> {
        if (repeat == BillRepeat.NONE || anchorDay <= 0L) return emptyList()
        val from = maxOf(cursorDay, anchorDay - 1L)
        val out = ArrayList<Long>()
        var step = 0L
        while (out.size < MAX_PER_PASS && step < MAX_STEPS) {
            val day = occurrence(repeat, anchorDay, step) ?: return out
            if (day > todayDay) break
            if (day > from) out += day
            step++
        }
        return out
    }

    /** 下一次将要入账那天（含今天）；规则不成立或日期溢出时返回 null。 */
    fun next(repeat: BillRepeat, anchorDay: Long, cursorDay: Long, todayDay: Long): Long? {
        if (repeat == BillRepeat.NONE || anchorDay <= 0L) return null
        var step = 0L
        while (step < MAX_STEPS) {
            val day = occurrence(repeat, anchorDay, step) ?: return null
            if (day > maxOf(cursorDay, anchorDay - 1L) && day >= todayDay) return day
            step++
        }
        return null
    }
}

/**
 * 到期把周期性账单落成真实条目。
 *
 * 只在进程启动时跑一次（见 `MoneyBlocksApp`），没有后台任务：这类 App 被系统杀掉是常态，
 * 起一次补一次即可，省下一条常驻通知和一套 WorkManager 依赖。
 */
class RecurringUseCase(
    private val bills: BillRepository,
    private val entries: EntryRepository,
) {

    /** [created] 是真正写进库的条数——唯一索引挡掉的不算，所以它才是"账本多了几笔"。 */
    data class Result(val created: Int, val touchedBills: Int)

    suspend fun runToday(todayDay: Long = today().toEpochDay()): Result {
        var created = 0
        var touched = 0
        bills.recurring().forEach { bill ->
            val days = RepeatSchedule.pending(bill.repeat, bill.repeatAnchorDay, bill.repeatCursorDay, todayDay)
            if (days.isEmpty()) return@forEach
            var last = bill.repeatCursorDay
            days.forEach { day ->
                if (entries.addAuto(bill.toAutoEntry(day))) created++
                last = day
            }
            bills.setRepeatCursor(bill.id, last)
            touched++
        }
        return Result(created, touched)
    }

    companion object {
        /** 自动条目落在当天 00:00：它属于"这一天"，不该假装有个具体时刻。 */
        fun Bill.toAutoEntry(epochDay: Long): Entry = Entry(
            billId = id,
            amount = repeatAmount,
            note = "",
            occurredAt = LocalDate.ofEpochDay(epochDay).startMillis(),
            autoDay = epochDay,
        )
    }
}

/** 规则还差什么。编辑弹窗用它决定"保存"能不能按，以及提示哪一项没填。 */
enum class RepeatGap {
    NONE_SET, NO_AMOUNT, NO_ANCHOR;

    companion object {
        fun of(repeat: BillRepeat, amountCents: Long, anchorDay: Long): RepeatGap = when {
            repeat == BillRepeat.NONE -> NONE_SET
            amountCents <= 0L -> NO_AMOUNT
            anchorDay <= 0L -> NO_ANCHOR
            else -> NONE_SET
        }
    }
}

/** 元 → 分。金额链路不允许 Double 参与，所以这里按"整数元或两位小数"逐字符解析。 */
fun yuanTextToCents(text: String): Long? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val parts = trimmed.split('.')
    if (parts.isEmpty() || parts.size > 2) return null
    val yuan = parts[0].toLongOrNull() ?: return null
    if (yuan < 0L) return null
    val fraction = when (parts.getOrNull(1)?.length) {
        null, 0 -> 0L
        1 -> (parts[1].toLongOrNull() ?: return null) * 10L
        2 -> parts[1].toLongOrNull() ?: return null
        else -> return null
    }
    return yuan * 100L + fraction
}

/** 分 → 元文本，去掉多余的尾随 0，给输入框回显用。 */
fun centsToYuanText(cents: Long): String =
    if (cents % 100L == 0L) (cents / 100L).toString()
    else String.format(java.util.Locale.US, "%.2f", cents / 100.0)

/** 供界面显示的"每月 ¥1,200"这类一句话摘要。 */
fun describeRepeat(bill: Bill): String? {
    if (!bill.isRecurring) return null
    val amount = bill.repeatAmount.toYuanWithSymbol()
    return when (bill.repeat) {
        BillRepeat.MONTHLY -> "每月 $amount"
        BillRepeat.WEEKLY -> "每周 $amount"
        BillRepeat.NONE -> null
    }
}
