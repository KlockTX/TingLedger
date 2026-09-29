package com.klocktx.moneyblocks.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "bills")
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconKey: String,
    val colorKey: String,
    val sortOrder: Int,
    val createdAt: Long,
    val archived: Boolean = false,
    /**
     * domain 层 BillKind 的 key。存文本而不是序号，手工修库时才看得懂。
     * defaultValue 必须和迁移里的 DEFAULT 一致，否则 Room 升级时校验 schema 会直接抛异常。
     */
    @ColumnInfo(defaultValue = "expense")
    val kind: String = "expense",
    /**
     * 以下四列是周期性账单规则，全部有 defaultValue——v2 升上来时老行必须能填上默认值，
     * 且 defaultValue 要和 MIGRATION_2_3 里的 DEFAULT 逐字一致，否则 Room 校验 schema 直接抛。
     */
    @ColumnInfo(defaultValue = "none")
    val repeatKey: String = "none",
    @ColumnInfo(defaultValue = "0")
    val repeatAmountCents: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val repeatAnchorDay: Long = 0L,
    @ColumnInfo(defaultValue = "0")
    val repeatCursorDay: Long = 0L,
    /**
     * 回收站：非 null 表示"已删除、还没清空"，值是放进回收站的那一刻（毫秒）。
     *
     * 为什么不用真删：`entries` 对 `bills` 是 `ON DELETE CASCADE`，真删一条账单
     * 会把它名下所有历史一起带走，且没有任何回头路。放进回收站只是给这一行打个标记，
     * 条目原地不动，所以恢复是清标记而不是重放数据——不可能对不上账。
     */
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "entries",
    foreignKeys = [
        ForeignKey(
            entity = BillEntity::class,
            parentColumns = ["id"],
            childColumns = ["billId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("billId"),
        Index("occurredAt"),
        Index("billId", "occurredAt"),
        // 一个账单的每一期只允许一条自动条目。手工条目 autoDay 为 NULL，
        // SQLite 唯一索引不约束 NULL，所以「同一天记两笔」照常。
        Index("billId", "autoDay", unique = true),
    ],
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long,
    val amountCents: Long,
    val note: String,
    val occurredAt: Long,
    val createdAt: Long,
    /** 周期规则生成的是哪一期（epochDay）；手工记账为 null。 */
    val autoDay: Long? = null,
)
