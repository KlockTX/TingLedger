package com.klocktx.moneyblocks.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klocktx.moneyblocks.core.cents
import com.klocktx.moneyblocks.core.toYuanString
import com.klocktx.moneyblocks.core.today
import com.klocktx.moneyblocks.domain.usecase.BackupData
import com.klocktx.moneyblocks.domain.usecase.BackupDecode
import com.klocktx.moneyblocks.domain.usecase.BackupFormat
import com.klocktx.moneyblocks.domain.usecase.BackupUseCase
import com.klocktx.moneyblocks.ui.LocalToast
import com.klocktx.moneyblocks.ui.common.AppToast
import com.klocktx.moneyblocks.ui.common.PanelDivider
import com.klocktx.moneyblocks.ui.common.Panels
import com.klocktx.moneyblocks.ui.common.SettingRow
import com.klocktx.moneyblocks.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 比十年账本还大两个量级的文件不可能是备份，别把它整个读进内存。 */
private const val MAX_BACKUP_BYTES = 32L * 1024 * 1024

/**
 * 备份与恢复那几行，由调用方所在的面板提供容器（和「导出 CSV」同属「数据」一节）。
 *
 * 三条规矩：
 * 1. 恢复前一定先把「这份文件能不能用、里面有多少东西」摆给用户看，确认后才动库；
 * 2. 动库是整库替换，替换失败一定回滚（这条由数据层保证，界面上如实说出来）；
 * 3. 替换成功后留一份替换前的快照，界面上的「撤销这次恢复」就是把它换回去。
 */
@Composable
fun BackupRows(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val showToast = LocalToast.current
    val lastBackupAt by viewModel.lastBackupAt.collectAsStateWithLifecycle()
    val undoable by viewModel.undoable.collectAsStateWithLifecycle()

    var busy by remember { mutableStateOf(false) }
    var pendingBackup by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<BackupData?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val text = pendingBackup
        pendingBackup = null
        if (uri == null || text == null) {
            busy = false
            if (uri == null && text != null) showToast(AppToast("已取消备份"))
        } else {
            scope.launch {
                val written = runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(text.toByteArray())
                            out.flush()
                        } ?: error("目标文件打不开")
                    }
                }
                busy = false
                written.onSuccess {
                    viewModel.markBackedUp()
                    showToast(
                        AppToast(
                            message = "已备份 ${sizeLabel(text)}",
                            leadingIcon = Icons.Default.FileDownload,
                        ),
                    )
                }.onFailure { error ->
                    showToast(AppToast("备份没写进去：${error.message}"))
                }
            }
        }
    }

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        busy = false
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val read = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        // readBytes() 是 Kotlin 标准库的；java.io.InputStream.readAllBytes() 要 API 33，
                        // minSdk 26 上会 NoSuchMethodError
                        val bytes = input.readBytes()
                        if (bytes.size.toLong() > MAX_BACKUP_BYTES) {
                            error("这个文件有 ${bytes.size / 1024 / 1024} MB，不像备份文件")
                        }
                        String(bytes, Charsets.UTF_8)
                    } ?: error("文件打不开")
                }
            }
            read.onSuccess { text ->
                when (val decoded = viewModel.inspect(text)) {
                    is BackupDecode.Success -> {
                        pendingRestore = text
                        preview = decoded.data
                    }
                    is BackupDecode.Rejected ->
                        showToast(AppToast("这份文件不能用：${decoded.reason}"))
                }
                busy = false
            }.onFailure { error ->
                busy = false
                showToast(AppToast("读不出来：${error.message}"))
            }
        }
    }

    SettingRow(
        icon = Icons.Default.FileDownload,
        title = "备份账本",
        summary = backupSummaryText(lastBackupAt),
        onClick = {
            if (busy) return@SettingRow
            busy = true
            viewModel.backupContents { text ->
                pendingBackup = text
                saveLauncher.launch("${BackupFormat.FILE_NAME_PREFIX}${today()}.txt")
            }
        },
    )
    PanelDivider()
    SettingRow(
        icon = Icons.Default.FileUpload,
        title = "从备份恢复",
        summary = "整个账本换回备份时的样子，动库前先给你看里面有什么",
        onClick = {
            if (busy) return@SettingRow
            busy = true
            openLauncher.launch(arrayOf("text/plain", "application/octet-stream"))
        },
    )
    undoable?.let { previous ->
        PanelDivider()
        SettingRow(
            icon = Icons.AutoMirrored.Filled.Undo,
            title = "撤销这次恢复",
            summary = "换回恢复前的 ${previous.bills.size} 个账单 · ${previous.entries.size} 笔",
            onClick = {
                if (busy) return@SettingRow
                busy = true
                viewModel.undoRestore { outcome ->
                    busy = false
                    showToast(restoreToast(outcome))
                }
            },
        )
    }

    preview?.let { data ->
        RestoreConfirmDialog(
            data = data,
            onDismiss = {
                preview = null
                pendingRestore = null
            },
            onConfirm = {
                val text = pendingRestore
                preview = null
                pendingRestore = null
                if (text.isNullOrEmpty()) {
                    showToast(AppToast("备份内容已失效，请重新选一次文件"))
                } else {
                    busy = true
                    viewModel.restore(text) { outcome ->
                        busy = false
                        showToast(restoreToast(outcome))
                    }
                }
            },
        )
    }
}

