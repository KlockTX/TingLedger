package com.klocktx.moneyblocks.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.klocktx.moneyblocks.domain.usecase.ExportRow
import kotlinx.coroutines.flow.Flow

data class BillSummaryRow(
    val id: Long,
    val name: String,
    val iconKey: String,
    val colorKey: String,
    val kind: String,
    val sortOrder: Int,
    val archived: Boolean,
    /** 首页账单行要显示周期标记，所以摘要也得带上规则本身（游标不用，它只在生成时读）。 */
    val repeatKey: String,
    val repeatAmountCents: Long,
    val repeatAnchorDay: Long,
    val periodCents: Long,
    val periodCount: Int,
    val allTimeCents: Long,
    val allTimeCount: Int,
    val lastEntryAt: Long?,
)

@Dao
interface BillDao {
    /**
     * 所有"活着"的账单（含已归档，界面自己分组）。
     * 回收站里的那些一律不出现——`deletedAt IS NULL` 这一句在本文件里每读账单的查询都要有，
     * 少一处就是"账单删掉了但它名下的钱还在统计里"。
     */
    @Query("SELECT * FROM bills WHERE deletedAt IS NULL ORDER BY archived, sortOrder, id")
    fun observeAll(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills WHERE id = :id AND deletedAt IS NULL")
    suspend fun findById(id: Long): BillEntity?

    @Query(
        """
        SELECT b.id AS id, b.name AS name, b.iconKey AS iconKey, b.colorKey AS colorKey,
               b.kind AS kind, b.sortOrder AS sortOrder, b.archived AS archived,
               b.repeatKey AS repeatKey, b.repeatAmountCents AS repeatAmountCents,
               b.repeatAnchorDay AS repeatAnchorDay,
               IFNULL(SUM(CASE WHEN e.occurredAt >= :start AND e.occurredAt < :end
                    THEN e.amountCents ELSE 0 END), 0) AS periodCents,
               COUNT(CASE WHEN e.occurredAt >= :start AND e.occurredAt < :end THEN 1 END) AS periodCount,
               IFNULL(SUM(e.amountCents), 0) AS allTimeCents,
               COUNT(e.id) AS allTimeCount,
               MAX(e.occurredAt) AS lastEntryAt
        FROM bills b LEFT JOIN entries e ON e.billId = b.id
        WHERE b.archived = 0 AND b.deletedAt IS NULL
        GROUP BY b.id
        ORDER BY b.sortOrder, b.id
        """,
    )
    fun observeSummaries(start: Long, end: Long): Flow<List<BillSummaryRow>>

    /** 回收站：按放进来的时间倒序，最新的在最上面（刚误删的最好找）。 */
    @Query("SELECT * FROM bills WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC, id")
    fun observeTrashed(): Flow<List<BillEntity>>

    /** 排序号取全表最大值（含回收站），否则删一个再建一个会撞号。 */
    @Query("SELECT IFNULL(MAX(sortOrder), -1) FROM bills")
    suspend fun maxSortOrder(): Int

    /**
     * 到期该生成自动条目的账单：未归档、不在回收站、规则完整。
     * 完整性在 SQL 里再判一次（而不是只靠调用方查 [com.klocktx.moneyblocks.domain.model.Bill.isRecurring]）：
     * 手工修库把金额改成 0 时，也不该冒出 ¥0.00 的记录。
     */
    @Query(
        """
        SELECT * FROM bills
        WHERE archived = 0 AND deletedAt IS NULL AND repeatKey <> 'none'
          AND repeatAmountCents > 0 AND repeatAnchorDay > 0
        ORDER BY sortOrder, id
        """,
    )
    suspend fun recurring(): List<BillEntity>

    /** 生成完把游标推到最后一期。删掉的自动条目不会被已走过的期次补回来，靠的就是这一列。 */
    @Query("UPDATE bills SET repeatCursorDay = :day WHERE id = :id")
    suspend fun setRepeatCursor(id: Long, day: Long)

    /** 真删一条。按 id 删而不是先查再删：`findById` 只看活着的账单，回收站里的查不到。 */
    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 进回收站 / 从回收站出来（传 null 就是恢复）。 */
    @Query("UPDATE bills SET deletedAt = :at WHERE id = :id")
    suspend fun setDeletedAt(id: Long, at: Long?)

    /** 保留期到了真删。条目由 `ON DELETE CASCADE` 一起带走，这是全 App 唯一允许它发生的地方。 */
    @Query("SELECT id FROM bills WHERE deletedAt IS NOT NULL AND deletedAt < :cutoffMillis")
    suspend fun trashedIdsOlderThan(cutoffMillis: Long): List<Long>

    /** 先取 id 再按 id 删：调用方要知道删了**哪几个**，账单上限得跟着一起清。 */
    @Query("DELETE FROM bills WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Insert
    suspend fun insert(entity: BillEntity): Long

    @Update
    suspend fun update(entity: BillEntity)

    @Delete
    suspend fun delete(entity: BillEntity)

    @Query("UPDATE bills SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("UPDATE bills SET sortOrder = :order WHERE id = :id")
    suspend fun setSortOrder(id: Long, order: Int)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSeed(entities: List<BillEntity>)

    @Transaction
    suspend fun applyOrder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> setSortOrder(id, index) }
    }
}

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries WHERE billId = :billId ORDER BY occurredAt DESC, id DESC")
    fun observeByBill(billId: Long): Flow<List<EntryEntity>>

