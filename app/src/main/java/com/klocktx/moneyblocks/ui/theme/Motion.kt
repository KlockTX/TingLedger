package com.klocktx.moneyblocks.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

/**
 * 动效时长与曲线集中在这里。散落的 duration 会让界面各处的动画节奏不一致，
 * 而这种不一致正是「廉价感」的来源之一。
 */
object Motion {
    /** 按压反馈这类即时响应 */
    const val QUICK = 140

    /** 轻提示进场：比 QUICK 缓一档、比 STANDARD 快，浮层不该"飘"进来。 */
    const val SOFT = 180

    /** 进出场、淡入淡出 */
    const val STANDARD = 240

    /** 骨架屏退场这类需要被注意到的变化 */
    const val SLOW = 380

    /** 骨架屏呼吸一次的周期。比 SLOW 还长：它是"等着"的提示，不该像动画那样抢注意。 */
    const val PULSE = 720

    /** M3 的 emphasized 曲线：起步快、收尾缓，比默认 EaseInOut 更利落 */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    val Decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** 入场用：减速收尾，元素「落」到位 */
    fun <T> enter(): FiniteAnimationSpec<T> = tween(STANDARD, easing = Decelerate)

    /** 出场用：更快消失，不拖住下一步操作 */
    fun <T> exit(): FiniteAnimationSpec<T> = tween(QUICK, easing = EaseOut)
}
