package com.klocktx.moneyblocks.domain.usecase

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

enum class CalcError(val message: String) {
    EMPTY("请输入金额"),
    TRAILING_OPERATOR("表达式未写完"),
    UNCLOSED_PAREN("括号未闭合"),
    EXTRA_PAREN("括号多余"),
    MISSING_OPERAND("缺少数字"),
    DOUBLE_DOT("小数点重复"),
    DIVIDE_BY_ZERO("不能除以 0"),
    TOO_DEEP("括号嵌套过多"),
    OUT_OF_RANGE("金额超出上限"),
    MALFORMED("表达式格式不正确"),
}

sealed interface CalcOutcome {
    data class Ok(val cents: Long) : CalcOutcome
    data class Failed(val error: CalcError) : CalcOutcome
}

/**
 * 四则运算求值：+ - × ÷ 与括号，按数学优先级。
 * 中间量全程 BigDecimal，避免 Double 在除法与小数上的精度漂移；只在最终落分时舍入一次。
 */
object ExpressionEvaluator {

    /** 单个数字与最终结果的上限：¥999,999.99。 */
    const val MAX_CENTS = 99_999_999L

    const val MAX_DEPTH = 8
    private val DIVISION_CONTEXT = MathContext(14, RoundingMode.HALF_UP)

    fun evaluate(rawExpression: String): CalcOutcome {
        val expression = rawExpression.trim()
        if (expression.isEmpty()) return CalcOutcome.Failed(CalcError.EMPTY)

        val parser = Parser(expression)
        val value = try {
            parser.parseExpression()
        } catch (error: CalcBreak) {
            return CalcOutcome.Failed(error.error)
        }

        if (!parser.atEnd()) return CalcOutcome.Failed(CalcError.MALFORMED)

        val cents = try {
            value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (error: ArithmeticException) {
            return CalcOutcome.Failed(CalcError.OUT_OF_RANGE)
        }
        if (cents > MAX_CENTS || cents < -MAX_CENTS) return CalcOutcome.Failed(CalcError.OUT_OF_RANGE)
        return CalcOutcome.Ok(cents)
    }

    /** 输入尚不完整时返回 null，让界面保留上一次合法预览而不是闪错误。 */
    fun previewCents(expression: String): Long? =
        (evaluate(expression) as? CalcOutcome.Ok)?.cents

    private class CalcBreak(val error: CalcError) : RuntimeException(error.name)

    private class Parser(private val src: String) {
        private var pos = 0
        private var depth = 0

        fun atEnd(): Boolean = pos >= src.length

        fun parseExpression(): BigDecimal {
            var result = parseTerm()
            while (true) {
                when (peek()) {
                    '+' -> { pos++; result = result.add(parseTerm()) }
                    '-', '−' -> { pos++; result = result.subtract(parseTerm()) }
                    else -> return result
                }
            }
        }

        private fun parseTerm(): BigDecimal {
            var result = parseFactor()
            while (true) {
                when (peek()) {
                    '×', '*', 'x', 'X' -> {
                        pos++
                        result = result.multiply(parseFactor())
                    }
                    '÷', '/' -> {
                        pos++
                        val divisor = parseFactor()
                        if (divisor.signum() == 0) throw CalcBreak(CalcError.DIVIDE_BY_ZERO)
                        result = result.divide(divisor, DIVISION_CONTEXT)
                    }
                    else -> return result
                }
            }
        }

        private fun parseFactor(): BigDecimal {
            when (peek()) {
                '-' -> { pos++; return parseFactor().negate() }
                '+' -> { pos++; return parseFactor() }
                '(' -> {
                    pos++
                    depth++
                    if (depth > MAX_DEPTH) throw CalcBreak(CalcError.TOO_DEEP)
                    val inner = parseExpression()
                    if (peek() != ')') throw CalcBreak(CalcError.UNCLOSED_PAREN)
                    pos++
                    depth--
                    return inner
                }
            }
            return parseNumber()
        }

        private fun parseNumber(): BigDecimal {
            val start = pos
            var sawDigit = false
            var dotCount = 0
            while (pos < src.length) {
                val ch = src[pos]
                when {
                    ch in '0'..'9' -> { sawDigit = true; pos++ }
                    ch == '.' -> { dotCount++; pos++ }
                    else -> break
                }
            }
            if (!sawDigit) {
                throw CalcBreak(if (pos >= src.length) CalcError.TRAILING_OPERATOR else CalcError.MISSING_OPERAND)
            }
            if (dotCount > 1) throw CalcBreak(CalcError.DOUBLE_DOT)

            val text = src.substring(start, pos)
            if (text.endsWith(".")) throw CalcBreak(CalcError.TRAILING_OPERATOR)
            val value = text.toBigDecimalOrNull() ?: throw CalcBreak(CalcError.MALFORMED)
            if (value.movePointRight(2).abs().compareTo(BigDecimal(MAX_CENTS)) > 0) {
                throw CalcBreak(CalcError.OUT_OF_RANGE)
            }
            return value
        }

        private fun peek(): Char? = src.getOrNull(pos)
    }
}
