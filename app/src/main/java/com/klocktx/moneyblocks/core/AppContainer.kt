package com.klocktx.moneyblocks.core

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import com.klocktx.moneyblocks.data.local.AppDatabase
import com.klocktx.moneyblocks.data.local.SCHEMA_VERSION
import com.klocktx.moneyblocks.data.repo.BackupRepositoryImpl
import com.klocktx.moneyblocks.data.repo.BillRepositoryImpl
import com.klocktx.moneyblocks.data.repo.EntryRepositoryImpl
import com.klocktx.moneyblocks.data.repo.ExportRepositoryImpl
import com.klocktx.moneyblocks.data.repo.SettingsRepositoryImpl
import com.klocktx.moneyblocks.data.repo.StatsRepositoryImpl
import com.klocktx.moneyblocks.domain.repo.BackupRepository
import com.klocktx.moneyblocks.domain.repo.BillRepository
import com.klocktx.moneyblocks.domain.repo.EntryRepository
import com.klocktx.moneyblocks.domain.repo.ExportRepository
import com.klocktx.moneyblocks.domain.repo.SettingsRepository
import com.klocktx.moneyblocks.domain.repo.StatsRepository
import com.klocktx.moneyblocks.domain.usecase.BackupUseCase
import com.klocktx.moneyblocks.domain.usecase.BuildStatsUseCase
import com.klocktx.moneyblocks.domain.usecase.ManageBillsUseCase
import com.klocktx.moneyblocks.domain.usecase.RecordEntryUseCase
import com.klocktx.moneyblocks.domain.usecase.RecurringUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** 手写组装图：这个规模下比 Hilt 少一层注解处理器，依赖方向仍然只有外向内。 */
class AppContainer(private val context: Context) {

    private val database: AppDatabase by lazy { AppDatabase.create(context) }

    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val billRepository: BillRepository by lazy { BillRepositoryImpl(database.billDao()) }
    val entryRepository: EntryRepository by lazy { EntryRepositoryImpl(database.entryDao()) }
    val statsRepository: StatsRepository by lazy { StatsRepositoryImpl(database.entryDao()) }
    val exportRepository: ExportRepository by lazy { ExportRepositoryImpl(database.exportDao()) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepositoryImpl(context) }

    val recordEntry: RecordEntryUseCase by lazy { RecordEntryUseCase(entryRepository) }
    val manageBills: ManageBillsUseCase by lazy { ManageBillsUseCase(billRepository, settingsRepository) }
    val buildStats: BuildStatsUseCase by lazy { BuildStatsUseCase(statsRepository) }
    val recurring: RecurringUseCase by lazy { RecurringUseCase(billRepository, entryRepository) }

    val backupRepository: BackupRepository by lazy { BackupRepositoryImpl(database.backupDao()) }
    val backupUseCase: BackupUseCase by lazy {
        BackupUseCase(
            backup = backupRepository,
            settings = settingsRepository,
            schemaVersion = SCHEMA_VERSION,
            appTag = appTag,
        )
    }

    /** "1.15.0(18)"。备份文件头与设置页「关于」都用它，出问题时好对着查。 */
    val appTag: String by lazy {
        val info = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull() ?: return@lazy "unknown"
        "${info.versionName}(${PackageInfoCompat.getLongVersionCode(info)})"
    }

    /** 桌面快捷记账项。Context 不外泄，界面只管把当前账单交给它。 */
    fun publishQuickShortcuts(bills: List<com.klocktx.moneyblocks.domain.model.Bill>) =
        QuickShortcuts.publish(context, bills)
}
