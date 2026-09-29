package com.klocktx.moneyblocks.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.klocktx.moneyblocks.core.cents
import com.klocktx.moneyblocks.data.local.AppDatabase
import com.klocktx.moneyblocks.data.local.BillEntity
import com.klocktx.moneyblocks.data.local.EntryEntity
import com.klocktx.moneyblocks.data.local.SCHEMA_VERSION
import com.klocktx.moneyblocks.data.repo.BackupRepositoryImpl
import com.klocktx.moneyblocks.domain.model.BillKind
import com.klocktx.moneyblocks.domain.model.ThemeMode
import com.klocktx.moneyblocks.domain.repo.BackupRepository
import com.klocktx.moneyblocks.domain.repo.BackupRows
import com.klocktx.moneyblocks.domain.repo.ReplaceOutcome
import com.klocktx.moneyblocks.domain.usecase.BackupBill
import com.klocktx.moneyblocks.domain.usecase.BackupDecode
import com.klocktx.moneyblocks.domain.usecase.BackupEntry
import com.klocktx.moneyblocks.domain.usecase.BackupFormat
import com.klocktx.moneyblocks.domain.usecase.BackupUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 备份/恢复对**真实数据库**的验证。
 *
 * 格式的纯单测证不了两件最关键的事：
 * 一是带着原 id 插回之后能不能接着正常自增（不撞号），
 * 二是恢复失败时到底有没有回滚——「换出一半账本」比不恢复糟得多。
 * 所以这里跑真 Room（Robolectric 带的是真 SQLite），把整条链走通。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BackupRestoreDbTest {

    private lateinit var db: AppDatabase
    private lateinit var backup: BackupRepository
    private lateinit var settings: FakeSettings
    private lateinit var useCase: BackupUseCase

    // FakeSettings 提成了同包内的共享类：回收站那边也要一份，而且上限必须走同一套编解码

    @Before
    fun setUp() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        backup = BackupRepositoryImpl(db.backupDao())
        settings = FakeSettings()
        useCase = BackupUseCase(
            backup = backup,
            settings = settings,
            schemaVersion = SCHEMA_VERSION,
            appTag = "test",
            clock = { 1_700_000_000_000L },
        )
        db.billDao().insertSeed(
            listOf(
                BillEntity(1, "吃饭", "restaurant", "green", 0, 11L, kind = BillKind.EXPENSE.key),
                BillEntity(2, "打车", "taxi", "blue", 1, 12L, kind = BillKind.EXPENSE.key),
                BillEntity(3, "工资", "money", "teal", 2, 13L, archived = true, kind = BillKind.INCOME.key),
            ),
        )
        listOf(
            EntryEntity(10, 1, 2_300L, "午饭", 100L, 100L),
            EntryEntity(11, 1, 15_000L, "请客\t加啤酒", 200L, 200L),
            EntryEntity(12, 2, 800L, "", 300L, 300L),
            EntryEntity(13, 3, 880_000L, "8月\n含税", 400L, 400L),
        ).forEach { db.entryDao().insert(it) }
        settings.setThemeMode(ThemeMode.DARK)
        settings.setMonthlyBudget(300_000L.cents)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun restoredLedgerIsIdenticalToTheSnapshot() = runTest {
        val file = useCase.fileContents()
        assertNotEquals(null, db.entryDao().findById(11L))

        // 把账本弄坏（删掉一张账单会按外键带走它的条目），再拿备份换回来
        db.billDao().delete(db.billDao().findById(1L)!!)
        db.entryDao().deleteById(12L)
        assertEquals(2, backup.read().bills.size)

        val restored = useCase.restoreFromText(file)
        assertTrue(restored.toString(), restored is BackupUseCase.Restored.Applied)

        assertEquals(
            listOf(
                BillEntity(1, "吃饭", "restaurant", "green", 0, 11L, false, "expense"),
                BillEntity(2, "打车", "taxi", "blue", 1, 12L, false, "expense"),
                BillEntity(3, "工资", "money", "teal", 2, 13L, true, "income"),
            ),
            db.backupDao().allBills(),
        )
        assertEquals(listOf(10L, 11L, 12L, 13L), db.backupDao().allEntries().map { it.id })
        assertEquals(
            listOf(2_300L, 15_000L, 800L, 880_000L),
            db.backupDao().allEntries().map { it.amountCents },
        )
        // 带制表符与换行的备注必须原样回来
        assertEquals("请客\t加啤酒", db.entryDao().findById(11L)?.note)
        assertEquals("8月\n含税", db.entryDao().findById(13L)?.note)
    }

    @Test
    fun idsKeepGrowingAfterARestore() = runTest {
        useCase.restoreFromText(useCase.fileContents())
        val fresh = db.entryDao().insert(EntryEntity(0, 1, 500L, "恢复之后新记的", 999L, 999L))
        assertTrue("新 id $fresh 应大于已恢复的最大 id 13", fresh > 13L)
        assertEquals(5, db.backupDao().countEntries())
    }

    @Test
    fun settingsComeBackWithTheLedger() = runTest {
        val file = useCase.fileContents()
        settings.setThemeMode(ThemeMode.LIGHT)
        settings.setMonthlyBudget(1L.cents)
        useCase.restoreFromText(file)
        assertEquals(ThemeMode.DARK, settings.themeMode.first())
        assertEquals(300_000L.cents, settings.monthlyBudget.first())
    }

    @Test
    fun undoPutsThePreviousLedgerBack() = runTest {
        val original = backup.read()
        val incoming = BackupFormat.decode(
            useCase.fileContents().replace("吃饭", "吃饭改过"),
            SCHEMA_VERSION,
        )
        assertTrue(incoming.toString(), incoming is BackupDecode.Success)

        val applied = useCase.restoreSnapshot((incoming as BackupDecode.Success).data)
        assertTrue(applied.toString(), applied is BackupUseCase.Restored.Applied)
        assertEquals("吃饭改过", backup.read().bills.first().name)

        val undone = useCase.restoreSnapshot((applied as BackupUseCase.Restored.Applied).previous)
        assertTrue(undone is BackupUseCase.Restored.Applied)
        assertEquals(original, backup.read())
    }

    @Test
    fun brokenFileLeavesTheDatabaseUntouched() = runTest {
        val text = useCase.fileContents()
        val outcome = useCase.restoreFromText(text.substring(0, text.length - 40))
        assertTrue(outcome.toString(), outcome is BackupUseCase.Restored.FileRejected)
        assertEquals(3, backup.read().bills.size)
        assertEquals(4, backup.read().entries.size)
        // 被拒收的文件也不该顺带把偏好改掉
        assertEquals(ThemeMode.DARK, settings.themeMode.first())
        assertEquals(300_000L.cents, settings.monthlyBudget.first())
    }

    @Test
    fun failedWriteRollsBackInsteadOfHalfRestoring() = runTest {
        // 绕过文件校验，直接给 DAO 一份外键悬空的数据：整个事务必须回滚
        val outcome = backup.replace(
            BackupRows(
                bills = listOf(BackupBill(77, "新账单", "box", "slate", "expense", 0, false, 1L)),
                entries = listOf(BackupEntry(88, 9_999L, 100L, "指向不存在的账单", 1L, 1L)),
            ),
        )
        assertTrue(outcome.toString(), outcome is ReplaceOutcome.Reverted)
        assertEquals(3, db.backupDao().countBills())
        assertEquals(4, db.backupDao().countEntries())
        assertEquals(898_100L, db.backupDao().sumEntries())
    }
}
