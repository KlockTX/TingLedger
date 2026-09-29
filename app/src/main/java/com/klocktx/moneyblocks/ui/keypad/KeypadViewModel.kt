package com.klocktx.moneyblocks.ui.keypad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.klocktx.moneyblocks.domain.repo.BillRepository
import com.klocktx.moneyblocks.domain.repo.EntryRepository
import com.klocktx.moneyblocks.domain.usecase.RecordEntryUseCase
import com.klocktx.moneyblocks.domain.model.Bill
import com.klocktx.moneyblocks.domain.usecase.ExpressionEvaluator
import com.klocktx.moneyblocks.domain.usecase.KeypadEditing
import com.klocktx.moneyblocks.domain.usecase.NoteSuggestions
import com.klocktx.moneyblocks.domain.usecase.RecordOutcome
import com.klocktx.moneyblocks.domain.usecase.RejectReason
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class KeypadViewModel(
    private val billRepository: BillRepository,
    private val entryRepository: EntryRepository,
    private val recordEntry: RecordEntryUseCase,
    private val billId: Long,
    private val editingEntryId: Long?,
    initialExpression: String,
    initialNote: String,
    initialOccurredAt: Long,
) : ViewModel() {

    private val _expression = MutableStateFlow(initialExpression)
    val expression: StateFlow<String> = _expression.asStateFlow()

    private val _note = MutableStateFlow(initialNote)
    val note: StateFlow<String> = _note.asStateFlow()

    private val _occurredAt = MutableStateFlow(initialOccurredAt)
    val occurredAt: StateFlow<Long> = _occurredAt.asStateFlow()

    private val _previewCents = MutableStateFlow(ExpressionEvaluator.previewCents(initialExpression))
    val previewCents: StateFlow<Long?> = _previewCents.asStateFlow()

    private val _rejectReason = MutableStateFlow<RejectReason?>(null)
    val rejectReason: StateFlow<RejectReason?> = _rejectReason.asStateFlow()

    val isEditing: Boolean get() = editingEntryId != null

    /** 可切换到的账单。只有多于一个时才在界面上给出入口。 */
    val bills: StateFlow<List<Bill>> = billRepository.observeBills()
        .map { list -> list.filterNot { it.archived } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedBillId = MutableStateFlow(billId)
    val selectedBillId: StateFlow<Long> = _selectedBillId.asStateFlow()

    /** 记完一笔后自增，让刚用过的备注立刻进建议。 */
    private val reload = MutableStateFlow(0)

    /**
     * 常用备注。跟着两个事件走：换账单要换一套建议，记完一笔后刚用的那条该排到最前。
     */
    val noteSuggestions: StateFlow<List<String>> = combine(_selectedBillId, reload) { id, _ -> id }
        .flatMapLatest { id ->
            flow {
                emit(
                    NoteSuggestions.from(
                        entryRepository.recentNotes(id, NoteSuggestions.RAW_LIMIT),
                    ),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectBill(id: Long) {
        _selectedBillId.value = id
    }

    fun appendDigit(digit: Char) = mutate(KeypadEditing.appendDigit(_expression.value, digit))

    fun appendDot() = mutate(KeypadEditing.appendDot(_expression.value))

    fun appendOperator(operator: Char) = mutate(KeypadEditing.appendOperator(_expression.value, operator))

    fun appendOpenParen() = mutate(KeypadEditing.appendOpenParen(_expression.value))

    fun appendCloseParen() = mutate(KeypadEditing.appendCloseParen(_expression.value))

    fun backspace() = mutate(KeypadEditing.backspace(_expression.value))

    fun clear() = mutate("")

    fun setNote(text: String) {
        _note.value = text
    }

    /** 归属日取当天中午，避免时区偏移把日期挪到前一天。 */
    fun setDay(date: LocalDate) {
        _occurredAt.value = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun shiftDay(days: Long) = setDay(occurredDate().plusDays(days))

    fun occurredDate(): LocalDate =
        Instant.ofEpochMilli(_occurredAt.value).atZone(ZoneId.systemDefault()).toLocalDate()

    fun submit(onResult: (RecordOutcome) -> Unit) {
        viewModelScope.launch {
            val target = _selectedBillId.value
            val outcome = if (editingEntryId == null) {
                recordEntry.record(target, _expression.value, _note.value, _occurredAt.value)
            } else {
                recordEntry.edit(
                    editingEntryId, target, _expression.value, _note.value, _occurredAt.value,
                )
            }
            _rejectReason.value = (outcome as? RecordOutcome.Rejected)?.reason
            // 记下就清空，否则连续记第二笔时还要手动删掉上一笔金额
            if (outcome is RecordOutcome.Saved) {
                _expression.value = ""
                _note.value = ""
                _previewCents.value = null
                reload.value += 1
            }
            onResult(outcome)
        }
    }

    private fun mutate(next: String) {
        _expression.value = next
        _previewCents.value = ExpressionEvaluator.previewCents(next)
        _rejectReason.value = null
    }
}
