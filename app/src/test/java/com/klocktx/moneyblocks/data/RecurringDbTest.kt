package com.klocktx.moneyblocks.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.klocktx.moneyblocks.core.cents
import com.klocktx.moneyblocks.core.millisToLocalDate
import com.klocktx.moneyblocks.data.local.AppDatabase
import com.klocktx.moneyblocks.data.repo.BillRepositoryImpl
import com.klocktx.moneyblocks.data.repo.EntryRepositoryImpl
import com.klocktx.moneyblocks.domain.model.Bill
import com.klocktx.moneyblocks.domain.model.BillKind
import com.klocktx.moneyblocks.domain.model.BillPalette
import com.klocktx.moneyblocks.domain.model.BillRepeat
import com.klocktx.moneyblocks.domain.model.Entry
import com.klocktx.moneyblocks.domain.usecase.RecurringUseCase
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 周期性账单对**真实数据库**的验证。
 *
 * 排期算法的纯单测证不了三件最要紧的事：
 * 一是重复生成会不会写两笔（靠 (billId, autoDay) 唯一索引兜），
 * 二是删掉的自动条目会不会被下一次启动补回来（靠游标，而游标要真的落库），
 * 三是手工条目会不会被这个唯一索引误伤（NULL 不参与唯一约束，这条只在真 SQLite 上说得清）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RecurringDbTest {

    private lateinit var db: AppDatabase
    private lateinit var bills: BillRepositoryImpl
    private lateinit var entries: EntryRepositoryImpl
    private lateinit var recurring: RecurringUseCase

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        bills = BillRepositoryImpl(db.billDao())
        entries = EntryRepositoryImpl(db.entryDao())
        recurring = RecurringUseCase(bills, entries)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun day(text: String): Long = LocalDate.parse(text).toEpochDay()

    private fun dayOf(millis: Long): String = millisToLocalDate(millis).toString()

    private suspend fun addRent(
        anchor: String,
        repeat: BillRepeat = BillRepeat.MONTHLY,
        amount: Long = 120_000L,
        kind: BillKind = BillKind.EXPENSE,
        archived: Boolean = false,
    ): Long = bills.save(
        Bill(
            name = if (kind == BillKind.INCOME) "工资" else "房租",
            iconKey = "home",
            palette = BillPalette.GREEN,
            kind = kind,
            archived = archived,
            repeat = repeat,
            repeatAmount = amount.cents,
            repeatAnchorDay = day(anchor),
        ),
    )

    private suspend fun rowsOf(billId: Long) = entries.observeEntries(billId).first()

    @Test
    fun generatesOneEntryPerDuePeriod() = runTest {
        val id = addRent("2026-07-10")
        val result = recurring.runToday(day("2026-09-25"))

        assertEquals(3, result.created)
        assertEquals(1, result.touchedBills)
        assertEquals(
            listOf("2026-07-10", "2026-08-10", "2026-09-10"),
            rowsOf(id).sortedBy { it.occurredAt }.map { dayOf(it.occurredAt) },
        )
        rowsOf(id).forEach {
            assertEquals(120_000L.cents, it.amount)
            assertEquals("", it.note)
            assertTrue("自动条目必须带期次", it.autoDay != null)
        }
    }

    @Test
    fun runningTwiceCreatesNothingTheSecondTime() = runTest {
        val id = addRent("2026-07-10")
        recurring.runToday(day("2026-09-25"))
        val again = recurring.runToday(day("2026-09-25"))

        assertEquals(0, again.created)
        assertEquals(0, again.touchedBills)
        assertEquals(3, rowsOf(id).size)
    }

    /** 用户删掉某个月的房租，是"这个月不交了"，不是"等 App 再提醒我一次"。 */
    @Test
    fun deletedAutoEntryDoesNotComeBack() = runTest {
        val id = addRent("2026-07-10")
        recurring.runToday(day("2026-09-25"))
        val august = rowsOf(id).first { dayOf(it.occurredAt) == "2026-08-10" }
        entries.delete(august.id)
        assertEquals(2, rowsOf(id).size)

        recurring.runToday(day("2026-09-25"))
        assertEquals(2, rowsOf(id).size)
    }

    @Test
    fun duplicatePeriodIsIgnoredInsteadOfCrashing() = runTest {
        val id = addRent("2026-09-10")
        val entry = Entry(
            billId = id,
            amount = 120_000L.cents,
            occurredAt = day("2026-09-10") * MILLIS_PER_DAY,
            autoDay = day("2026-09-10"),
        )
        assertTrue(entries.addAuto(entry))
        assertFalse(entries.addAuto(entry))
        assertEquals(1, rowsOf(id).size)
    }

    /** 唯一索引只管自动条目：同一天手工记两笔是常态，不能被它挡下来。 */
    @Test
    fun manualEntriesOnTheSameDayAreFine() = runTest {
        val id = addRent("2026-09-10", repeat = BillRepeat.NONE)
        val millis = day("2026-09-15") * MILLIS_PER_DAY
        entries.add(Entry(billId = id, amount = 30_00L.cents, note = "上午", occurredAt = millis))
        entries.add(Entry(billId = id, amount = 40_00L.cents, note = "下午", occurredAt = millis))

        val rows = rowsOf(id)
        assertEquals(2, rows.size)
        rows.forEach { assertNull(it.autoDay) }
    }

    @Test
    fun archivedAndFutureRulesGenerateNothing() = runTest {
        val archived = addRent("2026-07-10", archived = true)
        val future = addRent("2026-10-05")

        val result = recurring.runToday(day("2026-09-25"))
        assertEquals(0, result.created)
        assertEquals(0, rowsOf(archived).size)
        assertEquals(0, rowsOf(future).size)

        // 到了 10 月 5 日，那条未来的规则才开始起作用
        recurring.runToday(day("2026-10-06"))
        assertEquals(1, rowsOf(future).size)
    }

    @Test
    fun incompleteRulesAreSkipped() = runTest {
        val zeroAmount = addRent("2026-07-10", amount = 0L)
        assertEquals(0, recurring.runToday(day("2026-09-25")).created)
        assertEquals(0, rowsOf(zeroAmount).size)
    }

    @Test
    fun monthly31stClampsThenGoesBack() = runTest {
        val id = addRent("2026-01-31")
        recurring.runToday(day("2026-03-31"))
        assertEquals(
            listOf("2026-01-31", "2026-02-28", "2026-03-31"),
            rowsOf(id).sortedBy { it.occurredAt }.map { dayOf(it.occurredAt) },
        )
    }

    @Test
    fun weeklyAndIncomeBothWork() = runTest {
        val salary = addRent("2026-09-05", repeat = BillRepeat.WEEKLY, amount = 800_000L, kind = BillKind.INCOME)
        recurring.runToday(day("2026-09-25"))
        assertEquals(
            listOf("2026-09-05", "2026-09-12", "2026-09-19"),
            rowsOf(salary).sortedBy { it.occurredAt }.map { dayOf(it.occurredAt) },
        )
    }

    /** 改了首次入账日就是换了一条时间线：从新日期起重新排，但已走过的同一期不会重复。 */
    @Test
    fun movingAnchorReplaysFromTheNewDate() = runTest {
        val id = addRent("2026-09-10")
        recurring.runToday(day("2026-09-25"))
        assertEquals(1, rowsOf(id).size)

        bills.save(bills.find(id)!!.copy(repeatAnchorDay = day("2026-08-10")))
        recurring.runToday(day("2026-09-25"))

        assertEquals(
            listOf("2026-08-10", "2026-09-10"),
            rowsOf(id).sortedBy { it.occurredAt }.map { dayOf(it.occurredAt) },
        )
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
