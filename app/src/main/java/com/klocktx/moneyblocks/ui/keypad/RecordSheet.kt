package com.klocktx.moneyblocks.ui.keypad

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.klocktx.moneyblocks.core.cents
import com.klocktx.moneyblocks.core.formatDay
import com.klocktx.moneyblocks.core.formatWeekday
import com.klocktx.moneyblocks.core.toSignedYuanWithSymbol
import com.klocktx.moneyblocks.core.toYuanString
import com.klocktx.moneyblocks.core.today
import com.klocktx.moneyblocks.domain.model.Bill
import com.klocktx.moneyblocks.domain.model.BillKind
import com.klocktx.moneyblocks.domain.model.BillPalette
import com.klocktx.moneyblocks.domain.model.Entry
import com.klocktx.moneyblocks.domain.usecase.KeypadEditing
import com.klocktx.moneyblocks.domain.usecase.RecordOutcome
import com.klocktx.moneyblocks.ui.appViewModel
import com.klocktx.moneyblocks.ui.common.BillIconBadge
import com.klocktx.moneyblocks.ui.common.Chip
import com.klocktx.moneyblocks.ui.common.Panels
import com.klocktx.moneyblocks.ui.theme.Alpha
import com.klocktx.moneyblocks.ui.theme.Badge
import com.klocktx.moneyblocks.ui.theme.Bar
import com.klocktx.moneyblocks.ui.theme.IconSize
import com.klocktx.moneyblocks.ui.theme.MoneyDisplay
import com.klocktx.moneyblocks.ui.theme.MoneyTitle
import com.klocktx.moneyblocks.ui.theme.Motion
import com.klocktx.moneyblocks.ui.theme.Radius
import com.klocktx.moneyblocks.ui.theme.Rows
import com.klocktx.moneyblocks.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val MILLIS_PER_DAY = 86_400_000L

data class RecordTarget(
    val billId: Long,
    val billName: String,
    val billIconKey: String,
    val palette: BillPalette,
    val kind: BillKind = BillKind.EXPENSE,
    /** 改这一笔已有记录。 */
    val editing: Entry? = null,
    /** 新建一笔，但把某条旧记录的金额和备注带进来（长按「再记一笔」）。 */
    val prefill: Entry? = null,
) {
    val isIncome: Boolean get() = kind == BillKind.INCOME
}

