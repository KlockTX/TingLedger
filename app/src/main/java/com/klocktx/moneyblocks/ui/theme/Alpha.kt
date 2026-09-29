package com.klocktx.moneyblocks.ui.theme

/**
 * 透明度档。取值全部来自现在代码里已经在用的那些数（没有为了"统一"而挪动任何一个），
 * 目的是让"这根线该多淡"有地方可查，而不是每个页面自己想到 0.5 还是 0.6。
 *
 * 命名按**用途**，不按数值大小：同一档 `Tint` 在选中态和按压态会差 0.02，
 * 但它们在界面上干的是同一件事。
 */
object Alpha {
    /** 卡片内部的分隔线。 */
    const val Divider = 0.45f

    /** 概览区那种要明显看得见的分隔线。 */
    const val DividerStrong = 0.6f

    /** 面板外描边。 */
    const val Outline = 0.65f

    /** 进度条底槽。 */
    const val Track = 0.5f

    /** 危险操作的淡底（删除确认那条）。 */
    const val DangerTint = 0.10f

    /** 账单分类色的默认淡底。 */
    const val Tint = 0.12f

    /** 选中态的淡底。 */
    const val SelectedTint = 0.14f

    /** 选中态的描边（比淡底重一点，才看得清"是它"）。 */
    const val SelectedBorder = 0.45f

    /** 按压/悬停时的淡底。 */
    const val PressedTint = 0.16f

    /** 色块选中时的外环。 */
    const val Ring = 0.22f

    /** 柱子、热力格的实色。 */
    const val Chart = 0.72f

    /** 空柱子的底槽。比进度条底槽略重：柱子细，淡一点就看不见了。 */
    const val ChartTrack = 0.6f

    /**
     * 热力日历的深浅起点与步长（`level` 1..4）。
     * 起点 0.24 是最浅那档在深浅背景上都能被看见的最低值，步长 0.19 让四档刚好铺到 0.81。
     */
    const val HeatBase = 0.24f
    const val HeatStep = 0.19f

    /** 不可点的东西（还没到的日子、退场的骨架）。 */
    const val Muted = 0.35f

    /** 禁用态的底色。 */
    const val Disabled = 0.4f

    /** 禁用态的图标/文字。 */
    const val DisabledContent = 0.45f
}
