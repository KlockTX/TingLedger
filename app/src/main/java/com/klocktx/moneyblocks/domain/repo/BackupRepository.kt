package com.klocktx.moneyblocks.domain.repo

import com.klocktx.moneyblocks.domain.usecase.BackupBill
import com.klocktx.moneyblocks.domain.usecase.BackupEntry

/** 整库备份在库里的两半：账单与条目。偏好设置走 SettingsRepository，不在这里。 */
data class BackupRows(
    val bills: List<BackupBill>,
    val entries: List<BackupEntry>,
)

sealed interface ReplaceOutcome {
    data class Applied(val bills: Int, val entries: Int) : ReplaceOutcome

    /** 落库后自我对账没过，事务已回滚——本机数据一点没动。 */
    data class Reverted(val reason: String) : ReplaceOutcome
}

interface BackupRepository {
    suspend fun read(): BackupRows

    /** 整库替换。实现必须保证「要么全换成、要么一点不动」，不允许换出一半的状态。 */
    suspend fun replace(rows: BackupRows): ReplaceOutcome
}
