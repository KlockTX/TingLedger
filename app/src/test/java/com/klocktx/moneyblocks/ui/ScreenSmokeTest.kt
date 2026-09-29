package com.klocktx.moneyblocks.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.klocktx.moneyblocks.core.AppContainer
import com.klocktx.moneyblocks.core.cents
import com.klocktx.moneyblocks.domain.model.BillKind
import com.klocktx.moneyblocks.domain.model.BillPalette
import com.klocktx.moneyblocks.domain.usecase.MonthHeatmap
import com.klocktx.moneyblocks.ui.analytics.AnalyticsScreen
import com.klocktx.moneyblocks.ui.bill.BillDetailScreen
import com.klocktx.moneyblocks.ui.common.AppToast
import com.klocktx.moneyblocks.ui.home.HomeScreen
import com.klocktx.moneyblocks.ui.keypad.RecordSheet
import com.klocktx.moneyblocks.ui.keypad.RecordTarget
import com.klocktx.moneyblocks.ui.search.SearchScreen
import com.klocktx.moneyblocks.ui.settings.SettingsScreen
import com.klocktx.moneyblocks.ui.theme.MoneyBlocksTheme
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 每个页面都在 JVM 里真的组合一次。
 *
 * 起因是 v1.11.0 那个「进账单页闪退」：编译、纯逻辑单测、签名校验三个信号全绿，
 * 但崩溃只发生在组合期。这里把五块主要界面都过一遍，等的是**只有异步数据回来之后
 * 才会出现**的节点，否则测到的只是「空态没崩」。
 */
