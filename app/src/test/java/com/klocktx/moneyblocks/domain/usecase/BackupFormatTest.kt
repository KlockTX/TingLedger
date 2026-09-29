package com.klocktx.moneyblocks.domain.usecase

import com.klocktx.moneyblocks.core.Cents
import com.klocktx.moneyblocks.core.cents
import com.klocktx.moneyblocks.domain.model.BillRepeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份格式的编解码与拒收规则。
 *
 * 这份文件是用户账本的唯一副本，所以两类事必须钉住：
 * 一是**恢复回去必须一模一样**（备注里的制表符、换行、反斜杠都不能变形），
 * 二是**坏文件必须整份拒收**（截断、篡改、版本过新、外键悬空），
 * 绝不允许"能恢复多少算多少"——那会静悄悄丢账。
 */
class BackupFormatTest {

    private val bills = listOf(
        BackupBill(1, "吃饭", "restaurant", "green", "expense", 0, false, 1_700_000_000_000L),
        BackupBill(
            2, "工资", "money", "teal", "income", 1, false, 1_700_000_000_001L,
            repeatKey = "monthly", repeatAmountCents = 880_000L,
            repeatAnchorDay = 20_645L, repeatCursorDay = 20_675L,
        ),
        BackupBill(
            3, "已归档的", "box", "slate", "expense", 2, true, 1_700_000_000_002L,
            deletedAt = 1_700_000_500_000L,
        ),
    )

    private val entries = listOf(
        BackupEntry(10, 1, 2_300L, "午饭", 1_700_000_100_000L, 1_700_000_100_000L),
        BackupEntry(11, 1, 15_000L, "请客\t加啤酒", 1_700_000_200_000L, 1_700_000_200_000L),
        BackupEntry(12, 2, 880_000L, "8月\n含税", 1_700_000_300_000L, 1_700_000_300_000L, autoDay = 20_645L),
        BackupEntry(13, 3, 1L, "反斜杠 \\ 与字面 \\n", 1_700_000_400_000L, 1_700_000_400_000L),
        BackupEntry(14, 1, 0L, "", 1_700_000_500_000L, 1_700_000_500_000L),
    )

    private fun sample() = BackupData(
        schemaVersion = 4,
        appTag = "1.19.0(25)",
        createdAt = 1_700_000_600_000L,
        themeModeKey = "system",
        monthlyBudgetCents = 300_000L,
        bills = bills,
        entries = entries,
    )

    private fun decoded(text: String, supported: Int = 4): BackupDecode =
        BackupFormat.decode(text, supported)

    private fun reason(text: String, supported: Int = 4): String =
        (decoded(text, supported) as BackupDecode.Rejected).reason

    @Test
    fun roundTripKeepsEveryField() {
        val data = sample()
        val parsed = decoded(BackupFormat.encode(data))
        assertTrue(parsed.toString(), parsed is BackupDecode.Success)
        assertEquals(data, (parsed as BackupDecode.Success).data)
    }

    /** v1.17 之前的备份没有 MONET 行，必须照样能读，且不能把用户的主题选择抹掉。 */
    @Test
    fun oldFileWithoutThemeLineStillDecodes() {
        val data = sample().copy(monetThemeKey = "waterlily")
        val without = BackupFormat.encode(data).lines()
            .filterNot { it.startsWith("MONET") }
            .joinToString("\n")
        val parsed = decoded(without)
        assertTrue(parsed.toString(), parsed is BackupDecode.Success)
        assertEquals("", (parsed as BackupDecode.Success).data.monetThemeKey)
        assertEquals(data.bills, parsed.data.bills)
        assertEquals(data.entries, parsed.data.entries)
    }

    @Test
    fun themeKeySurvivesRoundTrip() {
        val data = sample().copy(monetThemeKey = "rouen-dusk")
        val back = decoded(BackupFormat.encode(data))
        assertEquals("rouen-dusk", (back as BackupDecode.Success).data.monetThemeKey)
    }

    @Test
    fun roundTripOfAnEmptyLedger() {
        val data = sample().copy(bills = emptyList(), entries = emptyList())
        assertEquals(data, (decoded(BackupFormat.encode(data)) as BackupDecode.Success).data)
    }

