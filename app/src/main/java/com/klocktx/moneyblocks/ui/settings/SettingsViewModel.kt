package com.klocktx.moneyblocks.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klocktx.moneyblocks.core.Cents
import com.klocktx.moneyblocks.core.cents
import com.klocktx.moneyblocks.domain.repo.BillRepository
import com.klocktx.moneyblocks.domain.repo.EntryRepository
import com.klocktx.moneyblocks.domain.repo.ExportRepository
import com.klocktx.moneyblocks.domain.usecase.ManageBillsUseCase
import com.klocktx.moneyblocks.domain.repo.SettingsRepository
import com.klocktx.moneyblocks.domain.model.Bill
import com.klocktx.moneyblocks.domain.model.ThemeMode
import com.klocktx.moneyblocks.domain.usecase.BackupData
import com.klocktx.moneyblocks.domain.usecase.BackupDecode
import com.klocktx.moneyblocks.domain.usecase.BackupUseCase
import com.klocktx.moneyblocks.ui.theme.MonetTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 设置页的 ViewModel。
 *
 * 它只管"设置页上的事"：偏好、导出、备份恢复、已归档账单善后。
 * 账单本身的新建/改名/排序已经搬去首页的编辑态，所以这里不再管顺序。
 */
class SettingsViewModel(private val backupUseCase: BackupUseCase, private val billRepository: BillRepository, private val entryRepository: EntryRepository, private val exportRepository: ExportRepository, private val manageBills: ManageBillsUseCase, private val settingsRepository: SettingsRepository, val appTag: String) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    /** 当前主题色。存的是 key 字符串，兜底交给 MonetTheme.fromKey。 */
    val monetTheme: StateFlow<MonetTheme> = settingsRepository.monetThemeKey
        .map(MonetTheme::fromKey)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonetTheme.GREY_BLUE)

    val monthlyBudget: StateFlow<Cents> = settingsRepository.monthlyBudget
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L.cents)

    /** 各账单的月度上限，编辑弹窗回填用。 */
    val billCaps: StateFlow<Map<Long, Cents>> = settingsRepository.billCaps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val archived: StateFlow<List<Bill>> = billRepository.observeBills()
        .map { list -> list.filter { it.archived } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 回收站：按放进去的时间倒序（DAO 里已排好）。 */
    val trashed: StateFlow<List<Bill>> = billRepository.observeTrashed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 每个账单名下的条目数，归档与回收站都要用。
     *
     * 原来这里拿 `observeBillSummaries` 拼，而那条查询带 `archived = 0`，
     * 结果每个已归档账单都显示"名下 0 笔"——偏偏这个数字是给用户看"删掉会带走多少"的。
     * 改成直接按条目分组，归档和回收站都算得出来。
     */
    val entryCounts: StateFlow<Map<Long, Int>> = entryRepository.observeCountsByBill()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 「关于」那行：账单总数（含归档）与条目总数，跟着库实时变。 */
    val dataScale: StateFlow<Pair<Int, Int>> = combine(
        billRepository.observeBills().map { it.size },
        entryRepository.observeCount(),
    ) { bills, entries -> bills to entries }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0 to 0)

    val lastBackupAt: StateFlow<Long> = settingsRepository.lastBackupAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    private val _undoable = MutableStateFlow<BackupData?>(null)

    /**
     * 上一次恢复**之前**的整库快照。「撤销这次恢复」就是把它换回去，
     * 所以撤销只在本次会话内有效——进程没了就没了，真要回溯更早得靠那时候导出的文件。
     */
    val undoable: StateFlow<BackupData?> = _undoable.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setMonetTheme(theme: MonetTheme) {
        viewModelScope.launch { settingsRepository.setMonetThemeKey(theme.key) }
    }

    fun setMonthlyBudget(cents: Cents) {
        viewModelScope.launch { settingsRepository.setMonthlyBudget(cents) }
    }

    fun exportCsv(onReady: (String) -> Unit) {
        viewModelScope.launch { onReady(exportRepository.exportCsv()) }
    }

    fun setArchived(bill: Bill, archived: Boolean) {
        viewModelScope.launch {
            if (archived) manageBills.archive(bill) else manageBills.restore(bill)
        }
    }

    /** 从回收站出来：清掉标记，名下条目本来就一行没动。 */
    fun untrash(bill: Bill) {
        viewModelScope.launch { manageBills.untrash(bill) }
    }

    /** 彻底删除。这一步才真的走 CASCADE 带走名下条目，所以界面上要单独确认。 */
    fun deleteForever(bill: Bill) {
        viewModelScope.launch { manageBills.deleteForever(bill) }
    }

    /** 新建或编辑账单：界面交回一份改好的 Bill，外加它的月度上限（0＝不限）。 */
    fun save(bill: Bill, cap: Cents = 0L.cents) {
        viewModelScope.launch { manageBills.save(bill, cap) }
    }

    fun delete(bill: Bill) {
        viewModelScope.launch { manageBills.remove(bill) }
    }

    fun backupContents(onReady: (String) -> Unit) {
        viewModelScope.launch { onReady(backupUseCase.fileContents()) }
    }

    fun markBackedUp() {
        viewModelScope.launch {
            settingsRepository.setLastBackupAt(System.currentTimeMillis())
        }
    }

    /** 先把文件解析出来给用户看。纯字符串处理，不碰数据。 */
    fun inspect(text: String): BackupDecode = backupUseCase.inspect(text)

    /**
     * 执行替换。这里会**再解析一次**而不是复用上面那份结果：多解析一遍纯函数几乎不要钱，
     * 换来的是"确认时看到的内容"和"真正写进库的内容"之间没有信任空隙。
     */
    fun restore(text: String, onDone: (BackupUseCase.Restored) -> Unit) {
        viewModelScope.launch {
            val outcome = backupUseCase.restoreFromText(text)
            if (outcome is BackupUseCase.Restored.Applied) _undoable.value = outcome.previous
            onDone(outcome)
        }
    }

    fun undoRestore(onDone: (BackupUseCase.Restored) -> Unit) {
        val snapshot = _undoable.value
        if (snapshot == null) {
            onDone(BackupUseCase.Restored.ApplyFailed("没有可撤销的恢复了"))
            return
        }
        viewModelScope.launch {
            val outcome = backupUseCase.restoreSnapshot(snapshot)
            if (outcome is BackupUseCase.Restored.Applied) _undoable.value = null
            onDone(outcome)
        }
    }
}
