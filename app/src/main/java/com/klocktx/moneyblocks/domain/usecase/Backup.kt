package com.klocktx.moneyblocks.domain.usecase

import com.klocktx.moneyblocks.core.Cents
import com.klocktx.moneyblocks.domain.model.BillKind
import com.klocktx.moneyblocks.domain.model.BillRepeat

/**
 * 整库备份的文件格式。
 *
 * 为什么不直接导数据库文件：
 * - 预算和黑白模式在 DataStore 里，拷 `.db` 会漏掉一半状态；
 * - 各家 ROM 的 WAL 与文件锁差异让「边写边拷」不可控，而我这边没有设备可验；
 * - 文本格式能靠文件自带的行数与金额和当场发现截断与篡改，二进制做不到。
 *
 * 所以这是一份**逻辑快照**：账单、条目、偏好全部列出，末尾把自己的行数和金额和再抄一遍
 * 当校验和。恢复前必须通过全部校验，任何一条不过就整份拒收，绝不"能恢复多少算多少"。
 *
 * 本文件不引任何 Android 类型，所以转义与拒收规则可以整套跑在普通单测里。
 */
data class BackupBill(
    val id: Long,
    val name: String,
    val iconKey: String,
    val colorKey: String,
    val kindKey: String,
    val sortOrder: Int,
    val archived: Boolean,
    val createdAt: Long,
    /** 格式 v2 才有的周期规则；v1 的老文件读进来是这四个默认值。 */
    val repeatKey: String = "none",
    val repeatAmountCents: Long = 0L,
    val repeatAnchorDay: Long = 0L,
    val repeatCursorDay: Long = 0L,
    /** 格式 v3 才有：非 null 表示备份时它就在回收站里，恢复之后也应该在。 */
    val deletedAt: Long? = null,
)

data class BackupEntry(
    val id: Long,
    val billId: Long,
    val amountCents: Long,
    val note: String,
    val occurredAt: Long,
    val createdAt: Long,
    /**
     * 自动条目属于哪一期（epochDay），手工记的为 null。
     * 必须一起备份：丢了它，恢复之后那一期在库里就变成"手工条目"，
     * 下次生成时唯一索引拦不住，会凭空多出一笔重复的房租。
     */
    val autoDay: Long? = null,
)

data class BackupData(
    val schemaVersion: Int,
    val appTag: String,
    val createdAt: Long,
    val themeModeKey: String,
    /** 主题色键。老备份没这一行时是空串，恢复时保持本机当前选择不动。 */
    val monetThemeKey: String = "",
    val monthlyBudgetCents: Long,
    /**
     * 格式 v4 才有的账单上限。老文件没这行时是空表——**不能**拿本机现值当默认，
     * 否则恢复一份旧备份会把后来的上限残在新账本上。
     */
    val billCaps: Map<Long, Cents> = emptyMap(),
    val bills: List<BackupBill>,
    val entries: List<BackupEntry>,
) {
    val amountSum: Long get() = entries.sumOf { it.amountCents }
}

/** [Rejected.reason] 是直接显示给用户看的一句话，不是错误码。 */
sealed interface BackupDecode {
    data class Success(val data: BackupData) : BackupDecode
    data class Rejected(val reason: String) : BackupDecode
}

object BackupFormat {

    const val FILE_TAG = "tingxuan-backup"

    /** 写出去的文件用的格式版本。v2 加周期规则四项，v3 加回收站标记一项，v4 加 CAPS 一行（账单上限）。 */
    const val FORMAT_VERSION = 4

    /**
     * 还认得的最老格式版本。加字段走"读旧写新"：v1 的账单行只有 8 个字段，
     * 缺的那几项用默认值补，所以老备份照样能恢复，不必逼人先升级再导出一次。
     */
    const val MIN_FORMAT_VERSION = 1

    const val FILE_NAME_PREFIX = "亭宣记账-备份-"

