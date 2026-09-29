package com.klocktx.moneyblocks.domain.usecase

import com.klocktx.moneyblocks.core.cents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BillCapsTest {

    @Test
    fun encodeSortsAndSkipsUnset() {
        val encoded = BillCaps.encode(mapOf(3L to 300_00L.cents, 1L to 100_00L.cents, 2L to 0L.cents))
        // 排序是必需的：同一份内容必须永远写成同一个字符串，否则备份文件无法逐字节比对
        assertEquals("1=10000;3=30000", encoded)
    }

    @Test
    fun encodeOfNothingIsNothing() {
        assertEquals("", BillCaps.encode(emptyMap()))
        assertEquals("", BillCaps.encode(mapOf(0L to 100L.cents, -3L to 100L.cents)))
    }

    @Test
    fun roundTripIsStable() {
        val caps = mapOf(7L to 77_700L.cents, 9L to 1L.cents, 12L to 999_999_99L.cents)
        val once = BillCaps.decode(BillCaps.encode(caps))
        assertEquals(caps, once)
        assertEquals(BillCaps.encode(caps), BillCaps.encode(once))
    }

    @Test
    fun decodeSkipsMalformedPiecesInsteadOfLosingEverything() {
        val decoded = BillCaps.decode("1=100;垃圾;abc=9;2=notacents;3=300;4=-5;5=0")
        assertEquals(mapOf(1L to 100L.cents, 3L to 300L.cents), decoded)
    }

    @Test
    fun decodeOfBlankIsNoCaps() {
        assertEquals(emptyMap<Long, com.klocktx.moneyblocks.core.Cents>(), BillCaps.decode(""))
        assertEquals(emptyMap<Long, com.klocktx.moneyblocks.core.Cents>(), BillCaps.decode("   "))
    }

    @Test
    fun duplicateIdCollapsesToTheLaterOne() {
        // 真实写入永远经 encode 去重，出现重复说明文件被动过；语义上取后者，
        // 和 setBillCap「同一账单再写一次就是改它」保持一致。严格解析那条线由 parseStrict 管。
        assertEquals(mapOf(1L to 900L.cents), BillCaps.decode("1=100;1=900"))
    }

    @Test
    fun strictParsingRefusesWhatLenientWouldRepair() {
        assertEquals(2, BillCaps.parseStrict("1=100;2=200")?.size)
        assertNull(BillCaps.parseStrict("1=100;垃圾"))
        assertNull(BillCaps.parseStrict("1=100;1=200"))
        assertNull(BillCaps.parseStrict("1=0"))
        assertNull(BillCaps.parseStrict("-4=100"))
        assertNull(BillCaps.parseStrict("4"))
        assertEquals(emptyMap<Long, com.klocktx.moneyblocks.core.Cents>(), BillCaps.parseStrict(""))
    }

    @Test
    fun dropOnlyRemovesThatBill() {
        val caps = mapOf(1L to 100L.cents, 2L to 200L.cents)
        assertEquals(mapOf(2L to 200L.cents), BillCaps.drop(caps, 1L))
        assertEquals(caps, BillCaps.drop(caps, 99L))
    }

    @Test
    fun statusWithoutCapSaysNothing() {
        val status = CapStatus(cap = 0L.cents, spent = 500_00L.cents)
        assertFalse(status.isActive)
        assertFalse(status.isOver)
        assertEquals(0.0, status.ratio, 0.0)
        assertEquals(0f, status.progress, 0f)
        assertEquals(0L, status.remaining.value)
        assertEquals(0L, status.overBy.value)
        assertEquals(0L, CapStatus.inactive(500_00L.cents).cap.value)
    }

    @Test
    fun ratioIsTheRealMultipleWhileProgressIsClamped() {
        val over = CapStatus(cap = 100_00L.cents, spent = 250_00L.cents)
        assertEquals(2.5, over.ratio, 1e-9)
        // 进度条只能画到满，但"超了多少"必须还能说出口
        assertEquals(1f, over.progress, 1e-6f)
        assertTrue(over.isOver)
        assertEquals(0L, over.remaining.value)
        assertEquals(150_00L, over.overBy.value)
    }

    @Test
    fun exactlyAtTheCapIsNotOver() {
        val atCap = CapStatus(cap = 100_00L.cents, spent = 100_00L.cents)
        assertFalse("花到上限不等于超上限", atCap.isOver)
        assertEquals(0L, atCap.overBy.value)
        assertEquals(0L, atCap.remaining.value)
        assertEquals(1.0, atCap.ratio, 1e-9)
    }

    @Test
    fun underTheCapReportsWhatIsLeft() {
        val ok = CapStatus(cap = 1_000_00L.cents, spent = 400_00L.cents)
        assertFalse(ok.isOver)
        assertEquals(600_00L, ok.remaining.value)
        assertEquals(0f, ok.progress.let { it - 0.4f }, 1e-6f)
    }

    @Test
    fun spendingNothingStillShowsZeroProgress() {
        val idle = CapStatus(cap = 500_00L.cents, spent = 0L.cents)
        assertTrue(idle.isActive)
        assertEquals(0f, idle.progress, 0f)
        assertEquals(500_00L, idle.remaining.value)
    }
}
