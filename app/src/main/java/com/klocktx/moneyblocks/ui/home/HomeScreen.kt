package com.klocktx.moneyblocks.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klocktx.moneyblocks.core.Cents
import com.klocktx.moneyblocks.core.cents
import com.klocktx.moneyblocks.core.dayRange
import com.klocktx.moneyblocks.core.formatTime
import com.klocktx.moneyblocks.core.millisToLocalDate
import com.klocktx.moneyblocks.core.monthRange
import com.klocktx.moneyblocks.core.relativeDayLabel
import com.klocktx.moneyblocks.core.toNetYuanString
import com.klocktx.moneyblocks.core.toSignedYuanString
import com.klocktx.moneyblocks.core.toYuanString
import com.klocktx.moneyblocks.core.toYuanWithSymbol
import com.klocktx.moneyblocks.core.today
import com.klocktx.moneyblocks.domain.model.Bill
import com.klocktx.moneyblocks.domain.model.BillKind
import com.klocktx.moneyblocks.domain.model.BillPalette
import com.klocktx.moneyblocks.domain.model.BillSummary
import com.klocktx.moneyblocks.domain.usecase.BuildStatsUseCase
import com.klocktx.moneyblocks.domain.usecase.describeRepeat
import com.klocktx.moneyblocks.ui.appViewModel
import com.klocktx.moneyblocks.ui.bill.BillEditorDialog
import com.klocktx.moneyblocks.ui.bill.newBillDraft
import com.klocktx.moneyblocks.ui.common.BillIconBadge
import com.klocktx.moneyblocks.ui.common.ItemRow
import com.klocktx.moneyblocks.ui.common.MetricColumn
import com.klocktx.moneyblocks.ui.common.MetricDivider
import com.klocktx.moneyblocks.ui.common.NoPadding
import com.klocktx.moneyblocks.ui.common.Panel
import com.klocktx.moneyblocks.ui.common.PanelDivider
import com.klocktx.moneyblocks.ui.common.Panels
import com.klocktx.moneyblocks.ui.common.ReorderHandle
import com.klocktx.moneyblocks.ui.common.ScreenTopBar
import com.klocktx.moneyblocks.ui.common.SectionHeader
import com.klocktx.moneyblocks.ui.common.TrackBar
import com.klocktx.moneyblocks.ui.theme.Alpha
import com.klocktx.moneyblocks.ui.theme.Badge
import com.klocktx.moneyblocks.ui.theme.Bar
import com.klocktx.moneyblocks.ui.theme.Col
import com.klocktx.moneyblocks.ui.theme.Drag
import com.klocktx.moneyblocks.ui.theme.IconSize
import com.klocktx.moneyblocks.ui.theme.MoneyPair
import com.klocktx.moneyblocks.ui.theme.MoneyTitle
import com.klocktx.moneyblocks.ui.theme.Motion
import com.klocktx.moneyblocks.ui.theme.Radius
import com.klocktx.moneyblocks.ui.theme.Rows
import com.klocktx.moneyblocks.ui.theme.Skeleton
import com.klocktx.moneyblocks.ui.theme.Spacing
import com.klocktx.moneyblocks.ui.theme.tint
import com.klocktx.moneyblocks.ui.theme.toComposeColor
import java.time.LocalDate
import java.util.Locale
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableItem

