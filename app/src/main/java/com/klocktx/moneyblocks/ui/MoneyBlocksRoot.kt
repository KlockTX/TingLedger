package com.klocktx.moneyblocks.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.klocktx.moneyblocks.core.AppContainer
import com.klocktx.moneyblocks.core.cents
import com.klocktx.moneyblocks.core.toYuanWithSymbol
import com.klocktx.moneyblocks.ui.analytics.AnalyticsScreen
import com.klocktx.moneyblocks.ui.bill.BillDetailScreen
import com.klocktx.moneyblocks.ui.common.AppToast
import com.klocktx.moneyblocks.ui.common.ToastHost
import com.klocktx.moneyblocks.ui.day.DaySheet
import com.klocktx.moneyblocks.ui.home.HomeScreen
import com.klocktx.moneyblocks.ui.keypad.RecordSheet
import com.klocktx.moneyblocks.ui.keypad.RecordTarget
import com.klocktx.moneyblocks.ui.keypad.asRecordTarget
import com.klocktx.moneyblocks.ui.search.SearchScreen
import com.klocktx.moneyblocks.ui.settings.SettingsScreen
import com.klocktx.moneyblocks.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val ANALYTICS = "analytics"
    const val SETTINGS = "settings"
    const val DETAIL = "bill/{billId}"
    const val SEARCH = "search"

    fun detail(billId: Long) = "bill/$billId"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "账本", Icons.AutoMirrored.Filled.ReceiptLong),
    Tab(Routes.ANALYTICS, "分析", Icons.Default.QueryStats),
    Tab(Routes.SETTINGS, "设置", Icons.Default.Settings),
)

@Composable
fun MoneyBlocksRoot(container: AppContainer, shortcut: Pair<Long, Long> = 0L to 0L) {
    CompositionLocalProvider(LocalContainer provides container) {
        AppScaffold(shortcut)
    }
}

@Composable
private fun AppScaffold(shortcut: Pair<Long, Long>) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    var recordTarget by remember { mutableStateOf<RecordTarget?>(null) }
    var dayWindow by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var toast by remember { mutableStateOf<AppToast?>(null) }

    // 账单一变就刷新桌面长按快捷项，用户不需要进过一次设置
    val allBills by container.billRepository.observeBills()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    LaunchedEffect(allBills) { container.publishQuickShortcuts(allBills) }

    // 从快捷项进来时，直接替用户弹到那条账单的录入层
    LaunchedEffect(shortcut) {
        val billId = shortcut.first
        if (billId > 0L) {
            container.billRepository.find(billId)?.let { recordTarget = it.asRecordTarget() }
        }
    }

    // 计时完全由这里控制，不依赖组件是否可见，提示条不会再挂着不消失
    LaunchedEffect(toast) {
        val shown = toast ?: return@LaunchedEffect
        delay(shown.durationMs)
        toast = null
    }

    CompositionLocalProvider(LocalToast provides { message -> toast = message }) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                bottomBar = {
                    // 详情/搜索是全屏场景，底栏会挤压弹层和内容，只在主标签上显示
                    if (tabs.any { it.route == currentRoute }) {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            tabs.forEach { tab ->
                                NavigationBarItem(
                                    selected = currentRoute == tab.route,
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            popUpTo(Routes.HOME) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = {
                                        Text(tab.label, style = MaterialTheme.typography.labelMedium)
                                    },
                                    alwaysShowLabel = true,
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                        indicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME,
                    modifier = Modifier.padding(padding),
                    // 只淡入淡出，不做水平位移：顶栏已经对齐了，再滑一下反而像没对齐。
                    // 位移是「你去了哪儿」的线索，但用户在意的正是「别动」——两相权衡留下不动的那个。
                    enterTransition = { fadeIn(tween(Motion.STANDARD, easing = Motion.Decelerate)) },
                    exitTransition = { fadeOut(tween(Motion.QUICK)) },
                    popEnterTransition = { fadeIn(tween(Motion.STANDARD)) },
                    popExitTransition = { fadeOut(tween(Motion.QUICK)) },
                ) {
                    composable(Routes.HOME) {
                        HomeScreen(
                            onSearch = { navController.navigate(Routes.SEARCH) },
                            onOpenDay = { start, end -> dayWindow = start to end },
                            onRecord = { summary -> recordTarget = summary.bill.asRecordTarget() },
                            onOpenBill = { bill -> navController.navigate(Routes.detail(bill.id)) },
                        )
                    }
                    composable(Routes.ANALYTICS) {
                        AnalyticsScreen(
                            onOpenDay = { start, end -> dayWindow = start to end },
                        )
                    }
                    composable(Routes.SETTINGS) { SettingsScreen() }
                    composable(Routes.SEARCH) {
                        SearchScreen(
                            onBack = { navController.popBackStack() },
                            onOpenBill = { bill -> navController.navigate(Routes.detail(bill.id)) },
                        )
                    }
                    composable(
                        route = Routes.DETAIL,
                        arguments = listOf(navArgument("billId") { type = NavType.LongType }),
                    ) { entry ->
                        val billId = entry.arguments?.getLong("billId") ?: 0L
                        BillDetailScreen(
                            billId = billId,
                            onBack = { navController.popBackStack() },
                            onOpenSheet = { target -> recordTarget = target },
                        )
                    }
                }
            }

            ToastHost(
                toast = toast,
                onDismiss = { toast = null },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    dayWindow?.let { (from, until) ->
        DaySheet(
            start = from,
            endExclusive = until,
            onDismiss = { dayWindow = null },
            onOpenBill = { bill ->
                dayWindow = null
                navController.navigate(Routes.detail(bill.id))
            },
        )
    }

    recordTarget?.let { target ->
        RecordSheet(
            target = target,
            onSubmitted = { entryId, cents ->
                recordTarget = null
                val wasEditing = target.editing != null
                toast = AppToast(
                    message = (if (wasEditing) "已修改 " else "已记 ") + cents.cents.toYuanWithSymbol(),
                    actionLabel = if (wasEditing) null else "撤销",
                    onAction = if (wasEditing) null else {
                        { scope.launch { container.entryRepository.delete(entryId) } }
                    },
                    leadingIcon = if (wasEditing) null else Icons.Default.Check,
                )
            },
            onDismiss = { recordTarget = null },
        )
    }
}
