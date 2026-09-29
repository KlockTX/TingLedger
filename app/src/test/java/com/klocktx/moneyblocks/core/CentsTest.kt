package com.klocktx.moneyblocks.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CentsTest {

    @Test
    fun formatsTwoDecimals() {
        assertEquals("0.00", 0L.cents.toYuanString())
        assertEquals("0.05", 5L.cents.toYuanString())
        assertEquals("1.00", 100L.cents.toYuanString())
        assertEquals("10.50", 1050L.cents.toYuanString())
    }

    @Test
    fun netStringPutsSignBeforeTheCurrencySymbol() {
        // 首页「本月变动」用这个：负号必须在 ¥ 前面，不能出现 ¥-380.00
        assertEquals("+¥1,200.00", 120000L.cents.toNetYuanString())
        assertEquals("-¥380.00", (-38000L).cents.toNetYuanString())
        assertEquals("¥0.00", 0L.cents.toNetYuanString())
    }

    @Test
    fun groupsThousands() {
        assertEquals("1,284.50", 128450L.cents.toYuanString())
        assertEquals("1,000.00", 100000L.cents.toYuanString())
        assertEquals("999.99", 99999L.cents.toYuanString())
        assertEquals("1,234,567.89", 123456789L.cents.toYuanString())
    }

    @Test
    fun keepsNegativeSign() {
        assertEquals("-38.00", (-3800L).cents.toYuanString())
    }

    @Test
    fun symbolPrefix() {
        assertEquals("¥1,284.50", 128450L.cents.toYuanWithSymbol())
    }

    @Test
    fun additionStaysExact() {
        // 分域整数相加，不存在浮点漂移
        assertEquals(30L, (10L.cents + 20L.cents).value)
        assertEquals(1000L, (333L.cents + 333L.cents + 334L.cents).value)
    }
}
