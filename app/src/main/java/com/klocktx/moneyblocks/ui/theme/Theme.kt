package com.klocktx.moneyblocks.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.klocktx.moneyblocks.domain.model.ThemeMode

/**
 * 当前真正生效的明暗（已经把「跟随系统」算进去了）。
 * 界面里想按明暗微调样式——比如主题色板的预览——读它，别再各自 isSystemInDarkTheme()。
 */
val LocalDarkTheme = compositionLocalOf { false }

/**
 * 把派生出来的 Long 配色翻译成 Material3 的 ColorScheme。
 * 界面各处只认 colorScheme 里的角色名，所以主题只需要改这一层就够了。
 */
private fun MonetTheme.Surface.toColorScheme(dark: Boolean): ColorScheme = if (dark) {
    darkColorScheme(
        primary = Color(primary),
        onPrimary = Color(onPrimary),
        primaryContainer = Color(containerHigh),
        onPrimaryContainer = Color(text),
        secondary = Color(accent),
        onSecondary = Color(onAccent),
        background = Color(background),
        onBackground = Color(text),
        surface = Color(surface),
        onSurface = Color(text),
        surfaceVariant = Color(containerHigh),
        onSurfaceVariant = Color(muted),
        surfaceContainerLowest = Color(containerLow),
        surfaceContainerLow = Color(surface),
        surfaceContainer = Color(container),
        surfaceContainerHigh = Color(containerHigh),
        outline = Color(outline),
        outlineVariant = Color(outline),
        error = Color(danger),
        onError = Color(onDanger),
    )
} else {
    lightColorScheme(
        primary = Color(primary),
        onPrimary = Color(onPrimary),
        primaryContainer = Color(containerHigh),
        onPrimaryContainer = Color(text),
        secondary = Color(accent),
        onSecondary = Color(onAccent),
        background = Color(background),
        onBackground = Color(text),
        surface = Color(surface),
        onSurface = Color(text),
        surfaceVariant = Color(containerHigh),
        onSurfaceVariant = Color(muted),
        surfaceContainerLowest = Color(containerLow),
        surfaceContainerLow = Color(surface),
        surfaceContainer = Color(container),
        surfaceContainerHigh = Color(containerHigh),
        outline = Color(outline),
        outlineVariant = Color(outline),
        error = Color(danger),
        onError = Color(onDanger),
    )
}

@Composable
fun MoneyBlocksTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    monet: MonetTheme = MonetTheme.GREY_BLUE,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // 状态栏图标配色要跟 App 内选择的模式走，而不是系统的：
    // 手动选「深色」但系统是浅色时，浅色图标配深色背景才看得清
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    val surface = remember(monet, darkTheme) {
        if (darkTheme) monet.dark() else monet.light()
    }

    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = surface.toColorScheme(darkTheme),
            typography = MoneyTypography,
            content = content,
        )
    }
}
