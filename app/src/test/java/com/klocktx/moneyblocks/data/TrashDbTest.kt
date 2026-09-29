package com.klocktx.moneyblocks.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.klocktx.moneyblocks.core.TimeRange
import com.klocktx.moneyblocks.core.cents
import com.klocktx.moneyblocks.data.local.AppDatabase
import com.klocktx.moneyblocks.data.local.BillEntity
import com.klocktx.moneyblocks.data.local.EntryEntity
import com.klocktx.moneyblocks.data.repo.BillRepositoryImpl
import com.klocktx.moneyblocks.data.repo.EntryRepositoryImpl
import com.klocktx.moneyblocks.data.repo.ExportRepositoryImpl
import com.klocktx.moneyblocks.data.repo.StatsRepositoryImpl
import com.klocktx.moneyblocks.domain.model.Bill
import com.klocktx.moneyblocks.domain.model.BillKind
import com.klocktx.moneyblocks.domain.model.BillPalette
import com.klocktx.moneyblocks.domain.model.SearchFilter
import com.klocktx.moneyblocks.domain.usecase.ManageBillsUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 回收站对**真实数据库**的行为。
 *
 * 这条测试要证的不是"能不能打个标记"，而是那圈读查询有没有漏：
 * 账单进了回收站之后，首页、统计、搜索、导出、最近条目、周期生成**每一处**都不该再看见它，
 * 而它名下的条目必须一行不少地留在库里等着被恢复。漏掉任何一处，表现都是
 * "账单删掉了但它的钱还在总数里"——这种错没人会去逐条核对，只会觉得账本莫名其妙。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TrashDbTest {

    private lateinit var db: AppDatabase
    private lateinit var bills: BillRepositoryImpl
    private lateinit var entries: EntryRepositoryImpl
    private lateinit var stats: StatsRepositoryImpl
    private lateinit var export: ExportRepositoryImpl
    private lateinit var settings: FakeSettings
    private lateinit var manage: ManageBillsUseCase

    private val allTime = TimeRange(0L, Long.MAX_VALUE)

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        bills = BillRepositoryImpl(db.billDao())
        entries = EntryRepositoryImpl(db.entryDao())
        stats = StatsRepositoryImpl(db.entryDao())
        export = ExportRepositoryImpl(db.exportDao())
        settings = FakeSettings()
        manage = ManageBillsUseCase(bills, settings)
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** 建一条账单 + 两笔流水，返回账单 id。 */
    private suspend fun seed(name: String, billId: Long, archived: Boolean = false): Long {
        db.billDao().insert(
            BillEntity(
                id = billId,
                name = name,
                iconKey = "restaurant",
                colorKey = BillPalette.GREEN.key,
                sortOrder = billId.toInt(),
                createdAt = 1_000L + billId,
                archived = archived,
                kind = BillKind.EXPENSE.key,
            ),
        )
        db.entryDao().insert(
            EntryEntity(id = billId * 100, billId = billId, amountCents = 1_000L, note = "a", occurredAt = 5_000L, createdAt = 5_000L),
        )
        db.entryDao().insert(
            EntryEntity(id = billId * 100 + 1, billId = billId, amountCents = 2_500L, note = "b", occurredAt = 6_000L, createdAt = 6_000L),
        )
        return billId
    }

    private suspend fun liveBillIds() = bills.observeBills().first().map { it.id }

    @Test
    fun trashingHidesTheBillEverywhereButKeepsItsEntries() = runTest {
        seed("吃饭", 1L)
        seed("打车", 2L)
        // 两条账单各 1000 + 2500
        assertEquals(7_000L, stats.total(allTime, BillKind.EXPENSE).value)

        bills.trash(1L)

        // 首页与统计
        assertEquals(listOf(2L), liveBillIds())
        assertEquals(
            listOf(2L),
            bills.observeBillSummaries(allTime).first().map { it.bill.id },
        )
        assertEquals(3_500L, stats.total(allTime, BillKind.EXPENSE).value)
        // 「关于」那行的笔数与导出
        assertEquals(2, entries.observeCount().first())
        val csv = export.exportCsv()
        assertEquals("表头 + 剩下的那一笔", 3, csv.trim().lines().size)
        assertTrue("回收站里的账单不该出现在 CSV 里", !csv.contains("吃饭"))
        // 最近条目、按日查看、搜索
        assertEquals(2, entries.observeRecent(20).first().size)
        assertEquals(2, entries.hitsBetween(0L, Long.MAX_VALUE).size)
        assertEquals(0, entries.search("吃饭", SearchFilter()).size)
        // 条目一行没少，只是账单被标记了
        assertEquals(2, db.entryDao().observeByBill(1L).first().size)
        assertEquals(1, bills.observeTrashed().first().size)
    }

    @Test
    fun restoringBringsBackExactlyTheSameRows() = runTest {
        seed("吃饭", 1L)
        val before = db.entryDao().observeByBill(1L).first().map { it.id to it.amountCents }

        bills.trash(1L)
        bills.restore(1L)

        assertEquals(listOf(1L), liveBillIds())
        assertEquals(3_500L, stats.total(allTime, BillKind.EXPENSE).value)
        assertEquals(before, db.entryDao().observeByBill(1L).first().map { it.id to it.amountCents })
        assertTrue(bills.observeTrashed().first().isEmpty())
    }

    @Test
    fun deleteForeverIsTheOnlyIrreversiblePath() = runTest {
        seed("吃饭", 1L)
        seed("打车", 2L)

        bills.trash(1L)
        bills.deleteForever(1L)

        assertEquals(listOf(2L), liveBillIds())
        assertEquals(0, db.entryDao().observeByBill(1L).first().size)
        assertEquals("CASCADE 该把名下条目一起带走", 2, entries.observeCount().first())
    }

    @Test
    fun purgeOnlyRemovesWhatPassedTheRetentionWindow() = runTest {
        val day = 86_400_000L
        seed("一直活着", 3L)
        seed("很久以前删的", 1L)
        seed("刚删的", 2L)
        val now = System.currentTimeMillis()
        // 一个远超保留期，一个还在期内
        db.billDao().setDeletedAt(1L, now - (ManageBillsUseCase.TRASH_RETENTION_DAYS + 5) * day)
        db.billDao().setDeletedAt(2L, now - day)

        val purged = bills.purgeTrash(now - ManageBillsUseCase.TRASH_RETENTION_DAYS * day)

        assertEquals(listOf(1L), purged)
        assertEquals("没进回收站的照常留着", listOf(3L), liveBillIds())
        assertEquals("到期的已经被真删，回收站里只剩没到期的那个", listOf(2L), bills.observeTrashed().first().map { it.id })
        assertEquals("过期的才真删，名下条目一起走", 0, db.entryDao().observeByBill(1L).first().size)
        assertEquals("没到期的原样留着", 2, db.entryDao().observeByBill(2L).first().size)
    }

    @Test
    fun trashedBillStopsGeneratingEntriesButArchivedCountIsStillKnown() = runTest {
        val day = 86_400_000L
        val anchor = (System.currentTimeMillis() / day) - 3
        db.billDao().insert(
            BillEntity(
                id = 9L,
                name = "房租",
                iconKey = "home",
                colorKey = BillPalette.AMBER.key,
                sortOrder = 0,
                createdAt = 1L,
                kind = BillKind.EXPENSE.key,
                repeatKey = "weekly",
                repeatAmountCents = 1_200_00L,
                repeatAnchorDay = anchor,
            ),
        )
        assertEquals("规则完整时该被捞出来", 1, db.billDao().recurring().size)

        bills.trash(9L)
        assertEquals("进回收站后不该再生成", emptyList<Any>(), db.billDao().recurring())
        bills.restore(9L)
        assertEquals(1, db.billDao().recurring().size)
    }

    /**
     * 归档与回收站里的账单也要知道"名下几笔"。
     *
     * 这条是钉住一个既有 bug 的：设置页原来拿 `observeBillSummaries` 拼这个数，
     * 而那条查询带 `archived = 0`，于是每个已归档账单都显示"名下 0 笔"——
     * 偏偏这是告诉用户"删掉会带走多少"的数字。
     */
    @Test
    fun entryCountsCoverArchivedAndTrashedBills() = runTest {
        seed("吃饭", 1L)
        seed("已归档的", 2L, archived = true)
        bills.trash(2L)

        val counts = entries.observeCountsByBill().first()
        assertEquals(mapOf(1L to 2, 2L to 2), counts)
    }

    @Test
    fun findRefusesToReturnATrashedBill() = runTest {
        seed("吃饭", 1L)
        assertNotNull(bills.find(1L))

        bills.trash(1L)
        // 记账目标是从 find 来的：返回 null 才不会出现"往一个已删账单里记一笔"
        val gone: Bill? = bills.find(1L)
        assertNull(gone)
    }

    /**
     * 真删账单时，DataStore 里的月度上限必须跟着没。
     *
     * 这两条是本轮唯一能证明"跨存储清理"发生了的地方：`deleteByIds` 是 SQL 删除，
     * 它成功与否完全说明不了偏好那侧还剩着什么。留着残值的后果不是报错，而是
     * 将来某个新账单拿到同一个 rowid，莫名其妙背上一个别人的上限。
     */
    @Test
    fun deleteForeverAlsoDropsTheStoredCap() = runTest {
        seed("吃饭", 1L)
        seed("打车", 2L)
        val first = bills.observeBills().first().first { it.id == 1L }
        settings.setBillCap(1L, 200_000L.cents)
        settings.setBillCap(2L, 50_000L.cents)

        manage.deleteForever(first)

        assertEquals(mapOf(2L to 50_000L.cents), settings.billCaps.first())
    }

    @Test
    fun purgingTrashDropsCapsOnlyForTheBillsItActuallyDeleted() = runTest {
        val day = 86_400_000L
        seed("很久以前删的", 1L)
        seed("一直活着", 3L)
        settings.setBillCap(1L, 100_000L.cents)
        settings.setBillCap(3L, 300_000L.cents)
        db.billDao().setDeletedAt(1L, System.currentTimeMillis() - (ManageBillsUseCase.TRASH_RETENTION_DAYS + 5) * day)

        val purged = manage.purgeTrash()

        assertEquals("到期那条被真删", 1, purged)
        assertEquals("活着的账单上限不能跟着丢", mapOf(3L to 300_000L.cents), settings.billCaps.first())
    }
}
