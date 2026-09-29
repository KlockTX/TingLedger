package com.klocktx.moneyblocks.domain.usecase

import com.klocktx.moneyblocks.domain.model.BillKind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ExportRow(
    val occurredAt: Long,
    val amountCents: Long,
    val note: String,
    val billName: String,
    val kind: String,
)

/**
 * 导出 CSV。纯函数、可单测——分隔符转义这类东西最容易在真实数据里炸。
 */
object CsvExport {

    private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    val header = listOf("日期", "时间", "账单", "类型", "金额", "备注")

    fun build(rows: List<ExportRow>, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val lines = StringBuilder()
        // Excel 打开 UTF-8 CSV 需要 BOM，否则中文账单名会乱码
        lines.append('﻿')
        lines.append(header.joinToString(",") { escape(it) })
        lines.append("\r\n")
        rows.forEach { row ->
            val instant = Instant.ofEpochMilli(row.occurredAt).atZone(zoneId)
            lines.append(
                listOf(
                    dateFormat.format(instant),
                    timeFormat.format(instant),
                    row.billName,
                    BillKind.fromKey(row.kind).label,
                    yuanOf(row.amountCents),
                    row.note,
                ).joinToString(",") { escape(it) },
            )
            lines.append("\r\n")
        }
        return lines.toString()
    }

    /**
     * 分 → 元，整数运算。
     * 类型列单独给出收支方向、金额列保持无千分位：带逗号的数字在表格里会被当成文本，
     * 用方向列做筛选也比读正负号省事。
     */
    private fun yuanOf(cents: Long): String {
        val abs = if (cents < 0) -cents else cents
        return "${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    }

    private fun escape(field: String): String {
        val needsQuote = field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuote) "\"" + field.replace("\"", "\"\"") + "\"" else field
    }
}
