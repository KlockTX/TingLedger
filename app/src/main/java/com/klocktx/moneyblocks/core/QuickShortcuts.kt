package com.klocktx.moneyblocks.core

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.klocktx.moneyblocks.MainActivity
import com.klocktx.moneyblocks.R
import com.klocktx.moneyblocks.domain.model.Bill

/**
 * 桌面长按快捷记账：长按图标直接给某个账单记一笔，省掉「打开 App → 找账单 → 点开」三步。
 *
 * 快捷项数量有限，「选哪几个账单」是一段有主张的逻辑，所以抽成纯函数单独测。
 */
object QuickShortcuts {

    const val EXTRA_BILL_ID = "com.klocktx.moneyblocks.extra.BILL_ID"
    const val MAX = 4

    /**
     * 未归档里挑，支出优先：快捷记账绝大多数是为了常花的那几类，
     * 工资这类收入一个月就记一次，占着快捷位反而是浪费。
     */
    fun selection(bills: List<Bill>, max: Int = MAX): List<Bill> = bills
        .filterNot { it.archived }
        .sortedWith(compareBy({ if (it.isIncome) 1 else 0 }, { it.sortOrder }))
        .take(max)

    fun billIdOf(intent: Intent?): Long =
        intent?.getLongExtra(EXTRA_BILL_ID, 0L) ?: 0L

    fun publish(context: Context, bills: List<Bill>) {
        val shortcuts = selection(bills).map { bill ->
            ShortcutInfoCompat.Builder(context, "record-${bill.id}")
                .setShortLabel(bill.name)
                .setLongLabel("给「${bill.name}」记一笔")
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(
                    Intent(context, MainActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                        .putExtra(EXTRA_BILL_ID, bill.id),
                )
                .build()
        }
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }
}