    /**
     * 首页「最近条目」。必须联到账单过滤回收站：条目本身不打标记，
     * 是它的账单进了回收站，所以这里不滤就会"账单删了、它的记录还在首页晃"。
     */
    @Query(
        """
        SELECT e.* FROM entries e JOIN bills b ON b.id = e.billId
        WHERE b.deletedAt IS NULL
        ORDER BY e.occurredAt DESC, e.id DESC LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun findById(id: Long): EntryEntity?

    /** 设置页「关于」那行用：账本里活着多少笔，回收站里的那些不算。 */
    @Query("SELECT COUNT(*) FROM entries e JOIN bills b ON b.id = e.billId WHERE b.deletedAt IS NULL")
    fun observeCount(): Flow<Int>

    /**
     * 每条账单名下有多少笔，**不过滤归档也不过滤回收站**。
     *
     * 为什么单独开一条：统计类查询都要排除回收站和归档，但"这个账单名下有几笔"
     * 问的是事实本身——归档行和回收站行都要拿它告诉用户"删掉会带走多少条"。
     * 之前那里是拿 `observeBillSummaries` 拼的，而那条查询带 `archived = 0`，
     * 于是每个已归档账单都显示"名下 0 笔"，恰好把最该说清的数字说错了。
     */
    @Query("SELECT billId AS billId, COUNT(*) AS count FROM entries GROUP BY billId")
    fun observeCountsByBill(): Flow<List<BillEntryCount>>

    @Insert
    suspend fun insert(entity: EntryEntity): Long

    /**
     * 周期生成专用。撞 (billId, autoDay) 唯一索引时返回 -1 而不是抛异常，
     * 所以"同一期生成两次"这种事故最多是白跑一趟，不会把账本写坏。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAuto(entity: EntryEntity): Long

    @Update
    suspend fun update(entity: EntryEntity)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 这条账单最近用过的备注。去重交给 NoteSuggestions，SQL 只负责按时间取。 */
    @Query(
        """
        SELECT note FROM entries
        WHERE billId = :billId AND note <> ''
        ORDER BY occurredAt DESC, id DESC
        LIMIT :limit
        """,
    )
    suspend fun recentNotes(billId: Long, limit: Int): List<String>

    /**
     * 搜索。pattern 由 Repository 侧转义并包好通配符，
     * 这样用户输入的 % 和 _ 会被当作普通字符而不是 LIKE 模式。
     *
     * 三个匹配条件必须整体括起来再 AND 回收站过滤：SQL 里 AND 比 OR 绑得紧，
     * 写成 `A OR B OR C AND deletedAt IS NULL` 会变成"A 命中就完全不看回收站"。
     *
     * 筛选条件全走哨兵值（`kind = ''`、`minCents = 0`、`endExclusive = Long.MAX_VALUE`、
     * `hasText = 0`），由 `SearchQuery.params` 一处生成——散在 SQL 里迟早写歪一个。
     * 关键词为空时 `hasText = 0`，此时**只按筛选查**，"500 以上的支出有哪些"才问得出来。
     */
    @Query(
        """
        SELECT e.id AS id, e.billId AS billId, e.amountCents AS amountCents, e.note AS note,
               e.occurredAt AS occurredAt, b.name AS billName, b.iconKey AS billIconKey,
               b.colorKey AS billColorKey, b.kind AS billKind
        FROM entries e JOIN bills b ON b.id = e.billId
        WHERE b.deletedAt IS NULL
          AND (:kind = '' OR b.kind = :kind)
          AND e.amountCents >= :minCents
          AND e.occurredAt >= :start AND e.occurredAt < :endExclusive
          AND (:hasText = 0
            OR e.note LIKE :notePattern ESCAPE '\'
            OR b.name LIKE :billPattern ESCAPE '\'
            OR (:exactCents <> '' AND CAST(e.amountCents AS TEXT) = :exactCents))
        ORDER BY e.occurredAt DESC, e.id DESC
        LIMIT :limit
        """,
    )
    suspend fun searchFiltered(
        hasText: Int,
        notePattern: String,
        billPattern: String,
        exactCents: String,
        kind: String,
        minCents: Long,
        start: Long,
        endExclusive: Long,
        limit: Int,
    ): List<SearchRow>

    /**
     * 区间总额。金额恒为正数，方向由账单决定，所以这里按 kind 过滤，
     * 而不是给条目存负数——负数会让计算器和求值器都变复杂。
     *
     * 回收站改造后这条 JOIN 不再"必然命中"：账单可以带着全部历史留在库里只是被标记掉，
     * 所以统计侧必须显式把 `deletedAt IS NULL` 写出来，否则删掉的账单还在贡献总额。
     */
    @Query(
        """
        SELECT IFNULL(SUM(e.amountCents), 0)
        FROM entries e JOIN bills b ON b.id = e.billId
        WHERE e.occurredAt >= :start AND e.occurredAt < :end AND b.kind = :kind
          AND b.deletedAt IS NULL
        """,
    )
    fun observeTotalBetween(start: Long, end: Long, kind: String): Flow<Long>

    @Query(
        """
        SELECT IFNULL(SUM(e.amountCents), 0)
        FROM entries e JOIN bills b ON b.id = e.billId
        WHERE e.occurredAt >= :start AND e.occurredAt < :end AND b.kind = :kind
          AND b.deletedAt IS NULL
        """,
    )
    suspend fun totalBetween(start: Long, end: Long, kind: String): Long

    /**
     * 区间内的条目数（不分方向）。「今天记过没有」要的是笔数不是金额——
     * 有人今天记的是一笔 0 元转账，那也算记过。跟着库实时变。
     */
    @Query(
        """
        SELECT COUNT(*) FROM entries e JOIN bills b ON b.id = e.billId
        WHERE b.deletedAt IS NULL AND e.occurredAt >= :start AND e.occurredAt < :end
        """,
    )
    fun observeCountBetween(start: Long, end: Long): Flow<Int>

    /**
     * 一段时间里"记过账"的日子（未去重，交给上层按本地日换算后去重）。
     * 连续记账天数用它——按本地日而不是按 UTC 天算，所以不能在这里 `occurredAt/86400000`。
     */
    @Query(
        """
        SELECT e.occurredAt AS occurredAt
        FROM entries e JOIN bills b ON b.id = e.billId
        WHERE b.deletedAt IS NULL AND e.occurredAt >= :start AND e.occurredAt < :end
        """,
    )
    suspend fun occurredAtBetween(start: Long, end: Long): List<Long>

    /** 某个时间区间内的条目（含账单信息），用于「按日查看」。 */
    @Query(
        """
        SELECT e.id AS id, e.billId AS billId, e.amountCents AS amountCents, e.note AS note,
               e.occurredAt AS occurredAt, b.name AS billName, b.iconKey AS billIconKey,
               b.colorKey AS billColorKey, b.kind AS billKind
        FROM entries e JOIN bills b ON b.id = e.billId
        WHERE b.deletedAt IS NULL
          AND e.occurredAt >= :start AND e.occurredAt < :end
        ORDER BY e.occurredAt DESC, e.id DESC
        """,
    )
    suspend fun rowsInRange(start: Long, end: Long): List<SearchRow>

    /** 逐日/逐月聚合只取某一方向，图表才不会把工资算成「花掉的钱」。 */
    @Query(
        """
        SELECT e.occurredAt AS occurredAt, e.amountCents AS amountCents
        FROM entries e JOIN bills b ON b.id = e.billId
        WHERE e.occurredAt >= :start AND e.occurredAt < :end AND b.kind = :kind
          AND b.deletedAt IS NULL
        """,
    )
    suspend fun rowsBetween(start: Long, end: Long, kind: String): List<EntryOccurrenceRow>

    @Query(
        """
        SELECT e.id AS id, e.billId AS billId, e.amountCents AS amountCents, e.note AS note,
               e.occurredAt AS occurredAt, b.name AS billName, b.iconKey AS billIconKey,
               b.colorKey AS billColorKey, b.kind AS billKind
        FROM entries e JOIN bills b ON b.id = e.billId
        WHERE e.occurredAt >= :start AND e.occurredAt < :end AND b.kind = :kind
          AND b.deletedAt IS NULL
        ORDER BY e.amountCents DESC LIMIT 1
        """,
    )
    suspend fun largestBetween(start: Long, end: Long, kind: String): LargestRow?
}

data class EntryOccurrenceRow(val occurredAt: Long, val amountCents: Long)

/** 一条账单名下有多少笔。归档与回收站都算，见 `observeCountsByBill` 的注释。 */
data class BillEntryCount(val billId: Long, val count: Int)

data class LargestRow(
    val id: Long,
    val billId: Long,
    val amountCents: Long,
    val note: String,
    val occurredAt: Long,
    val billName: String,
    val billIconKey: String,
    val billColorKey: String,
    val billKind: String,
)

@Dao
interface ExportDao {
    @Query(
        """
        SELECT e.occurredAt AS occurredAt, e.amountCents AS amountCents, e.note AS note,
               b.name AS billName, b.kind AS kind
        FROM entries e JOIN bills b ON b.id = e.billId
        -- 导出的是账本，不是回收站：账单在回收站里时它的历史也不该出现在 CSV 里
        WHERE b.deletedAt IS NULL
        ORDER BY e.occurredAt ASC, e.id ASC
        """,
    )
    suspend fun exportRows(): List<ExportRow>
}

data class SearchRow(
    val id: Long,
    val billId: Long,
    val amountCents: Long,
    val note: String,
    val occurredAt: Long,
    val billName: String,
    val billIconKey: String,
    val billColorKey: String,
    val billKind: String,
)

/**
 * 整库备份用。
 *
 * `replace` 显式带着 id 插回（autoGenerate 只在 id=0 时才分配），所以恢复之后
 * 条目 id、账单 id 与备份时一致，之后新记的账会接着往 larger id 走。
 */
@Dao
interface BackupDao {
    @Query("SELECT * FROM bills ORDER BY id")
    suspend fun allBills(): List<BillEntity>

    @Query("SELECT * FROM entries ORDER BY id")
    suspend fun allEntries(): List<EntryEntity>

    @Query("SELECT COUNT(*) FROM bills")
    suspend fun countBills(): Int

    @Query("SELECT COUNT(*) FROM entries")
    suspend fun countEntries(): Int

    @Query("SELECT IFNULL(SUM(amountCents), 0) FROM entries")
    suspend fun sumEntries(): Long

    @Query("DELETE FROM entries")
    suspend fun deleteAllEntries()

    @Query("DELETE FROM bills")
    suspend fun deleteAllBills()

    @Insert
    suspend fun insertBills(rows: List<BillEntity>)

    @Insert
    suspend fun insertEntries(rows: List<EntryEntity>)

    /**
     * 清空后整库替换，末尾自己跟自己对账。
     *
     * 整个方法在一个事务里：Room 的 @Transaction 在方法体抛异常时不会把事务标记为成功，
     * 于是全部回滚。所以「对账不过」不是把账本弄坏一半，而是回到替换前的原样。
     */
    @Transaction
    suspend fun replaceAll(bills: List<BillEntity>, entries: List<EntryEntity>) {
        deleteAllEntries()
        deleteAllBills()
        if (bills.isNotEmpty()) insertBills(bills)
        if (entries.isNotEmpty()) insertEntries(entries)

        val actualBills = countBills()
        check(actualBills == bills.size) {
            "账单写入 $actualBills 行，期望 ${bills.size} 行"
        }
        val actualEntries = countEntries()
        check(actualEntries == entries.size) {
            "条目写入 $actualEntries 行，期望 ${entries.size} 行"
        }
        val expectedSum = entries.sumOf { it.amountCents }
        val actualSum = sumEntries()
        check(actualSum == expectedSum) {
            "条目金额和为 $actualSum，期望 $expectedSum"
        }
    }
}
