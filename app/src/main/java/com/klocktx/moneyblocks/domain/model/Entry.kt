package com.klocktx.moneyblocks.domain.model

import com.klocktx.moneyblocks.core.Cents

data class Entry(
    val id: Long = 0,
    val billId: Long,
    val amount: Cents,
    val note: String = "",
    /** 记账归属时间，允许补记往日；与录入时间分离。 */
    val occurredAt: Long,
    /**
     * 由周期规则生成时记下它属于哪一期（epochDay），手工记的为 null。
     * 除了给界面打「自动」标记，它还是 (billId, autoDay) 唯一索引的列——
     * SQLite 的唯一索引不约束 NULL，所以手工条目永远不会互相冲突。
     */
    val autoDay: Long? = null,
)
