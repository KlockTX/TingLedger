package com.klocktx.moneyblocks.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteSuggestionsTest {

    @Test
    fun keepsMostRecentFirstAndDropsDuplicates() {
        val notes = listOf("地铁", "午饭", "地铁", "房租")
        assertEquals(listOf("地铁", "午饭", "房租"), NoteSuggestions.from(notes, max = 3))
    }

    @Test
    fun trimsBeforeDeduplicating() {
        // 「地铁」和「地铁 」在库里是两条，作为建议出现两次只会显得界面坏了
        val notes = listOf("地铁 ", " 地铁", "地铁")
        assertEquals(listOf("地铁"), NoteSuggestions.from(notes))
    }

    @Test
    fun skipsBlankNotes() {
        assertEquals(listOf("打车"), NoteSuggestions.from(listOf("", "   ", "打车")))
    }

    @Test
    fun capsAtMaxAndHandlesEmptyInput() {
        assertEquals(2, NoteSuggestions.from(listOf("a", "b", "c", "a"), max = 2).size)
        assertEquals(emptyList<String>(), NoteSuggestions.from(emptyList()))
    }
}
