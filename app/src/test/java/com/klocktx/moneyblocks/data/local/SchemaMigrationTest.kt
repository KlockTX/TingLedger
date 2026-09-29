package com.klocktx.moneyblocks.data.local

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 用 Room 自己导出的 schema 造一个**真实的 v2 库**，再交给 Room 一路升到当前版本。
 *
 * 为什么值得单独立一个测试：迁移写错的表现是**升级后打不开 App**（Room 校验结构不符直接抛），
 * 而平时那些 `Room.inMemoryDatabaseBuilder` 的测试走的是 onCreate，永远碰不到这条路径。
 *
 * 两个坑都在这儿踩过：
 * - 2.json 的 `setupQueries` 里含 `room_master_table`，它存着 v2 的 identity hash。
 *   自己手工调 `MIGRATION_2_3.migrate()` 不会更新那一行，Room 打开时照样判定结构不符；
 *   所以必须让 Room 自己走 onUpgrade，那才是真机升级发生的事。
 * - 库名只能按 name 传，两边都经 `context.getDatabasePath` 解析才作用在同一个文件上。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SchemaMigrationTest {

    /**
     * 读文件。
     *
     * 单测的编译类路径是 android.jar 的 Java 子集：`File.readText()`（kotlin-stdlib-jdk7 没引）、
     * `java.nio.file.*`、`InputStream.readAllBytes()` 都用不了，只能退回 Java 1.1 那套。
     */
    private fun readFile(file: File): String {
        val input = java.io.FileInputStream(file)
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        try {
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
            }
        } finally {
            input.close()
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    /** 读 Room 导出的 schema JSON（KSP 构建时生成在 app/schemas 下）。 */
    private fun schema(version: Int): JSONObject {
        val name = "schemas/${AppDatabase::class.java.name}/$version.json"
        val candidates = listOf(File(name), File("app/$name"), File("../app/$name"))
        val file = candidates.firstOrNull { it.isFile }
            // 不用 Assert.fail()：它返回 void，Kotlin 里不能当 Nothing 用在 ?: 右边
            ?: throw IllegalStateException("找不到导出 schema：$name（试过 ${candidates.map { it.path }}）")
        return JSONObject(readFile(file))
    }

    /** 照 2.json 一字不差建出 v2 的库：表、索引、setupQueries（含 room_master_table）都建。 */
    private fun buildLegacyV2(name: String) {
        val context: android.content.Context = ApplicationProvider.getApplicationContext()
        val file = context.getDatabasePath(name).also {
            it.parentFile?.mkdirs()
            it.delete()
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(2) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            val v2 = schema(2).getJSONObject("database")
                            val entities = v2.getJSONArray("entities")
                            for (i in 0 until entities.length()) {
                                val entity = entities.getJSONObject(i)
                                val table = entity.getString("tableName")
                                db.execSQL(
                                    entity.getString("createSql").replace("\${TABLE_NAME}", table),
                                )
                                entity.optJSONArray("indices")?.let { indices ->
                                    for (j in 0 until indices.length()) {
                                        db.execSQL(
                                            indices.getJSONObject(j).getString("createSql")
                                                .replace("\${TABLE_NAME}", table),
                                        )
                                    }
                                }
                            }
                            val setup = v2.getJSONArray("setupQueries")
                            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                        }

                        override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
                    },
                )
                .build(),
        )
        helper.writableDatabase.use { db ->
            db.execSQL(
                "INSERT INTO bills (id, name, iconKey, colorKey, sortOrder, createdAt, archived, kind) " +
                    "VALUES (1, '吃饭', 'restaurant', 'green', 0, 11, 0, 'expense'), " +
                    "(2, '房租', 'home', 'amber', 1, 12, 0, 'expense')",
            )
            db.execSQL(
                "INSERT INTO entries (id, billId, amountCents, note, occurredAt, createdAt) " +
                    "VALUES (1, 1, 1234, '上午', 100, 100), (2, 1, 5678, '', 200, 200)",
            )
            assertEquals(2, db.version)
        }
        helper.close()
    }

    @Test
    fun roomUpgradesARealV2Database() = runBlocking {
        val context: android.content.Context = ApplicationProvider.getApplicationContext()
        buildLegacyV2("legacy-v2.db")

        // Room 读到 user_version=2，自己去跑 2→3→4；结构与 @Database 声明不一致时这里就抛
        val db = Room.databaseBuilder(context, AppDatabase::class.java, "legacy-v2.db")
            .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .build()
        try {
            db.openHelper.writableDatabase.use { raw ->
                assertEquals(4, raw.version)
                assertEquals(
                    "老库升级后必须带上四个新列，且默认值是「不重复」",
                    listOf("none", 0L, 0L, 0L),
                    raw.query(
                        "SELECT repeatKey, repeatAmountCents, repeatAnchorDay, repeatCursorDay " +
                            "FROM bills WHERE id = 1",
                    ).use {
                        it.moveToFirst()
                        listOf(it.getString(0), it.getLong(1), it.getLong(2), it.getLong(3))
                    },
                )
                // v4 那一列可空：老行必须是 NULL（不在回收站），不能是 0
                assertTrue(
                    "老账单的 deletedAt 必须是 NULL",
                    raw.query("SELECT deletedAt FROM bills WHERE id = 1")
                        .use { it.moveToFirst() && it.isNull(0) },
                )
                assertTrue(
                    "老条目的 autoDay 必须是 NULL，否则唯一索引会把它们互相判重",
                    raw.query("SELECT autoDay FROM entries WHERE id = 1")
                        .use { it.moveToFirst() && it.isNull(0) },
                )
                // 索引是迁移里建的，不是只在 onCreate 里才有
                assertTrue(
                    raw.query(
                        "SELECT name FROM sqlite_master WHERE type = 'index' " +
                            "AND name = 'index_entries_billId_autoDay'",
                    ).use { it.moveToFirst() },
                )
            }

            // 数据一根没少
            assertEquals(2, db.backupDao().countBills())
            assertEquals(2, db.backupDao().countEntries())
            assertEquals(6_912L, db.backupDao().sumEntries())
            // 升级上来的老账单被当成"不重复"，而不是规则残缺
            assertEquals(emptyList<Any>(), db.billDao().recurring())

            // 唯一索引在升级上来的库上真的生效
            fun insertAuto(billId: Long, autoDay: String): Boolean = runCatching {
                db.openHelper.writableDatabase.execSQL(
                    "INSERT INTO entries (billId, amountCents, note, occurredAt, createdAt, autoDay) " +
                        "VALUES ($billId, 100, '', 200, 200, $autoDay)",
                )
            }.isSuccess
            assertTrue(insertAuto(1, "NULL"))
            assertTrue("同一天手工记两笔是常态，NULL 不该参与唯一约束", insertAuto(1, "NULL"))
            assertTrue(insertAuto(1, "900"))
            assertTrue("同一期第二次写入必须被唯一索引拒绝", !insertAuto(1, "900"))
            assertTrue("另一个账单的同一期不算重复", insertAuto(2, "900"))
        } finally {
            db.close()
        }
    }
}
