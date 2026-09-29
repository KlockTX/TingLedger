package com.klocktx.moneyblocks.core

import com.klocktx.moneyblocks.domain.model.Bill
import com.klocktx.moneyblocks.domain.model.BillKind
import com.klocktx.moneyblocks.domain.model.BillPalette
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickShortcutsTest {

    private fun bill(
        id: Long,
        name: String,
        kind: BillKind = BillKind.EXPENSE,
        order: Int = id.toInt(),
        archived: Boolean = false,
    ) = Bill(
        id = id,
        name = name,
        iconKey = "restaurant",
        palette = BillPalette.GREEN,
        kind = kind,
        sortOrder = order,
        archived = archived,
    )

    @Test
    fun prefersExpensesBecauseThatIsWhatQuickRecordingIsFor() {
        val picked = QuickShortcuts.selection(
            listOf(bill(1, "工资", BillKind.INCOME), bill(2, "吃饭"), bill(3, "打车")),
            max = 2,
        )
        assertEquals(listOf("吃饭", "打车"), picked.map { it.name })
    }

    @Test
    fun skipsArchivedBills() {
        val picked = QuickShortcuts.selection(
            listOf(bill(1, "旧账单", archived = true), bill(2, "吃饭")),
        )
        assertEquals(listOf("吃饭"), picked.map { it.name })
    }

    @Test
    fun keepsUserOrderWithinEachDirection() {
        val picked = QuickShortcuts.selection(
            listOf(bill(3, "购物", order = 3), bill(1, "吃饭", order = 1), bill(2, "打车", order = 2)),
        )
        assertEquals(listOf("吃饭", "打车", "购物"), picked.map { it.name })
    }

    @Test
    fun capsAtTheLauncherLimit() {
        val many = (1L..10L).map { bill(it, "账单$it") }
        assertEquals(QuickShortcuts.MAX, QuickShortcuts.selection(many).size)
    }
}
