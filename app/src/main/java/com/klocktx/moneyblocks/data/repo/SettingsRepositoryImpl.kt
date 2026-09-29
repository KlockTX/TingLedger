package com.klocktx.moneyblocks.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.klocktx.moneyblocks.core.Cents
import com.klocktx.moneyblocks.core.cents
import com.klocktx.moneyblocks.domain.model.ThemeMode
import com.klocktx.moneyblocks.domain.repo.SettingsRepository
import com.klocktx.moneyblocks.domain.usecase.BillCaps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
private val KEY_MONTHLY_BUDGET = longPreferencesKey("monthly_budget_cents")
private val KEY_MONET_THEME = stringPreferencesKey("monet_theme")
private val KEY_LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
private val KEY_NUDGE_SEEN_DAY = longPreferencesKey("nudge_seen_day")
private val KEY_BILL_CAPS = stringPreferencesKey("bill_caps")

class SettingsRepositoryImpl(context: Context) : SettingsRepository {

    private val store = context.applicationContext.settingsDataStore

    override val themeMode: Flow<ThemeMode> = store.data.map { prefs ->
        ThemeMode.fromKey(prefs[KEY_THEME_MODE])
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[KEY_THEME_MODE] = mode.key }
    }

    override val monthlyBudget: Flow<Cents> = store.data.map { prefs ->
        (prefs[KEY_MONTHLY_BUDGET] ?: 0L).cents
    }

    override suspend fun setMonthlyBudget(cents: Cents) {
        store.edit { it[KEY_MONTHLY_BUDGET] = cents.value.coerceAtLeast(0L) }
    }

    override val billCaps: Flow<Map<Long, Cents>> = store.data.map { prefs ->
        BillCaps.decode(prefs[KEY_BILL_CAPS].orEmpty())
    }

    override suspend fun setBillCap(billId: Long, cents: Cents) {
        store.edit { prefs ->
            val current = BillCaps.decode(prefs[KEY_BILL_CAPS].orEmpty())
            prefs[KEY_BILL_CAPS] = BillCaps.encode(current + (billId to cents))
        }
    }

    override suspend fun dropBillCap(billId: Long) {
        store.edit { prefs ->
            val current = BillCaps.decode(prefs[KEY_BILL_CAPS].orEmpty())
            prefs[KEY_BILL_CAPS] = BillCaps.encode(BillCaps.drop(current, billId))
        }
    }

    override suspend fun replaceBillCaps(caps: Map<Long, Cents>) {
        store.edit { it[KEY_BILL_CAPS] = BillCaps.encode(caps) }
    }

    override val lastBackupAt: Flow<Long> = store.data.map { prefs ->
        prefs[KEY_LAST_BACKUP_AT] ?: 0L
    }

    // 没设置过时给空串，由界面侧 MonetTheme.fromKey 兜底成默认主题——
    // 数据层不该认识 ui 层的主题枚举
    override val monetThemeKey: Flow<String> = store.data.map { prefs ->
        prefs[KEY_MONET_THEME].orEmpty()
    }

    override suspend fun setMonetThemeKey(key: String) {
        store.edit { it[KEY_MONET_THEME] = key }
    }

    override val nudgeSeenDay: Flow<Long> = store.data.map { prefs ->
        prefs[KEY_NUDGE_SEEN_DAY] ?: 0L
    }

    override suspend fun setNudgeSeenDay(day: Long) {
        store.edit { it[KEY_NUDGE_SEEN_DAY] = day.coerceAtLeast(0L) }
    }

    override suspend fun setLastBackupAt(millis: Long) {
        store.edit { it[KEY_LAST_BACKUP_AT] = millis.coerceAtLeast(0L) }
    }
}
