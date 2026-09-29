package com.klocktx.moneyblocks.domain.repo

import com.klocktx.moneyblocks.core.Cents
import com.klocktx.moneyblocks.core.TimeRange
import com.klocktx.moneyblocks.domain.model.Bill
import com.klocktx.moneyblocks.domain.model.BillKind
import com.klocktx.moneyblocks.domain.model.BillSummary
import com.klocktx.moneyblocks.domain.model.DayPoint
import com.klocktx.moneyblocks.domain.model.Entry
import com.klocktx.moneyblocks.domain.model.LargestEntry
import com.klocktx.moneyblocks.domain.model.MonthPoint
import com.klocktx.moneyblocks.domain.model.SearchHit
import com.klocktx.moneyblocks.domain.model.SearchFilter
import kotlinx.coroutines.flow.Flow

interface BillRepository {
    fun observeBills(): Flow<List<Bill>>
    fun observeBillSummaries(range: TimeRange): Flow<List<BillSummary>>
    suspend fun find(id: Long): Bill?
    suspend fun save(bill: Bill): Long

    /** 移入回收站：只打标记，名下条目原地不动。 */
    suspend fun trash(id: Long)

    suspend fun restore(id: Long)

    /** 真删。条目由 `ON DELETE CASCADE` 一起带走，不可恢复，只有回收站里用。 */
    suspend fun deleteForever(id: Long)

    fun observeTrashed(): Flow<List<Bill>>

    /**
     * 清掉早于 [cutoffMillis] 放进回收站的账单。返回被真删的 id。
     *
     * 返回 id 而不是条数：账单上限存在 DataStore 里、按 id 索引，
     * rowid 可能被下一个新账单复用，所以真删时必须知道删了哪几个好一起摘掉。
     */
    suspend fun purgeTrash(cutoffMillis: Long): List<Long>

    suspend fun setArchived(id: Long, archived: Boolean)
    suspend fun reorder(orderedIds: List<Long>)
    suspend fun maxSortOrder(): Int

    /** 未归档且规则完整的周期性账单，按显示顺序。 */
    suspend fun recurring(): List<Bill>

    /** 生成完把游标推到最后一期；这一列决定了"删掉的自动条目会不会再冒出来"。 */
    suspend fun setRepeatCursor(id: Long, day: Long)
}

interface EntryRepository {
    fun observeEntries(billId: Long): Flow<List<Entry>>
    fun observeRecent(limit: Int): Flow<List<Entry>>

    /** 全库条目数，设置页「关于」那行用。 */
    fun observeCount(): Flow<Int>

    /** 每条账单名下有几笔。归档和回收站里的都算——那是"删掉会带走多少"的依据。 */
    fun observeCountsByBill(): Flow<Map<Long, Int>>
    suspend fun add(entry: Entry): Long

    /** 写入一条自动条目；这一期已经存在就不写，返回 false。 */
    suspend fun addAuto(entry: Entry): Boolean
    suspend fun update(entry: Entry)
    suspend fun delete(id: Long)
    suspend fun find(id: Long): Entry?

    /**
     * 按备注、账单名或精确金额搜索，并可叠加 [filter]（方向 / 金额下限 / 时间范围）。
     * 关键词为空且筛选也没开时返回空列表——那时没人说要查什么。
     */
    suspend fun search(keyword: String, filter: SearchFilter): List<SearchHit>

    /** 某时间区间内的全部条目，用于「看某一天」。 */
    suspend fun hitsBetween(start: Long, endExclusive: Long): List<SearchHit>

    /** 区间内的条目数，不分方向。首页用它判断「今天还没记」。 */
    fun observeCount(range: TimeRange): Flow<Int>

    /** 某个账单最近用过的备注，给录入界面当常用建议。 */
    suspend fun recentNotes(billId: Long, limit: Int): List<String>

    companion object {
        /**
         * 搜索结果上限。原来这个数写死在 SQL 里，界面不知道；
         * 加了筛选之后"共 N 笔 · 合计 ¥X"必须能被判断是否被截断，否则那行数字会说谎。
         */
        const val SEARCH_LIMIT = 400
    }
}

/**
 * 统计一律按资金方向取数。默认值是 [BillKind.EXPENSE]，因为绝大多数视图看的就是支出；
 * 收入要靠显式传参，避免一不小心把工资计进「本月花了多少」。
 */
interface StatsRepository {
    fun observeTotal(range: TimeRange, kind: BillKind = BillKind.EXPENSE): Flow<Cents>
    suspend fun total(range: TimeRange, kind: BillKind = BillKind.EXPENSE): Cents
    suspend fun dailySeries(range: TimeRange, kind: BillKind = BillKind.EXPENSE): List<DayPoint>
    suspend fun monthlySeries(months: Int, kind: BillKind = BillKind.EXPENSE): List<MonthPoint>
    suspend fun largestEntry(range: TimeRange, kind: BillKind = BillKind.EXPENSE): LargestEntry?

    /** 区间里"记过账"的日子，去重升序。连续天数用它，回收站账单的不算。 */
    suspend fun activeDays(range: TimeRange): List<java.time.LocalDate>
}

/** 导出一段 CSV 文本。写到哪里由界面决定（SAF），这里只管取数与格式化。 */
interface ExportRepository {
    suspend fun exportCsv(zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault()): String
}
