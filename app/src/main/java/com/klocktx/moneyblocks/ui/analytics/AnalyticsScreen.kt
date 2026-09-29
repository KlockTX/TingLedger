package com.klocktx.moneyblocks.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klocktx.moneyblocks.core.Cents
import com.klocktx.moneyblocks.core.cents
import com.klocktx.moneyblocks.core.dayRange
import com.klocktx.moneyblocks.core.formatDay
import com.klocktx.moneyblocks.core.millisToLocalDate
import com.klocktx.moneyblocks.core.toSignedYuanString
import com.klocktx.moneyblocks.core.today
import com.klocktx.moneyblocks.core.toYuanString
import com.klocktx.moneyblocks.core.toYuanWithSymbol
import com.klocktx.moneyblocks.domain.model.Balance
import com.klocktx.moneyblocks.domain.model.BillPalette
import com.klocktx.moneyblocks.domain.model.DayPoint
import com.klocktx.moneyblocks.domain.model.LargestEntry
import com.klocktx.moneyblocks.domain.model.MonthPoint
import com.klocktx.moneyblocks.domain.model.ShareSlice
import com.klocktx.moneyblocks.domain.usecase.Pace
import com.klocktx.moneyblocks.domain.usecase.PaceKind
import com.klocktx.moneyblocks.domain.usecase.CapStatus
import com.klocktx.moneyblocks.domain.usecase.HeatCell
import com.klocktx.moneyblocks.domain.usecase.MonthHeatmap
import com.klocktx.moneyblocks.ui.appViewModel
import com.klocktx.moneyblocks.ui.common.BillIconBadge
import com.klocktx.moneyblocks.ui.common.MetricColumn
import com.klocktx.moneyblocks.ui.common.MetricDivider
import com.klocktx.moneyblocks.ui.common.Panel
import com.klocktx.moneyblocks.ui.common.PanelDivider
import com.klocktx.moneyblocks.ui.common.Panels
import com.klocktx.moneyblocks.ui.common.ScreenTopBar
import com.klocktx.moneyblocks.ui.common.SectionHeader
import com.klocktx.moneyblocks.ui.common.TrackBar
import com.klocktx.moneyblocks.ui.theme.Alpha
import com.klocktx.moneyblocks.ui.theme.Badge
import com.klocktx.moneyblocks.ui.theme.Bar
import com.klocktx.moneyblocks.ui.theme.Chart
import com.klocktx.moneyblocks.ui.theme.Col
import com.klocktx.moneyblocks.ui.theme.IconSize
import com.klocktx.moneyblocks.ui.theme.MoneyCaption
import com.klocktx.moneyblocks.ui.theme.MoneyDisplay
import com.klocktx.moneyblocks.ui.theme.MoneyTitle
import com.klocktx.moneyblocks.ui.theme.Radius
import com.klocktx.moneyblocks.ui.theme.Spacing
import com.klocktx.moneyblocks.ui.theme.toComposeColor
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.abs