@Composable
private fun RestoreConfirmDialog(
    data: BackupData,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = Panels.dialogShape,
        title = { Text("确认恢复这份备份", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(
                    text = "备份于 ${formatBackupTime(data.createdAt)}" +
                        if (data.appTag.isBlank()) "。" else "，来自 ${data.appTag}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = "${data.bills.size} 个账单 · ${data.entries.size} 笔 · " +
                        "合计 ${data.amountSum.cents.toYuanString()}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = "恢复是整个替换：备份之后新记的不会留下，现在的账本会先被留档，" +
                        "恢复后可以在这一页立刻撤销。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("恢复") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun restoreToast(outcome: BackupUseCase.Restored): AppToast = when (outcome) {
    is BackupUseCase.Restored.Applied -> AppToast(
        message = "已恢复 ${outcome.bills} 个账单 · ${outcome.entries} 笔，可在这一页撤销",
        leadingIcon = Icons.AutoMirrored.Filled.Undo,
    )

    is BackupUseCase.Restored.FileRejected -> AppToast("这份文件不能用：${outcome.reason}")
    is BackupUseCase.Restored.ApplyFailed -> AppToast("恢复失败，账本没动过：${outcome.reason}")
}

/** 「3 个账单 · 4 笔 · 46 KB」，写完文件顺手把规模报给用户，别让「已备份」变成一句空话。 */
private fun sizeLabel(text: String): String {
    val bytes = text.toByteArray().size
    return "${text.lines().count { it.startsWith("B\t") }} 个账单 · " +
        "${text.lines().count { it.startsWith("E\t") }} 笔 · ${humanSize(bytes)}"
}

private fun humanSize(bytes: Int): String {
    val kb = (bytes + 512) / 1024
    if (kb < 1024) return "${kb.coerceAtLeast(1)} KB"
    // 手算小数，不用 String.format：它按 locale 走，有些环境会给出「1,5 MB」
    return "${kb / 1024}.${kb % 1024 * 10 / 1024} MB"
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun formatBackupTime(millis: Long): String =
    if (millis <= 0L) {
        "未知时间"
    } else {
        TIME_FORMAT.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
    }

private fun backupSummaryText(lastBackupAt: Long): String {
    if (lastBackupAt <= 0L) return "还没备份过。换机或卸载之前先导一份"
    val now = System.currentTimeMillis()
    val days = BackupFormat.daysSince(lastBackupAt, now) ?: 0L
    val whenText = when {
        days <= 0L -> "今天"
        days == 1L -> "昨天"
        else -> "$days 天前"
    }
    return if (BackupFormat.isStale(lastBackupAt, now)) {
        "上次备份 $whenText — 太久了，建议重新导一份"
    } else {
        "上次备份 $whenText"
    }
}