    @Test
    fun literalBackslashNSurvivesAsTextNotNewline() {
        // 备注里真写了「\n」两个字符，解码后不能变成换行——这是转义最容易翻车的一处
        val note = "写个 \\n 看看"
        val data = sample().copy(entries = listOf(BackupEntry(1, 1, 100L, note, 0L, 0L)))
        val back = (decoded(BackupFormat.encode(data)) as BackupDecode.Success).data
        assertEquals(note, back.entries.single().note)
    }

    @Test
    fun missingEndLineIsRejectedAsTruncation() {
        val text = BackupFormat.encode(sample())
        val cut = text.substringBeforeLast("#END")
        assertTrue(reason(cut), reason(cut).contains("结尾"))
    }

    @Test
    fun halfWrittenFileIsRejected() {
        // 写到一半断掉（选中的存储提供方中断、云盘同步没落盘）：任何一处截断都不能被接受，
        // 具体报哪一句不重要，重要的是绝不能 Success 之后按半份数据恢复。
        val text = BackupFormat.encode(sample())
        for (stop in listOf(0.1, 0.3, 0.6, 0.9)) {
            val cut = text.substring(0, (text.length * stop).toInt())
            val outcome = decoded(cut)
            assertTrue("截到 ${(stop * 100).toInt()}% 竟然通过了：$outcome", outcome is BackupDecode.Rejected)
        }
    }

    @Test
    fun tamperedSumIsRejected() {
        val text = BackupFormat.encode(sample()).replace("SUM\t", "SUM\t1")
        assertTrue(reason(text), reason(text).contains("对不上"))
    }

    @Test
    fun tamperedEndLineIsRejected() {
        val sum = sample().amountSum
        val tampered = BackupFormat.encode(sample()).replace("sum=$sum", "sum=${sum + 1}")
        val reason = reason(tampered)
        assertTrue(reason, reason.contains("不一致"))
    }

    @Test
    fun entryCountMismatchIsRejected() {
        val text = BackupFormat.encode(sample()).replace("ENTRIES\t5", "ENTRIES\t6")
        assertTrue(reason(text), reason(text).contains("条目行数"))
    }

    @Test
    fun foreignFormatTagIsRejected() {
        // 不写死 "tingxuan-backup/1"：格式版本会往前走，写死的替换会静悄悄不生效，测试就白过了
        val text = BackupFormat.encode(sample()).replace(
            "FORMAT\t${BackupFormat.FILE_TAG}/${BackupFormat.FORMAT_VERSION}",
            "FORMAT\tsomebody-else/9",
        )
        assertTrue(reason(text), reason(text).contains("备份"))
    }

    @Test
    fun futureFormatVersionIsRejected() {
        val text = BackupFormat.encode(sample()).replace(
            "FORMAT\t${BackupFormat.FILE_TAG}/${BackupFormat.FORMAT_VERSION}",
            "FORMAT\t${BackupFormat.FILE_TAG}/${BackupFormat.FORMAT_VERSION + 1}",
        )
        assertTrue(reason(text), reason(text).contains("格式"))
    }

    /** 格式 v1 的账单行只有 8 个字段、条目行只有 6 个：老备份必须照样能恢复。 */
    @Test
    fun v1FileStillDecodesWithDefaults() {
        val v1 = buildString {
            append("FORMAT\t").append(BackupFormat.FILE_TAG).append("/1\n")
            append("SCHEMA\t2\n")
            append("APP\told\n")
            append("CREATED\t1700000600000\n")
            append("BILLS\t1\n")
            append("ENTRIES\t1\n")
            append("SUM\t2300\n")
            append("THEME\tsystem\n")
            append("BUDGET\t0\n")
            append("B\t1\t吃饭\trestaurant\tgreen\texpense\t0\t0\t1700000000000\n")
            append("E\t10\t1\t2300\t午饭\t1700000100000\t1700000100000\n")
            append("#END\tbills=1\tentries=1\tsum=2300\n")
        }
        val data = (BackupFormat.decode(v1, supportedSchema = 3) as? BackupDecode.Success)?.data
        assertNotNull("v1 备份应当能读：${(BackupFormat.decode(v1, 3) as? BackupDecode.Rejected)?.reason}", data)
        assertEquals(BillRepeat.NONE.key, data!!.bills.single().repeatKey)
        assertEquals(0L, data.bills.single().repeatCursorDay)
        assertNull(data.entries.single().autoDay)
    }