@Composable
fun AnalyticsScreen(
    onOpenDay: (Long, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = appViewModel {
        AnalyticsViewModel(
            billRepository = it.billRepository,
            buildStats = it.buildStats,
            settingsRepository = it.settingsRepository,
            statsRepository = it.statsRepository,
        )
    }
    val monthFirstDay by viewModel.monthFirstDay.collectAsStateWithLifecycle()
    val monthTotal by viewModel.monthTotal.collectAsStateWithLifecycle()
    val todayTotal by viewModel.todayTotal.collectAsStateWithLifecycle()
    val prevSameSpan by viewModel.prevMonthSameSpanTotal.collectAsStateWithLifecycle()
    val slices by viewModel.shareSlices.collectAsStateWithLifecycle()
    val pace by viewModel.pace.collectAsStateWithLifecycle()
    val dailyAverage by viewModel.dailyAverage.collectAsStateWithLifecycle()
    val trend by viewModel.trend.collectAsStateWithLifecycle()
    val largest by viewModel.largest.collectAsStateWithLifecycle()
    val monthly by viewModel.monthlyComparison.collectAsStateWithLifecycle()
    val canGoNext by viewModel.canGoNext.collectAsStateWithLifecycle()
    val monthIncome by viewModel.monthIncome.collectAsStateWithLifecycle()
    val tracksIncome by viewModel.tracksIncome.collectAsStateWithLifecycle()
    val monthlyIncome by viewModel.monthlyIncome.collectAsStateWithLifecycle()
    val digest by viewModel.digest.collectAsStateWithLifecycle()
    val heatCells by viewModel.heatCells.collectAsStateWithLifecycle()
    val streakDays by viewModel.streakDays.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // 与首页同一套内边距：左右 20dp、上 8dp，顶栏才不会切过来就跳
            .padding(horizontal = Spacing.xl)
            .padding(top = Spacing.sm),
    ) {
        ScreenTopBar(
            monthFirst = monthFirstDay,
            canGoNext = canGoNext,
            onPrevious = viewModel::previousMonth,
            onNext = viewModel::nextMonth,
            onPick = viewModel::goToMonth,
        )

        Spacer(Modifier.height(Spacing.sm))

        OverviewBlock(
            monthTotal = monthTotal,
            todayTotal = todayTotal,
            previous = prevSameSpan,
            dailyAverage = dailyAverage,
            income = monthIncome,
            showIncome = tracksIncome,
        )

        Spacer(Modifier.height(Spacing.xl))

        // 没设总预算的人不该看到一排空槽，所以整块按需出现
        if (pace.isActive) {
            SectionHeader("预算节奏", modifier = Modifier.padding(bottom = Spacing.md))
            TonalCard { PaceBlock(pace = pace, monthFirst = monthFirstDay) }
            Spacer(Modifier.height(Spacing.xl))
        }

        SectionHeader("本月节奏", modifier = Modifier.padding(bottom = Spacing.md))
        TonalCard {
            Row(Modifier.fillMaxWidth()) {
                MetricColumn("笔数", "${digest.entryCount} 笔", Modifier.weight(1f))
                MetricDivider()
                MetricColumn("平均单笔", digest.average.toYuanString(), Modifier.weight(1f))
                MetricDivider()
                // 最贵一天可点：看到「哪天最多」之后，下一个动作就是点开那天
                val busiest = digest.busiestDay
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(Panels.cornerSm)
                        .then(
                            if (busiest != null) {
                                Modifier.clickable {
                                    val range = dayRange(busiest.date)
                                    onOpenDay(range.start, range.endExclusive)
                                }
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    MetricColumn(
                        label = "最贵一天",
                        value = busiest?.let { "${it.date.dayOfMonth}日 ${it.total.toYuanString()}" } ?: "—",
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.xl))

        SectionHeader("支出占比", modifier = Modifier.padding(bottom = Spacing.md))
        val active = slices.filter { it.total.value != 0L }
        if (active.isEmpty()) {
            MutedNote("这个月还没有支出记录")
        } else {
            TonalCard {
                active.forEachIndexed { index, slice ->
                    ShareRow(slice)
                    if (index != active.lastIndex) {
                        PanelDivider(modifier = Modifier.padding(vertical = Spacing.md))
                    }
                }
                // 只有真设了上限的账单才多摆这一段：没设的人不该看到一排空槽
                val capped = active.filter { it.cap.value > 0L }
                if (capped.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.md))
                    PanelDivider()
                    Spacer(Modifier.height(Spacing.md))
                    capped.forEach { slice -> CapRow(slice) }
                }
            }
        }

        Spacer(Modifier.height(Spacing.xl))

        SectionHeader("消费日历", modifier = Modifier.padding(bottom = Spacing.md))
        TonalCard {
            MonthHeatmapGrid(cells = heatCells, onPickDay = onOpenDay)
            Spacer(Modifier.height(Spacing.md))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "少",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(Spacing.xs))
                HeatLegend()
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (streakDays > 0) "连续记了 $streakDays 天" else "还没开始连",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (streakDays > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        Spacer(Modifier.height(Spacing.xl))

        SectionHeader(
            title = "近 ${AnalyticsViewModel.TREND_DAYS} 天支出",
            modifier = Modifier.padding(bottom = Spacing.md),
        )
        TonalCard {
            TrendChart(points = trend, onPickDay = onOpenDay)
            Spacer(Modifier.height(Spacing.sm))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = trend.firstOrNull()?.let { formatDay(it.date) }?.plus(" 起") ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "日峰 " + (trend.maxOfOrNull { it.total }?.toYuanString() ?: "0.00"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(Spacing.xl))

                SectionHeader(
            title = if (tracksIncome) "月度收支对比" else "月度支出对比",
            modifier = Modifier.padding(bottom = Spacing.md),
        )
        TonalCard {
            MonthCompareChart(
                points = monthly,
                income = monthlyIncome,
                selected = monthFirstDay,
                onPick = viewModel::goToMonth,
            )
        }

        Spacer(Modifier.height(Spacing.xl))

        SectionHeader("最大单笔支出", modifier = Modifier.padding(bottom = Spacing.md))
        largest?.let { LargestRow(it) } ?: MutedNote("本月暂无支出记录")

        Spacer(Modifier.height(Spacing.listEnd))
    }
}

@Composable
private fun OverviewBlock(
    monthTotal: Cents,
    todayTotal: Cents,
    previous: Cents,
    dailyAverage: Cents,
    income: Cents,
    showIncome: Boolean,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "总开支",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(monthTotal.toYuanWithSymbol(), style = MoneyDisplay)
        Spacer(Modifier.height(Spacing.lg))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = Alpha.DividerStrong))
        Spacer(Modifier.height(Spacing.md))
        Row(Modifier.fillMaxWidth()) {
            MetricColumn("今日", todayTotal.toYuanString(), Modifier.weight(1f))
            MetricColumn("日均", dailyAverage.toYuanString(), Modifier.weight(1f))
            MetricColumn("上月同期", previous.toYuanString(), Modifier.weight(1f))
        }
        if (showIncome) {
            // 有收入账单才摆这一行：只看支出的月份里「结余」毫无意义
            val net = Balance(expense = monthTotal, income = income).net
            Spacer(Modifier.height(Spacing.md))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MetricColumn("本月收入", income.toSignedYuanString(income = true), Modifier.weight(1f))
                MetricDivider()
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "结余",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        text = net.toYuanString(),
                        style = MoneyCaption,
                        color = if (net.isNegative()) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.md))
        MonthOverMonthLine(monthTotal, previous)
    }
}

