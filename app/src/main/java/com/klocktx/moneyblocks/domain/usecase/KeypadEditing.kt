package com.klocktx.moneyblocks.domain.usecase

/**
 * 键盘输入的合法前缀规则。界面据此置灰按钮，避免用户写完才发现表达式非法。
 */
object KeypadEditing {

    const val MAX_LENGTH = 32
    const val MAX_DECIMALS = 2

    val OPERATORS = listOf('+', '-', '×', '÷')

    /** 正在输入的那个数字。尾部已闭合（运算符、括号）时为空，避免小数点落到运算符之后。 */
    fun currentNumber(expression: String): String {
        val trimmed = expression.trimEnd()
        if (trimmed.isEmpty()) return ""
        val last = trimmed.last()
        if (last in OPERATORS || last == '(' || last == ')') return ""
        return trimmed.takeLastWhile { it.isDigit() || it == '.' }
    }

    fun lastChar(expression: String): Char? = expression.trimEnd().lastOrNull()

    fun endsWithOperator(expression: String): Boolean = lastChar(expression) in OPERATORS

    fun unmatchedOpenParens(expression: String): Int =
        expression.count { it == '(' } - expression.count { it == ')' }

    fun isFull(expression: String): Boolean = expression.length >= MAX_LENGTH

    fun appendDigit(expression: String, digit: Char): String {
        if (!isDigitEnabled(expression, digit)) return expression
        val number = currentNumber(expression)
        // 前导 0 不叠加：按 5 变成 5，再按 0 仍是 0（避免出现 007 这种歧义写法）
        if (number == "0") {
            return if (digit == '0') expression else expression.dropLast(1) + digit
        }
        return expression + digit
    }

    fun isDigitEnabled(expression: String, digit: Char): Boolean {
        if (isFull(expression)) return false
        val number = currentNumber(expression)
        if (number.isEmpty()) return true
        if (number.contains('.') && number.substringAfter('.').length >= MAX_DECIMALS) return false
        if (number.length >= 10) return false
        return true
    }

    fun appendDot(expression: String): String =
        if (isDotEnabled(expression)) expression + "." else expression

    fun isDotEnabled(expression: String): Boolean {
        if (isFull(expression)) return false
        val number = currentNumber(expression)
        return !number.contains('.')
    }

    /** 在表达式末尾按运算符：若已是运算符则替换，避免留下连续运算符。 */
    fun appendOperator(expression: String, operator: Char): String {
        if (!isOperatorEnabled(expression, operator)) return expression
        return if (endsWithOperator(expression.trimEnd()) && lastChar(expression) != '(') {
            expression.trimEnd().dropLast(1) + operator
        } else {
            expression + operator
        }
    }

    fun isOperatorEnabled(expression: String, operator: Char): Boolean {
        if (isFull(expression)) return false
        val last = lastChar(expression) ?: return operator == '-'
        return when {
            last == '(' -> operator == '-'
            last == ')' -> true
            last in OPERATORS -> true
            else -> true
        }
    }

    fun appendOpenParen(expression: String): String =
        if (isOpenParenEnabled(expression)) expression + "(" else expression

    fun isOpenParenEnabled(expression: String): Boolean {
        if (isFull(expression)) return false
        if (unmatchedOpenParens(expression) >= ExpressionEvaluator.MAX_DEPTH) return false
        val last = lastChar(expression) ?: return true
        return last == '(' || last in OPERATORS
    }

    fun appendCloseParen(expression: String): String =
        if (isCloseParenEnabled(expression)) expression + ")" else expression

    fun isCloseParenEnabled(expression: String): Boolean {
        if (isFull(expression)) return false
        if (unmatchedOpenParens(expression) <= 0) return false
        val last = lastChar(expression) ?: return false
        return last.isDigit() || last == ')'
    }

    fun backspace(expression: String): String {
        if (expression.isEmpty()) return expression
        val last = expression.last()
        return if (last == '.') expression.dropLast(currentNumber(expression).length) else expression.dropLast(1)
    }

    fun isValid(expression: String): Boolean {
        if (expression.isBlank()) return false
        return unmatchedOpenParens(expression) == 0 && !endsWithOperator(expression.trimEnd())
    }
}