    /** 格式 v2 有周期四项、没有回收站标记：照样能读，恢复出来就是"没被删过"。 */
    @Test
    fun v2FileStillDecodesWithoutTrashMark() {
        val v2 = buildString {
            append("FORMAT\t").append(BackupFormat.FILE_TAG).append("/2\n")
            append("SCHEMA\t3\n")
            append("APP\told\n")
            append("CREATED\t1700000600000\n")
            append("BILLS\t1\n")
            append("ENTRIES\t1\n")
            append("SUM\t2300\n")
            append("THEME\tsystem\n")
            append("MONET\twaterlily\n")
            append("BUDGET\t0\n")
            append(
                "B\t1\t房租\thome\tamber\texpense\t0\t0\t1700000000000\t" +
                    "monthly\t120000\t20645\t20675\n",
            )
            append("E\t10\t1\t2300\t午饭\t1700000100000\t1700000100000\t20645\n")
            append("#END\tbills=1\tentries=1\tsum=2300\n")
        }
        val data = (BackupFormat.decode(v2, supportedSchema = 4) as? BackupDecode.Success)?.data
        assertNotNull("v2 备份应当能读", data)
        val bill = data!!.bills.single()
        assertEquals("monthly", bill.repeatKey)
        assertEquals(20_675L, bill.repeatCursorDay)
        assertNull("v2 没有这一项，读出来就是没被删过", bill.deletedAt)
        assertEquals(20_645L, data.entries.single().autoDay)
    }

    /** 回收站状态要跟着备份走：否则一份带着回收站的备份恢复出来会多出几个活账单。 */
    @Test
    fun trashMarkSurvivesRoundTrip() {
        val decoded = (BackupFormat.decode(BackupFormat.encode(sample()), 4) as BackupDecode.Success).data
        assertEquals(sample().bills, decoded.bills)
        assertEquals(1_700_000_500_000L, decoded.bills.first { it.id == 3L }.deletedAt)
        assertNull(decoded.bills.first { it.id == 1L }.deletedAt)
    }

    /** 周期规则和"哪一期"都得原样回来，否则恢复之后会凭空多一笔房租。 */
    @Test
    fun repeatRuleAndAutoPeriodSurviveRoundTrip() {
        val decoded = (decoded(BackupFormat.encode(sample())) as BackupDecode.Success).data
        assertEquals(sample().bills, decoded.bills)
        assertEquals(sample().entries, decoded.entries)
        val rent = decoded.bills.first { it.id == 2L }
        assertEquals("monthly", rent.repeatKey)
        assertEquals(880_000L, rent.repeatAmountCents)
        assertEquals(20_675L, rent.repeatCursorDay)
        assertEquals(20_645L, decoded.entries.first { it.id == 12L }.autoDay)
        assertNull(decoded.entries.first { it.id == 10L }.autoDay)
    }

    @Test
    fun unknownRepeatKeyIsRejected() {
        val text = BackupFormat.encode(sample()).replace("\tmonthly\t", "\tyearly\t")
        assertTrue(reason(text), reason(text).contains("周期"))
    }

    @Test
    fun missingFormatTagIsRejected() {
        val text = BackupFormat.encode(sample()).substringAfter("SCHEMA")
        assertTrue(reason("SCHEMA$text"), reason("SCHEMA$text").contains("FORMAT"))
    }

    @Test
    fun backupFromNewerSchemaIsRejected() {
        val text = BackupFormat.encode(sample())
        val reason = reason(text, supported = 1)
        assertTrue(reason, reason.contains("更新"))
    }

    @Test
    fun entryPointingAtMissingBillIsRejected() {
        val data = sample().copy(entries = entries + BackupEntry(99, 404, 100L, "孤儿", 0L, 0L))
        val reason = reason(BackupFormat.encode(data))
        assertTrue(reason, reason.contains("不存在"))
    }

    @Test
    fun unknownDirectionKeyIsRejectedInsteadOfSilentlyBecomingExpense() {
        // 工资这类收入若被兜底成支出，账本会静悄悄错掉，比直接报错恶劣
        val data = sample().copy(
            bills = bills + BackupBill(50, "奖金", "money", "teal", "income_v2", 9, false, 0L),
        )
        val reason = reason(BackupFormat.encode(data))
        assertTrue(reason, reason.contains("方向"))
    }

