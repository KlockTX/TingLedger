package com.klocktx.moneyblocks.domain.repo

import com.klocktx.moneyblocks.core.Cents
import com.klocktx.moneyblocks.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)

    /** 月度预算，0 表示未设置。 */
    val monthlyBudget: Flow<Cents>
    suspend fun setMonthlyBudget(cents: Cents)

    /**
     * 每个账单的月度上限。空表表示一个都没设。
     *
     * 键是账单 id，所以彻底删除账单时必须一起 [dropBillCap]——SQLite 可能把同一个 rowid
     * 发给下一个新建的账单，留下残值就是让新账单背一个莫名其妙的上限。
     */
    val billCaps: Flow<Map<Long, Cents>>
    suspend fun setBillCap(billId: Long, cents: Cents)
    suspend fun dropBillCap(billId: Long)
    /** 整表替换，只给恢复备份用：恢复必须以文件为准，不能被本机残值污染。 */
    suspend fun replaceBillCaps(caps: Map<Long, Cents>)

    /** 莫奈主题的 key（MonetTheme.key）。存字符串，这样加主题不用动库。 */
    val monetThemeKey: Flow<String>
    suspend fun setMonetThemeKey(key: String)

    /**
     * 「今天还没记」被点掉那天的 epochDay，0 表示没点过。
     * 只存"哪一天"而不是布尔值：跨了天它自然就失效，不需要谁去重置。
     */
    val nudgeSeenDay: Flow<Long>
    suspend fun setNudgeSeenDay(day: Long)

    /** 上一次成功导出备份的时刻（毫秒），0 表示从未备份过。 */
    val lastBackupAt: Flow<Long>
    suspend fun setLastBackupAt(millis: Long)
}
