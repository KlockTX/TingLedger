package com.klocktx.moneyblocks.ui.theme

import androidx.compose.ui.graphics.Color
import com.klocktx.moneyblocks.domain.model.BillPalette

/**
 * 界面配色不再写在这里：主题色由 [MonetTheme] 的四颗种子派生（见 Monet.kt），
 * 这一层只负责把账单的 8 档分类色翻成 Compose 颜色。
 *
 * 账单色不跟随主题变化——它们是**分类标识**，换主题时跟着变就没法辨认了。
 */
fun BillPalette.toComposeColor(): Color = Color(argb.toInt())

fun BillPalette.tint(alpha: Float = 0.12f): Color = toComposeColor().copy(alpha = alpha)
