package com.klocktx.moneyblocks.ui.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.klocktx.moneyblocks.core.AppContainer
import com.klocktx.moneyblocks.ui.LocalContainer
import com.klocktx.moneyblocks.ui.LocalToast
import com.klocktx.moneyblocks.ui.common.AppToast
import com.klocktx.moneyblocks.ui.theme.MonetTheme
import com.klocktx.moneyblocks.ui.theme.MoneyBlocksTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 设置页的组合冒烟。
 *
 * 这一页从「账单」页拆出来，页面上再没有 reorderable 那种组合期就崩的东西，
 * 但它承载了备份/恢复这条新链路的所有入口——入口没渲染出来，功能等于不存在。
 * 备份那几行在页面底部，LazyColumn 不组合视口外内容，所以要先滚过去。
 */
@RunWith(AndroidJUnit4::class)
// 屏幕给足高度：这一页的面板会随功能增加而变高，靠手势滚到底既脆弱又慢，
// 而"节点在不在"才是这里要判的东西。
@Config(sdk = [34], qualifiers = "w411dp-h3200dp")
@Suppress("DEPRECATION") // createComposeRule v1；v2 换 StandardTestDispatcher，要显式同步
class SettingsScreenSmokeTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var container: AppContainer

    private fun showScreen() {
        container = AppContainer(ApplicationProvider.getApplicationContext())
        compose.setContent {
            CompositionLocalProvider(
                LocalContainer provides container,
                LocalToast provides { _: AppToast -> },
            ) {
                MoneyBlocksTheme { SettingsScreen() }
            }
        }
    }

    private fun awaitTexts(vararg texts: String) {
        compose.waitUntil(TIMEOUT_MS) {
            texts.all { text -> compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        }
    }

    @Test
    fun preferencesAndExportRender() {
        showScreen()
        awaitTexts("黑白模式", "主题色", "月度预算", "导出 CSV")
    }

    /** 六颗色板都要能同时摆出来：少一颗就等于少一个主题。 */
    @Test
    fun everyThemeSwatchRenders() {
        showScreen()
        awaitTexts("主题色")
        MonetTheme.entries.forEach { theme ->
            assertEquals("缺色板：${theme.key}", 1, chip(theme).fetchSemanticsNodes().size)
        }
    }

    /** 选中的那颗描述里带「已选中」，所以按前缀找而不是全等。 */
    private fun chip(theme: MonetTheme) =
        compose.onAllNodes(hasContentDescription("${theme.label} · ${theme.note}", substring = true))

    /** 点色板要真的落到设置里——这条管的是「UI → DataStore → 重启还在」那半截。 */
    @Test
    fun pickingASwatchPersistsIt() {
        showScreen()
        awaitTexts("主题色")
        val target = MonetTheme.WATERLILY
        chip(target)[0].performClick()
        // 区头右侧的主题名要跟着换。这一步本身就是完整回路：
        // 点击 → ViewModel 写 DataStore → Flow 推回来 → 界面重绘，中间任何一环断了这里就红。
        compose.waitUntil(8_000) {
            compose.onAllNodesWithText(target.label).fetchSemanticsNodes().isNotEmpty()
        }
        // 注意别把 runBlocking 塞进 waitUntil 的谓词里：DataStore 的写入要主线程跑，
        // 谓词里阻塞主线程等于自己等自己，实测会把这条测试拖成超时。
        assertEquals(
            target.key,
            runBlocking { container.settingsRepository.monetThemeKey.first() },
        )
    }

    @Test
    fun backupRowsRender() {
        showScreen()
        awaitTexts(
            "备份账本", "从备份恢复", "还没备份过。换机或卸载之前先导一份",
        )
    }

    @Test
    fun aboutSectionRenders() {
        showScreen()
        awaitTexts("数据", "关于")
        // 这两行都带动态尾巴（版本号、账单与条目数），只能按前缀认
        awaitContains("亭宣记账 ")
        awaitContains("数据只存在这台手机上")
    }

    /** 文案里带会变的数字，所以按子串找而不是全等。 */
    private fun awaitContains(needle: String) {
        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodesWithText(needle, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 30_000L
    }
}
