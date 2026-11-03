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
import local.daybook.data.MemorySettingsStore
import local.daybook.data.SettingsStore
import local.daybook.model.*

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class AppViewModel(
    private val repository: EntryRepository,
    private val savedState: SavedStateHandle,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val settingsStore: SettingsStore = MemorySettingsStore(),
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
    // 分类与筛选条件一起来自数据库，分类被删除后列表立即回到未筛选状态。
    val categories = combine(repository.categories(), retryToken) { list, _ -> list }
        .asReadState("无法读取分类，请重试。")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadState.Loading)

    // 外观设置同步读取（文件极小），改动后立即写回并驱动整棵界面重组。
    private val mutableSettings = MutableStateFlow(settingsStore.load())
    val settings = mutableSettings.asStateFlow()
    // 批量选择只保存 ID，进程恢复后仍能继续操作同一批记录。
    private val mutableSelection = MutableStateFlow(savedState.get<List<String>>("selection").orEmpty())
    val selection = mutableSelection.asStateFlow()

    // 轻量提示队列：只承载“刚刚发生了什么”，失败仍在页面内持久展示。
    private val mutableMessages = MutableStateFlow<List<AppMessage>>(emptyList())
    val messages = mutableMessages.asStateFlow()
    private var messageCounter = 0L

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
                date, draft.kind, draft.countUp, draft.includeFirstDay, categoryId = draft.categoryId))
            storeDraft(null)
        }
    }
    fun delete(id: String) = execute(success = "已删除这条记录") {
        repository.delete(id)
        if (detailId.value == id) savedState["detailId"] = null
    }
    fun complete(entry: Entry) = execute(success = "已更新完成状态") {
        repository.setCompleted(entry.id, !entry.completed)
    }
    fun setPinned(entry: Entry, pinned: Boolean) = execute(success = if (pinned) "已置顶" else "已取消置顶") {
        repository.setPinned(entry.id, pinned)
    }
    fun archive(entry: Entry) = execute(success = "已归档，可在“已归档”里恢复") {
        repository.archive(entry.id, ArchiveReason.MANUAL)
    }
    fun restore(entry: Entry) = execute(success = "已恢复为未归档") {
        repository.restore(entry.id)
    }

    fun createCategory(name: String) = execute(success = "已新增分类") { repository.createCategory(name) }
    fun renameCategory(id: String, name: String) = execute { repository.renameCategory(id, name) }
    fun moveCategory(id: String, offset: Int) = execute { repository.moveCategory(id, offset) }
    fun deleteCategory(id: String) = execute(success = "已删除分类，记录回到未分类") {
        repository.deleteCategory(id)
        // 条目已迁移到未分类，当前按该分类筛选的列表随之回到全部分类。
        listOf(EntryKind.DATE to mutableDateOptions, EntryKind.TODO to mutableTodoOptions).forEach { (kind, options) ->
            if (options.value.categoryId == id) updateOptions(kind, options.value.copy(categoryId = null))
        }
    }

    /** 同文案去重，最多保留 3 条；提示只做反馈，不作为失败状态的唯一载体。 */
    fun notify(text: String) {
        if (text.isBlank() || mutableMessages.value.any { it.text == text }) return
        messageCounter += 1
        mutableMessages.value = (mutableMessages.value + AppMessage(messageCounter, text)).takeLast(3)
    }

    fun dismissMessage(id: Long) {
        mutableMessages.value = mutableMessages.value.filterNot { it.id == id }
    }

    fun updateSettings(next: AppSettings) {
        val normalized = next.normalized()
        settingsStore.save(normalized)
        mutableSettings.value = normalized
    }

    fun toggleSelection(id: String) {
        val current = mutableSelection.value
        storeSelection(if (id in current) current - id else current + id)
    }

    fun selectAll(ids: List<String>) = storeSelection(ids.distinct())
    fun clearSelection() = storeSelection(emptyList())

    fun batchDelete() {
        val ids = mutableSelection.value
        if (ids.isEmpty()) return
        execute(success = "已删除 ${ids.size} 条记录") {
            repository.deleteMany(ids)
            storeSelection(emptyList())
            if (detailId.value in ids) savedState["detailId"] = null
        }
    }

    fun batchAssignCategory(categoryId: String?) {
        val ids = mutableSelection.value
        if (ids.isEmpty()) return
        execute(success = if (categoryId == null) "已设为未分类" else "已移动到所选分类") {
            repository.assignCategoryMany(ids, categoryId)
            storeSelection(emptyList())
        }
    }

    private fun storeSelection(ids: List<String>) {
        val clean = ids.distinct()
        savedState["selection"] = ArrayList(clean)
        mutableSelection.value = clean
    }
    private fun storeDraft(draft: EntryDraft?) { savedState["draft"] = draft?.encode(); mutableDraft.value = draft }
    private fun execute(success: String? = null, operation: suspend () -> Unit) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        mutableError.value = null
        viewModelScope.launch {
            try {
                operation()
                // 成功提示只解释“刚刚发生了什么”，失败仍留在页面内的持久错误卡。
                if (success != null) notify(success)
            }
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
