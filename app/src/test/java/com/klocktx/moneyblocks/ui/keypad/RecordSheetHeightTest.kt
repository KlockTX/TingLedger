package com.klocktx.moneyblocks.ui.keypad

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.klocktx.moneyblocks.core.AppContainer
import com.klocktx.moneyblocks.domain.model.BillKind
import com.klocktx.moneyblocks.domain.model.BillPalette
import com.klocktx.moneyblocks.ui.LocalContainer
import com.klocktx.moneyblocks.ui.LocalToast
import com.klocktx.moneyblocks.ui.common.AppToast
import com.klocktx.moneyblocks.ui.theme.MoneyBlocksTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 记账弹层必须装得进一台小手机。
 *
 * 这条测试存在的理由：`ModalBottomSheet` 的可见高度**等于内容高度**
 * （拆 material3 1.4.0 字节码确认过，展开位就是 offset 0，它内部也没有滚动容器）。
 * 所以"弹层顶到全屏"不是布局 bug，而是内容本身长过了屏幕；
 * 而想靠"封顶 + 内容可滚"去治它，会让同一个纵向手势既滚内容又拖弹层——就是抽搐。
 * 唯一的正解是让内容比屏幕矮，那就得先有真实数字。
 *
 * 注意屏幕 qualifier 要开得很高：**在小屏幕上量会被窗口裁掉**，
 * 内容 738 还是 692 都只会读出"窗口减状态栏"那一个数，看不出改动有没有生效。
 * 这里在 1600dp 的窗口上量真实高度，再拿小手机的可用高度当判据。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp")
@Suppress("DEPRECATION")
class RecordSheetHeightTest {

    @get:Rule
    val compose = createComposeRule()

    /** 一台窄高的现代手机：411×740dp，扣掉状态栏与手势条后大约 700dp 可用。 */
    private val budgetDp = 700f

    @Test
    fun sheetFitsOnASmallPhone() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        val density = ctx.resources.displayMetrics.density
        val container = AppContainer(ctx)
        compose.setContent {
            CompositionLocalProvider(
                LocalContainer provides container,
                LocalToast provides { _: AppToast -> },
            ) {
                MoneyBlocksTheme {
                    RecordSheet(
                        target = RecordTarget(
                            billId = 1L,
                            billName = "吃饭",
                            billIconKey = "restaurant",
                            palette = BillPalette.GREEN,
                            kind = BillKind.EXPENSE,
                        ),
                        onSubmitted = { _, _ -> },
                        onDismiss = {},
                    )
                }
            }
        }
        compose.waitForIdle()

        val heightDp = compose.onNodeWithTag("record-sheet").fetchSemanticsNode()
            .boundsInRoot.height / density
        println("SHEET_HEIGHT_DP=%.1f WINDOW_DP=%.1f".format(heightDp, 1600f))
        assertTrue(
            "弹层内容 %.1f dp，小手机可用约 %.0f dp".format(heightDp, budgetDp),
            heightDp <= budgetDp,
        )
    }
}
