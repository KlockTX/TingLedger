package com.klocktx.moneyblocks.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.klocktx.moneyblocks.ui.theme.Elev
import com.klocktx.moneyblocks.ui.theme.FloatOffset
import com.klocktx.moneyblocks.ui.theme.Motion
import com.klocktx.moneyblocks.ui.theme.Spacing
import com.klocktx.moneyblocks.ui.theme.Toast

data class AppToast(
    val message: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val leadingIcon: ImageVector? = null,
    /** 带「撤销」这类可操作按钮时给更长时间，否则手还没移到按钮上就消失了。 */
    val durationMs: Long = if (actionLabel == null) 2_600L else 5_000L,
)

/**
 * 自管理的轻量提示条。
 * 不用 SnackbarHostState，因为它的超时计时要等 Snackbar 真正可见才走，
 * 而记账弹层是独立窗口会压在它上面，导致提示挂着不消失。
 */
@Composable
fun ToastHost(
    toast: AppToast?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var lastToast by remember { mutableStateOf(toast) }
    if (toast != null) lastToast = toast
    val shown = lastToast ?: return

    AnimatedVisibility(
        visible = toast != null,
        enter = slideInVertically(tween(Motion.SOFT)) { it / 2 } + fadeIn(tween(Motion.SOFT)),
        exit = slideOutVertically(tween(Motion.QUICK)) { it / 2 } + fadeOut(tween(Motion.QUICK)),
        modifier = modifier.padding(bottom = FloatOffset.toast),
    ) {
        Surface(
            shape = Panels.buttonShape,
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            tonalElevation = Elev.toast,
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = Toast.maxWidth)
                    .padding(
                        start = Spacing.lg,
                        end = if (shown.actionLabel == null) Spacing.lg else Toast.endWithAction,
                        top = Spacing.sm,
                        bottom = Spacing.sm,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                shown.leadingIcon?.let {
                    Icon(it, contentDescription = null, modifier = Modifier.padding(Spacing.xxs))
                }
                Text(
                    text = shown.message,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                shown.actionLabel?.let { label ->
                    TextButton(
                        onClick = {
                            shown.onAction?.invoke()
                            onDismiss()
                        },
                        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
