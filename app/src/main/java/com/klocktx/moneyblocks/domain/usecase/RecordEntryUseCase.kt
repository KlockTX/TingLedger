package com.klocktx.moneyblocks.domain.usecase

import com.klocktx.moneyblocks.core.Cents
import com.klocktx.moneyblocks.core.cents
import com.klocktx.moneyblocks.domain.model.Bill
import com.klocktx.moneyblocks.domain.model.Entry
import com.klocktx.moneyblocks.domain.repo.BillRepository
import com.klocktx.moneyblocks.domain.repo.EntryRepository
import com.klocktx.moneyblocks.domain.repo.SettingsRepository

sealed interface RecordOutcome {
    data class Saved(val entryId: Long) : RecordOutcome
    data class Rejected(val reason: RejectReason) : RecordOutcome
}

enum class RejectReason(val message: String) {
    BLANK_EXPRESSION("请输入金额"),
    INVALID_EXPRESSION("表达式格式不正确"),
    NOT_POSITIVE("金额需大于 0"),
    OUT_OF_RANGE("金额超出上限"),
}

/** 记一笔 / 改一笔：表达式 → 分 → 落库。本 App 只记支出，0 与负数一律拒绝。 */
class RecordEntryUseCase(private val entries: EntryRepository) {

    suspend fun record(billId: Long, expression: String, note: String, occurredAt: Long): RecordOutcome {
        val cents = toCents(expression) ?: return invalid(expression)
        if (cents <= 0L) return RecordOutcome.Rejected(RejectReason.NOT_POSITIVE)
        val id = entries.add(
            Entry(billId = billId, amount = cents.cents, note = note.trim(), occurredAt = occurredAt),
        )
        return RecordOutcome.Saved(id)
    }

    suspend fun edit(
        entryId: Long,
        billId: Long,
        expression: String,
        note: String,
        occurredAt: Long,
    ): RecordOutcome {
        val existing = entries.find(entryId)
            ?: return RecordOutcome.Rejected(RejectReason.INVALID_EXPRESSION)
        val cents = toCents(expression) ?: return invalid(expression)
        if (cents <= 0L) return RecordOutcome.Rejected(RejectReason.NOT_POSITIVE)
        // billId 一并写入：记错账单时可以直接挪到别的账单，不用删了重记
        entries.update(
            existing.copy(billId = billId, amount = cents.cents, note = note.trim(), occurredAt = occurredAt),
        )
        return RecordOutcome.Saved(entryId)
    }

    private fun invalid(expression: String): RecordOutcome =
        if (expression.isBlank()) RecordOutcome.Rejected(RejectReason.BLANK_EXPRESSION)
        else RecordOutcome.Rejected(
            (ExpressionEvaluator.evaluate(expression) as? CalcOutcome.Failed)?.error?.toReason()
                ?: RejectReason.INVALID_EXPRESSION,
        )

    private fun toCents(expression: String): Long? =
        (ExpressionEvaluator.evaluate(expression) as? CalcOutcome.Ok)?.cents
}

private fun CalcError.toReason(): RejectReason = when (this) {
    CalcError.OUT_OF_RANGE -> RejectReason.OUT_OF_RANGE
    CalcError.EMPTY -> RejectReason.BLANK_EXPRESSION
    else -> RejectReason.INVALID_EXPRESSION
}

class ManageBillsUseCase(
    private val bills: BillRepository,
    private val settings: SettingsRepository,
) {

    /**
     * 建或改一条账单。id==0 就是新建，其余一律整行覆盖。
     *
     * 原来这里是 `create(四个参数)` 和 `save(五个参数)` 两份，加了周期规则之后参数要涨到七个，
     * 两处还都得跟着改——所以收成一个：界面拿着一份 Bill 改完直接交回来。
     *
     * [cap] 也在这里一起落：它是偏好不是账单字段（不占 schema 列），但改账单时用户看到的
     * 是同一个表单，拆开保存就会出现"名字改了上限没改"的半提交。传 0 就是清掉上限。
     */
    suspend fun save(bill: Bill, cap: Cents = 0L.cents): Long {
        val trimmed = bill.copy(name = bill.name.trim())
        val id = if (trimmed.id == 0L) {
            bills.save(trimmed.copy(sortOrder = bills.maxSortOrder() + 1))
        } else {
            bills.save(trimmed)
            trimmed.id
        }
        settings.setBillCap(id, cap)
        return id
    }

    suspend fun archive(bill: Bill) = bills.setArchived(bill.id, true)

    suspend fun restore(bill: Bill) = bills.setArchived(bill.id, false)

    /**
     * 删除 = 移入回收站。
     *
     * 名字仍然叫 remove 是有意的：调用方（首页/设置页的删除按钮）要的就是"从账本里消失"，
     * 至于它其实是可恢复的，属于这里的实现细节。真删走 [deleteForever]，只在回收站里给。
     */
    suspend fun remove(bill: Bill) = bills.trash(bill.id)

    suspend fun untrash(bill: Bill) = bills.restore(bill.id)

    /**
     * 真删。上限存在 DataStore 里、按 id 索引，所以必须跟着一起摘：
     * SQLite 的 rowid 会被复用，留着残值就是让将来某个新账单莫名背上一个旧上限。
     */
    suspend fun deleteForever(bill: Bill) {
        bills.deleteForever(bill.id)
        settings.dropBillCap(bill.id)
    }

    /**
     * 清掉过了保留期的回收站条目。进程启动时跑一次，和周期生成同一个时机——
     * 不需要后台任务：这类 App 被杀是常态，起一次清一次足够。
     */
    suspend fun purgeTrash(now: Long = System.currentTimeMillis()): Int {
        val purged = bills.purgeTrash(now - TRASH_RETENTION_DAYS * 86_400_000L)
        purged.forEach { settings.dropBillCap(it) }
        return purged.size
    }

    /** 拖动结束时整表落库：一次写入，避免每换一格就写一轮数据库。 */
    suspend fun commitOrder(orderedIds: List<Long>) = bills.reorder(orderedIds)

    /** 上移/下移：调用方传入当前可见顺序，避免为了挪一位再去查一次库。 */
    suspend fun move(active: List<Bill>, billId: Long, up: Boolean) {
        val index = active.indexOfFirst { it.id == billId }
        if (index < 0) return
        val target = if (up) index - 1 else index + 1
        if (target !in active.indices) return
        val swapped = active.toMutableList().apply { add(target, removeAt(index)) }
        bills.reorder(swapped.map { it.id })
    }

    companion object {
        /**
         * 回收站保留天数。
         *
         * 30 天的来由：误删基本都在当天或几天内发现，超过一个月才想起来的话，
         * 更可能的是"本来就该删"。再长下去回收站就变成第二个账本，
         * 每一处统计都要多养一个过滤条件。
         */
        const val TRASH_RETENTION_DAYS = 30L
    }
}
