package com.klocktx.moneyblocks.ui.bill

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.DialogProperties
import com.klocktx.moneyblocks.core.Cents
import com.klocktx.moneyblocks.core.cents
import com.klocktx.moneyblocks.core.formatDay
import com.klocktx.moneyblocks.core.today
import com.klocktx.moneyblocks.domain.model.Bill
import com.klocktx.moneyblocks.domain.model.BillKind
import com.klocktx.moneyblocks.domain.model.BillPalette
import com.klocktx.moneyblocks.domain.model.BillRepeat
import com.klocktx.moneyblocks.domain.usecase.ManageBillsUseCase
import com.klocktx.moneyblocks.domain.usecase.RepeatGap
import com.klocktx.moneyblocks.domain.usecase.RepeatSchedule
import com.klocktx.moneyblocks.domain.usecase.centsToYuanText
import com.klocktx.moneyblocks.domain.usecase.yuanTextToCents
import com.klocktx.moneyblocks.ui.common.Panels
import com.klocktx.moneyblocks.ui.common.PickerCell
import com.klocktx.moneyblocks.ui.common.SegmentedPick
import com.klocktx.moneyblocks.ui.common.SelectedBadge
import com.klocktx.moneyblocks.ui.theme.Alpha
import com.klocktx.moneyblocks.ui.theme.Badge
import com.klocktx.moneyblocks.ui.theme.BillIcons
import com.klocktx.moneyblocks.ui.theme.Elev
import com.klocktx.moneyblocks.ui.theme.IconSize
import com.klocktx.moneyblocks.ui.theme.Rows
import com.klocktx.moneyblocks.ui.theme.Spacing
import com.klocktx.moneyblocks.ui.theme.Swatch
import com.klocktx.moneyblocks.ui.theme.tint
import com.klocktx.moneyblocks.ui.theme.toComposeColor
import java.time.LocalDate

