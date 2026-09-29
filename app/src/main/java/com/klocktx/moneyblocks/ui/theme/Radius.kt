package com.klocktx.moneyblocks.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 圆角的唯一来源。之前这里有两套并行：`MoneyShapes`(8/12/18/24/30，被 MaterialTheme 接走但
 * 界面上一处都没引用) 和 `Panels` 里的五个 `RoundedCornerShape`，加上各页面自己又写了 28 个。
 *
 * 收敛成四档，并且**按用途命名而不是按大小**——"再圆一点"没法讨论，"卡片这一档"可以。
 */
object Radius {
    /** 小胶囊、色块、标签。 */
    val Chip: Shape = RoundedCornerShape(8.dp)

    /** 输入框、按钮、列表里的内层面板。 */
    val Field: Shape = RoundedCornerShape(12.dp)

    /** 页面上的卡片。 */
    val Card: Shape = RoundedCornerShape(16.dp)

    /** 弹层：对话框、底部弹层这种盖在页面之上的东西。 */
    val Overlay: Shape = RoundedCornerShape(20.dp)

    /** 完全圆角（圆形头像、圆点）。 */
    val Pill: Shape = CircleShape

    /** 底部弹层：只圆上面两个角，和 [Overlay] 同一档，免得弹层与对话框分层分不出来。 */
    val SheetTop: Shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

    /**
     * 记一笔那个大弹层的顶部两角。
     * 原来随手写的是 28dp；收到 24 是为了守住"位移不超过 4dp"的约定，
     * 同时仍然比 [SheetTop] 大一档——弹层越大圆角越大，层次才读得出来。
     */
    val RecordSheetTop: Shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    /** 比 [Tiny] 还小的 1dp 圆角：给 2dp 粗的短横用，再大就看不出是线了。 */
    val Hairline: Shape = RoundedCornerShape(1.dp)

    /**
     * 只圆上面两个角——柱状图那根柱子。
     * 柱顶圆角跟着柱子宽度走会得到完全不同的观感，这里固定一个小值。
     */
    val BarTop: Shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 0.dp, bottomEnd = 0.dp)

    /** 热力日历一格：格子上 1.5dp 间隙，圆角必须比间隙大才看得出是"一格一格"。 */
    val HeatCell: Shape = RoundedCornerShape(6.dp)

    /**
     * 图例那种几 dp 见方的小色块。
     * 它们比 [Chip] 的 8dp 还小，套上去圆角就会吃掉整个块——所以留一档更小的。
     */
    val Tiny: Shape = RoundedCornerShape(3.dp)

    /** 进度条这类高度决定圆角的：给它半径等于高度的一半，永远是胶囊。 */
    fun pillOf(height: Dp): Shape = RoundedCornerShape(height / 2)
}
