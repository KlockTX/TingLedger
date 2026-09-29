package com.klocktx.moneyblocks.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klocktx.moneyblocks.core.Cents
import com.klocktx.moneyblocks.core.cents
import com.klocktx.moneyblocks.core.toYuanString
import com.klocktx.moneyblocks.core.today
import com.klocktx.moneyblocks.domain.model.Bill
import com.klocktx.moneyblocks.domain.model.ThemeMode
import com.klocktx.moneyblocks.domain.usecase.ManageBillsUseCase
import com.klocktx.moneyblocks.ui.LocalToast
import com.klocktx.moneyblocks.ui.appViewModel
import com.klocktx.moneyblocks.ui.bill.BillEditorDialog
import com.klocktx.moneyblocks.ui.common.AppToast
import com.klocktx.moneyblocks.ui.common.BillIconBadge
import com.klocktx.moneyblocks.ui.common.NoPadding
import com.klocktx.moneyblocks.ui.common.Panel
import com.klocktx.moneyblocks.ui.common.PanelDivider
import com.klocktx.moneyblocks.ui.common.Panels
import com.klocktx.moneyblocks.ui.common.SectionHeader
import com.klocktx.moneyblocks.ui.common.SegmentedPick
import com.klocktx.moneyblocks.ui.common.SettingRow
import com.klocktx.moneyblocks.ui.theme.Badge
import com.klocktx.moneyblocks.ui.theme.Bar
import com.klocktx.moneyblocks.ui.theme.IconSize
import com.klocktx.moneyblocks.ui.theme.LocalDarkTheme
import com.klocktx.moneyblocks.ui.theme.MonetTheme
import com.klocktx.moneyblocks.ui.theme.Radius
import com.klocktx.moneyblocks.ui.theme.Spacing
import com.klocktx.moneyblocks.ui.theme.Swatch
import java.math.RoundingMode
import kotlinx.coroutines.launch