@Composable
private fun MonthOverMonthLine(monthTotal: Cents, previous: Cents) {
    if (previous.value == 0L) {
        Text(
            text = "上月同期无记录，暂无法比较",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val ratio = (monthTotal.value - previous.value).toDouble() / previous.value
    val text: String
    val color: Color
    when {
        abs(ratio) < 0.005 -> {
            text = "与上月同期基本持平"
            color = MaterialTheme.colorScheme.onSurfaceVariant
        }
        ratio > 0 -> {
            text = "比上月同期多支出 ${formatPercent(ratio)}"
            color = MaterialTheme.colorScheme.error
        }
        else -> {
            text = "比上月同期少支出 ${formatPercent(-ratio)}"
            color = MaterialTheme.colorScheme.primary
        }
    }
    Text(text, style = MaterialTheme.typography.labelLarge, color = color)
}

@Composable
private fun ShareRow(slice: ShareSlice) {
    val accent = slice.bill.palette.toComposeColor()
    Row(verticalAlignment = Alignment.CenterVertically) {
        BillIconBadge(iconKey = slice.bill.iconKey, palette = slice.bill.palette, size = Badge.sm, iconSize = IconSize.sm)
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = slice.bill.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(Col.name),
        )
        TrackBar(
            fraction = slice.share.toFloat(),
            color = accent,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = formatPercent(slice.share),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(Col.percent),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(slice.total.toYuanString(), style = MoneyTitle)
    }
}

/**
 * 设了上限的账单在占比卡片里多占一行：本月花了多少 / 上限多少，超了就把金额染成警示色。
 *
 * 不新加控件、不新加页面——占比那行本来就在说"这个账单占多大头"，这里只是把同一件事
 * 换成"相对它自己的上限"再说一遍。没设上限的账单根本不出现。
 */
@Composable
private fun CapRow(slice: ShareSlice) {
    val cap = slice.cap
    val status = CapStatus(cap = cap, spent = slice.total)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = slice.bill.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(Col.name),
        )
        Spacer(Modifier.width(Spacing.sm))
        TrackBar(
            fraction = status.progress,
            color = if (status.isOver) MaterialTheme.colorScheme.error else slice.bill.palette.toComposeColor(),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = "${slice.total.toYuanString()} / ${cap.toYuanString()}",
            style = MaterialTheme.typography.labelMedium,
            color = if (status.isOver) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
        )
    }
}

/**
 * 预算节奏那一块。三句话各说一件事：进度条（花了多少）、还能怎么花（日均）、
 * 照这样下去会怎样（预测）。第三句在天数太少时**故意不说**——见 [Pace.projectionIsReliable]。
 */
