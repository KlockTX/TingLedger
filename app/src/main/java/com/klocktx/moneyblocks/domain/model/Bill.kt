package com.klocktx.moneyblocks.domain.model

import com.klocktx.moneyblocks.core.Cents
import com.klocktx.moneyblocks.core.cents

enum class BillPalette(val key: String, val label: String, val argb: Long) {
    GREEN("green", "苔绿", 0xFF5E8C6D),
    AMBER("amber", "赭石", 0xFFB0783F),
    BLUE("blue", "雾蓝", 0xFF4E7FA8),
    CORAL("coral", "陶红", 0xFFC0685C),
    VIOLET("violet", "藕紫", 0xFF8B74A8),
    TEAL("teal", "池青", 0xFF4E96A0),
    SLATE("slate", "石墨", 0xFF64757F),
    ROSE("rose", "玫灰", 0xFFB4607F);

    companion object {
        fun fromKey(key: String): BillPalette = entries.firstOrNull { it.key == key } ?: GREEN
    }
}

/**
 * 账单的资金方向。方向挂在账单上而不是单笔记录上：
 * 「吃饭」永远是支出、「工资」永远是收入，记一笔时不必再选一次。
 * 条目金额因此始终为正数，符号只在展示与汇总时按账单方向推导。
 */
enum class BillKind(val key: String, val label: String) {
    EXPENSE("expense", "支出"),
    INCOME("income", "收入");

    companion object {
        fun fromKey(key: String): BillKind = entries.firstOrNull { it.key == key } ?: EXPENSE
    }
}

/**
 * 账单的重复周期。到期自动落一笔固定金额，省掉"每月房租都要手记一次"。
 *
 * 存 key 而不是序号：和 [BillKind] 一样，手工修库时看得懂这一列是什么。
 */
enum class BillRepeat(val key: String, val label: String) {
    NONE("none", "不重复"),
    MONTHLY("monthly", "每月"),
    WEEKLY("weekly", "每周");

    companion object {
        fun fromKey(key: String): BillRepeat = entries.firstOrNull { it.key == key } ?: NONE
    }
}

data class Bill(
    val id: Long = 0,
    val name: String,
    val iconKey: String,
    val palette: BillPalette,
    val kind: BillKind = BillKind.EXPENSE,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    /** 重复周期。[BillRepeat.NONE] 时下面三个重复字段全部无意义。 */
    val repeat: BillRepeat = BillRepeat.NONE,
    /** 每期自动入账的金额（分）。0 视为"没设完"，不会生成。 */
    val repeatAmount: Cents = 0L.cents,
    /** 首次入账那天（epochDay）。由用户挑，所以"从哪个月开始算"是显式的，不靠猜。 */
    val repeatAnchorDay: Long = 0L,
    /** 已经生成到哪一期（epochDay）。删掉的自动条目不会被更早的期次再补回来。 */
    val repeatCursorDay: Long = 0L,
    /**
     * 回收站里的时刻（毫秒），null 表示没被删。
     *
     * 删除账单是这 App 里唯一不可逆的操作（条目对账单是 CASCADE），所以"删"先只做标记：
     * 名下历史原地不动，恢复就是清掉这个标记，不需要重放任何数据，也就不会对不上账。
     */
    val deletedAt: Long? = null,
) {
    val isIncome: Boolean get() = kind == BillKind.INCOME

    /** 规则是否完整可用。归档与否由查询侧筛，这里只管"这条规则本身成不成立"。 */
    val isRecurring: Boolean get() =
        repeat != BillRepeat.NONE && repeatAmount.value > 0L && repeatAnchorDay > 0L
}