/**
 * 设置页。
 *
 * 这一页以前叫「账单」，列表和偏好混在一起，两头都不专。
 * 现在账单的新建/改名/排序都在首页的编辑态里就地完成，这里只留：偏好、数据、
 * 归档账单的善后，以及关于。
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val viewModel = appViewModel { SettingsViewModel(
            it.backupUseCase,
            it.billRepository,
            it.entryRepository,
            it.exportRepository,
            it.manageBills,
            it.settingsRepository,
            it.appTag,
        ) }
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val monetTheme by viewModel.monetTheme.collectAsStateWithLifecycle()
    val monthlyBudget by viewModel.monthlyBudget.collectAsStateWithLifecycle()
    val caps by viewModel.billCaps.collectAsStateWithLifecycle()
    val archived by viewModel.archived.collectAsStateWithLifecycle()
    val trashed by viewModel.trashed.collectAsStateWithLifecycle()
    val entryCounts by viewModel.entryCounts.collectAsStateWithLifecycle()
    val dataScale by viewModel.dataScale.collectAsStateWithLifecycle()
    var showBudgetDialog by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Bill?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.xl, end = Spacing.xl, top = Spacing.sm, bottom = Spacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item(key = "prefs") {
            Column {
                SectionHeader("偏好", modifier = Modifier.padding(bottom = Spacing.md))
                PreferencesPanel(
                    themeMode = themeMode,
                    monet = monetTheme,
                    budget = monthlyBudget,
                    onTheme = viewModel::setThemeMode,
                    onMonet = viewModel::setMonetTheme,
                    onBudget = { showBudgetDialog = true },
                )
            }
        }

        // 导出去看、备份存起来、恢复换回来：都是"数据要离开手机/回到手机"，放一张面板
        item(key = "data") {
            Column {
                SectionHeader("数据", modifier = Modifier.padding(bottom = Spacing.md))
                DataPanel(viewModel)
            }
        }

        if (archived.isNotEmpty()) {
            item(key = "archived") {
                Column {
                    SectionHeader("已归档", modifier = Modifier.padding(bottom = Spacing.md))
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        archived.forEach { bill ->
                            Panel(contentPadding = NoPadding) {
                                ArchivedRow(
                                    bill = bill,
                                    entryCount = entryCounts[bill.id] ?: 0,
                                    onEdit = { editing = bill },
                                    onRestore = { viewModel.setArchived(bill, false) },
                                )
                            }
                        }
                    }
                    Text(
                        text = "归档只是从首页收起来，历史条目都还在。要连名下条目一起删掉，" +
                            "点进去用「删除账单」——删掉的账单会先进下面的回收站。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
            }
        }

        if (trashed.isNotEmpty()) {
            item(key = "trash") {
                Column {
                    SectionHeader("回收站", modifier = Modifier.padding(bottom = Spacing.md))
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        trashed.forEach { bill ->
                            Panel(contentPadding = NoPadding) {
                                TrashRow(
                                    bill = bill,
                                    entryCount = entryCounts[bill.id] ?: 0,
                                    onRestore = { viewModel.untrash(bill) },
                                    onDeleteForever = { viewModel.deleteForever(bill) },
                                )
                            }
                        }
                    }
                    Text(
                        text = "删除的账单先留在这里，名下 ${ManageBillsUseCase.TRASH_RETENTION_DAYS} " +
                            "天内随时可以恢复；到期后连同名下条目一起彻底删除，那一步不可恢复。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
            }
        }

        item(key = "about") {
            Column {
                SectionHeader("关于", modifier = Modifier.padding(bottom = Spacing.md))
                Panel {
                    Text(
                        text = "亭宣记账 ${viewModel.appTag}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = "数据只存在这台手机上，不联网、不上传 · " +
                            "${dataScale.first} 个账单 · ${dataScale.second} 笔记录",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showBudgetDialog) {
        BudgetDialog(
            initial = monthlyBudget,
            onDismiss = { showBudgetDialog = false },
            onConfirm = {
                viewModel.setMonthlyBudget(it)
                showBudgetDialog = false
            },
        )
    }

    editing?.let { bill ->
        BillEditorDialog(
            initial = bill,
            onDismiss = { editing = null },
            onConfirm = { edited, cap ->
                viewModel.save(edited, cap)
                editing = null
            },
            initialCap = caps[bill.id] ?: 0L.cents,
            entryCount = entryCounts[bill.id] ?: 0,
            onDelete = {
                viewModel.delete(bill)
                editing = null
            },
        )
    }
}

/** 黑白模式 + 主题色 + 月度预算。分段控件与色板都摊在页面上，不必点进去再选。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreferencesPanel(
    themeMode: ThemeMode,
    monet: MonetTheme,
    budget: Cents,
    onTheme: (ThemeMode) -> Unit,
    onMonet: (MonetTheme) -> Unit,
    onBudget: () -> Unit,
) {
    Panel(contentPadding = NoPadding) {
        // 顺序照小样来：先挑主题色（决定整页气质），再挑黑白模式，最后才是预算
        ThemePicker(
            monet = monet,
            onMonet = onMonet,
        )
        PanelDivider()
        Column(Modifier.padding(Spacing.md)) {
            Text("黑白模式", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(Spacing.sm))
            SegmentedPick(
                options = ThemeMode.entries.map { it.label },
                selected = ThemeMode.entries.indexOf(themeMode),
                onSelect = { onTheme(ThemeMode.entries[it]) },
            )
        }
        PanelDivider()
        SettingRow(
            icon = Icons.Default.Paid,
            title = "月度预算",
            summary = if (budget.value > 0L) "首页会显示本月还剩多少"
            else "设一个数，超支时首页会变红提醒",
            value = if (budget.value > 0L) budget.toYuanString() else null,
            onClick = onBudget,
        )
    }
}

/**
 * 主题色板。
 *
 * 上一版是六格挤在一行、每格里画"页面底 + 卡片 + 两条色带"——格子只有 48dp 宽，
 * 而页面底和卡片本来就几乎同色，看着像六个空灰框。现在改成一颗颗**双色漆点**：
 * 圆里沿对角线一分为二，一半主色一半强调色，下面两字名，选中就套一圈同色的环。
 * 画不出"这套主题的界面长什么样"，但至少一眼能分辨哪颗是哪颗。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemePicker(monet: MonetTheme, onMonet: (MonetTheme) -> Unit) {
    val current = if (LocalDarkTheme.current) monet.dark() else monet.light()
    Column(Modifier.padding(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "主题色",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = monet.label,
                style = MaterialTheme.typography.labelMedium,
                color = Color(current.primary),
            )
        }
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            text = monet.note,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.md))
        // 固定每行三颗。让它自然换行的话，360dp 宽的屏上会排成 5+1，第二行孤零零一颗很难看。
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = 3,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            MonetTheme.entries.forEach { theme ->
                MonetChip(
                    theme = theme,
                    selected = theme == monet,
                    onClick = { onMonet(theme) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MonetChip(
    theme: MonetTheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = if (LocalDarkTheme.current) theme.dark() else theme.light()
    val hue = Color(palette.primary)
    val bands = palette.deck.map { Color(it) }
    // 热区是「色卡 + 名字」整块。只让 44dp 的色卡可点，按起来会小到落不准。
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(Radius.Field)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs)
            .semantics {
                contentDescription = theme.label + " · " + theme.note +
                    if (selected) "，已选中" else ""
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            // 选中环常驻占位，只在选中时才画边框：条件添加会让名字上下跳。
            Box(
                Modifier
                    .size(Swatch.box)
                    .border(if (selected) Bar.emphasized else 0.dp, hue, Radius.Field),
            )
            Canvas(
                Modifier
                    .size(Swatch.inner)
                    .clip(Radius.Field)
                    .border(Bar.subtle, Color(palette.outline), Radius.Field),
            ) {
                // 四栏漆卡：雾底 → 浅霭 → 主色 → 亮色，从左到右由淡到实。
                // 这就是一管颜料的色卡，比"半个圆两种颜色"更能说明这套主题长什么样。
                // 最左那栏几乎和卡片同色是故意的——莫奈的画面里那层雾本来就该快化在空气里，
                // 所以整块描了一圈边，让它仍然有轮廓。
                val w = size.width / bands.size
                bands.forEachIndexed { i, c ->
                    drawRect(
                        color = c,
                        size = Size(w, size.height),
                        topLeft = Offset(i * w, 0f),
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = theme.short,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) hue else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 导出 + 备份 + 恢复。备份那三行由 [BackupRows] 提供，接在同一张面板里。 */
@Composable
private fun DataPanel(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val showToast = LocalToast.current
    var pendingCsv by remember { mutableStateOf<String?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val csv = pendingCsv
        pendingCsv = null
        if (uri != null && csv != null) {
            scope.launch {
                runCatching {
                    context.contentResolver.openOutputStream(uri)
                        ?.use { it.write(csv.toByteArray()) }
                }.onSuccess { showToast(AppToast(message = "已导出账单数据")) }
                    .onFailure { error -> showToast(AppToast(message = "导出失败：${error.message}")) }
            }
        }
    }
    Panel(contentPadding = NoPadding) {
        SettingRow(
            icon = Icons.Default.FileDownload,
            title = "导出 CSV",
            summary = "导成表格，用 Excel / Numbers 看和加工",
            onClick = {
                viewModel.exportCsv { csv ->
                    pendingCsv = csv
                    saveLauncher.launch("亭宣记账-${today()}.csv")
                }
            },
        )
        PanelDivider()
        BackupRows(viewModel)
    }
}