@Composable
private fun PaceBlock(pace: Pace, monthFirst: LocalDate) {
    val alarming = pace.state == PaceKind.OVER_NOW || pace.state == PaceKind.OVER_SOON
    val accent = if (alarming) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant

    TrackBar(fraction = pace.consumed.coerceIn(0.0, 1.0).toFloat(), color = accent, height = Bar.thick)
    Spacer(Modifier.height(Spacing.md))

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (pace.isOverNow) {
                "已超 " + (pace.spent.value - pace.budget.value).cents.toYuanString()
            } else {
                "日均可用 " + pace.allowance.toYuanString()
            },
            style = MaterialTheme.typography.bodyMedium,
            color = accent,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "剩 ${pace.daysLeft} 天",
            style = MaterialTheme.typography.labelMedium,
            color = quiet,
        )
    }
    Spacer(Modifier.height(Spacing.xs))
    Text(
        text = when {
            !pace.projectionIsReliable ->
                "才过 ${pace.elapsedDays} 天，还看不出这个月的速度"
            pace.state == PaceKind.OVER_NOW ->
                "预算 ${pace.budget.toYuanString()}，已花到 ${pace.spent.toYuanString()}"
            pace.willOverrun -> {
                val day = pace.overrunDay
                "按当前速度月末 " + pace.projected.toYuanString() +
                    if (day != null) "，预计 ${formatDay(monthFirst.withDayOfMonth(day))} 花超" else ""
            }
            else -> "按当前速度月末 " + pace.projected.toYuanString() + "，在预算内"
        },
        style = MaterialTheme.typography.labelSmall,
        color = if (pace.willOverrun || pace.state == PaceKind.OVER_NOW) accent else quiet,
    )
}

@Composable
private fun TrendChart(points: List<DayPoint>, onPickDay: (Long, Long) -> Unit) {
    if (points.isEmpty()) return
    val maxValue = points.maxOf { it.total.value }.coerceAtLeast(1L)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Chart.trendHeight),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(Chart.gap),
    ) {
        points.forEach { point ->
            // 零支出也留一条细基线，否则整段看起来像缺数据
            val barHeight = if (point.total.value == 0L) 2
            else (88 * point.total.value / maxValue).toInt().coerceIn(5, 88)
            Box(
                Modifier
                    .weight(1f)
                    .height(barHeight.dp)
                    .clip(Radius.BarTop)
                    .background(
                        if (point.total.value == 0L) MaterialTheme.colorScheme.outlineVariant.copy(alpha = Alpha.ChartTrack)
                        else MaterialTheme.colorScheme.primary.copy(alpha = Alpha.Chart),
                    )
                    // 点柱子直接展开那天的全部条目
                    .clickable {
                        val range = dayRange(point.date)
                        onPickDay(range.start, range.endExclusive)
                    },
            )
        }
    }
}

@Composable
private fun LargestRow(entry: LargestEntry) {
    TonalCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BillIconBadge(
                iconKey = entry.billIconKey,
                palette = entry.billPalette,
                size = Badge.lg,
                iconSize = IconSize.md,
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.entry.note.ifEmpty { entry.billName },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${entry.billName} · ${formatDay(millisToLocalDate(entry.entry.occurredAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(entry.entry.amount.toYuanWithSymbol(), style = MoneyTitle)
        }
    }
}

@Composable
private fun TonalCard(content: @Composable ColumnScope.() -> Unit) {
    Panel(modifier = Modifier.fillMaxWidth(), content = content)
}

@Composable
private fun MutedNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = Spacing.md),
    )
}

/** 图表配色说明。柱状图靠颜色区分收支，所以图例必须跟着双色柱一起出现。 */
@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(Chart.legendDot).clip(CircleShape).background(color))
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatPercent(ratio: Double): String = String.format(Locale.CHINA, "%.1f%%", ratio * 100)

/**
 * 近 12 个月支出柱状对比。柱子可以直接点，点哪个月整页就切到哪个月——
 * 只能看不能查的图表价值有限。
 */
