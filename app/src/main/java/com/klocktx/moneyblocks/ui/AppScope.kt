package com.klocktx.moneyblocks.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.klocktx.moneyblocks.MoneyBlocksApp
import com.klocktx.moneyblocks.core.AppContainer
import com.klocktx.moneyblocks.ui.common.AppToast

val LocalContainer = compositionLocalOf<AppContainer> { error("AppContainer 未提供") }

/** 跨页面复用的轻提示入口：调用 LocalToast.current(AppToast(...)) 弹出。 */
val LocalToast = compositionLocalOf<(AppToast) -> Unit> { error("Toast 未提供") }

@Composable
fun rememberAppContainer(): AppContainer =
    (LocalContext.current.applicationContext as MoneyBlocksApp).container

@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = LocalContainer.current
    return viewModel(
        key = key,
        factory = viewModelFactory { initializer { create(container) } },
    )
}
