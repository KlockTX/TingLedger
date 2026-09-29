package com.klocktx.moneyblocks.domain.usecase

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExportTest {

    private val utc = ZoneId.of("UTC")

    private fun row(
        amountCents: Long,
        note: String,
        bill: String = "吃饭",
        kind: String = "expense",
    ) = ExportRow(
        occurredAt = 1_700_000_000_000L,
        amountCents = amountCents,
        note = note,
        billName = bill,
        kind = kind,
    )

    @Test
    fun writesHeaderAndRows() {
        val csv = CsvExport.build(listOf(row(3800, "午饭"), row(1200, "地铁")), utc)
        val lines = csv.removePrefix("﻿").trim().split("\r\n")
        assertEquals(3, lines.size)
        assertEquals("日期,时间,账单,类型,金额,备注", lines[0])
        assertTrue(lines[1].endsWith("吃饭,支出,38.00,午饭"))
        assertTrue(lines[2].endsWith("吃饭,支出,12.00,地铁"))
    }

    @Test
    fun marksIncomeRows() {
        val csv = CsvExport.build(listOf(row(1_200_000L, "十月工资", "工资", "income")), utc)
        assertTrue(csv.contains("工资,收入,12000.00,十月工资"))
    }

    @Test
    fun writesCentsWithoutGroupingSeparators() {
        // 千分位逗号会让 Excel 把金额读成文本，导出必须是纯数字
        val csv = CsvExport.build(listOf(row(1_234_567_89L, "")), utc)
        assertTrue(csv.contains(",1234567.89,"))
    }

    @Test
    fun prefixesBomForExcel() {
        // Excel 无 BOM 会把中文账单名显示成乱码
        assertTrue(CsvExport.build(emptyList(), utc).startsWith("﻿"))
    }

    @Test
    fun quotesFieldsContainingSeparators() {
        val csv = CsvExport.build(listOf(row(100, "a,b")), utc)
        assertTrue(csv.contains("\"a,b\""))
    }

    @Test
    fun escapesEmbeddedQuotes() {
        val csv = CsvExport.build(listOf(row(100, "说\"好\"的")), utc)
        assertTrue(csv.contains("\"说\"\"好\"\"的\""))
    }

    @Test
    fun keepsNewlineInsideQuotes() {
        val csv = CsvExport.build(listOf(row(100, "第一行\n第二行")), utc)
        assertTrue(csv.contains("\"第一行\n第二行\""))
    }

    @Test
    fun formatsAmountsWithTwoDecimals() {
        val csv = CsvExport.build(listOf(row(5, "")), utc)
        // 5 分必须写成 0.05，而不是 0.5 或 5
        assertTrue(csv.contains(",0.05,"))
    }

    @Test
    fun emptyExportStillHasHeader() {
        val csv = CsvExport.build(emptyList(), utc)
        assertEquals("﻿日期,时间,账单,类型,金额,备注\r\n", csv)
    }
}
