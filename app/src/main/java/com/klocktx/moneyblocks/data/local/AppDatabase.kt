package com.klocktx.moneyblocks.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.klocktx.moneyblocks.domain.model.BillKind

/**
 * 库结构版本。备份文件头部要写这个数，恢复时靠它拒绝"来自更新版本"的备份，
 * 所以标注和读取必须共用一个常量——写两处迟早对不上。
 */
const val SCHEMA_VERSION = 4

@Database(
    entities = [BillEntity::class, EntryEntity::class],
    version = SCHEMA_VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun billDao(): BillDao
    abstract fun entryDao(): EntryDao
    abstract fun exportDao(): ExportDao
    abstract fun backupDao(): BackupDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "moneyblocks.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .addCallback(SeedCallback)
                .build()

        /**
         * v1 只有支出概念，v2 给账单加方向。
         * 这里写显式迁移而不是 fallbackToDestructiveMigration：手机上已经装了 v1.6 并且
         * 记了真实流水，破坏性回退会把账本清空。
         */
        private val MIGRATION_1_2 = Migration(1, 2) { db ->
            db.execSQL(
                "ALTER TABLE bills ADD COLUMN kind TEXT NOT NULL DEFAULT '${BillKind.EXPENSE.key}'",
            )
            // 早期种子的 iconKey 写错成了库里不存在的 shopping_bag / gift，一直落到兜底图标。
            // 键存在库里，改代码修不到已装的设备，只能在这里纠正。
            db.execSQL("UPDATE bills SET iconKey = 'shopping_cart' WHERE iconKey = 'shopping_bag'")
            db.execSQL("UPDATE bills SET iconKey = 'card_giftcard' WHERE iconKey = 'gift'")
        }

        /**
         * v3 加周期性账单：账单上多四个规则列，条目上多一个"哪一期"的列，
         * 并给 (billId, autoDay) 建唯一索引兜住重复生成。
         *
         * 四条 ALTER 的 DEFAULT 必须和 [BillEntity] 上的 @ColumnInfo(defaultValue) 逐字一致；
         * 索引名必须和 Room 自己生成的命名规则一致（index_表名_列名_用下划线连），
         * 否则升级完打开房间校验 schema 会抛 IllegalStateException，比不升级还糟。
         */
        /** 对装机的老库跑这条 SQL；[com.klocktx.moneyblocks.data.local.SchemaMigrationTest] 拿导出 schema 逐句比对它。 */
        internal val MIGRATION_2_3 = Migration(2, 3) { db ->
            db.execSQL("ALTER TABLE bills ADD COLUMN repeatKey TEXT NOT NULL DEFAULT 'none'")
            db.execSQL("ALTER TABLE bills ADD COLUMN repeatAmountCents INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE bills ADD COLUMN repeatAnchorDay INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE bills ADD COLUMN repeatCursorDay INTEGER NOT NULL DEFAULT 0")
            // 手工条目这一列是 NULL，所以不给 DEFAULT：NULL 不参与唯一约束
            db.execSQL("ALTER TABLE entries ADD COLUMN autoDay INTEGER")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_entries_billId_autoDay` " +
                    "ON `entries` (`billId`, `autoDay`)",
            )
        }

        /**
         * v4 给账单加回收站标记。
         *
         * 只此一列，且可空无默认：老行读出来就是 null，也就是"没被删过"，
         * 不需要任何回填 UPDATE——`ALTER TABLE ADD COLUMN` 不带 DEFAULT 时
         * SQLite 直接把已有行的该列填成 NULL，正好是我们要的语义。
         */
        internal val MIGRATION_3_4 = Migration(3, 4) { db ->
            db.execSQL("ALTER TABLE bills ADD COLUMN deletedAt INTEGER")
        }

        private val SeedCallback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()
                DEFAULT_BILLS.forEachIndexed { index, seed ->
                    db.execSQL(
                        "INSERT INTO bills (name, iconKey, colorKey, sortOrder, createdAt, archived, kind) " +
                            "VALUES (?, ?, ?, ?, ?, 0, ?)",
                        arrayOf<Any>(seed.name, seed.iconKey, seed.colorKey, index, now, seed.kind),
                    )
                }
            }
        }
    }
}

private data class BillSeed(
    val name: String,
    val iconKey: String,
    val colorKey: String,
    val kind: String = BillKind.EXPENSE.key,
)

private val DEFAULT_BILLS = listOf(
    BillSeed("吃饭", "restaurant", "green"),
    BillSeed("打车", "taxi", "blue"),
    BillSeed("水电", "bolt", "amber"),
    BillSeed("购物", "shopping_cart", "coral"),
    BillSeed("日常其他", "card_giftcard", "slate"),
    // 方向是新加的概念，给一份收入账单打底，新装机才看得到「结余」
    BillSeed("工资", "money", "teal", BillKind.INCOME.key),
)
