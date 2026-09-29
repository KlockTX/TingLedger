package com.klocktx.moneyblocks.data.repo

import com.klocktx.moneyblocks.data.local.BackupDao
import com.klocktx.moneyblocks.data.local.BillEntity
import com.klocktx.moneyblocks.data.local.EntryEntity
import com.klocktx.moneyblocks.domain.repo.BackupRepository
import com.klocktx.moneyblocks.domain.repo.BackupRows
import com.klocktx.moneyblocks.domain.repo.ReplaceOutcome
import com.klocktx.moneyblocks.domain.usecase.BackupBill
import com.klocktx.moneyblocks.domain.usecase.BackupEntry
import kotlinx.coroutines.CancellationException

fun BillEntity.toBackup(): BackupBill = BackupBill(
    id = id,
    name = name,
    iconKey = iconKey,
    colorKey = colorKey,
    kindKey = kind,
    sortOrder = sortOrder,
    archived = archived,
    createdAt = createdAt,
    repeatKey = repeatKey,
    repeatAmountCents = repeatAmountCents,
    repeatAnchorDay = repeatAnchorDay,
    // 游标必须一起备份：丢了它，恢复之后那一期会被重新生成一遍，账本凭空多一笔房租
    repeatCursorDay = repeatCursorDay,
    // 回收站状态也要备份：否则一份"带着回收站"的备份恢复出来会多出几个活账单
    deletedAt = deletedAt,
)

fun BackupBill.toEntity(): BillEntity = BillEntity(
    id = id,
    name = name,
    iconKey = iconKey,
    colorKey = colorKey,
    sortOrder = sortOrder,
    createdAt = createdAt,
    archived = archived,
    kind = kindKey,
    repeatKey = repeatKey,
    repeatAmountCents = repeatAmountCents,
    repeatAnchorDay = repeatAnchorDay,
    repeatCursorDay = repeatCursorDay,
    deletedAt = deletedAt,
)

fun EntryEntity.toBackup(): BackupEntry = BackupEntry(
    id = id,
    billId = billId,
    amountCents = amountCents,
    note = note,
    occurredAt = occurredAt,
    createdAt = createdAt,
    autoDay = autoDay,
)

fun BackupEntry.toEntity(): EntryEntity = EntryEntity(
    id = id,
    billId = billId,
    amountCents = amountCents,
    note = note,
    occurredAt = occurredAt,
    createdAt = createdAt,
    autoDay = autoDay,
)

class BackupRepositoryImpl(private val dao: BackupDao) : BackupRepository {

    override suspend fun read(): BackupRows = BackupRows(
        bills = dao.allBills().map { it.toBackup() },
        entries = dao.allEntries().map { it.toBackup() },
    )

    override suspend fun replace(rows: BackupRows): ReplaceOutcome {
        val bills = rows.bills.map { it.toEntity() }
        val entries = rows.entries.map { it.toEntity() }
        return try {
            dao.replaceAll(bills, entries)
            ReplaceOutcome.Applied(bills = bills.size, entries = entries.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // replaceAll 整个跑在一个事务里，异常时已经回滚，本机数据还是原样
            ReplaceOutcome.Reverted(e.message ?: e.javaClass.simpleName)
        }
    }
}
