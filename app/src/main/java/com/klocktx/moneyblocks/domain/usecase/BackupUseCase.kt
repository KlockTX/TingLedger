package com.klocktx.moneyblocks.domain.usecase

import com.klocktx.moneyblocks.core.cents
import com.klocktx.moneyblocks.domain.model.ThemeMode
import com.klocktx.moneyblocks.domain.repo.BackupRepository
import com.klocktx.moneyblocks.domain.repo.BackupRows
import com.klocktx.moneyblocks.domain.repo.ReplaceOutcome
import com.klocktx.moneyblocks.domain.repo.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * 备份与恢复的编排。
 *
 * 「恢复」刻意分成三段，任何一段不过都不碰数据：
 * 1. 文件校验（[BackupFormat.decode]，纯函数，坏文件在这里全挡掉）；
 * 2. 先给**当前**库拍一份快照留档，这份快照就是界面上「撤销」的原料；
 * 3. 整库替换 + 库内自我对账，失败即回滚。
 *
 * 偏好（预算、黑白模式）走 SettingsRepository，所以备份必须带它们——
 * 单拷数据库文件是漏掉这一半的。
 */
class BackupUseCase(
    private val backup: BackupRepository,
    private val settings: SettingsRepository,
    private val schemaVersion: Int,
    private val appTag: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** 恢复成功后界面上能拿到的东西：[previous] 是替换前的整份快照，用来撤销。 */
    sealed interface Restored {
        data class Applied(val previous: BackupData, val bills: Int, val entries: Int) : Restored
        data class FileRejected(val reason: String) : Restored
        data class ApplyFailed(val reason: String) : Restored
    }

    suspend fun currentSnapshot(): BackupData {
        val rows = backup.read()
        return BackupData(
            schemaVersion = schemaVersion,
            appTag = appTag,
            createdAt = clock(),
            themeModeKey = settings.themeMode.first().key,
            monetThemeKey = settings.monetThemeKey.first(),
            monthlyBudgetCents = settings.monthlyBudget.first().value,
            billCaps = settings.billCaps.first(),
            bills = rows.bills,
            entries = rows.entries,
        )
    }

    suspend fun fileContents(): String = BackupFormat.encode(currentSnapshot())

    /** 只解析与校验，不碰数据库。恢复前给用户看摘要靠它。 */
    fun inspect(text: String): BackupDecode = BackupFormat.decode(text, schemaVersion)

    suspend fun restoreFromText(text: String): Restored =
        when (val parsed = BackupFormat.decode(text, schemaVersion)) {
            is BackupDecode.Rejected -> Restored.FileRejected(parsed.reason)
            is BackupDecode.Success -> applySnapshot(parsed.data)
        }

    /** 「撤销」也走同一条路：把留档那份快照原样换回去。 */
    suspend fun restoreSnapshot(data: BackupData): Restored = applySnapshot(data)

    private suspend fun applySnapshot(data: BackupData): Restored {
        val previous = currentSnapshot()
        return when (
            val outcome = backup.replace(BackupRows(data.bills, data.entries))
        ) {
            is ReplaceOutcome.Reverted -> Restored.ApplyFailed(outcome.reason)
            is ReplaceOutcome.Applied -> {
                settings.setThemeMode(ThemeMode.fromKey(data.themeModeKey))
                // 空串=老备份没这个信息，别把用户的主题选择清掉
                if (data.monetThemeKey.isNotBlank()) settings.setMonetThemeKey(data.monetThemeKey)
                settings.setMonthlyBudget(data.monthlyBudgetCents.cents)
                // 整表替换而不是逐条 set：恢复必须以文件为准，本机残值一条都不能留
                settings.replaceBillCaps(data.billCaps)
                Restored.Applied(previous, outcome.bills, outcome.entries)
            }
        }
    }
}
