package com.klocktx.moneyblocks.core

/** 金额一律以「分」为单位存储与运算，全链路不允许 Double 参与记账金额计算。 */
@JvmInline
value class Cents(val value: Long) : Comparable<Cents> {
    override fun compareTo(other: Cents): Int = value.compareTo(other.value)

    operator fun plus(other: Cents): Cents = Cents(value + other.value)

    operator fun minus(other: Cents): Cents = Cents(value - other.value)

    fun isNegative(): Boolean = value < 0
}

val Long.cents: Cents get() = Cents(this)

fun Cents.toYuanString(): String {
    val negative = value < 0
    val abs = if (negative) -value else value
    val yuan = abs / 100
    val fraction = abs % 100

    val digits = yuan.toString()
    val grouped = StringBuilder()
    for ((index, ch) in digits.withIndex()) {
        if (index > 0 && (digits.length - index) % 3 == 0) grouped.append(',')
        grouped.append(ch)
    }
    grouped.append('.').append(if (fraction < 10) "0$fraction" else fraction.toString())
    return if (negative) "-$grouped" else grouped.toString()
}

fun Cents.toYuanWithSymbol(): String = "¥${toYuanString()}"

/**
 * 按账单方向加符号。条目金额始终存正数，符号只在展示时推导，
 * 这样「分」存储和四则运算都不用处理负数。
 */
fun Cents.toSignedYuanString(income: Boolean): String =
    if (income) "+${toYuanString()}" else toYuanString()

fun Cents.toSignedYuanWithSymbol(income: Boolean): String =
    if (income) "+${toYuanWithSymbol()}" else toYuanWithSymbol()

/**
 * 变动额（收入减支出）专用：正数加 +，负数把负号放到货币符前面。
 * 直接用 toYuanWithSymbol 会得到「¥-380.00」，读起来像货币符参与了运算。
 */
fun Cents.toNetYuanString(): String = when {
    value > 0L -> "+¥${toYuanString()}"
    value < 0L -> "-¥${(-value).cents.toYuanString()}"
    else -> "¥0.00"
}

/** 仅供图表绘制等展示换算使用，不参与任何金额累加。 */
fun Cents.asYuanForChart(): Double = value / 100.0