fun newBillDraft(kind: BillKind = BillKind.EXPENSE) = Bill(
    id = 0,
    name = "",
    iconKey = BillIcons.choices.first().first,
    palette = BillPalette.GREEN,
    kind = kind,
)

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * 新建 / 编辑账单的弹窗。首页的「新建」和设置页的「编辑」共用这一份，
 * 免得两处各留一份而慢慢改得不一样。
 *
 * 交回的是**整份改好的 Bill**而不是四个散参数：周期规则又添了三项，
 * 参数列表再长下去就该变成一个小对象了，不如直接复用 Bill。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillEditorDialog(
    initial: Bill,
    onDismiss: () -> Unit,
    onConfirm: (Bill, Cents) -> Unit,
    onDelete: (() -> Unit)? = null,
    entryCount: Int = 0,
    /** 该账单已有的月度上限（分）。0 表示没设过。 */
    initialCap: Cents = 0L.cents,
) {
    var name by remember(initial) { mutableStateOf(initial.name) }
    var iconKey by remember(initial) { mutableStateOf(initial.iconKey) }
    var palette by remember(initial) { mutableStateOf(initial.palette) }
    var kind by remember(initial) { mutableStateOf(initial.kind) }
    var repeat by remember(initial) { mutableStateOf(initial.repeat) }
    var amountText by remember(initial) {
        mutableStateOf(
            if (initial.repeatAmount.value > 0L) centsToYuanText(initial.repeatAmount.value) else "",
        )
    }
    // 首次入账日默认今天：新建时最常见的意图就是"这个月开始算"
    var anchorDay by remember(initial) {
        mutableStateOf(if (initial.repeatAnchorDay > 0L) initial.repeatAnchorDay else today().toEpochDay())
    }
    var pickingAnchor by remember(initial) { mutableStateOf(false) }
    // 月度上限单独一份文本：它是偏好，不是 Bill 的字段，所以不进 initial.copy 那串
    var capText by remember(initial) {
        mutableStateOf(if (initialCap.value > 0L) centsToYuanText(initialCap.value) else "")
    }
    // 删除会连带删掉该账单下所有条目，且没有撤销，所以第一次点击只进入确认态
    var confirmingDelete by remember(initial) { mutableStateOf(false) }

    val amountCents = yuanTextToCents(amountText) ?: 0L
    // 收入账单没有"上限"这回事，界面已经把那一行藏了，这里也要跟着归零：
    // 否则从支出改成收入时，旧文本会留在状态里并被当成上限交出去。
    val capCents = if (kind == BillKind.EXPENSE) yuanTextToCents(capText) ?: 0L else 0L
    val gap = RepeatGap.of(repeat, amountCents, anchorDay)
    val nextDay = if (gap == RepeatGap.NONE_SET) {
        RepeatSchedule.next(repeat, anchorDay, initial.repeatCursorDay, today().toEpochDay())
    } else {
        null
    }
    // 名字必填；开了周期就必须有金额。首次入账日永远有默认值，不用判。
    val canSave = name.isNotBlank() && (repeat == BillRepeat.NONE || amountCents > 0L)

    /**
     * 壳子自己搭，不用现成的 `AlertDialog`：Material3 1.4.0 的它**不给 text 槽加滚动**
     * （textScrollFlags 那个参数在这个版本并不存在），图标 + 颜色 + 周期三组一起比屏高时，
     * 最底下那行颜色会被直接裁掉。
     *
     * `usePlatformDefaultWidth = false` 是配套的：平台默认宽度会让弹窗宽度由内容反推，
     * 里面再放纵向滚动就成了"宽度等内容、内容等宽度"的死锁
     * （在 Robolectric 里表现为 Compose 永远不空闲）。宽度交给窗口，滚动才成立。
     */
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = Panels.dialogShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = Elev.dialog,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.xl)
                .imePadding(),
        ) {
            Column {
                Text(
                    text = if (initial.id == 0L) "新建账单" else "编辑账单",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(
                        start = Spacing.lg,
                        top = Spacing.lg,
                        end = Spacing.lg,
                        bottom = Spacing.sm,
                    ),
                )

                // 中间这段可滚；标题和按钮留在滚动区外面，怎么滚都在
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.lg),
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { if (it.length <= 12) name = it },
                        label = { Text("名称") },
                        placeholder = { Text("如：吃饭、打车、水电") },
                        singleLine = true,
                        shape = Panels.innerShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bill-name"),
                    )

                    Spacer(Modifier.height(Spacing.lg))
                    Text(
                        "方向",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    // 方向决定这笔记录进「花了多少」还是「赚了多少」，放名称下面先定调
                    SegmentedPick(
                        options = BillKind.entries.map { it.label },
                        selected = BillKind.entries.indexOf(kind),
                        onSelect = { kind = BillKind.entries[it] },
                    )

                    Spacer(Modifier.height(Spacing.lg))
                    Text(
                        "自动记账",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    SegmentedPick(
                        options = BillRepeat.entries.map { it.label },
                        selected = BillRepeat.entries.indexOf(repeat),
                        onSelect = { repeat = BillRepeat.entries[it] },
                    )
                    if (repeat != BillRepeat.NONE) {
                        Spacer(Modifier.height(Spacing.md))
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { raw -> amountText = trimToMoney(raw) },
                            label = { Text("每期金额（元）") },
                            placeholder = { Text("如：1200 或 1200.50") },
                            singleLine = true,
                            isError = amountText.isNotBlank() && amountCents <= 0L,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done,
                            ),
                            shape = Panels.innerShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("repeat-amount"),
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        AnchorRow(
                            label = "首次入账",
                            value = formatDay(LocalDate.ofEpochDay(anchorDay)),
                            onClick = { pickingAnchor = true },
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = when (gap) {
                                RepeatGap.NO_AMOUNT -> "填上每期金额才会自动入账"
                                RepeatGap.NO_ANCHOR -> "选一个开始计算的日期"
                                RepeatGap.NONE_SET -> nextDay?.let {
                                    val date = LocalDate.ofEpochDay(it)
                                    "${repeat.label}自动入账，下次 ${formatDay(date)}" +
                                        if (it == today().toEpochDay()) "（今天）" else ""
                                } ?: "${repeat.label}自动入账"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (gap == RepeatGap.NONE_SET) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }

                    if (kind == BillKind.EXPENSE) {
                        Spacer(Modifier.height(Spacing.lg))
                        Text(
                            "本月上限",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        OutlinedTextField(
                            value = capText,
                            onValueChange = { raw -> capText = trimToMoney(raw) },
                            label = { Text("月度上限（元）") },
                            placeholder = { Text("留空＝不限" ) },
                            singleLine = true,
                            isError = capText.isNotBlank() && capCents <= 0L,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Done,
                            ),
                            shape = Panels.innerShape,
                            supportingText = {
                                Text(
                                    text = if (capCents > 0L) {
                                        "分析页那行会显示本月用了多少"
                                    } else {
                                        "不填就是这个账单不设限"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (capText.isNotBlank() && capCents <= 0L) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("bill-cap"),
                        )
                    }

                    Spacer(Modifier.height(Spacing.lg))
                    Text(
                        "图标",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    BillIcons.choices.chunked(6).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { (key, vector) ->
                                val selected = key == iconKey
                                val tint = palette.toComposeColor()
                                PickerCell(
                                    selected = selected,
                                    accent = tint,
                                    onClick = { iconKey = key },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    // 选中时图标本身仍然可见，只用角标表达状态
                                    Icon(
                                        imageVector = vector,
                                        contentDescription = null,
                                        tint = if (selected) tint else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(IconSize.lg),
                                    )
                                    if (selected) SelectedBadge(tint)
                                }
                            }
                            // 不足一行时补齐，避免末行图标被拉伸
                            repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }

                    Spacer(Modifier.height(Spacing.lg))
                    Text(
                        "颜色",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    // 用 weight 均分而不是固定尺寸 + 间距：8 个色块按 26dp + 12dp 间距算要 292dp，
                    // 超出弹窗内容宽度，最后一个会被挤掉；选中时再变大就更明显。
                    Row(Modifier.fillMaxWidth()) {
                        BillPalette.entries.forEach { option ->
                            val selected = option == palette
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(Rows.grid)
                                    .clickable { palette = option },
                                contentAlignment = Alignment.Center,
                            ) {
                                // 选中态靠光环表达，色块本身尺寸不变，所以任何屏宽都不会挤压
                                Box(
                                    Modifier
                                        .size(if (selected) Badge.md else Badge.sm)
                                        .clip(CircleShape)
                                        .background(
                                            if (selected) option.tint(Alpha.Ring) else Color.Transparent,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        Modifier
                                            .size(Swatch.dot)
                                            .clip(CircleShape)
                                            .background(option.toComposeColor()),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (selected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "已选",
                                                tint = Color.White,
                                                modifier = Modifier.size(IconSize.xs),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (confirmingDelete) {
                        Spacer(Modifier.height(Spacing.lg))
                        DeleteWarning(initial.name, entryCount)
                    }
                    // 末尾留一点余量：滚到底时最后一行色块不会被按钮行压住
                    Spacer(Modifier.height(Spacing.md))
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = Spacing.sm, end = Spacing.sm, bottom = Spacing.sm),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onDelete != null) {
                        TextButton(
                            onClick = {
                                if (confirmingDelete) onDelete() else confirmingDelete = true
                            },
                        ) {
                            Text(
                                text = if (confirmingDelete) "确认删除" else "删除",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(
                        onClick = {
                            onConfirm(
                                initial.copy(
                                    name = name,
                                    iconKey = iconKey,
                                    palette = palette,
                                    kind = kind,
                                    repeat = repeat,
                                    repeatAmount = amountCents.cents,
                                    repeatAnchorDay = if (repeat == BillRepeat.NONE) 0L else anchorDay,
                                ),
                                capCents.cents,
                            )
                        },
                        enabled = canSave,
                    ) { Text(if (initial.id == 0L) "创建" else "保存") }
                }
            }
        }
    }

    if (pickingAnchor) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = anchorDay * MILLIS_PER_DAY,
            // 允许选到未来（"下个月 1 号开始交房租"），但不让选 1970 年那种离谱值
            selectableDates = AnchorSelectableDates,
        )
        DatePickerDialog(
            onDismissRequest = { pickingAnchor = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        // M3 的日期选择器给的是 UTC 零点毫秒，按 epochDay 取整，不能再套时区
                        anchorDay = (millis / MILLIS_PER_DAY).coerceAtLeast(1L)
                    }
                    pickingAnchor = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { pickingAnchor = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** 可选日期：过去三年到未来一年。再往前没有意义，再往后没人真会用到。 */
private val AnchorSelectableDates = object : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
        val day = utcTimeMillis / MILLIS_PER_DAY
        val today = today().toEpochDay()
        return day in (today - 3 * 366L)..(today + 366L)
    }
}

/** 只留数字和一个小数点，小数最多两位。每期金额必须是定值，不收表达式。 */
private fun trimToMoney(raw: String): String {
    val digits = buildString(raw.length + 1) {
        raw.forEach { if (it.isDigit() || it == '.') append(it) }
    }
    val dot = digits.indexOf('.')
    if (dot < 0) return digits
    val fraction = digits.substring(dot + 1).filter { it.isDigit() }
    return digits.take(dot + 1) + fraction.take(2)
}

/** 首次入账日那一行：左标题右日期，整行可点。 */
@Composable
private fun AnchorRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Panels.innerShape)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 删除后果说明：说清"进回收站、还能反悔"，以及哪一步才是不可逆的。 */
@Composable
private fun DeleteWarning(name: String, entryCount: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Panels.innerShape)
            .background(MaterialTheme.colorScheme.error.copy(alpha = Alpha.DangerTint))
            .padding(Spacing.md),
    ) {
        Text(
            text = "删除「$name」会把它移进回收站",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = if (entryCount > 0) {
                "名下 $entryCount 笔一起收进去，${ManageBillsUseCase.TRASH_RETENTION_DAYS} 天内可在" +
                    "设置页恢复；到期才彻底删除，那一步不可恢复。"
            } else {
                "它名下暂时没有记录。账单本身在回收站里留 " +
                    "${ManageBillsUseCase.TRASH_RETENTION_DAYS} 天，期间可以恢复。"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
