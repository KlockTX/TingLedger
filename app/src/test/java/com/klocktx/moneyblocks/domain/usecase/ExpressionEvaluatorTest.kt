package com.klocktx.moneyblocks.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressionEvaluatorTest {

    private fun cents(expression: String): Long {
        val outcome = ExpressionEvaluator.evaluate(expression)
        assertTrue("expected ok for <$expression> but got $outcome", outcome is CalcOutcome.Ok)
        return (outcome as CalcOutcome.Ok).cents
    }

    private fun errorOf(expression: String): CalcError {
        val outcome = ExpressionEvaluator.evaluate(expression)
        assertTrue("expected failure for <$expression> but got $outcome", outcome is CalcOutcome.Failed)
        return (outcome as CalcOutcome.Failed).error
    }

    @Test
    fun singleAmountBecomesCents() {
        assertEquals(3800L, cents("38"))
        assertEquals(4850L, cents("48.5"))
        assertEquals(123456L, cents("1234.56"))
    }

    @Test
    fun additionOfSeparateTickets() {
        assertEquals(3800L, cents("23+15"))
        assertEquals(3850L, cents("23+15.5"))
    }

    @Test
    fun subtractionForDiscount() {
        assertEquals(2000L, cents("23-3"))
    }

    @Test
    fun multiplicationForUnitPrice() {
        assertEquals(1350L, cents("4.5×3"))
        assertEquals(1350L, cents("3*4.5"))
    }

    @Test
    fun divisionSplitsShares() {
        // 100 元 3 人分摊：¥33.33，HALF_UP 只发生在最终落分
        assertEquals(3333L, cents("100÷3"))
        assertEquals(1667L, cents("100/6"))
    }

    @Test
    fun noFloatDriftOnDecimalSums() {
        // Double 会算出 0.30000000000000004
        assertEquals(30L, cents("0.1+0.2"))
        assertEquals(690L, cents("1.1+2.2+3.6"))
    }

    @Test
    fun operatorPrecedenceIsMathematical() {
        assertEquals(300L, cents("9-2×3"))
        assertEquals(2000L, cents("100÷(2+3)"))
        assertEquals(2500L, cents("(2+3)×5"))
        assertEquals(1400L, cents("2+3×4"))
    }

    @Test
    fun unaryMinusForReversal() {
        assertEquals(700L, cents("-3+10"))
        assertEquals(-700L, cents("3-10"))
    }

    @Test
    fun nestedParens() {
        assertEquals(900L, cents("(1+2)×(1+2)"))
        assertEquals(600L, cents("((3))×2"))
    }

    @Test
    fun roundingAtTwoDecimalsOnly() {
        // 0.005 元级别的中间结果按 HALF_UP 落到分
        assertEquals(1L, cents("0.005+0.005"))
        assertEquals(10L, cents("1÷10"))
    }

    @Test
    fun rejectsErrors() {
        assertEquals(CalcError.EMPTY, errorOf(""))
        assertEquals(CalcError.TRAILING_OPERATOR, errorOf("3+"))
        assertEquals(CalcError.DIVIDE_BY_ZERO, errorOf("3÷0"))
        assertEquals(CalcError.UNCLOSED_PAREN, errorOf("(3+2"))
        assertEquals(CalcError.DOUBLE_DOT, errorOf("1.2.3"))
        // 一元正号合法（3+(+2)），真正缺操作数的是运算符后既无数字也无线索
        assertEquals(CalcError.MISSING_OPERAND, errorOf("3+*2"))
        assertEquals(CalcError.OUT_OF_RANGE, errorOf("999999.99+999999.99"))
    }

    @Test
    fun previewReturnsNullForIncompleteInput() {
        assertEquals(null, ExpressionEvaluator.previewCents("3+"))
        assertEquals(300L, ExpressionEvaluator.previewCents("3"))
    }
}