/** 归档账单的一行：能改名、能恢复，删除走编辑弹窗里的二次确认。 */
@Composable
private fun ArchivedRow(
    bill: Bill,
    entryCount: Int,
    onEdit: () -> Unit,
    onRestore: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(start = Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BillIconBadge(iconKey = bill.iconKey, palette = bill.palette, size = Badge.lg, iconSize = IconSize.md)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = bill.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${bill.kind.label} · 名下 $entryCount 笔",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onRestore) {
            Text("恢复", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * 回收站里的一行：恢复，或者彻底删除。
 *
 * 彻底删除是全 App 唯一不可逆的操作（CASCADE 连带名下条目），所以和编辑弹窗里一样，
 * 第一次点只进入确认态，不直接动手。
 */
@Composable
private fun TrashRow(
    bill: Bill,
    entryCount: Int,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
) {
    var confirming by remember(bill.id) { mutableStateOf(false) }
    val daysLeft = daysLeftInTrash(bill.deletedAt)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BillIconBadge(iconKey = bill.iconKey, palette = bill.palette, size = Badge.lg, iconSize = IconSize.md)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = bill.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "名下 $entryCount 笔 · " +
                    if (daysLeft == null) "待清理" else if (daysLeft > 0) "还剩 $daysLeft 天" else "今天到期清理",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onRestore) {
            Text("恢复", style = MaterialTheme.typography.labelMedium)
        }
        TextButton(onClick = { if (confirming) onDeleteForever() else confirming = true }) {
            Text(
                text = if (confirming) "确认删除" else "彻底删除",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** 还能在回收站里留几天；拿不到删除时刻时返回 null（手工修库把 deletedAt 写成 0 之类）。 */
private fun daysLeftInTrash(deletedAt: Long?): Int? {
    if (deletedAt == null || deletedAt <= 0L) return null
    val elapsedDays = ((System.currentTimeMillis() - deletedAt) / 86_400_000L).toInt()
    return (ManageBillsUseCase.TRASH_RETENTION_DAYS - elapsedDays).toInt()
}

@Composable
private fun BudgetDialog(
    initial: Cents,
    onDismiss: () -> Unit,
    onConfirm: (Cents) -> Unit,
) {
    var text by remember(initial) {
        mutableStateOf(if (initial.value > 0L) initial.toYuanString() else "")
    }
    val parsed = parseBudget(text)
    val invalid = text.isNotBlank() && parsed == null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = Panels.dialogShape,
        title = { Text("月度预算", style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 11) text = it },
                label = { Text("每月最多想花多少") },
                placeholder = { Text("如 3000") },
                singleLine = true,
                isError = invalid,
                supportingText = {
                    Text(
                        text = if (invalid) "只能填数字，最多两位小数" else "留空表示不设置",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (invalid) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = Panels.innerShape,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(parsed ?: 0L.cents) },
                enabled = !invalid,
            ) { Text("保存") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (initial.value > 0L) {
                    TextButton(onClick = { onConfirm(0L.cents) }) {
                        Text("清除", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

/** 预算金额解析：与记账金额同一套规则，只在落分时舍入一次。 */
private fun parseBudget(raw: String): Cents? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val value = trimmed.toBigDecimalOrNull() ?: return null
    return try {
        val cents = value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        if (cents < 0L || cents > 99_999_999L) null else cents.cents
    } catch (error: ArithmeticException) {
        null
    }
}