@RunWith(AndroidJUnit4::class)
// 屏幕给足高度，两组账单才会同时组合：LazyColumn 视口外的内容根本不存在，
// 那样「另一组没被带着进编辑态」这种断言只能靠滚，而滚动在编辑态里会被拖动抢走。
@Config(sdk = [34], qualifiers = "w411dp-h3200dp")
@Suppress("DEPRECATION") // createComposeRule v1；v2 换 StandardTestDispatcher，要显式同步
class ScreenSmokeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setContent(screen: @Composable () -> Unit) {
        val container = AppContainer(ApplicationProvider.getApplicationContext())
        compose.setContent {
            CompositionLocalProvider(
                LocalContainer provides container,
                LocalToast provides { _: AppToast -> },
            ) {
                MoneyBlocksTheme { screen() }
            }
        }
    }

    /**
     * 顶栏必须钉在同一个位置：左右各 20dp 页边距、月份胶囊水平居中。
     * 首页与分析页共用 ScreenTopBar，这条断言防的是以后有人再各自摆一行，
     * 切 tab 时胶囊横跳几十 dp。
     */
    private fun assertTopBarAnchored() {
        val bar = compose.onNodeWithTag("top-bar").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val side = with(compose.density) { 20.dp.toPx() }
        assertEquals(root.width / 2f, bar.center.x, 1f)
        assertEquals(root.width - side * 2, bar.width, 1f)
    }

    private fun awaitText(text: String) {
        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun homeRendersSeededBills() {
        setContent {
            HomeScreen(
                onSearch = {},
                onRecord = {},
                onOpenBill = {},
                onOpenDay = { _, _ -> },
            )
        }
        awaitText("吃饭")
        awaitText("本月变动")
        assertTopBarAnchored()
        // 这个月一笔都没记，所以「今天还没记」不该冒出来——催一个还没开始用的人只会反感
        assertTrue(
            compose.onAllNodesWithText("今天还没记").fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun analyticsRendersEmptyMonth() {
        setContent { AnalyticsScreen(onOpenDay = { _, _ -> }) }
        awaitText("这个月还没有支出记录")
        assertTopBarAnchored()
    }

    @Test
    fun detailRendersItsBill() {
        setContent { BillDetailScreen(billId = 1L, onBack = {}, onOpenSheet = {}) }
        awaitText("吃饭")
    }

    @Test
    fun searchShowsItsHint() {
        setContent { SearchScreen(onBack = {}, onOpenBill = {}) }
        awaitText("可以搜什么")
    }

    /** 筛选面板是列表第一项，永远在：三行各自是一个维度，缺一行就少一个问法。 */
    @Test
    fun searchOffersAllThreeFilterDimensions() {
        setContent { SearchScreen(onBack = {}, onOpenBill = {}) }
        awaitText("方向")
        awaitText("金额")
        awaitText("时间")
        awaitText("不限")
    }

    /**
     * 日历格子数 = 前导空格 + 当月天数。
     *
     * 期望值当场从 `MonthHeatmap` 算，不写死数字：这条测的是**界面有没有把模型给的格子
     * 一格不落摆出来**（补位、chunked 分行、末行 Spacer 都会影响节点数），
     * 而对齐本身已由 HeatmapTest 逐月核过。
     */
    @Test
    fun analyticsRendersEveryCellTheModelProduces() {
        setContent { AnalyticsScreen(onOpenDay = { _, _ -> }) }
        awaitText("消费日历")
        val first = java.time.LocalDate.now().withDayOfMonth(1)
        val expected = MonthHeatmap.columnOf(first.dayOfWeek) + first.lengthOfMonth()
        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodesWithTag("heat-cell").fetchSemanticsNodes().size == expected
        }
        assertEquals(expected, compose.onAllNodesWithTag("heat-cell").fetchSemanticsNodes().size)
        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodesWithText("连续记了", substring = true).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("还没开始连").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun recordSheetComposesWithATarget() {
        val target = RecordTarget(
            billId = 1L,
            billName = "吃饭",
            billIconKey = "restaurant",
            palette = BillPalette.GREEN,
            kind = BillKind.EXPENSE,
        )
        setContent {
            RecordSheet(target = target, onSubmitted = { _, _ -> }, onDismiss = {})
        }
        awaitText("记一笔")
        // 标题行从第一帧起就带着账单名（来自 target，不等 bills），且不再有第二行「记入」。
        // 总高度能不能装进屏幕由 RecordSheetHeightTest 量，这里只钉"账单身份只出现一次"。
        compose.onNodeWithTag("record-title-row").assertExists()
        assertTrue(
            "「记入」那一行又回来了",
            compose.onAllNodesWithText("记入").fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun rootComposesWithItsBottomBar() {
        // 快捷记账的发布与意图处理都挂在根组件上，这里挂了就是整机启动白屏
        val container = AppContainer(ApplicationProvider.getApplicationContext())
        compose.setContent { MoneyBlocksRoot(container) }
        compose.waitForIdle()
        awaitText("账本")
    }

    @Test
    fun settingsRenderPreferences() {
        setContent { SettingsScreen() }
        awaitText("黑白模式")
    }

    /**
     * 首页编辑态两件事：
     * 一是行尾从「合计 + 明细」换成「归档 + 双横」，明细入口必须消失（否则长按既要看明细
     * 又要拖动，两个手势打架）；
     * 二是支出与收入**各自独立**：只点支出的编辑，收入行必须一个把手都没有、明细照旧在。
     */
    @Test
    fun homeEditModeIsPerGroupAndSwapsDetailForHandle() {
        setContent {
            HomeScreen(
                onSearch = {},
                onRecord = {},
                onOpenBill = {},
                onOpenDay = { _, _ -> },
            )
        }
        awaitText("吃饭")
        assertEquals(2, editTexts().size)
        assertTrue(detailChips().isEmpty().not())
        assertTrue(handlesFor("吃饭").isEmpty())

        compose.onAllNodesWithText("编辑")[0].performClick()
        awaitText("完成")
        assertEquals(1, editTexts().size)
        assertEquals(1, compose.onAllNodesWithText("完成").fetchSemanticsNodes().size)

        assertTrue(handlesFor("吃饭").isEmpty().not())
        assertTrue(archiveChips().isEmpty().not())
        // 支出组 5 个明细入口全没了，只剩收入那一个
        assertEquals(1, detailChips().size)
        // 收入组没被带进编辑态：它自己那行没有把手
        assertTrue("收入行被带着进编辑态了", handlesFor("工资").isEmpty())
    }

    /**
     * 把手按账单名打标，才能区分「哪一行进了编辑态」，而不是数屏幕上一共有几个把手。
     * 整行是 mergeDescendants 节点，testTag 留在未合并的子节点上，所以查它要走未合并树。
     */
    private fun handlesFor(billName: String) = compose
        .onAllNodesWithTag("drag-handle-$billName", useUnmergedTree = true)
        .fetchSemanticsNodes()

    private fun editTexts() = compose.onAllNodesWithText("编辑").fetchSemanticsNodes()

    private fun archiveChips() =
        compose.onAllNodesWithContentDescription("归档此账单").fetchSemanticsNodes()

    private fun detailChips() =
        compose.onAllNodesWithContentDescription("查看明细").fetchSemanticsNodes()

    /**
     * 设了总预算之后，「预算节奏」那块要真的组合出来。
     *
     * 这个类存在的理由就是"只有数据回来之后才出现的节点"——节奏块正是这种：
     * 它按 `pace.isActive` 条件出现，一旦组合期越界（比如月初日均除零）只有这条路能发现。
     * 结尾把预算清掉，免得影响同一个类里的其它用例。
     */
    @Test
    fun analyticsComposesThePaceBlockOnceABudgetExists() {
        val container = AppContainer(ApplicationProvider.getApplicationContext())
        // 写完先读一次：DataStore 的首次读才把它的后台作用域拉起来。
        // 不等这一步直接进组合，界面可能一直收不到那条 emission（我这边实测超时过一次，30s 假红）。
        kotlinx.coroutines.runBlocking {
            container.settingsRepository.setMonthlyBudget(3_000_00L.cents)
            assertEquals(3_000_00L, container.settingsRepository.monthlyBudget.first().value)
        }
        try {
            setContent { AnalyticsScreen(onOpenDay = { _, _ -> }) }
            compose.waitUntil(TIMEOUT_MS) { hasTextNow("预算节奏") }
            assertTrue("节奏块出现了却没有那句日均可用", hasTextNow("日均可用"))
        } finally {
            kotlinx.coroutines.runBlocking { container.settingsRepository.setMonthlyBudget(0L.cents) }
        }
    }

    private fun hasTextNow(text: String) =
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        // Robolectric 下首次建库 + Room 首查会明显偏慢（实测有一条 11.6s），
        // 超时留足余量，否则这台机器上会偶发假红。
        const val TIMEOUT_MS = 30_000L
    }
}
