package com.klocktx.moneyblocks.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.klocktx.moneyblocks.core.today
import com.klocktx.moneyblocks.core.zone
import com.klocktx.moneyblocks.data.local.AppDatabase
import com.klocktx.moneyblocks.data.local.BillEntity
import com.klocktx.moneyblocks.data.local.EntryEntity
import com.klocktx.moneyblocks.data.repo.EntryRepositoryImpl
import com.klocktx.moneyblocks.domain.model.AmountFloor
import com.klocktx.moneyblocks.domain.model.BillKind
import com.klocktx.moneyblocks.domain.model.BillPalette
import com.klocktx.moneyblocks.domain.model.DateScope
import com.klocktx.moneyblocks.domain.model.SearchFilter
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 搜索筛选跑在**真实 SQL** 上。
 *
 * `SearchFilterTest` 只能证明日期区间换算对；哨兵值（`kind = ''`、`endExclusive = MAX_VALUE`、
 * `hasText = 0`）和那句 `A OR B OR C` 的括号要生效，必须让 SQLite 真跑一遍。
 * 两层错的表现也完全不同：前者是"算错日子"，后者是"整类结果凭空消失"。
 *
 * 种进去的日期全部写成相对**今天**的明确日子，不假设今天是一个月里的第几号——
 * 否则这条测试会在每个月的某几天假失败。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SearchFilterDbTest {

    private lateinit var db: AppDatabase
    private lateinit var entries: EntryRepositoryImpl

    private val now: LocalDate get() = today()

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        entries = EntryRepositoryImpl(db.entryDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun bill(id: Long, name: String, kind: BillKind = BillKind.EXPENSE) {
        db.billDao().insert(
            BillEntity(
                id = id,
                name = name,
                iconKey = "restaurant",
                colorKey = BillPalette.GREEN.key,
                sortOrder = id.toInt(),
                createdAt = 1_000L + id,
                kind = kind.key,
            ),
        )
    }

    /** 记在**那天的中午**，避开零点边界和夏令时。 */
    private suspend fun entry(id: Long, billId: Long, cents: Long, note: String, occurred: LocalDate) {
        val millis = occurred.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        db.entryDao().insert(
            EntryEntity(
                id = id,
                billId = billId,
                amountCents = cents,
                note = note,
                occurredAt = millis,
                createdAt = millis,
            ),
        )
    }

    private suspend fun hitIds(keyword: String, filter: SearchFilter = SearchFilter()) =
        entries.search(keyword, filter).map { it.entry.id }

    private suspend fun seedMixed() {
        bill(1L, "吃饭")
        bill(2L, "工资", BillKind.INCOME)
        entry(11L, 1L, 3_850L, "午饭", occurred = now)
        entry(12L, 1L, 38_500L, "晚饭请客", occurred = now.minusDays(3))
        entry(13L, 1L, 38_500L, "打车", occurred = now.minusDays(9))
        entry(21L, 2L, 1_200_000L, "九月工资", occurred = now.minusDays(5))
    }

    @Test
    fun nothingFilteredAndNothingTypedQueriesNothing() = runTest {
        seedMixed()
        assertEquals("空关键词 + 全不限＝没人说要查什么", emptyList<Long>(), hitIds(""))
        assertEquals("只有空格也算没输入", emptyList<Long>(), hitIds("   "))
    }

    @Test
    fun emptyKeywordWithAFilterIsARealQuery() = runTest {
        seedMixed()
        assertEquals(
            "「100 以上的有哪几笔」问得出来，收入也算",
            listOf(12L, 21L, 13L),
            hitIds("", SearchFilter(floor = AmountFloor.Y100)),
        )
        assertEquals(listOf(21L), hitIds("", SearchFilter(kind = BillKind.INCOME)))
    }

    @Test
    fun amountFloorIsInclusiveAtTheBoundary() = runTest {
        bill(1L, "吃饭")
        entry(1L, 1L, 9_999L, "差一分", occurred = now)
        entry(2L, 1L, 10_000L, "刚好一百", occurred = now)
        assertEquals(listOf(2L), hitIds("", SearchFilter(floor = AmountFloor.Y100)))
    }

    @Test
    fun directionFilterNeverLeaksTheOtherKind() = runTest {
        seedMixed()
        assertEquals(listOf(11L, 12L, 13L), hitIds("", SearchFilter(kind = BillKind.EXPENSE)))
        assertEquals(
            "关键词命中但方向不符＝不该出现",
            emptyList<Long>(),
            hitIds("工资", SearchFilter(kind = BillKind.EXPENSE)),
        )
    }

    @Test
    fun weekCoversTodayThroughSixDaysBack() = runTest {
        seedMixed()
        assertEquals(
            "9 天前那笔出界（收入这笔没有方向筛选也在内）",
            listOf(11L, 12L, 21L),
            hitIds("", SearchFilter(scope = DateScope.WEEK)),
        )
    }

    @Test
    fun monthAndLastMonthAreCalendarMonthsNotRollingThirtyDays() = runTest {
        bill(1L, "吃饭")
        // 本月 1 号 + 上月的最后一天：自然月口径下各归各家，滚动 30 天口径会把它们混在一起
        entry(1L, 1L, 1L, "本月", occurred = now.withDayOfMonth(1))
        entry(2L, 1L, 1L, "上月末", occurred = now.withDayOfMonth(1).minusDays(1))
        assertEquals(listOf(1L), hitIds("", SearchFilter(scope = DateScope.MONTH)))
        assertEquals(listOf(2L), hitIds("", SearchFilter(scope = DateScope.LAST_MONTH)))
    }

    /** 不限时间时 `endExclusive` 绑的是 Long.MAX_VALUE：这条要证明它没把任何行挡在外面。 */
    @Test
    fun allScopeKeepsAncientRowsReachable() = runTest {
        bill(1L, "吃饭")
        entry(1L, 1L, 1L, "很久以前", occurred = now.minusYears(2).withDayOfMonth(1))
        entry(2L, 1L, 1L, "今天", occurred = now)
        assertEquals(listOf(2L, 1L), hitIds("", SearchFilter(kind = BillKind.EXPENSE)))
        assertEquals("同一条查询换成近 7 天，那笔两年前的就该被挡在外面", listOf(2L), hitIds("", SearchFilter(scope = DateScope.WEEK)))
    }

    /**
     * 三个匹配条件是 `(A OR B OR C) AND deletedAt IS NULL` 的关系。
     * 括号写歪的表现为"回收站里的账单还能搜到"，而且只在关键词恰好命中那笔时出现。
     */
    @Test
    fun trashedBillStopsMatchingThroughEveryOrBranch() = runTest {
        bill(1L, "吃饭")
        bill(2L, "打车")
        entry(11L, 1L, 100L, "吃饭钱", occurred = now)
        entry(21L, 2L, 100L, "吃饭钱", occurred = now)
        assertEquals(listOf(11L, 21L), hitIds("吃饭").sorted())

        db.billDao().setDeletedAt(1L, System.currentTimeMillis())

        assertEquals("条目备注命中的那半也要看回收站", listOf(21L), hitIds("吃饭"))
        assertEquals("账单名命中的那半也一样", listOf(21L), hitIds("打车"))
        assertEquals("金额精确匹配也不例外", listOf(21L), hitIds("1.00"))
    }

    @Test
    fun numericKeywordMatchesTheAmountExactly() = runTest {
        seedMixed()
        assertEquals("38.50 元 → 3850 分", listOf(11L), hitIds("38.50"))
        assertEquals("3850 元（385000 分）没有对应记录", emptyList<Long>(), hitIds("3850"))
        assertEquals("小数位不同也要按分对齐", listOf(12L, 13L), hitIds("385").sorted())
    }

    @Test
    fun wildcardCharactersStayLiteral() = runTest {
        seedMixed()
        assertEquals("% 不该变成任意串", emptyList<Long>(), hitIds("%"))
        assertEquals("_ 不该变成单字符", emptyList<Long>(), hitIds("_"))
        assertTrue("反斜杠本身也要当普通字符", hitIds("\\").isEmpty())
    }

    @Test
    fun keywordAndFilterMustBothHold() = runTest {
        seedMixed()
        assertEquals(listOf(12L), hitIds("晚饭", SearchFilter(floor = AmountFloor.Y100)))
        assertEquals("关键词在，金额不够", emptyList<Long>(), hitIds("晚饭", SearchFilter(floor = AmountFloor.Y1000)))
        assertEquals("关键词在，日期出界", emptyList<Long>(), hitIds("打车", SearchFilter(scope = DateScope.WEEK)))
    }
}