@Composable
fun HomeScreen(
    onSearch: () -> Unit,
    onRecord: (BillSummary) -> Unit,
    onOpenBill: (Bill) -> Unit,
    /** 打开「某一天」的明细面板，参数是当天的起止毫秒。 */
    onOpenDay: (Long, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = appViewModel {
        HomeViewModel(
            billRepository = it.billRepository,
            entryRepository = it.entryRepository,
            manageBills = it.manageBills,
            settingsRepository = it.settingsRepository,
            statsRepository = it.statsRepository,
        )
    }
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val summaries by viewModel.summaries.collectAsStateWithLifecycle()
    val periodTotal by viewModel.periodTotal.collectAsStateWithLifecycle()
    val todayTotal by viewModel.todayTotal.collectAsStateWithLifecycle()
    val allTimeTotal by viewModel.allTimeTotal.collectAsStateWithLifecycle()
    val canGoNext by viewModel.canGoNext.collectAsStateWithLifecycle()
    val monthFirstDay by viewModel.monthFirstDay.collectAsStateWithLifecycle()
    val periodPrefix = remember(monthFirstDay) { periodPrefixOf(monthFirstDay) }
    val recent by viewModel.recentEntries.collectAsStateWithLifecycle()
    val budget by viewModel.monthlyBudget.collectAsStateWithLifecycle()
    val netBalance by viewModel.balance.collectAsStateWithLifecycle()
    val homeDailyAverage by viewModel.dailyAverage.collectAsStateWithLifecycle()
    val caps by viewModel.billCaps.collectAsStateWithLifecycle()
    val periodIncome by viewModel.periodIncome.collectAsStateWithLifecycle()
    var editorKind by rememberSaveable { mutableStateOf<BillKind?>(null) }
    var editing by remember { mutableStateOf<Bill?>(null) }

    /**
     * 编辑态。两个组各自一个开关，**不联动**：整理支出时没必要把收入也掀成编辑样，
     * 半屏控件突然换脸看着像 App 出了毛病。
     *
     * 进编辑态的行只换尾部内容（合计↔归档、明细↔双横），骨架与内边距两态完全一致。
     */
    var editingExpense by rememberSaveable { mutableStateOf(false) }
    var editingIncome by rememberSaveable { mutableStateOf(false) }
    val todayCount by viewModel.todayCount.collectAsStateWithLifecycle()
    val monthCount by viewModel.monthCount.collectAsStateWithLifecycle()
    val viewingCurrentMonth by viewModel.viewingCurrentMonth.collectAsStateWithLifecycle()
    val nudgeSeenDay by viewModel.nudgeSeenDay.collectAsStateWithLifecycle()

    // 最近条目的归属账单直接复用已加载的摘要，不再单独查一次库
    val billById = remember(summaries) { summaries.associate { it.bill.id to it.bill } }
    // 名下条目数同样从摘要拿（allTimeCount 本来就是全期计数），不必再发一轮全表聚合
    val entryCounts = remember(summaries) { summaries.associate { it.bill.id to it.allTimeCount } }
    val recentSlice = remember(recent) { recent.take(3) }
    val expenseBills = remember(summaries) { summaries.filterNot { it.bill.isIncome } }
    val incomeBills = remember(summaries) { summaries.filter { it.bill.isIncome } }
    // 「今天还没记」：本月记过、今天一笔都没有、且确实看着本月时才出现。
    // 给谁记——最近那笔所属的账单；它被归档了就退到第一个支出账单，再没有就收入第一个。
    val nudgeTarget = remember(recent, expenseBills, incomeBills) {
        val lastBillId = recent.firstOrNull()?.billId
        expenseBills.firstOrNull { it.bill.id == lastBillId }
            ?: expenseBills.firstOrNull()
            ?: incomeBills.firstOrNull()
    }
    val showTodayNudge = BuildStatsUseCase.shouldNudgeToday(
        todayCount = todayCount,
        monthCount = monthCount,
        viewingCurrentMonth = viewingCurrentMonth,
        todayDay = today().toEpochDay(),
        nudgeSeenDay = nudgeSeenDay,
    ) && nudgeTarget != null

    // 拖动用的必须是「换实例」的列表：ReorderableListState 在 remember(list, spacing)
    // 时按 list.size 预分配内部数组，传一个实例不变、只在原地增删的列表，
    // 首帧 size=0 建出来的状态会一直用下去并在 ReorderableItem 里越界闪退（v1.11.0 的教训）。
    var expenseRows by remember { mutableStateOf<List<BillSummary>>(emptyList()) }
    var incomeRows by remember { mutableStateOf<List<BillSummary>>(emptyList()) }
    val expenseIds = expenseBills.map { it.bill.id }
    val incomeIds = incomeBills.map { it.bill.id }
    // 只在"集合变了"（新建/归档/删除）时跟库对齐；单纯拖动不改集合，所以不会被冲掉
    LaunchedEffect(expenseIds) { expenseRows = expenseBills }
    LaunchedEffect(incomeIds) { incomeRows = incomeBills }

    /**
     * 落定后整表写一次顺序。
     * sortOrder 是全局字段，所以把两组按新顺序拼起来一起提交，
     * 否则只写一组会把另一组的相对顺序打乱。
     */
    fun commit(expense: List<BillSummary>, income: List<BillSummary>) {
        viewModel.commitOrder(expense.map { it.bill.id } + income.map { it.bill.id })
    }
    val moveExpense: (Int, Int) -> Unit = { from, to ->
        if (from != to && to in expenseRows.indices) {
            val next = expenseRows.toMutableList().apply { add(to, removeAt(from)) }
            expenseRows = next
            commit(next, incomeRows)
        }
    }
    val moveIncome: (Int, Int) -> Unit = { from, to ->
        if (from != to && to in incomeRows.indices) {
            val next = incomeRows.toMutableList().apply { add(to, removeAt(from)) }
            incomeRows = next
            commit(expenseRows, next)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // 左右 20dp、上 8dp 与分析页对齐：切 tab 时顶栏不能挪位置
            contentPadding = PaddingValues(
                start = Spacing.xl, end = Spacing.xl, top = Spacing.sm, bottom = Spacing.listEnd,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item(key = "header") {
                ScreenTopBar(
                    monthFirst = monthFirstDay,
                    canGoNext = canGoNext,
                    onPrevious = viewModel::previousMonth,
                    onNext = viewModel::nextMonth,
                    onPick = viewModel::goToMonth,
                    leading = {
                        IconButton(onClick = onSearch) {
                            Icon(Icons.Default.Search, contentDescription = "搜索记录")
                        }
                    },
                )
            }

            item(key = "hero") {
                HeroTotal(
                    prefix = periodPrefix,
                    net = netBalance,
                    total = periodTotal,
                    todayTotal = todayTotal,
                    dailyAverage = homeDailyAverage,
                    allTimeTotal = allTimeTotal,
                    budgetStatus = BudgetStatus(spent = periodTotal, budget = budget, monthFirst = monthFirstDay),
                    nudgeVisible = showTodayNudge,
                    onNudge = {
                        // 先记下"今天提过了"，再开弹层：哪怕这次没记成，今天也不再重复催
                        viewModel.markNudgeSeen()
                        nudgeTarget?.let { onRecord(it) }
                    },
                    onTodayClick = {
                        val range = dayRange(today())
                        onOpenDay(range.start, range.endExclusive)
                    },
                    onMonthClick = {
                        val range = monthRange(monthFirstDay.year, monthFirstDay.monthValue)
                        onOpenDay(range.start, range.endExclusive)
                    },
                )
            }

            if (recentSlice.isNotEmpty()) {
                item(key = "recent") {
                    Panel(contentPadding = NoPadding) {
                        recentSlice.forEachIndexed { index, entry ->
                            val bill = billById[entry.billId]
                            if (bill != null) {
                                ItemRow(
                                    title = entry.note.ifEmpty { bill.name },
                                    subtitle = "${bill.name} · ${relativeDayLabel(millisToLocalDate(entry.occurredAt))} ${formatTime(entry.occurredAt)}",
                                    amount = entry.amount.toSignedYuanString(bill.isIncome),
                                    iconKey = bill.iconKey,
                                    palette = bill.palette,
                                    onClick = { onOpenBill(bill) },
                                )
                            }
                            if (index != recentSlice.lastIndex) PanelDivider(indent = Col.rowIndent)
                        }
                    }
                }
            }

            // 只记支出时不必突然出现「支出」这个标题，那是在制造新概念；
            // 一旦有了收入账单，两组才需要各自的名字
            val grouped = incomeBills.isNotEmpty()
            item(key = "bills-header") {
                SectionHeader(
                    title = if (grouped) "支出" else "账单",
                    action = { EditToggle(editingExpense) { editingExpense = it } },
                )
            }

            if (!ready) {
                item(key = "skeleton") { SkeletonPanel() }
            } else if (summaries.isEmpty()) {
                item(key = "empty") { EmptyState(onCreateBill = { editorKind = BillKind.EXPENSE }) }
            } else {
                item(key = "expense-list") {
                    BillGroup(
                        rows = if (editingExpense) expenseRows else expenseBills,
                        groupTotal = periodTotal,
                        editMode = editingExpense,
                        onRecord = { onRecord(it) },
                        onOpenDetail = { onOpenBill(it.bill) },
                        onRename = { editing = it.bill },
                        onArchive = { viewModel.setArchived(it.bill, true) },
                        onMove = moveExpense,
                    )
                }
            }

            if (grouped) {
                item(key = "income-header") {
                    SectionHeader(
                        title = "收入",
                        action = { EditToggle(editingIncome) { editingIncome = it } },
                    )
                }
                item(key = "income-list") {
                    BillGroup(
                        rows = if (editingIncome) incomeRows else incomeBills,
                        groupTotal = periodIncome,
                        editMode = editingIncome,
                        onRecord = { onRecord(it) },
                        onOpenDetail = { onOpenBill(it.bill) },
                        onRename = { editing = it.bill },
                        onArchive = { viewModel.setArchived(it.bill, true) },
                        onMove = moveIncome,
                    )
                }
            }
        }

        // 只编辑收入时，首页那个唯一的「+」不能再暗示"新建支出"
        FloatingActionButton(
            onClick = { editorKind = if (editingIncome && !editingExpense) BillKind.INCOME else BillKind.EXPENSE },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Spacing.xl),
            shape = Radius.Card,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(Icons.Default.Add, contentDescription = "新建账单")
        }
    }

    editorKind?.let { kind ->
        BillEditorDialog(
            initial = newBillDraft(kind),
            onDismiss = { editorKind = null },
            onConfirm = { draft, cap ->
                viewModel.saveBill(draft, cap)
                editorKind = null
            },
        )
    }

    editing?.let { bill ->
        BillEditorDialog(
            initial = bill,
            onDismiss = { editing = null },
            onConfirm = { edited, cap ->
                viewModel.saveBill(edited, cap)
                editing = null
            },
            initialCap = caps[bill.id] ?: 0L.cents,
            entryCount = entryCounts[bill.id] ?: 0,
            onDelete = {
                viewModel.deleteBill(bill)
                editing = null
            },
        )
    }
}

/** 区头的「编辑 / 完成」。创建已经交给右下角的加号，这里不再放新建入口。 */
@Composable
private fun EditToggle(editMode: Boolean, onChange: (Boolean) -> Unit) {
    TextButton(onClick = { onChange(!editMode) }) {
        Text(if (editMode) "完成" else "编辑", style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * 一组账单。
 *
 * 平时每条各自成卡、卡间留一个小间隔，分组感来自间距而不是分隔线。
 * 编辑态整组交给 reorderable 库：跨过几行就连着移几行，边缘还会自动滚动，
 * 落库只发生在松手那一刻，所以一次拖动只写一轮。
 */
@Composable
private fun BillGroup(
    rows: List<BillSummary>,
    groupTotal: Cents,
    editMode: Boolean,
    onRecord: (BillSummary) -> Unit,
    onOpenDetail: (BillSummary) -> Unit,
    onRename: (BillSummary) -> Unit,
    onArchive: (BillSummary) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    fun shareOf(summary: BillSummary): Double =
        if (groupTotal.value == 0L) 0.0
        else summary.periodTotal.value.toDouble() / groupTotal.value.toDouble()

    if (editMode) {
        ReorderableColumn(
            list = rows,
            onSettle = onMove,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.fillMaxWidth(),
        ) { index, summary, isDragging ->
            val elevation by animateDpAsState(
                targetValue = if (isDragging) Drag.lift else 0.dp,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "dragElevation",
            )
            ReorderableItem {
                Panel(
                    // 读屏拿不到「拖动」，所以把上移/下移作为自定义动作显式暴露，
                    // 走的是和拖动完全同一条 onMove，不会出现两套行为不一致
                    modifier = Modifier.semantics {
                        customActions = buildList {
                            if (index > 0) {
                                add(CustomAccessibilityAction("上移") { onMove(index, index - 1); true })
                            }
                            if (index < rows.size - 1) {
                                add(CustomAccessibilityAction("下移") { onMove(index, index + 1); true })
                            }
                        }
                    },
                    contentPadding = NoPadding,
                    shadowElevation = elevation,
                ) {
                    BillRow(
                        summary = summary,
                        share = shareOf(summary),
                        editMode = true,
                        isDragging = isDragging,
                        onClick = { onRename(summary) },
                        onOpenDetail = { onOpenDetail(summary) },
                        onArchive = { onArchive(summary) },
                        cardModifier = Modifier.longPressDraggableHandle(),
                        handleModifier = Modifier.draggableHandle(),
                    )
                }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            rows.forEach { summary ->
                Panel(contentPadding = NoPadding) {
                    BillRow(
                        summary = summary,
                        share = shareOf(summary),
                        onClick = { onRecord(summary) },
                        onOpenDetail = { onOpenDetail(summary) },
                    )
                }
            }
        }
    }
}

private fun periodPrefixOf(first: LocalDate): String {
    val now = today()
    return when {
        now.year == first.year && now.monthValue == first.monthValue -> "本月"
        now.year == first.year -> "${first.monthValue}月"
        else -> "${first.year}年${first.monthValue}月"
    }
}

@Composable
private fun HeroTotal(
    prefix: String,
    net: Cents,
    total: Cents,
    todayTotal: Cents,
    dailyAverage: Cents,
    allTimeTotal: Cents,
    budgetStatus: BudgetStatus,
    nudgeVisible: Boolean,
    onNudge: () -> Unit,
    onTodayClick: () -> Unit,
    onMonthClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        // 一横排两个：变动额是这个月口袋的真实变化，支出是花掉多少
        Row(Modifier.fillMaxWidth()) {
            HeroFigure(
                label = "${prefix}变动",
                value = net.toNetYuanString(),
                color = when {
                    net.isNegative() -> MaterialTheme.colorScheme.error
                    net.value > 0L -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.md))
            HeroFigure(
                label = "${prefix}支出",
                value = total.toYuanWithSymbol(),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                onClick = onMonthClick,
            )
        }
        Spacer(Modifier.height(Spacing.lg))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = Alpha.DividerStrong))
        Spacer(Modifier.height(Spacing.md))
        Row(Modifier.fillMaxWidth()) {
            // 今日一格可点，直接看当天全部条目
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(Panels.cornerSm)
                    .clickable(onClick = onTodayClick),
            ) {
                MetricColumn("今日", todayTotal.toYuanString())
            }
            MetricDivider()
            MetricColumn("日均", dailyAverage.toYuanString(), Modifier.weight(1f))
            MetricDivider()
            MetricColumn("累计", allTimeTotal.toYuanString(), Modifier.weight(1f))
        }
        AnimatedVisibility(
            visible = budgetStatus.isActive,
            enter = expandVertically(Motion.enter()) + fadeIn(Motion.enter()),
            exit = shrinkVertically(Motion.exit()) + fadeOut(Motion.exit()),
        ) {
            BudgetLine(budgetStatus)
        }
        AnimatedVisibility(
            visible = nudgeVisible,
            enter = expandVertically(Motion.enter()) + fadeIn(Motion.enter()),
            exit = shrinkVertically(Motion.exit()) + fadeOut(Motion.exit()),
        ) {
            // 一行小字，不加边框不加底色：这是提醒，不是又一个按钮
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(Panels.cornerSm)
                    .clickable(onClick = onNudge)
                    .padding(top = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "今天还没记",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "记一笔",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * 顶部大数字。字号固定（MoneyPair 20sp），最长金额也放得下，
 * 不做运行时自适应——那版在 AnimatedContent 里量到 0 宽度，数字直接不显示。
 */
@Composable
private fun HeroFigure(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier
            .clip(Panels.cornerSm)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (onClick != null) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xs))
        // 金额变化时上滚淡入：记账时你想看到的就是「它涨上去了」
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                (slideInVertically(Motion.enter()) { it / 3 } + fadeIn(Motion.enter()))
                    .togetherWith(slideOutVertically(Motion.exit()) { -it / 3 } + fadeOut(Motion.exit()))
            },
            label = "heroFigure",
        ) { text ->
            Text(
                text = text,
                style = MoneyPair,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
/**
 * 账单行。两个模式共用**完全一样的骨架**：左侧图标/名称/占比条一动不动，
 * 右侧两行各自叠着两个互斥内容（合计↔归档、明细箭头↔双横把手），只淡入淡出。
 *
 * 之所以不用 if/else 直接换结构：那样行高和右侧宽度会跟着内容跳，
 * 一点「编辑」整列跟着动，视觉很割裂。槽位外层框高度固定，跳不了。
 */
@Composable
private fun BillRow(
    summary: BillSummary,
    share: Double,
    onClick: () -> Unit,
    onOpenDetail: () -> Unit,
    editMode: Boolean = false,
    isDragging: Boolean = false,
    cardModifier: Modifier = Modifier,
    handleModifier: Modifier = Modifier,
    onArchive: () -> Unit = {},
) {
    val palette = summary.bill.palette
    val accent = palette.toComposeColor()
    val income = summary.bill.isIncome
    Row(
        Modifier
            .fillMaxWidth()
            .then(cardModifier)
            // 长按的归属必须唯一：编辑态长按归拖动，所以这里不再挂"查看明细"
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (editMode) null else onOpenDetail,
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BillIconBadge(iconKey = summary.bill.iconKey, palette = palette)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = summary.bill.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 周期规则用一枚 12dp 的矢量标记表达，不额外占行、不改行高；
                // 具体是"每月多少钱"放进 contentDescription，读屏和长按都拿得到
                if (summary.bill.isRecurring) {
                    Spacer(Modifier.width(Spacing.xs))
                    Icon(
                        imageVector = Icons.Filled.Repeat,
                        contentDescription = describeRepeat(summary.bill),
                        tint = accent,
                        modifier = Modifier.size(IconSize.xs),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "${summary.periodCount} 笔 · ${formatShare(share)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.sm))
            TrackBar(
                fraction = share.toFloat(),
                color = accent,
                modifier = Modifier.width(Col.groupBar),
                height = Bar.regular,
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        // 尾部整块高度钉死（26 + 4 + 26 = Rows.tailSize），两个模式各自往里换内容：
        // 普通态上下两排（合计 / 明细箭头），编辑态左右并排（归档 / 双横把手）。
        // 用 AnimatedVisibility 而不是 Crossfade：Crossfade 的淡出内容一直挂在语义树里，
        // 看不见的「查看明细」照样能被点、被读屏念到，等于两个鬼控件。
        Column(
            Modifier
                .height(Rows.tailSize)
                .widthIn(min = Rows.tailSize),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.End,
        ) {
            AnimatedVisibility(
                visible = !editMode,
                enter = fadeIn(tween(Motion.STANDARD)),
                exit = fadeOut(tween(Motion.STANDARD)),
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    AnimatedContent(
                        targetState = summary.periodTotal.toSignedYuanString(income),
                        transitionSpec = { fadeIn(Motion.enter()) togetherWith fadeOut(Motion.exit()) },
                        label = "billTotal",
                    ) { text ->
                        Box(Modifier.height(Rows.groupHeader), contentAlignment = Alignment.Center) {
                            Text(
                                text = text,
                                style = MoneyTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.height(Spacing.xs))
                    DetailChip(accent = accent, onClick = onOpenDetail)
                }
            }
            AnimatedVisibility(
                visible = editMode,
                enter = fadeIn(tween(Motion.STANDARD)),
                exit = fadeOut(tween(Motion.STANDARD)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ArchiveChip(onArchive = onArchive)
                    Spacer(Modifier.width(Spacing.sm))
                    Box(
                        // testTag 带账单名：测试要能区分「哪一行进了编辑态」，而不是数屏幕上有几个把手
                        handleModifier
                            .size(IconSize.xl)
                            .testTag("drag-handle-${summary.bill.name}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        ReorderHandle(
                            color = if (isDragging) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** 归档：与明细箭头同尺寸的色块，换模式时右侧那一列的宽度不会变。 */
@Composable
private fun ArchiveChip(onArchive: () -> Unit) {
    Box(
        Modifier
            .size(IconSize.xl)
            .clip(Radius.Chip)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onArchive),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Archive,
            contentDescription = "归档此账单",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(IconSize.sm),
        )
    }
}

/** 明细入口：与账单同色的小色块，摆在金额下方。 */
@Composable
private fun DetailChip(accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(IconSize.xl)
            .clip(Radius.Chip)
            .background(accent.copy(alpha = Alpha.SelectedTint))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = "查看明细",
            tint = accent,
            modifier = Modifier.size(IconSize.sm),
        )
    }
}

@Composable
private fun BudgetLine(status: BudgetStatus) {
    val over = status.isOver
    val barColor = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    // 超支时显示「超了多少」而不是「剩余 -xxx」，负数余额在这里没有意义
    val gap = if (over) (-status.remaining.value).cents else status.remaining
    Column(Modifier.padding(top = Spacing.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "预算 ${status.budget.toYuanString()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = (if (over) "超 " else "剩 ") + gap.toYuanString(),
                style = MaterialTheme.typography.labelLarge,
                color = barColor,
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        TrackBar(fraction = status.fraction, color = barColor, height = Bar.thick)
        // 只多一行小字，不多一个控件：月中的"日均还能花多少"是这页最该知道却最难自己算的数
        val allowance = status.pace?.takeIf { it.isActive && !it.isOverNow && it.allowance.value > 0L }
        if (allowance != null) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "日均还能花 ${allowance.allowance.toYuanString()} · 剩 ${allowance.daysLeft} 天",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 首次查询回来前占位，避免冷启动闪一下「还没有账单」的空状态。 */
@Composable
private fun SkeletonPanel() {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(Motion.PULSE, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    val tint = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha)
    Panel(contentPadding = NoPadding) {
        repeat(3) { index ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(Badge.lg).clip(CircleShape).background(tint))
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .width(Skeleton.titleWidth)
                            .height(Skeleton.titleHeight)
                            .clip(Radius.pillOf(Skeleton.titleHeight))
                            .background(tint),
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Box(
                        Modifier
                            .width(Skeleton.subWidth)
                            .height(Skeleton.subHeight)
                            .clip(Radius.pillOf(Skeleton.subHeight))
                            .background(tint),
                    )
                }
                Box(
                    Modifier
                        .width(Skeleton.chipWidth)
                        .height(Skeleton.chipHeight)
                        .clip(Radius.pillOf(Skeleton.chipHeight))
                        .background(tint),
                )
            }
            if (index != 2) PanelDivider()
        }
    }
}

@Composable
private fun EmptyState(onCreateBill: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BillIconBadge(
            iconKey = "category",
            palette = BillPalette.SLATE,
            size = Badge.xl,
            iconSize = IconSize.xl,
        )
        Spacer(Modifier.height(Spacing.md))
        Text("还没有账单", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = "建一个「吃饭」「打车」这样的账单，点它就记一笔",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.md))
        TextButton(onClick = onCreateBill) { Text("新建第一个账单") }
    }
}


private fun formatShare(share: Double): String = String.format(Locale.CHINA, "%.0f%%", share * 100)
