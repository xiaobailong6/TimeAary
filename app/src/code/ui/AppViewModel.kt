package local.daybook.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import local.daybook.model.*

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class AppViewModel(
    private val repository: EntryRepository,
    private val savedState: SavedStateHandle,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val mutableToday = MutableStateFlow(LocalDate.now(clock))
    val today = mutableToday.asStateFlow()
    private val selectedDate = savedState.getStateFlow<String?>("referenceDate", null)
    val reference = combine(selectedDate, today) { selected, current ->
        selected?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: current
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalDate.now(clock))

    private val mutableDateOptions = MutableStateFlow(EntryOptions.decode(savedState["dateOptions"]).normalized(EntryKind.DATE))
    private val mutableTodoOptions = MutableStateFlow(EntryOptions.decode(savedState["todoOptions"]).normalized(EntryKind.TODO))
    val dateOptions = mutableDateOptions.asStateFlow()
    val todoOptions = mutableTodoOptions.asStateFlow()
    val dates = paging(EntryKind.DATE, dateOptions)
    val todos = paging(EntryKind.TODO, todoOptions)
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private val mutableDraft = MutableStateFlow(EntryDraft.decode(savedState["draft"]))
    val draft = mutableDraft.asStateFlow()
    val detailId = savedState.getStateFlow<String?>("detailId", null)
    private val retryToken = MutableStateFlow(0)
    val detail = combine(detailId, retryToken) { id, _ -> id }.flatMapLatest { id ->
        if (id == null) flowOf<ReadState<Entry?>>(ReadState.Ready(null))
        else repository.observe(id).asReadState("无法读取详情，请重试。")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0), ReadState.Loading)

    private val periodName = savedState.getStateFlow("analysisPeriod", AnalysisPeriod.MONTH.name)
    private val analysisDate = savedState.getStateFlow<String?>("analysisDate", null)
    val window = combine(periodName, analysisDate, today) { name, date, current ->
        AnalysisWindow(runCatching { AnalysisPeriod.valueOf(name) }.getOrDefault(AnalysisPeriod.MONTH),
            date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: current)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalysisWindow(AnalysisPeriod.MONTH, LocalDate.now(clock)))
    val summary = combine(today, retryToken) { date, _ -> date }.flatMapLatest {
        repository.summary(it).asReadState("无法读取记录汇总，请重试。")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadState.Loading)
    val distribution = combine(window, retryToken) { range, _ -> range }.flatMapLatest {
        repository.distribution(it.start(), it.endInclusive()).map { buckets -> it to buckets }
            .asReadState("无法读取日期分布，请重试。")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadState.Loading)

    private fun paging(kind: EntryKind, options: StateFlow<EntryOptions>) = combine(options, reference) { value, date ->
        EntryQuery(value.copy(search = value.search.trim()), date)
    }.debounce(250).distinctUntilChanged().flatMapLatest { repository.entries(kind, it) }.cachedIn(viewModelScope)

    fun refreshToday() { mutableToday.value = LocalDate.now(clock) }
    fun selectReference(date: LocalDate) { savedState["referenceDate"] = if (date == today.value) null else date.toString() }
    fun updateOptions(kind: EntryKind, options: EntryOptions) {
        val normalized = options.normalized(kind)
        savedState[if (kind == EntryKind.DATE) "dateOptions" else "todoOptions"] = normalized.encode()
        (if (kind == EntryKind.DATE) mutableDateOptions else mutableTodoOptions).value = normalized
    }
    fun openDetail(id: String) { clearError(); savedState["detailId"] = id }
    fun closeDetail() { savedState["detailId"] = null; clearError() }
    fun retryReads() { retryToken.value += 1 }
    fun setAnalysisPeriod(period: AnalysisPeriod) { savedState["analysisPeriod"] = period.name }
    fun moveAnalysis(amount: Long) {
        val moved = window.value.shifted(amount)
        if (moved.anchor.year in 1900..2200) savedState["analysisDate"] = moved.anchor.toString()
    }
    fun resetAnalysis() { savedState["analysisDate"] = null }
    fun clearError() { mutableError.value = null }

    fun openEditor(kind: EntryKind, entry: Entry? = null) {
        if (mutableBusy.value) return
        clearError()
        storeDraft(entry?.let { EntryDraft.from(it) }
            ?: EntryDraft.create(kind).copy(date = LocalDate.now(clock).toString()))
    }
    fun updateDraft(draft: EntryDraft) { if (!mutableBusy.value) storeDraft(draft) }
    fun closeEditor() { if (!mutableBusy.value) { storeDraft(null); clearError() } }

    fun save() {
        val draft = mutableDraft.value ?: return
        execute {
            val date = try { LocalDate.parse(draft.date) } catch (_: java.time.DateTimeException) {
                throw IllegalArgumentException("请输入有效日期，例如 2026-03-21")
            }
            require(draft.title.isNotBlank() && draft.title.trim().length <= 80) { "标题须为 1～80 个字符" }
            require(draft.note.length <= 4000) { "备注最多 4000 个字符" }
            require(date.year in 1900..2200) { "日期须在 1900～2200 年之间" }
            repository.save(Entry(draft.id, draft.title.trim(), draft.note,
                date, draft.kind, draft.countUp, draft.includeFirstDay))
            storeDraft(null)
        }
    }
    fun delete(id: String) = execute {
        repository.delete(id)
        if (detailId.value == id) savedState["detailId"] = null
    }
    fun complete(entry: Entry) = execute { repository.setCompleted(entry.id, !entry.completed) }

    private fun storeDraft(draft: EntryDraft?) { savedState["draft"] = draft?.encode(); mutableDraft.value = draft }
    private fun execute(operation: suspend () -> Unit) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        mutableError.value = null
        viewModelScope.launch {
            try { operation() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (invalid: IllegalArgumentException) { mutableError.value = invalid.message }
            catch (_: Exception) { mutableError.value = "操作失败，数据未确认保存。请重试并检查存储空间。" }
            finally { mutableBusy.value = false }
        }
    }

    private fun <T> Flow<T>.asReadState(message: String): Flow<ReadState<T>> =
        map<T, ReadState<T>> { ReadState.Ready(it) }.onStart { emit(ReadState.Loading) }.catch {
            if (it is CancellationException) throw it
            emit(ReadState.Failed(message))
        }
}