    /** 超过这么久没备份，就在管理页提一句。 */
    const val STALE_AFTER_DAYS = 30L

    private const val END = "#END"

    /** 距上次备份多少天；从未备份（<=0）返回 null。 */
    fun daysSince(lastBackupAt: Long, now: Long): Long? =
        if (lastBackupAt <= 0L || now < lastBackupAt) null
        else (now - lastBackupAt) / 86_400_000L

    fun isStale(lastBackupAt: Long, now: Long): Boolean {
        val days = daysSince(lastBackupAt, now) ?: return true
        return days >= STALE_AFTER_DAYS
    }

    /** 自由文本里的分隔符必须转义，否则一行记录会被自己的内容劈开。 */
    private fun escape(text: String): String = buildString(text.length + 8) {
        text.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
    }

    /** 出现非法转义序列就返回 null，让整份文件被拒收，而不是悄悄丢掉字符。 */
    private fun unescape(text: String): String? {
        if (!text.contains('\\')) return text
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c != '\\') {
                out.append(c)
                i++
                continue
            }
            when (text.getOrNull(i + 1)) {
                '\\' -> out.append('\\')
                'n' -> out.append('\n')
                'r' -> out.append('\r')
                't' -> out.append('\t')
                else -> return null
            }
            i += 2
        }
        return out.toString()
    }

    fun encode(data: BackupData): String = buildString(4096) {
        fun meta(key: String, value: String) = append(key).append('\t').append(value).append('\n')
        meta("FORMAT", "$FILE_TAG/$FORMAT_VERSION")
        meta("SCHEMA", data.schemaVersion.toString())
        meta("APP", escape(data.appTag))
        meta("CREATED", data.createdAt.toString())
        meta("BILLS", data.bills.size.toString())
        meta("ENTRIES", data.entries.size.toString())
        meta("SUM", data.amountSum.toString())
        meta("THEME", escape(data.themeModeKey))
        meta("MONET", escape(data.monetThemeKey))
        meta("BUDGET", data.monthlyBudgetCents.toString())
        meta("CAPS", BillCaps.encode(data.billCaps))
        data.bills.forEach {
            append(
                listOf(
                    "B", it.id, it.name, it.iconKey, it.colorKey, it.kindKey, it.sortOrder,
                    if (it.archived) "1" else "0", it.createdAt,
                    it.repeatKey, it.repeatAmountCents, it.repeatAnchorDay, it.repeatCursorDay,
                    it.deletedAt?.toString().orEmpty(),
                ).joinToString("\t") { field -> escape(field.toString()) } + "\n",
            )
        }
        data.entries.forEach {
            append(
                listOf(
                    "E", it.id, it.billId, it.amountCents, it.note, it.occurredAt, it.createdAt,
                    it.autoDay?.toString().orEmpty(),
                ).joinToString("\t") { field -> escape(field.toString()) } + "\n",
            )
        }
        append(
            listOf(
                END, "bills=${data.bills.size}",
                "entries=${data.entries.size}", "sum=${data.amountSum}",
            ).joinToString("\t") + "\n",
        )
    }

    /**
     * 解析并做全部一致性校验。[supportedSchema] 是本机认识的库版本——比它新的备份一定拒收：
     * 表结构对不上却硬塞进去，等于把账本毁掉。
     */
    fun decode(text: String, supportedSchema: Int): BackupDecode {
        if (text.isBlank()) return BackupDecode.Rejected("文件是空的")

        val lines = text.split('\n').map { it.removeSuffix("\r") }.dropLastWhile { it.isEmpty() }

        /**
         * 格式版本必须先看，因为它决定账单行该有几个字段。
         * 只看第一行：写出去的文件 FORMAT 永远在第一行，不在第一行就不是我们生成的文件。
         */
        val header = lines.firstOrNull()?.split('\t').orEmpty()
        if (header.size != 2 || header[0] != "FORMAT") {
            return BackupDecode.Rejected("第一行不是 FORMAT 标识，这不是亭宣记账的备份文件")
        }
        val headerParts = header[1].split('/')
        if (headerParts.firstOrNull() != FILE_TAG) {
            return BackupDecode.Rejected("文件标记为「${header[1]}」，不是亭宣记账的备份")
        }
        val formatVersion = headerParts.getOrNull(1)?.toIntOrNull()
            ?: return BackupDecode.Rejected("备份格式版本号缺失或不是数字：${header[1]}")
        if (formatVersion < MIN_FORMAT_VERSION || formatVersion > FORMAT_VERSION) {
            return BackupDecode.Rejected(
                "备份格式为 $formatVersion，本版本支持 $MIN_FORMAT_VERSION–$FORMAT_VERSION",
            )
        }

        val meta = HashMap<String, String>()
        val bills = ArrayList<BackupBill>()
        val entries = ArrayList<BackupEntry>()
        var endLine: String? = null

        for ((offset, raw) in lines.withIndex()) {
            if (raw.isEmpty()) continue
            val at = "第 ${offset + 1} 行"
            val fields = raw.split('\t')
            val tag = fields.first()
            if (tag == END) {
                if (endLine != null) {
                    return BackupDecode.Rejected("$at：出现了多个 $END，文件可能被拼接过")
                }
                endLine = fields.drop(1).joinToString("\t")
                continue
            }
            val body = fields.drop(1)
            when (tag) {
                "B" -> when (val row = parseBill(body, formatVersion)) {
                    is RowResult.Bad -> return BackupDecode.Rejected("$at：${row.reason}")
                    is RowResult.Good -> bills += row.value
                }

                "E" -> when (val row = parseEntry(body, formatVersion)) {
                    is RowResult.Bad -> return BackupDecode.Rejected("$at：${row.reason}")
                    is RowResult.Good -> entries += row.value
                }

                else -> {
                    if (body.size != 1) return BackupDecode.Rejected("$at：未知记录类型「$tag」")
                    meta[tag] = body[0]
                }
            }
        }

        val end = endLine
            ?: return BackupDecode.Rejected("缺少结尾行，文件不完整（多半是被截断了）")

        val schema = meta["SCHEMA"]?.toLongOrNull()
            ?: return BackupDecode.Rejected("SCHEMA 缺失或不是数字")
        if (schema > supportedSchema) {
            return BackupDecode.Rejected(
                "这份备份来自更新的版本（库结构 $schema > 本机 $supportedSchema）",
            )
        }
        val declaredBills = meta["BILLS"]?.toIntOrNull()
            ?: return BackupDecode.Rejected("BILLS 缺失或不是数字")
        val declaredEntries = meta["ENTRIES"]?.toIntOrNull()
            ?: return BackupDecode.Rejected("ENTRIES 缺失或不是数字")
        val declaredSum = meta["SUM"]?.toLongOrNull()
            ?: return BackupDecode.Rejected("SUM 缺失或不是数字")

        if (declaredBills != bills.size) {
            return BackupDecode.Rejected("账单行数对不上：声明 $declaredBills，实有 ${bills.size}")
        }
        if (declaredEntries != entries.size) {
            return BackupDecode.Rejected("条目行数对不上：声明 $declaredEntries，实有 ${entries.size}")
        }
        val sum = entries.sumOf { it.amountCents }
        if (declaredSum != sum) {
            return BackupDecode.Rejected("金额和对不上：声明 $declaredSum，实有 $sum")
        }
        val endSum = end.split('\t').firstOrNull { it.startsWith("sum=") }
            ?.removePrefix("sum=")?.toLongOrNull()
            ?: return BackupDecode.Rejected("结尾行缺少 sum")
        if (endSum != sum) {
            return BackupDecode.Rejected("结尾行的金额和与正文不一致")
        }

        val billIds = HashSet<Long>()
        bills.forEach {
            if (!billIds.add(it.id)) return BackupDecode.Rejected("账单 id 重复：${it.id}")
            if (BillKind.fromKey(it.kindKey).key != it.kindKey) {
                // 不能兜底成支出——那会把一笔工资静悄悄记成支出，比报错恶劣得多
                return BackupDecode.Rejected("账单「${it.name}」的方向无法识别：${it.kindKey}")
            }
            // 周期键同理：认不出来的键不能悄悄当成"不重复"，那等于把用户设好的规则弄丢
            if (BillRepeat.fromKey(it.repeatKey).key != it.repeatKey) {
                return BackupDecode.Rejected("账单「${it.name}」的周期无法识别：${it.repeatKey}")
            }
        }
        // CAPS 是可选行：v3 及更早的文件没有它，读成空表。有就必须整条认得——
        // 上限指向文件里不存在的账单同样拒收，因为那正是一条"残值等着被复用 id 接上"的线。
        val caps = when (val raw = meta["CAPS"]) {
            null -> emptyMap()
            else -> BillCaps.parseStrict(raw)
                ?: return BackupDecode.Rejected("CAPS 行格式不对：$raw")
        }
        caps.keys.firstOrNull { it !in billIds }?.let {
            return BackupDecode.Rejected("上限指向不存在的账单 $it")
        }
        val entryIds = HashSet<Long>()
        entries.forEach {
            if (!entryIds.add(it.id)) return BackupDecode.Rejected("条目 id 重复：${it.id}")
            if (it.amountCents < 0L) {
                return BackupDecode.Rejected("条目 ${it.id} 金额为负，方向本该记在账单上")
            }
            if (it.billId !in billIds) {
                return BackupDecode.Rejected("条目 ${it.id} 指向不存在的账单 ${it.billId}")
            }
        }

        return BackupDecode.Success(
            BackupData(
                schemaVersion = schema.toInt(),
                appTag = unescape(meta["APP"].orEmpty()).orEmpty(),
                createdAt = meta["CREATED"]?.toLongOrNull() ?: 0L,
                themeModeKey = unescape(meta["THEME"].orEmpty()).orEmpty(),
                // 可选行：老备份没写过 MONET，这里给空串而不是报错
                monetThemeKey = unescape(meta["MONET"].orEmpty()).orEmpty(),
                monthlyBudgetCents = meta["BUDGET"]?.toLongOrNull() ?: 0L,
                billCaps = caps,
                bills = bills,
                entries = entries,
            ),
        )
    }

    private sealed interface RowResult<out T> {
        data class Good<T>(val value: T) : RowResult<T>
        data class Bad(val reason: String) : RowResult<Nothing>
    }

    /** 与 [encode] 的列顺序严格对应（都不含行首的 B / E 标记）。 */
    private const val BILL_FIELDS = 13

    /** 格式 v2 的账单行没有回收站标记。 */
    private const val BILL_FIELDS_V2 = 12

    /** 格式 v1 的账单行没有周期那四项。 */
    private const val BILL_FIELDS_V1 = 8

    /** id 所属账单 金额 备注 归属时间 录入时间。 */
    private const val ENTRY_FIELDS = 7

    /** 格式 v1 的条目行没有末尾那一期。 */
    private const val ENTRY_FIELDS_V1 = 6

    /**
     * 每个格式版本对应的账单行字段数。加字段只往末尾加，
     * 所以老文件永远能按它自己那个长度读——不需要"迁移备份文件"这种多余动作。
     */
    private fun billFieldsFor(formatVersion: Int): Int = when {
        formatVersion >= 3 -> BILL_FIELDS
        formatVersion == 2 -> BILL_FIELDS_V2
        else -> BILL_FIELDS_V1
    }

    private fun parseBill(fields: List<String>, formatVersion: Int): RowResult<BackupBill> {
        val expected = billFieldsFor(formatVersion)
        if (fields.size != expected) {
            return RowResult.Bad("账单应有 $expected 个字段，实际 ${fields.size}")
        }
        val id = fields[0].toLongOrNull() ?: return RowResult.Bad("账单 id 不是数字")
        val name = unescape(fields[1]) ?: return RowResult.Bad("账单名里有非法转义")
        val iconKey = unescape(fields[2]) ?: return RowResult.Bad("图标键里有非法转义")
        val colorKey = unescape(fields[3]) ?: return RowResult.Bad("颜色键里有非法转义")
        val kindKey = unescape(fields[4]) ?: return RowResult.Bad("方向键里有非法转义")
        val sortOrder = fields[5].toIntOrNull() ?: return RowResult.Bad("排序号不是数字")
        val archived = when (unescape(fields[6])) {
            "0" -> false
            "1" -> true
            else -> return RowResult.Bad("归档标记只允许 0 或 1")
        }
        val createdAt = fields[7].toLongOrNull() ?: return RowResult.Bad("建档时间不是数字")
        if (formatVersion < 2) {
            return RowResult.Good(BackupBill(id, name, iconKey, colorKey, kindKey, sortOrder, archived, createdAt))
        }
        val repeatKey = unescape(fields[8]) ?: return RowResult.Bad("周期键里有非法转义")
        val repeatAmount = fields[9].toLongOrNull() ?: return RowResult.Bad("周期金额不是数字")
        val repeatAnchor = fields[10].toLongOrNull() ?: return RowResult.Bad("首次入账日不是数字")
        val repeatCursor = fields[11].toLongOrNull() ?: return RowResult.Bad("周期游标不是数字")
        if (repeatAmount < 0L) return RowResult.Bad("周期金额为负")
        // 空串表示"不在回收站"，不是 0 时刻
        val deletedText = if (formatVersion >= 3) {
            unescape(fields[12]) ?: return RowResult.Bad("回收站标记里有非法转义")
        } else {
            ""
        }
        val deletedAt = when {
            deletedText.isEmpty() -> null
            else -> deletedText.toLongOrNull() ?: return RowResult.Bad("回收站标记不是数字：$deletedText")
        }
        return RowResult.Good(
            BackupBill(
                id, name, iconKey, colorKey, kindKey, sortOrder, archived, createdAt,
                repeatKey, repeatAmount, repeatAnchor, repeatCursor, deletedAt,
            ),
        )
    }

    private fun parseEntry(fields: List<String>, formatVersion: Int): RowResult<BackupEntry> {
        val expected = if (formatVersion >= 2) ENTRY_FIELDS else ENTRY_FIELDS_V1
        if (fields.size != expected) {
            return RowResult.Bad("条目应有 $expected 个字段，实际 ${fields.size}")
        }
        val id = fields[0].toLongOrNull() ?: return RowResult.Bad("条目 id 不是数字")
        val billId = fields[1].toLongOrNull() ?: return RowResult.Bad("所属账单 id 不是数字")
        val amount = fields[2].toLongOrNull() ?: return RowResult.Bad("金额不是数字")
        val note = unescape(fields[3]) ?: return RowResult.Bad("备注里有非法转义")
        val occurredAt = fields[4].toLongOrNull() ?: return RowResult.Bad("归属时间不是数字")
        val createdAt = fields[5].toLongOrNull() ?: return RowResult.Bad("录入时间不是数字")
        if (formatVersion < 2) {
            return RowResult.Good(BackupEntry(id, billId, amount, note, occurredAt, createdAt))
        }
        // 空串表示"手工条目"，不是 0 期
        val autoText = unescape(fields[6]) ?: return RowResult.Bad("期次里有非法转义")
        val autoDay = when {
            autoText.isEmpty() -> null
            else -> autoText.toLongOrNull() ?: return RowResult.Bad("期次不是数字：$autoText")
        }
        return RowResult.Good(BackupEntry(id, billId, amount, note, occurredAt, createdAt, autoDay))
    }
}
