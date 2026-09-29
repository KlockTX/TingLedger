package com.klocktx.moneyblocks.data.repo

import com.klocktx.moneyblocks.core.Cents
import com.klocktx.moneyblocks.core.TimeRange
import com.klocktx.moneyblocks.core.cents
import com.klocktx.moneyblocks.core.millisToLocalDate
import com.klocktx.moneyblocks.core.today
import com.klocktx.moneyblocks.data.local.BillDao
import com.klocktx.moneyblocks.data.local.BillEntity
import com.klocktx.moneyblocks.data.local.BillSummaryRow
import com.klocktx.moneyblocks.data.local.EntryDao
import com.klocktx.moneyblocks.data.local.EntryEntity
import com.klocktx.moneyblocks.data.local.SearchRow
import com.klocktx.moneyblocks.domain.model.Bill
import com.klocktx.moneyblocks.domain.model.BillKind
import com.klocktx.moneyblocks.domain.model.BillPalette
import com.klocktx.moneyblocks.domain.model.BillRepeat
import com.klocktx.moneyblocks.domain.model.BillSummary
import com.klocktx.moneyblocks.domain.model.DayPoint
import com.klocktx.moneyblocks.domain.model.Entry
import com.klocktx.moneyblocks.domain.model.LargestEntry
import com.klocktx.moneyblocks.domain.model.MonthPoint
import com.klocktx.moneyblocks.domain.model.SearchHit
import com.klocktx.moneyblocks.domain.model.SearchFilter
import com.klocktx.moneyblocks.domain.repo.BillRepository
import com.klocktx.moneyblocks.domain.repo.EntryRepository
import com.klocktx.moneyblocks.domain.repo.StatsRepository
import com.klocktx.moneyblocks.domain.usecase.SearchQuery
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

fun BillEntity.toDomain(): Bill = Bill(
    id = id,
    name = name,
    iconKey = iconKey,
    palette = BillPalette.fromKey(colorKey),
    kind = BillKind.fromKey(kind),
    sortOrder = sortOrder,
    archived = archived,
    repeat = BillRepeat.fromKey(repeatKey),
    repeatAmount = repeatAmountCents.cents,
    repeatAnchorDay = repeatAnchorDay,
    repeatCursorDay = repeatCursorDay,
    deletedAt = deletedAt,
)

fun Bill.toEntity(): BillEntity = BillEntity(
    id = id,
    name = name,
    iconKey = iconKey,
    colorKey = palette.key,
    sortOrder = sortOrder,
    createdAt = System.currentTimeMillis(),
    archived = archived,
    kind = kind.key,
    repeatKey = repeat.key,
    repeatAmountCents = repeatAmount.value,
    repeatAnchorDay = repeatAnchorDay,
    repeatCursorDay = repeatCursorDay,
    deletedAt = deletedAt,
)

fun EntryEntity.toDomain(): Entry = Entry(
    id = id,
    billId = billId,
    amount = amountCents.cents,
    note = note,
    occurredAt = occurredAt,
    autoDay = autoDay,
)

fun Entry.toEntity(): EntryEntity = EntryEntity(
    id = id,
    billId = billId,
    amountCents = amount.value,
    note = note,
    occurredAt = occurredAt,
    createdAt = System.currentTimeMillis(),
    autoDay = autoDay,
)

private fun BillSummaryRow.toDomain(): BillSummary = BillSummary(
    bill = Bill(
        id = id,
        name = name,
        iconKey = iconKey,
        palette = BillPalette.fromKey(colorKey),
        kind = BillKind.fromKey(kind),
        sortOrder = sortOrder,
        archived = archived,
        repeat = BillRepeat.fromKey(repeatKey),
        repeatAmount = repeatAmountCents.cents,
        repeatAnchorDay = repeatAnchorDay,
    ),
    periodTotal = periodCents.cents,
    periodCount = periodCount,
    allTimeTotal = allTimeCents.cents,
    allTimeCount = allTimeCount,
    lastEntryAt = lastEntryAt,
)