    @Test
    fun duplicateIdsAreRejected() {
        val dupBill = reason(BackupFormat.encode(sample().copy(bills = bills + bills.first().copy(name = "重复的"))))
        assertTrue(dupBill, dupBill.contains("重复"))
        val dupEntry = reason(BackupFormat.encode(sample().copy(entries = entries + entries.first().copy(billId = 2))))
        assertTrue(dupEntry, dupEntry.contains("重复"))
    }

    @Test
    fun negativeAmountIsRejected() {
        val data = sample().copy(entries = listOf(BackupEntry(1, 1, -100L, "退款?", 0L, 0L)))
        assertTrue(reason(BackupFormat.encode(data)).contains("负"))
    }

    @Test
    fun brokenFieldCountIsRejected() {
        val text = BackupFormat.encode(sample())
        val withShortRow = text.lines().map {
            if (it.startsWith("E\t10\t")) "E\t10\t1\t2300" else it
        }.joinToString("\n")
        assertTrue(reason(withShortRow), reason(withShortRow).contains("字段"))
    }

    @Test
    fun unparsableEscapeIsRejected() {
        val text = BackupFormat.encode(sample()).replace("午饭", "午饭\\q")
        assertTrue(reason(text).contains("转义"))
    }

    @Test
    fun windowsLineEndingsStillDecode() {
        val crlf = BackupFormat.encode(sample()).replace("\n", "\r\n")
        assertEquals(sample(), (decoded(crlf) as BackupDecode.Success).data)
    }

    @Test
    fun blankFileIsRejected() {
        assertTrue(reason("   ").contains("空"))
    }

    @Test
    fun staleAgeIsMeasuredInWholeDays() {
        val day = 86_400_000L
        val now = 1_700_000_000_000L
        assertEquals(null, BackupFormat.daysSince(0L, now))
        assertEquals(29L, BackupFormat.daysSince(now - 29 * day - 1, now))
        assertEquals(30L, BackupFormat.daysSince(now - 30 * day, now))
        assertTrue(BackupFormat.isStale(0L, now))
        assertTrue(!BackupFormat.isStale(now - 29 * day, now))
        assertTrue(BackupFormat.isStale(now - 30 * day, now))
        // 时间被改过（备份时间在未来）也要提醒，别显示一个负数天数
        assertTrue(BackupFormat.isStale(now + day, now))
    }

    /** 上限要进备份：否则恢复账本之后所有账单悄悄变回"不限"。 */
    @Test
    fun capsRoundTrip() {
        val withCaps = sample().copy(billCaps = mapOf(1L to 230_00L.cents, 2L to 5_000L.cents))
        val back = decoded(BackupFormat.encode(withCaps))
        assertEquals(withCaps, (back as BackupDecode.Success).data)
    }

    /** v3 及更早的文件没有 CAPS 行：读成空表，而不是拿本机现值当默认。 */
    @Test
    fun v3FileStillDecodesWithoutCaps() {
        val v3 = BackupFormat.encode(sample())
            .split('\n')
            .filterNot { it.startsWith("CAPS\t") }
            .joinToString("\n")
            .replace("FORMAT\t${BackupFormat.FILE_TAG}/4", "FORMAT\t${BackupFormat.FILE_TAG}/3")
        val data = decoded(v3)
        val ok = data as? BackupDecode.Success
        assertNotNull("v3 备份应当能读：${(data as? BackupDecode.Rejected)?.reason}", ok)
        assertEquals(emptyMap<Long, Cents>(), ok!!.data.billCaps)
        assertEquals(sample().monthlyBudgetCents, ok.data.monthlyBudgetCents)
    }

    /** CAPS 行被改坏时整份拒收——"能读多少算多少"在恢复这件事上是错的。 */
    @Test
    fun corruptCapsLineIsRejected() {
        val text = BackupFormat.encode(sample().copy(billCaps = mapOf(1L to 100L.cents)))
            .replace("CAPS\t1=100", "CAPS\t1=100;瞎写的")
        assertTrue(reason(text), reason(text).contains("CAPS"))
    }

    @Test
    fun capPointingAtAMissingBillIsRejected() {
        val text = BackupFormat.encode(sample())
            .replace(Regex("CAPS\t[^\n]*"), "CAPS\t999=100")
        assertTrue(reason(text), reason(text).contains("不存在的账单"))
    }
}
