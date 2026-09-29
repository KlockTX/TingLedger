package com.klocktx.moneyblocks.data

import com.klocktx.moneyblocks.core.Cents
import com.klocktx.moneyblocks.core.cents
import com.klocktx.moneyblocks.domain.model.ThemeMode
import com.klocktx.moneyblocks.domain.repo.SettingsRepository
import com.klocktx.moneyblocks.domain.usecase.BillCaps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 内存版偏好仓储，给需要 `ManageBillsUseCase` / `BackupUseCase` 的测试用。
 *
 * 上限这三条方法**照着真实实现的结构写**：存进一个 map，读出时经过 [BillCaps] 编解码一遍。
 * 如果这里图省事直接 `map[id] = cents`，测试就会比线上宽容——线上会因为编码丢掉的
 * 0 与负数，这里却留着，等于测了个不存在的实现。
 */
class FakeSettings : SettingsRepository {

    private val mode = MutableStateFlow(ThemeMode.SYSTEM)
    private val budget = MutableStateFlow(0L.cents)
    private val backupAt = MutableStateFlow(0L)
    private val monet = MutableStateFlow("")
    private val seenDay = MutableStateFlow(0L)
    private val caps = MutableStateFlow<Map<Long, Cents>>(emptyMap())

    override val themeMode: Flow<ThemeMode> = mode
    override val monthlyBudget: Flow<Cents> = budget
    override val lastBackupAt: Flow<Long> = backupAt
    override val monetThemeKey: Flow<String> = monet
    override val nudgeSeenDay: Flow<Long> = seenDay
    override val billCaps: Flow<Map<Long, Cents>> = caps

    override suspend fun setMonetThemeKey(key: String) {
        monet.value = key
    }

    override suspend fun setNudgeSeenDay(day: Long) {
        seenDay.value = day
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        this.mode.value = mode
    }

    override suspend fun setMonthlyBudget(cents: Cents) {
        budget.value = cents
    }

    override suspend fun setLastBackupAt(millis: Long) {
        backupAt.value = millis
    }

    override suspend fun setBillCap(billId: Long, cents: Cents) {
        caps.value = roundTrip(caps.value + (billId to cents))
    }

    override suspend fun dropBillCap(billId: Long) {
        caps.value = roundTrip(BillCaps.drop(caps.value, billId))
    }

    override suspend fun replaceBillCaps(newCaps: Map<Long, Cents>) {
        caps.value = roundTrip(newCaps)
    }

    /** 写出去再读回来，和 DataStore 那条路一样经过一次编码。 */
    private fun roundTrip(input: Map<Long, Cents>): Map<Long, Cents> =
        BillCaps.decode(BillCaps.encode(input))
}
