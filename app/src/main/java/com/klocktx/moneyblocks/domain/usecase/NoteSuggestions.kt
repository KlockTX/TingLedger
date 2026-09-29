package com.klocktx.moneyblocks.domain.usecase

/**
 * 录入时的「常用备注」建议。
 *
 * 同一类账反复填同样的备注（地铁、午饭、房租），逐个打字很啰嗦；
 * 把这条账单最近用过的备注列成胶囊，点一下就填好。
 */
object NoteSuggestions {

    /** 一次取足够多的原始备注，再去空白、去重、限量。 */
    const val RAW_LIMIT = 30

    /**
     * 去重放在 trim 之后：「地铁」和「地铁 」在库里是两条不同文本，
     * 但作为建议出现两次只会让人觉得界面坏了。
     */
    fun from(rawNotes: List<String>, max: Int = 3): List<String> = rawNotes
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(max)
        .toList()
}