fun Bill.asRecordTarget(editing: Entry? = null) = RecordTarget(
    billId = id,
    billName = name,
    billIconKey = iconKey,
    palette = palette,
    kind = kind,
    editing = editing,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordSheet(
    target: RecordTarget,
    onSubmitted: (entryId: Long, cents: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val editing = target.editing
    val prefill = target.prefill
    val seed = editing ?: prefill
    val viewModel = appViewModel(
        key = "keypad-${target.billId}-${if (editing != null) "edit" else "new"}${seed?.id ?: 0L}",
    ) { container ->
        KeypadViewModel(
            billRepository = container.billRepository,
            entryRepository = container.entryRepository,
            recordEntry = container.recordEntry,
            billId = target.billId,
            editingEntryId = editing?.id,
            initialExpression = seed?.amount?.toYuanString() ?: "",
            initialNote = seed?.note ?: "",
            // 复记归属于今天，只有「编辑原有记录」才沿用原来那天的时间
            initialOccurredAt = editing?.occurredAt ?: System.currentTimeMillis(),
        )
    }

    val expression by viewModel.expression.collectAsStateWithLifecycle()
    val note by viewModel.note.collectAsStateWithLifecycle()
    val previewCents by viewModel.previewCents.collectAsStateWithLifecycle()
    val rejectReason by viewModel.rejectReason.collectAsStateWithLifecycle()
    val occurredMillis by viewModel.occurredAt.collectAsStateWithLifecycle()
    val bills by viewModel.bills.collectAsStateWithLifecycle()
    val selectedBillId by viewModel.selectedBillId.collectAsStateWithLifecycle()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    /**
     * 弹层的强调色跟**软件主题**（设置页选的莫奈色），不跟账单配色。
     *
     * 之前取的是 `target.palette`，于是同一个计算器在「吃饭」是绿的、在「打车」是蓝的，
     * 换账单时整块键盘跟着改颜色，看着像主题坏了。账单的身份已经由左上角那枚图标徽章
     * 表达（那张仍然用 palette），键盘是操作区，该跟着主题走。
     */
    val accent = MaterialTheme.colorScheme.primary
    var showDatePicker by remember { mutableStateOf(false) }
    var showBillPicker by remember { mutableStateOf(false) }
    // 只能在同方向之间改归属：把一笔支出挪进收入账单本身就是记错了账
    val switchable = remember(bills, target.kind) { bills.filter { it.kind == target.kind } }
    val selectedDay = remember(occurredMillis) {
        Instant.ofEpochMilli(occurredMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = Radius.RecordSheetTop,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .testTag("record-sheet")
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.lg),
        ) {
            /**
             * 标题行同时是"记进哪个账单"和"换账单"入口。
             *
             * 原来这两件事占两行：标题一行，下面再来一整行「记入 · 图标 · 名字 · 换账单」。
             * 实测弹层内容 692dp，而一台 411×740dp 的小手机可用高度就 740dp——
             * 那一行既重复（图标和账单名上面刚出现过）又白吃 46dp。并回标题行之后
             * 弹层矮一截，也少了一个"要等 bills 才长出来"的爬升源。
             *
             * 名字优先取 current（切过账单之后），否则取 target：target 一进来就有，
             * 所以这一行从第一帧起就是最终形态。
             */
            val canSwitch = switchable.size > 1
            val current = switchable.firstOrNull { it.id == selectedBillId }
            val shownName = current?.name ?: target.billName
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Rows.minTouch)
                    .testTag("record-title-row"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BillIconBadge(
                    iconKey = current?.iconKey ?: target.billIconKey,
                    palette = current?.palette ?: target.palette,
                )
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (prefill != null && !viewModel.isEditing) "$shownName · 再记一笔" else shownName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                    Text(
                        text = when {
                            viewModel.isEditing -> "修改这笔的金额或备注"
                            prefill != null -> "已带上上次的金额，改个数字就能记"
                            else -> if (target.isIncome) "记一笔收入，可直接连算" else "输入金额，可直接连算"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (canSwitch) {
                    Text(
                        text = "换账单",
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        modifier = Modifier
                            .clip(Panels.cornerSm)
                            .clickable { showBillPicker = true }
                            .padding(start = Spacing.sm)
                            .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                    )
                }
            }

            Spacer(Modifier.height(Spacing.sm))

            ExpressionDisplay(expression, previewCents, accent, target.isIncome)

            rejectReason?.let { reason ->
                Text(
                    text = reason.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.xs),
                )
            }

            DayRow(
                selectedDay = selectedDay,
                accent = accent,
                onSelectDay = viewModel::setDay,
                onOpenPicker = { showDatePicker = true },
            )

            // 常用备注：同类账反复填同样几个字，点一下比重新打一遍快。
            //
            // 它挪进了备注框自己的 supportingText——这些胶囊本来就是"填这个框"的快捷方式，
            // 单占一行既白吃 46dp，又会在 noteSuggestions 到位时让弹层凭空长高一截（就是"爬"）。
            // 辅助区**永远占住、高度钉死**，只有胶囊按条件出现，所以备注框从第一帧起
            // 就是最终高度。备注已经打了字时也只是把胶囊收起来，不留跳变。
            val suggestions by viewModel.noteSuggestions.collectAsStateWithLifecycle()
            val showSuggestions = suggestions.isNotEmpty() && note.isEmpty()
            OutlinedTextField(
                value = note,
                onValueChange = viewModel::setNote,
                placeholder = { Text("备注 · 和谁、买了什么（选填）") },
                singleLine = true,
                shape = Panels.innerShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                textStyle = MaterialTheme.typography.bodyMedium,
                supportingText = {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(Rows.chips),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (showSuggestions) {
                            Text(
                                text = "常用",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Row(
                                Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                suggestions.forEach { text ->
                                    Chip(text = text, selected = false, onClick = { viewModel.setNote(text) })
                                }
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
            )

            KeypadGrid(
                expression = expression,
                accent = accent,
                onDigit = viewModel::appendDigit,
                onDot = viewModel::appendDot,
                onOperator = viewModel::appendOperator,
                onOpenParen = viewModel::appendOpenParen,
                onCloseParen = viewModel::appendCloseParen,
                onBackspace = viewModel::backspace,
                onClear = viewModel::clear,
            )

            Spacer(Modifier.height(Spacing.sm))

            Button(
                onClick = {
                    val submitted = previewCents
                    viewModel.submit { outcome ->
                        if (outcome is RecordOutcome.Saved && submitted != null) {
                            onSubmitted(outcome.entryId, submitted)
                        }
                    }
                },
                enabled = KeypadEditing.isValid(expression) && (previewCents ?: 0L) > 0L,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Rows.key),
                shape = Panels.buttonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    // 主题派生时 onPrimary 与 primary 是配对着解的（保证 ≥4.5），
                    // 写死 surface 当字色在中间调主题上只有 3 出头
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                val suffix = previewCents?.let { "   ${it.cents.toSignedYuanWithSymbol(target.isIncome)}" } ?: ""
                Text(
                    text = (if (viewModel.isEditing) "保存修改"
                    else if (target.isIncome) "记收入" else "记一笔") + suffix,
                    style = MoneyTitle,
                )
            }
        }
    }

    if (showBillPicker) {
        AlertDialog(
            onDismissRequest = { showBillPicker = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = Panels.dialogShape,
            title = { Text("记入哪个账单") },
            text = {
                Column {
                    switchable.forEach { option ->
                        val isSelected = option.id == selectedBillId
                        Surface(
                            onClick = {
                                viewModel.selectBill(option.id)
                                showBillPicker = false
                            },
                            shape = Panels.innerShape,
                            color = if (isSelected) accent.copy(alpha = Alpha.Tint)
                            else Color.Transparent,
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BillIconBadge(
                                    iconKey = option.iconKey,
                                    palette = option.palette,
                                    size = Badge.md,
                                    iconSize = IconSize.sm,
                                )
                                Spacer(Modifier.width(Spacing.md))
                                Text(option.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = "当前", modifier = Modifier.size(IconSize.md))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBillPicker = false }) { Text("关闭") }
            },
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDay.toEpochDay() * MILLIS_PER_DAY,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        viewModel.setDay(LocalDate.ofEpochDay(millis / MILLIS_PER_DAY))
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun ExpressionDisplay(
    expression: String,
    previewCents: Long?,
    accent: Color,
    income: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Panels.buttonShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(Spacing.md),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = expression.ifEmpty { "0" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.End,
        )
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            text = previewCents?.cents?.toSignedYuanWithSymbol(income) ?: "¥0.00",
            style = MoneyDisplay,
            color = if (previewCents == null) MaterialTheme.colorScheme.onSurfaceVariant else accent,
        )
    }
}

@Composable
private fun DayRow(
    selectedDay: LocalDate,
    accent: Color,
    onSelectDay: (LocalDate) -> Unit,
    onOpenPicker: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        listOf("今天" to 0L, "昨天" to -1L, "前天" to -2L).forEach { (label, offset) ->
            val day = today().plusDays(offset)
            Chip(
                text = label,
                selected = selectedDay == day,
                accent = accent,
                onClick = { onSelectDay(day) },
            )
        }
        Spacer(Modifier.weight(1f))
        Surface(
            onClick = onOpenPicker,
            shape = Panels.cornerSm,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Row(
                Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = "选择日期",
                    modifier = Modifier.width(Bar.legendRule),
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    "${formatDay(selectedDay)} ${formatWeekday(selectedDay)}",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun KeypadGrid(
    expression: String,
    accent: Color,
    onDigit: (Char) -> Unit,
    onDot: () -> Unit,
    onOperator: (Char) -> Unit,
    onOpenParen: () -> Unit,
    onCloseParen: () -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ToolKey("AC", enabled = expression.isNotEmpty(), onClick = onClear)
            ToolKey("(", enabled = KeypadEditing.isOpenParenEnabled(expression), onClick = onOpenParen)
            ToolKey(")", enabled = KeypadEditing.isCloseParenEnabled(expression), onClick = onCloseParen)
            ToolKey("⌫", enabled = expression.isNotEmpty(), onClick = onBackspace)
        }
        DigitRow("789", '÷', expression, accent, onDigit, onOperator)
        DigitRow("456", '×', expression, accent, onDigit, onOperator)
        DigitRow("123", '-', expression, accent, onDigit, onOperator)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            DigitKey(".", KeypadEditing.isDotEnabled(expression)) { onDot() }
            DigitKey("0", KeypadEditing.isDigitEnabled(expression, '0')) { onDigit('0') }
            DigitKey("00", KeypadEditing.isDigitEnabled(expression, '0')) {
                onDigit('0')
                onDigit('0')
            }
            OperatorKey('+', expression, accent) { onOperator(it) }
        }
    }
}

@Composable
private fun DigitRow(
    digits: String,
    operator: Char,
    expression: String,
    accent: Color,
    onDigit: (Char) -> Unit,
    onOperator: (Char) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        digits.forEach { digit ->
            DigitKey(digit.toString(), KeypadEditing.isDigitEnabled(expression, digit)) { onDigit(digit) }
        }
        OperatorKey(operator, expression, accent) { onOperator(it) }
    }
}

@Composable
private fun RowScope.DigitKey(text: String, enabled: Boolean, onClick: () -> Unit) {
    KeySurface(text = text, enabled = enabled, onClick = onClick)
}

/** 顶部的清除 / 括号 / 退格，比数字矮一档，让键盘重心落在数字上。 */
@Composable
private fun RowScope.ToolKey(text: String, enabled: Boolean, onClick: () -> Unit) {
    KeySurface(text = text, enabled = enabled, height = 42, onClick = onClick)
}

@Composable
private fun RowScope.OperatorKey(symbol: Char, expression: String, accent: Color, onClick: (Char) -> Unit) {
    KeySurface(
        text = symbol.toString(),
        enabled = KeypadEditing.isOperatorEnabled(expression, symbol),
        accentColor = accent,
        onClick = { onClick(symbol) },
    )
}

@Composable
private fun RowScope.KeySurface(
    text: String,
    enabled: Boolean,
    height: Int = 54,
    accentColor: Color? = null,
    onClick: () -> Unit,
) {
    val container = when {
        !enabled -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = Alpha.DisabledContent)
        accentColor != null -> accentColor.copy(alpha = Alpha.Tint)
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val content = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Alpha.Disabled)
        accentColor != null -> accentColor
        else -> MaterialTheme.colorScheme.onSurface
    }
    // 按键按下时轻微收缩，比默认灰色 ripple 更贴合这套线性设计
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.94f else 1f,
        animationSpec = tween(Motion.QUICK, easing = Motion.Decelerate),
        label = "keyScale",
    )
    Box(
        modifier = Modifier
            .weight(1f)
            .height(height.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(Panels.innerShape)
            .background(container)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = if (height < 54) MaterialTheme.typography.labelLarge else MoneyTitle,
            fontWeight = if (accentColor != null) FontWeight.SemiBold else FontWeight.Normal,
            color = content,
        )
    }
}
