package com.klocktx.moneyblocks.domain.usecase

import com.klocktx.moneyblocks.domain.model.SearchFilter
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * 搜索关键词 → SQL 查询参数。放在 domain 层是因为转义规则最容易写错，
 * 需要能脱离 Android 直接单测。
 */
object SearchQuery {

    private const val ESCAPE = "\\"

    /** 包含匹配。用户输入的 % 和下划线必须转义，否则会被当成 LIKE 通配符。 */
    fun like(keyword: String): String =
        "%" + keyword.replace(ESCAPE, ESCAPE + ESCAPE)
            .replace("%", ESCAPE + "%")
            .replace("_", ESCAPE + "_") + "%"

    /** 「38.50」这类纯数字关键词额外按精确金额匹配，返回分的字符串；非数字返回空串。 */
    fun exactCents(keyword: String): String {
        val value = keyword.trim().toBigDecimalOrNull() ?: return ""
        return try {
            val cents = value.movePointRight(2).setScale(0, RoundingMode.HALF_UP)
            if (cents.abs() > BigDecimal(9_999_999_99L)) "" else cents.toPlainString()
        } catch (error: ArithmeticException) {
            ""
        }
    }

    /**
     * 一次查询要绑的全部参数。
     *
     * 单独拎出来是为了能测：SQL 里那些"空串/0 表示不限"的哨兵值，写错一个就是
     * 整类结果凭空消失，而它在界面上看起来完全正常（因为默认就是不限）。
     */
    data class Params(
        val hasText: Int,
        val notePattern: String,
        val billPattern: String,
        val exactCents: String,
        val kind: String,
        val minCents: Long,
        val start: Long,
        val endExclusive: Long,
    )

    /**
     * 关键词 + 筛选 → 查询参数。哨兵约定：
     * `hasText = 0` 时不看关键词；`kind = ""` 时不限方向；
     * `minCents = 0` 时不限金额；`endExclusive = Long.MAX_VALUE` 时不限时间。
     */
    fun params(keyword: String, filter: SearchFilter, today: LocalDate): Params {
        val trimmed = keyword.trim()
        val range = filter.rangeFor(today)
        return Params(
            hasText = if (trimmed.isEmpty()) 0 else 1,
            notePattern = like(trimmed),
            billPattern = like(trimmed),
            exactCents = exactCents(trimmed),
            kind = filter.kind?.key.orEmpty(),
            minCents = filter.floor.cents,
            start = range?.start ?: 0L,
            // 哨兵：不限时间时给 Long.MAX_VALUE，SQL 里那句 `occurredAt < :endExclusive` 恒真。
            // 用 -1 当"不限"也行，但要靠注释维持；MAX_VALUE 是**结构上不可能被早于它的时刻排除**，
            // 少一个能被写错的约定。
            endExclusive = range?.endExclusive ?: Long.MAX_VALUE,
        )
    }
}
