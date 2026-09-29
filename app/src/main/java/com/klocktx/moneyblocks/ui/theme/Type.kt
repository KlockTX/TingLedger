package com.klocktx.moneyblocks.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

private val base = TextStyle(
    fontFamily = FontFamily.Default,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

/** 中文界面上过大的字距会显得松散，这里整体收紧并压一点行高。 */
val MoneyTypography = Typography(
    displaySmall = base.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp, lineHeight = 40.sp),
    headlineMedium = base.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, lineHeight = 32.sp),
    headlineSmall = base.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp, lineHeight = 28.sp),
    titleLarge = base.copy(fontSize = 19.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, lineHeight = 25.sp),
    titleMedium = base.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp, lineHeight = 20.sp),
    titleSmall = base.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp, lineHeight = 18.sp),
    bodyLarge = base.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp, lineHeight = 23.sp),
    bodyMedium = base.copy(fontSize = 14.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp, lineHeight = 20.sp),
    bodySmall = base.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.1.sp, lineHeight = 17.sp),
    labelLarge = base.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp, lineHeight = 18.sp),
    labelMedium = base.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp, lineHeight = 15.sp),
    labelSmall = base.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp, lineHeight = 14.sp),
)

/** 金额统一走等宽数字（tnum），逐行刷新时数字宽度不会跳。 */
private const val TABULAR = "tnum"

val MoneyDisplay = TextStyle(
    fontSize = 40.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-1.2).sp,
    fontFeatureSettings = TABULAR,
)

val MoneyHeadline = TextStyle(
    fontSize = 30.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-0.8).sp,
    fontFeatureSettings = TABULAR,
)

val MoneyTitle = TextStyle(
    fontSize = 21.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = (-0.5).sp,
    fontFeatureSettings = TABULAR,
)

/**
 * 首页顶部并排两个数字。360dp 屏上并排一格只有约 150dp，
 * 20sp 等宽数字放「¥1,234,567.89」这种最长形态也约 143dp，仍然放得下，
 * 所以不需要运行时测字号——上一版用 TextMeasurer 自适应，结果在
 * AnimatedContent 里拿到 0 宽度，两个数字直接不显示了。
 */
val MoneyPair = TextStyle(
    fontSize = 20.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = (-0.8).sp,
    fontFeatureSettings = TABULAR,
)

val MoneyBody = TextStyle(
    fontSize = 16.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = (-0.2).sp,
    fontFeatureSettings = TABULAR,
)

val MoneyCaption = TextStyle(
    fontSize = 13.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.sp,
    fontFeatureSettings = TABULAR,
)
