package com.klocktx.moneyblocks.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 尺寸阶。这些**不是间距**，所以不走 [Spacing] 的 4 倍网格：
 * 图标字形、徽标盒子、进度条高度是"东西本身有多大"，把它们硬凑到间距网格上只会让图标忽大忽小。
 *
 * 每一档都是从现在的散落取值里归拢出来的（取的是出现次数最多的那个值），
 * 所以这一步本身不改变观感——它只是让"下一个尺寸该是多少"有地方可查。
 */
object IconSize {
    /** 行内提示、角标里的小图形。 */
    val xs = 14.dp

    /** 搜索框、列表行尾这类次要图标。现有取值 15/16 归到这里。 */
    val sm = 16.dp

    /** 用得多的一档：18。 */
    val md = 18.dp

    /** 主要操作图标、键盘上的符号。 */
    val lg = 20.dp

    /** 空态与强调位。 */
    val xl = 26.dp
}

/** 账单图标徽标的盒子（里面的字形由 [IconSize] 给）。 */
object Badge {
    /** 分析页占比行那种紧凑档。 */
    val sm = 30.dp

    /** 列表行、换账单行、设置页图标底。 */
    val md = 34.dp

    /** 首页账单行。 */
    val lg = 42.dp

    /** 顶栏/头部那种强调档。 */
    val xl = 56.dp

    /** 选中角标那个小圆点，以及点在里面的对勾。 */
    val dot = 15.dp
    val dotGlyph = 10.dp
}

/**
 * 行与控件的固定高度。这些值互相咬合（顶栏 56 与它里面的 48 触摸区、
 * 键盘那一行 54 与弹层最小高 48），放在一起才看得出是不是真的对齐。
 */
object Rows {
    /** 图标网格一格。 */
    val grid = 44.dp

    /** 标题行/触摸区的最小高度（48dp 是可点区域的下限，别再往下压）。 */
    val minTouch = 48.dp

    /** 记账键盘那一行按键。 */
    val key = 54.dp

    /** 顶栏。 */
    val header = 56.dp

    /** 常用备注那一行（固定高度，避免选中态改变行高导致弹层抖动）。 */
    val chips = 30.dp

    /** 分组小标题那一行。 */
    val groupHeader = 26.dp

    /** 月份胶囊的直径。 */
    val monthPill = 40.dp

    /**
     * 列表行尾部那一块的名义尺寸。它是 **26 + 4 + 26** 加出来的：
     * 行高与里面那道间隙是耦合的，间隙从 3 抬到 4（对齐间距网格）时这里必须一起变 56，
     * 否则上下两排会被裁掉一截——所以它不能是个自由常量。
     */
    val tailSize = 56.dp
}

/** 线条与条：分隔线、描边、进度条、图表。 */
object Bar {
    /** 发丝线（1dp 描边、1dp 竖线）。 */
    val hairline = 1.dp

    /** 比发丝线再轻一点的描边（色板小格那种"有边但不抢"）。 */
    val subtle = 0.8.dp

    /** 图例里那根小短横。 */
    val thin = 2.dp

    /** 首页分组那根细进度条。 */
    val regular = 3.dp

    /** 默认进度条厚度（`TrackBar` 的默认值）。 */
    val track = 4.dp

    /** 预算节奏那种要被多看一眼的条。 */
    val thick = 6.dp

    /** 选中色块的外环。比 hairline 重一档，正好能看见又不过粗。 */
    val emphasized = 1.6.dp

    /** 指标列之间那道竖线的高度。 */
    val metricRule = 26.dp

    /** 图例短横的长度。 */
    val legendRule = 15.dp

    /** 图例短横之间的间隙（比 [Chart.gap] 小一档，短横才成组）。 */
    val legendGap = 3.5.dp
}

/** 容器的高度与浮层的定位。 */
object Sheet {
    /** 按日查看这种半屏弹层内容的最大高度。 */
    val maxHeight = 420.dp

    /** 账单详情那条筛选条的高度。 */
    val strip = 50.dp
}

/** 轻提示自己的尺寸档（它浮在所有东西上面，留白规则和页面不同）。 */
object Toast {
    val maxWidth = 420.dp
    val bottomGap = 100.dp
    /** 带操作按钮时右边收一点，让按钮自己管自己的边距。 */
    val endWithAction = 6.dp
}

/** 色板那一格里的小方块。 */
object Swatch {
    val box = 50.dp
    val inner = 40.dp
    val dot = 24.dp
}

/** 表面抬升。只有这两处用到，但写死数字会让人以为"卡片都该有这个高度"。 */
object Elev {
    /** 对话框。 */
    val dialog = 6.dp

    /** 轻提示。 */
    val toast = 3.dp
}

/** 图表几何。这些值互相咬合（间隙、柱宽、高度），所以放在一起而不是散到各页。 */
object Chart {
    /** 柱与柱、色块与色块之间的间隙。 */
    val gap = 3.dp

    /** 双柱（支出/收入）成对时，对内那 1dp 的缝。 */
    val pairGap = 1.dp

    /** 热力日历一格与格之间的缝隙：比 [gap] 小，格子才连成一片。 */
    val cellGap = 1.5.dp

    /** 图例圆点。 */
    val legendDot = 8.dp

    /** 图例那个小方块的边长。 */
    val legendSquare = 9.dp

    /** 近 30 天趋势那一格的高度。 */
    val trendHeight = 88.dp

    /** 月度对比柱状图那一格的高度。 */
    val monthHeight = 84.dp
}

/** 表格里定宽的那几列。宽度和字号是咬合的（金额列要放得下最长形态）。 */
object Col {
    /** 账单名那一列。占比行与上限行共用，两行才能对得齐。 */
    val name = 72.dp

    /** 百分比那一列。 */
    val percent = 38.dp

    /** 分隔线在列表行里缩进多少才对齐到文字（越过图标列）。 */
    val rowIndent = 66.dp

    /** 首页分组那根细进度条的宽度。 */
    val groupBar = 84.dp

    /** 筛选行左边"方向/金额/时间"那个标签列。 */
    val filterLabel = 30.dp
}

/** 骨架屏那几条占位灰块。它们只有"像不像最终版面"这一个标准，所以集中放一起。 */
object Skeleton {
    val titleWidth = 72.dp
    val titleHeight = 13.dp
    val subWidth = 96.dp
    val subHeight = 9.dp
    val chipWidth = 56.dp
    val chipHeight = 18.dp
}

/** 拖动时的位移。 */
object Drag {
    /** 被拖起来那一行抬高多少。太小看不出在拖，太大会顶到隔壁行。 */
    val lift = 10.dp
}

/**
 * 悬浮元素离底边的距离——底栏内容留白和它不是一回事，别混用。
 *
 * 别把这个对象叫 `Float`：它是 `ui.theme` 包级声明，会在本包内遮蔽 `kotlin.Float`，
 * 于是 `alpha: Float = 0.12f` 这类签名直接编译不过（我踩过一次）。
 */
object FloatOffset {
    /** 轻提示离底边。 */
    val toast = 100.dp

    /** 有悬浮按钮的列表底部留白（`Spacing.listBottom` 是 120，那是没有悬浮按钮的页面）。 */
    val aboveFab = 96.dp
}
