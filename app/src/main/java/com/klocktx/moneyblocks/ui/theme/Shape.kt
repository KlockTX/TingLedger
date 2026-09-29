package com.klocktx.moneyblocks.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/**
 * 间距只走 4 的倍数，节奏感来自一致而不是随意取值。
 *
 * 例外只留一个：[xxs]。2dp 用在"两行文字之间要贴得很近但又不能贴着"这种地方，
 * 抬到 4dp 会散、降到 0 会糊成一坨，所以给它一个名字而不是继续让人随手写 `2.dp`。
 *
 * 圆角不在这里——见 [Radius]。图标与条的粗细也不在，见 [IconSize] / [Bar]：
 * 那些是"东西本身多大"，不是"东西之间隔多远"。
 */
object Spacing {
    /** 主副标题之间那一口气。抬到 2dp 会散，0 会糊成一坨。 */
    val hairline = 1.dp
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 28.dp
    val screen = PaddingValues(horizontal = 20.dp)
    val listBottom = PaddingValues(bottom = 120.dp)

    /** 可滚动页面最后一段留白，和 [listBottom] 同值：底部内容才不会被悬浮按钮压住。 */
    val listEnd = 120.dp
}