class BillRepositoryImpl(private val dao: BillDao) : BillRepository {
    override fun observeBills(): Flow<List<Bill>> = dao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    override fun observeBillSummaries(range: TimeRange): Flow<List<BillSummary>> =
        dao.observeSummaries(range.start, range.endExclusive).map { rows -> rows.map { it.toDomain() } }

    override suspend fun find(id: Long): Bill? = dao.findById(id)?.toDomain()

    override suspend fun save(bill: Bill): Long =
        if (bill.id == 0L) dao.insert(bill.toEntity())
        else {
            val stored = dao.findById(bill.id)
            /**
             * 游标只认库里那一版，不认调用方传回来的：首页改名走的是 BillSummary 里的 Bill，
             * 那条路径根本没有游标，照抄会把 0 写进去，等于把已生成过的期次全部重新打开。
             * 唯一例外是改了首次入账日——那等于换了一条时间线，游标退回新起点之前，
             * 让那一天该生成的那笔照常生成。
             */
            val cursor = when {
                stored == null -> bill.repeatCursorDay
                stored.repeatAnchorDay == bill.repeatAnchorDay -> stored.repeatCursorDay
                else -> bill.repeatAnchorDay - 1L
            }
            dao.update(bill.toEntity().copy(repeatCursorDay = cursor))
            bill.id
        }

    override suspend fun recurring(): List<Bill> = dao.recurring().map { it.toDomain() }

    override suspend fun setRepeatCursor(id: Long, day: Long) = dao.setRepeatCursor(id, day)

    /**
     * "删除"就是打个时间标记。名下条目一行都不动，所以恢复也不需要重放任何数据——
     * 真删（CASCADE 带走全部历史）只留给付费确认的「彻底删除」和到期清空。
     */
    override suspend fun trash(id: Long) = dao.setDeletedAt(id, System.currentTimeMillis())

    override suspend fun restore(id: Long) = dao.setDeletedAt(id, null)

    override suspend fun deleteForever(id: Long) = dao.deleteById(id)

    override fun observeTrashed(): Flow<List<Bill>> =
        dao.observeTrashed().map { list -> list.map { it.toDomain() } }

    override suspend fun purgeTrash(cutoffMillis: Long): List<Long> {
        val ids = dao.trashedIdsOlderThan(cutoffMillis)
        if (ids.isEmpty()) return emptyList()
        dao.deleteByIds(ids)
        return ids
    }

    override suspend fun setArchived(id: Long, archived: Boolean) = dao.setArchived(id, archived)

    override suspend fun reorder(orderedIds: List<Long>) = dao.applyOrder(orderedIds)

    override suspend fun maxSortOrder(): Int = dao.maxSortOrder()
}

class EntryRepositoryImpl(private val dao: EntryDao) : EntryRepository {
    override fun observeEntries(billId: Long): Flow<List<Entry>> =
        dao.observeByBill(billId).map { list -> list.map { it.toDomain() } }

    override fun observeRecent(limit: Int): Flow<List<Entry>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override fun observeCount(): Flow<Int> = dao.observeCount()

    override fun observeCountsByBill(): Flow<Map<Long, Int>> =
        dao.observeCountsByBill().map { rows -> rows.associate { it.billId to it.count } }

    override fun observeCount(range: TimeRange): Flow<Int> =
        dao.observeCountBetween(range.start, range.endExclusive)

    override suspend fun add(entry: Entry): Long = dao.insert(entry.toEntity())

    override suspend fun addAuto(entry: Entry): Boolean = dao.insertAuto(entry.toEntity()) != -1L

    override suspend fun update(entry: Entry) = dao.update(entry.toEntity())

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun find(id: Long): Entry? = dao.findById(id)?.toDomain()

    override suspend fun search(keyword: String, filter: SearchFilter): List<SearchHit> {
        val trimmed = keyword.trim()
        // 既没关键词又没筛选＝没人说要查什么。这时候返回全库前 200 条只是一屏噪音。
        if (trimmed.isEmpty() && filter.isPristine) return emptyList()
        val params = SearchQuery.params(trimmed, filter, today())
        return dao.searchFiltered(
            hasText = params.hasText,
            notePattern = params.notePattern,
            billPattern = params.billPattern,
            exactCents = params.exactCents,
            kind = params.kind,
            minCents = params.minCents,
            start = params.start,
            endExclusive = params.endExclusive,
            limit = EntryRepository.SEARCH_LIMIT,
        ).map { it.toHit() }
    }