@Composable
private fun MonthCompareChart(
    points: List<MonthPoint>,
    income: List<MonthPoint>,
    selected: LocalDate,
    onPick: (LocalDate) -> Unit,
) {
    if (points.isEmpty()) return
    // 没有收入账单时保持单柱：多一列恒为 0 的柱子只是噪音
    val paired = income.isNotEmpty() && income.any { it.total.value > 0L }
    val expenseColor = MaterialTheme.colorScheme.primary
    val incomeColor = BillPalette.TEAL.toComposeColor()
    val maxValue = maxOf(
        points.maxOf { it.total.value },
        if (paired) income.maxOf { it.total.value } else 0L,
    ).coerceAtLeast(1L)
    fun heightOf(cents: Long): Int =
        if (cents == 0L) 2 else (84 * cents / maxValue).toInt().coerceIn(5, 84)
    val incomeByMonth = if (paired) income.associate { it.monthFirst to it.total } else emptyMap()
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Chart.monthHeight),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(Chart.gap),
        ) {
            points.forEach { point ->
                val isSelected = YearMonth.from(point.monthFirst) == YearMonth.from(selected)
                val dim = if (isSelected) 1f else 0.32f
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(Radius.BarTop)
                        .clickable { onPick(point.monthFirst) },
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(if (paired) Chart.pairGap else 0.dp),
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(heightOf(point.total.value).dp)
                            .clip(Radius.BarTop)
                            .background(
                                if (point.total.value == 0L)
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = Alpha.ChartTrack)
                                else expenseColor.copy(alpha = dim),
                            ),
                    )
                    if (paired) {
                        val inc = incomeByMonth[point.monthFirst]?.value ?: 0L
                        Box(
                            Modifier
                                .weight(1f)
                                .height(heightOf(inc).dp)
                                .clip(Radius.BarTop)
                                .background(
                                    if (inc == 0L) MaterialTheme.colorScheme.outlineVariant.copy(alpha = Alpha.ChartTrack)
                                    else incomeColor.copy(alpha = dim),
                                ),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Chart.gap),
        ) {
            points.forEach { point ->
                val isSelected = YearMonth.from(point.monthFirst) == YearMonth.from(selected)
                Text(
                    text = "${point.monthFirst.monthValue}",
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onPick(point.monthFirst) },
                )
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        if (paired) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LegendDot(expenseColor, "支出")
                Spacer(Modifier.width(Spacing.md))
                LegendDot(incomeColor, "收入")
            }
            Spacer(Modifier.height(Spacing.xs))
        }
        Text(
            text = "最高 " + (points.maxOfOrNull { it.total }?.toYuanString() ?: "0.00") + " · 点柱子看那个月",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 一格该是什么颜色。
 *
 * 用**主题主色**的深浅，不用账单配色：日历是整页的分析视图，不属于任何一个账单，
 * 让它跟着软件主题走才和页面其它地方一致（记账计算器那轮定的同一条规矩）。
 */
@Composable
private fun heatColor(level: Int): Color = when {
    level <= 0 -> MaterialTheme.colorScheme.surfaceContainerHigh
    else -> MaterialTheme.colorScheme.primary.copy(alpha = Alpha.HeatBase + Alpha.HeatStep * (level - 1))
}

/** 深浅图例：五格一次摆出来，用户不用猜"深一点是多少"。 */
@Composable
private fun HeatLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(Chart.gap)) {
        (0..MonthHeatmap.LEVELS).forEach { level ->
            Box(
                Modifier
                    .size(Chart.legendSquare)
                    .clip(Radius.Tiny)
                    .background(heatColor(level)),
            )
        }
    }
}

/**
 * 消费日历。一格一天，周一开头。
 *
 * 格子本身不放数字：日历要回答的是"哪几天重"，数字会把它变成表格。
 * 每天的花费走 `contentDescription`，读屏和点进去看当天都拿得到。
 */
@Composable
private fun MonthHeatmapGrid(cells: List<HeatCell>, onPickDay: (Long, Long) -> Unit) {
    val today = today()
    Column {
        Row(Modifier.fillMaxWidth()) {
            MonthHeatmap.weekColumns.forEach { column ->
                Text(
                    text = "一二三四五六日".substring(column.value - 1, column.value),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    val date = cell.date
                    val reachable = date != null && !date.isAfter(today)
                    val label = when {
                        date == null -> " "
                        !reachable -> "${formatDay(date)} 还没到"
                        cell.cents.value > 0L -> "${formatDay(date)} 花了 ${cell.cents.toYuanWithSymbol()}"
                        else -> "${formatDay(date)} 没花钱"
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(Chart.cellGap)
                            .clip(Radius.HeatCell)
                            .background(heatColor(cell.level))
                            .testTag("heat-cell")
                            .then(
                                if (reachable) {
                                    Modifier
                                        .clickable {
                                            val range = dayRange(date)
                                            onPickDay(range.start, range.endExclusive)
                                        }
                                        .semantics { contentDescription = label }
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
                // 末行不满七天时补位，否则前面的格子会被拉伸
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
