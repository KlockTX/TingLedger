package com.klocktx.moneyblocks.domain.usecase

import com.klocktx.moneyblocks.core.Cents
import com.klocktx.moneyblocks.core.cents

/**
 * 某个账单本月的上限与已花的对比。
 *
 * 比例不给上限：`ratio` 是真实倍数（超一倍就该说超一倍），界面画进度条时才用
 * [progress] 夹到 0..1——把夹取放进数值里，"超了多少"这句话就说不出来了。
 */
data class CapStatus(
    val cap: Cents,
    val spent: Cents,
) {
    /** 没设上限＝这一行不该出现任何预算语言。0 不是"上限为零"，是"没说"。 */
    val isActive: Boolean get() = cap.value > 0L

    val ratio: Double
        get() = if (!isActive) 0.0 else spent.value.toDouble() / cap.value.toDouble()

    val progress: Float
        get() = if (!isActive) 0f else ratio.coerceIn(0.0, 1.0).toFloat()

    val isOver: Boolean get() = isActive && spent.value > cap.value

    /** 还剩多少可用；没设上限或已超则为 0。 */
    val remaining: Cents
        get() = if (!isActive) 0L.cents else (cap.value - spent.value).coerceAtLeast(0L).cents

    /** 超了多少；没设上限或没超则为 0。和 remaining 分成两个而不是给一个带符号的数，
     *  是为了让界面不必判断正负就能拿到该显示的那句话。
     *  两边都必须先看 isActive：没设上限时"超出 ¥500"这句话是假的，第一次跑测试就抓到了。 */
    val overBy: Cents
        get() = if (!isActive) 0L.cents else (spent.value - cap.value).coerceAtLeast(0L).cents

    companion object {
        /** 没设上限的账单走这一条，界面按 isActive 决定要不要多说。 */
        fun inactive(spent: Cents): CapStatus = CapStatus(0L.cents, spent)
    }
}

/**
 * 账单上限的存储编码：一条 `id=cents;id=cents` 的字符串。
 *
 * 为什么不进 bills 表加一列：那要再来一次 schema 迁移，而 v3→v4 那次还没在真机上验证过，
 * 不该在同一轮里叠两个迁移风险。上限是偏好，和总预算同类，本来就该和它住一起。
 *
 * 为什么写成纯函数而不是散在 Repository 里：`rowid` 是可能被复用的（彻底删除账单后
 * 新建的账单可能拿到同一个 id），这条串读对/读歪决定的是"新账单莫名背上一个旧上限"，
 * 必须能在普通单测里逐条钉住。
 */
object BillCaps {

    fun encode(caps: Map<Long, Cents>): String =
        caps.entries
            .filter { (id, cents) -> id > 0L && cents.value > 0L }
            .sortedBy { it.key }
            .joinToString(";") { "${it.key}=${it.value.value}" }

    /**
     * 解析。认不出的片段**跳过而不是整条弃用**：一处写坏不该让用户丢掉全部上限。
     * 上限为 0 等于没设，不留在表里，否则界面上会出现"上限 ¥0.00"这种句子。
     */
    fun decode(raw: String): Map<Long, Cents> {
        if (raw.isBlank()) return emptyMap()
        val out = LinkedHashMap<Long, Cents>()
        for (token in raw.split(';', ',')) {
            val parts = token.split('=', limit = 2)
            if (parts.size != 2) continue
            val id = parts[0].trim().toLongOrNull() ?: continue
            val cents = parts[1].trim().toLongOrNull() ?: continue
            if (id > 0L && cents > 0L) out[id] = cents.cents
        }
        return out
    }

    /**
     * 严格解析，给备份文件用：任何一片认不出来就整条失败（返回 null）。
     *
     * 和 [decode] 分成两条是刻意的：DataStore 里的残值坏一片，丢掉那一片比丢掉全部上限好；
     * 而一份被截断过的备份必须整份拒收，"能恢复多少算多少"在这里等于改账本。
     */
    fun parseStrict(raw: String): Map<Long, Cents>? {
        if (raw.isEmpty()) return emptyMap()
        val out = LinkedHashMap<Long, Cents>()
        for (token in raw.split(';')) {
            val parts = token.split('=', limit = 2)
            if (parts.size != 2) return null
            val id = parts[0].toLongOrNull() ?: return null
            val cents = parts[1].toLongOrNull() ?: return null
            if (id <= 0L || cents <= 0L) return null
            if (out.put(id, cents.cents) != null) return null
        }
        return out
    }

    /** 删干净一个账单时把它名下的上限一起摘掉，避免将来 id 复用背错数。 */
    fun drop(caps: Map<Long, Cents>, billId: Long): Map<Long, Cents> =
        caps.filterKeys { it != billId }
}
