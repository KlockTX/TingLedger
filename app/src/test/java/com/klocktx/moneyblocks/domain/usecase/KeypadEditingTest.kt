package com.klocktx.moneyblocks.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeypadEditingTest {

    @Test
    fun leadingZeroIsReplacedByDigit() {
        assertEquals("5", KeypadEditing.appendDigit("0", '5'))
        assertEquals("0", KeypadEditing.appendDigit("0", '0'))
        assertEquals("0.05", KeypadEditing.appendDigit("0.0", '5'))
    }

    @Test
    fun currentNumberTracksLastOperand() {
        assertEquals("3.5", KeypadEditing.currentNumber("12+3.5"))
        assertEquals("", KeypadEditing.currentNumber("12+"))
        // 括号已闭合，这个因子算完了，正在输入的数字为空
        assertEquals("", KeypadEditing.currentNumber("(1+2)"))
        assertEquals("3.", KeypadEditing.currentNumber("12+3."))
    }

    @Test
    fun decimalsCappedAtTwo() {
        assertTrue(KeypadEditing.isDigitEnabled("1.2", '3'))
        assertFalse(KeypadEditing.isDigitEnabled("1.23", '4'))
    }

    @Test
    fun onlyOneDotPerOperand() {
        assertTrue(KeypadEditing.isDotEnabled("12"))
        assertFalse(KeypadEditing.isDotEnabled("1.2"))
        assertEquals("12.", KeypadEditing.appendDot("12"))
        assertEquals("1.2", KeypadEditing.appendDot("1.2"))
    }

    @Test
    fun operatorReplacesTrailingOperator() {
        assertEquals("3×", KeypadEditing.appendOperator("3+", '×'))
        assertEquals("3÷", KeypadEditing.appendOperator("3-", '÷'))
        assertEquals("3+", KeypadEditing.appendOperator("3", '+'))
    }

    @Test
    fun negativeStartAllowedButNotMultiplication() {
        assertTrue(KeypadEditing.isOperatorEnabled("", '-'))
        assertFalse(KeypadEditing.isOperatorEnabled("", '×'))
        assertEquals("-", KeypadEditing.appendOperator("", '-'))
        assertEquals("", KeypadEditing.appendOperator("", '×'))
    }

    @Test
    fun parenRules() {
        assertTrue(KeypadEditing.isOpenParenEnabled(""))
        assertFalse(KeypadEditing.isOpenParenEnabled("3"))
        assertTrue(KeypadEditing.isOpenParenEnabled("3+"))
        assertFalse(KeypadEditing.isCloseParenEnabled(""))
        assertTrue(KeypadEditing.isCloseParenEnabled("(3"))
        assertFalse(KeypadEditing.isCloseParenEnabled("(3+"))
        assertEquals("(3)", KeypadEditing.appendCloseParen("(3"))
        // 空括号会被求值器判为缺少操作数，键盘直接不给按
        assertEquals("(", KeypadEditing.appendCloseParen("("))
    }

    @Test
    fun backspaceRemovesWholePartialOperand() {
        assertEquals("12+", KeypadEditing.backspace("12+3."))
        assertEquals("12+", KeypadEditing.backspace("12+3"))
        assertEquals("", KeypadEditing.backspace("3"))
        assertEquals("", KeypadEditing.backspace(""))
    }

    @Test
    fun validOnlyWhenComplete() {
        assertFalse(KeypadEditing.isValid(""))
        assertFalse(KeypadEditing.isValid("3+"))
        assertFalse(KeypadEditing.isValid("(3+2"))
        assertTrue(KeypadEditing.isValid("3+2"))
        assertTrue(KeypadEditing.isValid("(3+2)×5"))
    }

    @Test
    fun rejectsInputBeyondMaxLength() {
        val full = "1".repeat(KeypadEditing.MAX_LENGTH)
        assertTrue(KeypadEditing.isFull(full))
        assertEquals(full, KeypadEditing.appendDigit(full, '2'))
    }

    @Test
    fun unmatchedParensCount() {
        // 两个左括号一个右括号，未闭合的是 1 个
        assertEquals(1, KeypadEditing.unmatchedOpenParens("((1+2)"))
        assertEquals(2, KeypadEditing.unmatchedOpenParens("((1+2"))
        assertEquals(0, KeypadEditing.unmatchedOpenParens("(1+2)"))
    }
}