    override suspend fun hitsBetween(start: Long, endExclusive: Long): List<SearchHit> =
        dao.rowsInRange(start, endExclusive).map { it.toHit() }

    override suspend fun recentNotes(billId: Long, limit: Int): List<String> =
        dao.recentNotes(billId, limit)
}

class StatsRepositoryImpl(private val dao: EntryDao) : StatsRepository {
    override fun observeTotal(range: TimeRange, kind: BillKind): Flow<Cents> =
        dao.observeTotalBetween(range.start, range.endExclusive, kind.key).map { it.cents }

    override suspend fun total(range: TimeRange, kind: BillKind): Cents =
        dao.totalBetween(range.start, range.endExclusive, kind.key).cents

    /** 逐日补 0，保证趋势图不断档；分组在 Kotlin 侧完成以跟随设备时区。 */
    override suspend fun dailySeries(range: TimeRange, kind: BillKind): List<DayPoint> {
        val rows = dao.rowsBetween(range.start, range.endExclusive, kind.key)
        val byDay = rows.groupBy({ millisToLocalDate(it.occurredAt) }, { it.amountCents })
        val lastDay = millisToLocalDate(range.endExclusive - 1)
        val days = generateSequence(millisToLocalDate(range.start)) { date ->
            if (date.isBefore(lastDay)) date.plusDays(1) else null
        }.toList()
        return days.map { day -> DayPoint(day, (byDay[day]?.sum() ?: 0L).cents) }
    }

    /** 近 n 个自然月（含本月）逐月聚合，没有支出的月份补 0。 */
    override suspend fun monthlySeries(months: Int, kind: BillKind): List<MonthPoint> {
        val thisMonth = today().withDayOfMonth(1)
        val first = thisMonth.minusMonths((months - 1).coerceAtLeast(0).toLong())
        val range = TimeRange(
            first.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            thisMonth.plusMonths(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
        val rows = dao.rowsBetween(range.start, range.endExclusive, kind.key)
        val byMonth = rows.groupBy({ millisToLocalDate(it.occurredAt).withDayOfMonth(1) }, { it.amountCents })
        return (0 until months).map { offset ->
            val monthFirst = first.plusMonths(offset.toLong())
            MonthPoint(monthFirst, (byMonth[monthFirst]?.sum() ?: 0L).cents)
        }
    }

    /** 按本地日去重升序。换算放在 Kotlin 侧做，SQL 里的除法是按 UTC 切的，会差一天。 */
    override suspend fun activeDays(range: TimeRange): List<LocalDate> =
        dao.occurredAtBetween(range.start, range.endExclusive)
            .map { millisToLocalDate(it) }
            .distinct()
            .sorted()

    override suspend fun largestEntry(range: TimeRange, kind: BillKind): LargestEntry? =
        dao.largestBetween(range.start, range.endExclusive, kind.key)?.let { row ->
            LargestEntry(
                entry = Entry(
                    id = row.id,
                    billId = row.billId,
                    amount = row.amountCents.cents,
                    note = row.note,
                    occurredAt = row.occurredAt,
                ),
                billName = row.billName,
                billIconKey = row.billIconKey,
                billPalette = BillPalette.fromKey(row.billColorKey),
                billKind = BillKind.fromKey(row.billKind),
            )
        }
}

/** 搜索与按日查看共用同一套行结构。 */
private fun SearchRow.toHit(): SearchHit = SearchHit(
    entry = Entry(
        id = id,
        billId = billId,
        amount = amountCents.cents,
        note = note,
        occurredAt = occurredAt,
    ),
    bill = Bill(
        id = billId,
        name = billName,
        iconKey = billIconKey,
        palette = BillPalette.fromKey(billColorKey),
        kind = BillKind.fromKey(billKind),
    ),
)
