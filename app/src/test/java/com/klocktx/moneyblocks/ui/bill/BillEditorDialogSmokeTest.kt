package com.klocktx.moneyblocks.ui.bill

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.klocktx.moneyblocks.core.Cents
import com.klocktx.moneyblocks.core.cents
import com.klocktx.moneyblocks.core.today
import com.klocktx.moneyblocks.domain.model.Bill
import com.klocktx.moneyblocks.domain.model.BillKind
import com.klocktx.moneyblocks.domain.model.BillPalette
import com.klocktx.moneyblocks.domain.model.BillRepeat
import com.klocktx.moneyblocks.ui.theme.MoneyBlocksTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 账单编辑弹窗的组合冒烟。屏幕用真实手机尺寸（411×891dp）而不是默认那档很小的合成屏幕，
 * 这样"内容比弹窗高"会真的发生，颜色行有没有被裁掉才测得出来。
 *
 * 上一版直接用 Material3 的 `AlertDialog`，而它在这个版本**不给 text 槽加滚动**
 * （没有 textScrollFlags 那个参数），图标 + 颜色 + 周期三组一起就比屏高，
 * 最底下那行颜色被整行裁掉——是装机才发现的。现在壳子自己搭、中间可滚。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
@Suppress("DEPRECATION")
class BillEditorDialogSmokeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setContent(content: @Composable () -> Unit) {
        compose.setContent { MoneyBlocksTheme { content() } }
    }

    private fun hasText(text: String, substring: Boolean = false) =
        compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    /**
     * 周期那三项只在开了「自动记账」之后出现，而且没填每期金额时不给保存。
     * 这类"条件出现的控件"最容易在折叠分支里留下崩溃或漏渲染，所以真的点一遍。
     */
    @Test
    fun armsRepeatRuleAndGatesSave() {
        var saved: Bill? = null
        setContent {
            BillEditorDialog(
                initial = newBillDraft(BillKind.EXPENSE),
                onDismiss = {},
                onConfirm = { bill, _ -> saved = bill },
            )
        }

        assertTrue("缺少「自动记账」分组", hasText("自动记账"))
        assertFalse("没开周期就出现了金额框", hasText("每期金额", substring = true))

        compose.onNodeWithText("每月").performClick()
        assertTrue("开了周期没有金额框", hasText("每期金额", substring = true))
        assertTrue("没有提示缺金额", hasText("填上每期金额才会自动入账"))
        compose.onNodeWithTag("repeat-amount").performTextInput("1200.5")
        assertTrue("没有提示下次入账日", hasText("每月自动入账，下次", substring = true))
        compose.onNodeWithText("创建").assertIsNotEnabled()

        compose.onNodeWithTag("bill-name").performTextInput("房租")
        compose.onNodeWithText("创建").assertIsEnabled().performClick()

        val bill = saved
        assertNotNull("保存没有回调", bill)
        assertEquals(BillRepeat.MONTHLY, bill!!.repeat)
        assertEquals(120_050L, bill.repeatAmount.value)
        assertEquals(today().toEpochDay(), bill.repeatAnchorDay)
    }

    /** 已经设了周期的账单进弹窗，三项是回填好的，不是从头再填一遍。 */
    @Test
    fun restoresAnExistingRepeatRule() {
        setContent {
            BillEditorDialog(
                initial = Bill(
                    id = 7,
                    name = "房租",
                    iconKey = "home",
                    palette = BillPalette.AMBER,
                    repeat = BillRepeat.WEEKLY,
                    repeatAmount = 120_005L.cents,
                    repeatAnchorDay = LocalDate.of(2026, 9, 3).toEpochDay(),
                    repeatCursorDay = LocalDate.of(2026, 9, 24).toEpochDay(),
                ),
                onDismiss = {},
                onConfirm = { _, _ -> },
            )
        }
        compose.onNodeWithTag("repeat-amount").assertTextContains("1200.05")
        assertTrue("首次入账日没有回填", hasText("9月3日"))
        assertTrue("规则完整却不提示下次入账", hasText("下次", substring = true))
        compose.onNodeWithText("保存").assertIsEnabled()
    }

    /** 关掉周期时，交回的 Bill 必须把日期一起清零，否则库里留着一条"看不见的规则"。 */
    @Test
    fun turningRepeatOffClearsTheAnchor() {
        var saved: Bill? = null
        setContent {
            BillEditorDialog(
                initial = Bill(
                    id = 8,
                    name = "订阅",
                    iconKey = "ondemand_video",
                    palette = BillPalette.VIOLET,
                    repeat = BillRepeat.MONTHLY,
                    repeatAmount = 2_500L.cents,
                    repeatAnchorDay = LocalDate.of(2026, 9, 1).toEpochDay(),
                ),
                onDismiss = {},
                onConfirm = { bill, _ -> saved = bill },
            )
        }
        compose.onNodeWithText("不重复").performClick()
        compose.onNodeWithText("保存").performClick()

        val bill = saved
        assertNotNull(bill)
        assertEquals(BillRepeat.NONE, bill!!.repeat)
        assertEquals(0L, bill.repeatAnchorDay)
    }

    /**
     * 上限只对支出有意义：收入账单那不该出现这一行；而填了的必须原样交回去，
     * 已经设过的必须回填得到——否则每次编辑都在悄悄清零。
     */
    @Test
    fun capFieldAppearsOnlyForExpenseAndRoundTrips() {
        setContent {
            BillEditorDialog(
                initial = newBillDraft(BillKind.INCOME),
                onDismiss = {},
                onConfirm = { _, _ -> },
            )
        }
        assertFalse("收入账单出现了月度上限", hasText("月度上限", substring = true))
    }

    @Test
    fun capIsRefilledAndSurvivesAnUntouchedSave() {
        var savedCap: Cents? = null
        setContent {
            BillEditorDialog(
                initial = Bill(
                    id = 9,
                    name = "吃饭",
                    iconKey = "restaurant",
                    palette = BillPalette.GREEN,
                ),
                onDismiss = {},
                onConfirm = { _, cap -> savedCap = cap },
                initialCap = 123_456L.cents,
            )
        }
        compose.onNodeWithTag("bill-cap").assertTextContains("1234.56")
        compose.onNodeWithText("保存").performClick()
        assertEquals("没动上限字段就不该改它", 123_456L, savedCap?.value)
    }

    @Test
    fun typedCapIsHandedBackInCents() {
        var savedCap: Cents? = null
        setContent {
            BillEditorDialog(
                initial = newBillDraft(BillKind.EXPENSE),
                onDismiss = {},
                onConfirm = { _, cap -> savedCap = cap },
            )
        }
        compose.onNodeWithTag("bill-name").performTextInput("娱乐")
        compose.onNodeWithTag("bill-cap").performTextInput("800.5")
        compose.onNodeWithText("创建").performClick()
        assertEquals(80_050L, savedCap?.value)
    }

    @Test
    fun anEmptyCapFieldMeansNoCap() {
        var savedCap: Cents? = null
        setContent {
            BillEditorDialog(
                initial = newBillDraft(BillKind.EXPENSE),
                onDismiss = {},
                onConfirm = { _, cap -> savedCap = cap },
            )
        }
        compose.onNodeWithTag("bill-name").performTextInput("娱乐")
        compose.onNodeWithText("创建").performClick()
        assertEquals("留空是「不设限」，不是保留旧值", 0L, savedCap?.value)
    }

    /**
     * 颜色行必须**滚得动、看得见**。这条就是钉住上面那个回归的：
     * 节点存在不代表没被裁掉（Compose 对被裁剪的语义节点照样报存在），
     * 所以先 performScrollTo 再 assertIsDisplayed。
     */
    @Test
    fun colorRowIsReachable() {
        setContent {
            BillEditorDialog(
                initial = newBillDraft(BillKind.EXPENSE),
                onDismiss = {},
                onConfirm = { _, _ -> },
            )
        }
        val colors = compose.onNodeWithText("颜色")
        colors.performScrollTo()
        colors.assertIsDisplayed()

        val icons = compose.onNodeWithText("图标")
        icons.performScrollTo()
        icons.assertIsDisplayed()
    }
}
