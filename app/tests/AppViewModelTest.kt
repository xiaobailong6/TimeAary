package local.daybook.ui

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import local.daybook.model.EntryQuery
import local.daybook.model.EntryOptions
import local.daybook.model.EntryFilter
import local.daybook.model.EntrySort
import local.daybook.model.EntrySummary
import local.daybook.model.DayBucket
import java.time.LocalDate
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import local.daybook.model.Entry
import local.daybook.model.EntryKind
import local.daybook.model.EntryRepository
import local.daybook.data.MemorySettingsStore
import local.daybook.datetime.CalendarDates
import local.daybook.model.ArchiveReason
import local.daybook.model.AppSettings
import local.daybook.model.FontScale
import local.daybook.model.NavEntry
import local.daybook.model.ThemeMode
import local.daybook.model.Category
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-03-21T12:00:00Z"), ZoneOffset.UTC)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    // 假仓库只记录调用与可观察状态，不模拟 Room；真实 SQL 由 Python 内存 SQLite 回归覆盖。
    private class Repository : EntryRepository {
        val saved = mutableListOf<Entry>()
        var fail = false
        val queries = mutableListOf<Pair<EntryKind, EntryQuery>>()
        val records = MutableStateFlow<Map<String, Entry>>(emptyMap())
        var readFail = false
        val categoryRows = MutableStateFlow<List<Category>>(emptyList())
        val pins = mutableListOf<Pair<String, Boolean>>()
        val archivedCalls = mutableListOf<Pair<String, ArchiveReason>>()
        val restoreCalls = mutableListOf<String>()
        val moves = mutableListOf<Pair<String, Int>>()
        var categoryFailure: String? = null
        override fun entries(kind: EntryKind, query: EntryQuery) = flowOf(PagingData.empty<Entry>()).also { queries.add(kind to query) }
        override fun observe(id: String) = records.map { it[id] }
        override fun summary(today: LocalDate) = records.map {
            if (readFail) throw java.io.IOException("unavailable")
            EntrySummary(todoCount = it.values.count { entry -> entry.kind == EntryKind.TODO }.toLong())
        }
        override fun distribution(start: LocalDate, endInclusive: LocalDate) = flowOf(emptyList<DayBucket>())
        override fun categories() = categoryRows
        // fail 模拟“存储不可用”：真实仓库此时所有写操作都会抛异常，假仓库保持一致。
        private fun requireWritable() {
            if (fail) throw java.io.IOException("storage unavailable")
        }
        override suspend fun save(entry: Entry) {
            requireWritable()
            saved.add(entry)
            records.value = records.value + (entry.id to entry)
        }
        override suspend fun delete(id: String) {
            requireWritable()
            records.value = records.value - id
        }

        val deleteManyCalls = mutableListOf<List<String>>()
        val assignCalls = mutableListOf<Pair<List<String>, String?>>()

        override suspend fun deleteMany(ids: List<String>) {
            requireWritable()
            deleteManyCalls.add(ids)
            records.value = records.value - ids.toSet()
        }

        override suspend fun assignCategoryMany(ids: List<String>, categoryId: String?) {
            requireWritable()
            assignCalls.add(ids to categoryId)
            ids.forEach { id ->
                records.value[id]?.let { records.value = records.value + (id to it.copy(categoryId = categoryId)) }
            }
        }
        override suspend fun setCompleted(id: String, completed: Boolean) {
            requireWritable()
            records.value[id]?.let { records.value = records.value + (id to it.copy(completed = completed)) }
        }
        override suspend fun setPinned(id: String, pinned: Boolean) {
            requireWritable()
            pins.add(id to pinned)
            records.value[id]?.let { records.value = records.value + (id to it.copy(pinned = pinned)) }
        }
        override suspend fun archive(id: String, reason: ArchiveReason) {
            requireWritable()
            archivedCalls.add(id to reason)
            records.value[id]?.let { records.value = records.value + (id to it.copy(archivedAt = 1_000L, archiveReason = reason)) }
        }
        override suspend fun restore(id: String) {
            requireWritable()
            restoreCalls.add(id)
            records.value[id]?.let { records.value = records.value + (id to it.copy(archivedAt = null, archiveReason = null)) }
        }
        override suspend fun createCategory(name: String) {
            requireWritable()
            categoryFailure?.let { throw IllegalArgumentException(it) }
            val row = Category("category-$name", name, categoryRows.value.size)
            categoryRows.value = categoryRows.value + row
        }
        override suspend fun renameCategory(id: String, name: String) {
            requireWritable()
            categoryFailure?.let { throw IllegalArgumentException(it) }
            categoryRows.value = categoryRows.value.map { if (it.id == id) it.copy(name = name) else it }
        }
        override suspend fun moveCategory(id: String, offset: Int) {
            requireWritable()
            moves.add(id to offset)
        }
        override suspend fun deleteCategory(id: String) {
            requireWritable()
            categoryRows.value = categoryRows.value.filterNot { it.id == id }
        }
    }

    @Test fun saveUsesStableIdentityAndClosesEditor() = runTest(dispatcher) {
        val repository = Repository()
        val state = AppViewModel(repository, SavedStateHandle(), clock)
        state.openEditor(EntryKind.DATE)
        val draft = state.draft.value!!.copy(title = "  生日  ")
        state.updateDraft(draft)
        state.save()
        state.save() // 第二次点击仍在门控内，不重复写入。
        advanceUntilIdle()
        assertEquals(1, repository.saved.size)
        assertEquals(draft.id, repository.saved.single().id)
        assertEquals("生日", repository.saved.single().title)
        assertNull(state.draft.value)
        assertFalse(state.busy.value)
    }

    @Test fun invalidDateRetainsDraftAndShowsError() = runTest(dispatcher) {
        val repository = Repository()
        val state = AppViewModel(repository, SavedStateHandle(), clock)
        state.openEditor(EntryKind.DATE)
        state.updateDraft(state.draft.value!!.copy(title = "生日", date = "2026-02-30"))
        state.save()
        advanceUntilIdle()
        assertTrue(repository.saved.isEmpty())
        assertNotNull(state.draft.value)
        assertNotNull(state.error.value)
    }

    @Test fun failedSaveRetainsDraftForRetry() = runTest(dispatcher) {
        val repository = Repository().apply { fail = true }
        val state = AppViewModel(repository, SavedStateHandle(), clock)
        state.openEditor(EntryKind.TODO)
        state.updateDraft(state.draft.value!!.copy(title = "读书"))
        val id = state.draft.value!!.id
        state.save()
        advanceUntilIdle()
        assertNotNull(state.error.value)
        assertEquals(id, state.draft.value!!.id)
        repository.fail = false
        state.save()
        advanceUntilIdle()
        assertEquals(id, repository.saved.single().id)
        assertNull(state.draft.value)
    }

    @Test fun savedStateRestoresEditingDraft() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val first = AppViewModel(Repository(), handle, clock)
        first.openEditor(EntryKind.TODO)
        first.updateDraft(first.draft.value!!.copy(title = "未保存", note = "保留输入"))
        val restoredHandle = SavedStateHandle(mapOf("draft" to handle.get<ArrayList<String>>("draft")))
        val restored = AppViewModel(Repository(), restoredHandle, clock)
        assertEquals(first.draft.value, restored.draft.value)
        restored.closeEditor()
        assertNull(restored.draft.value)
    }

    @Test fun queryOptionsRestoreAndNormalizeByKind() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val state = AppViewModel(Repository(), handle, clock)
        state.updateOptions(EntryKind.DATE, EntryOptions("生日", EntryFilter.DONE, EntrySort.TITLE))
        state.updateOptions(EntryKind.TODO, EntryOptions("书", EntryFilter.OPEN, EntrySort.DATE_DESC))
        assertEquals(EntryFilter.ALL, state.dateOptions.value.filter)
        val restored = AppViewModel(Repository(), handle, clock)
        assertEquals("生日", restored.dateOptions.value.search)
        assertEquals(EntrySort.TITLE, restored.dateOptions.value.sort)
        assertEquals(EntryFilter.OPEN, restored.todoOptions.value.filter)
    }

    @Test fun queryDebouncesAndUsesSelectedReferenceDate() = runTest(dispatcher) {
        val repository = Repository()
        val state = AppViewModel(repository, SavedStateHandle(), clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.dates.collect() }
        state.updateOptions(EntryKind.DATE, EntryOptions(search = "生"))
        state.updateOptions(EntryKind.DATE, EntryOptions(search = "生日"))
        state.selectReference(LocalDate.of(2024, 2, 29))
        advanceTimeBy(300)
        runCurrent()
        advanceUntilIdle()
        val query = repository.queries.last().second
        assertEquals("生日", query.options.search)
        assertEquals(LocalDate.of(2024, 2, 29), query.reference)
    }

    @Test fun detailObservesChangesAndSuccessfulDeleteClosesIt() = runTest(dispatcher) {
        val repository = Repository()
        val entry = Entry("24af1fc8-c846-4689-9027-d1a619a9126f", "读书", "", LocalDate.of(2026, 3, 21), EntryKind.TODO)
        repository.records.value = mapOf(entry.id to entry)
        val state = AppViewModel(repository, SavedStateHandle(), clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.detail.collect() }
        state.openDetail(entry.id)
        runCurrent()
        assertEquals(entry, state.detail.value.valueOrNull())
        state.complete(entry)
        runCurrent()
        assertTrue(state.detail.value.valueOrNull()!!.completed)
        state.delete(entry.id)
        runCurrent()
        assertNull(state.detailId.value)
        assertFalse(repository.records.value.containsKey(entry.id))
    }

    @Test fun summaryFailureCanRetryWithoutPretendingZero() = runTest(dispatcher) {
        val repository = Repository().apply { readFail = true }
        val state = AppViewModel(repository, SavedStateHandle(), clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.summary.collect() }
        runCurrent()
        assertTrue(state.summary.value is ReadState.Failed)
        repository.readFail = false
        state.retryReads()
        runCurrent()
        assertNotNull(state.summary.value.valueOrNull())
    }

    @Test fun stoppedDetailSubscriptionDropsCachedRecord() = runTest(dispatcher) {
        val repository = Repository()
        val entry = Entry("24af1fc8-c846-4689-9027-d1a619a9126f", "读书", "", LocalDate.of(2026, 3, 21), EntryKind.TODO)
        repository.records.value = mapOf(entry.id to entry)
        val state = AppViewModel(repository, SavedStateHandle(), clock)
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.detail.collect() }
        state.openDetail(entry.id)
        runCurrent()
        assertNotNull(state.detail.value.valueOrNull())
        observer.cancel()
        runCurrent()
        assertTrue(state.detail.value is ReadState.Loading)
        repository.records.value = mapOf(entry.id to entry.copy(completed = true))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.detail.collect() }
        runCurrent()
        assertTrue(state.detail.value.valueOrNull()!!.completed)
    }
    @Test fun archivedFilterAndCategoryReachTheQuery() = runTest(dispatcher) {
        val repository = Repository()
        val state = AppViewModel(repository, SavedStateHandle(), clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.dates.collect() }
        state.updateOptions(EntryKind.DATE, EntryOptions(filter = EntryFilter.ARCHIVED, categoryId = "c1"))
        advanceTimeBy(300)
        runCurrent()
        advanceUntilIdle()
        val options = repository.queries.last().second.options
        assertEquals(EntryFilter.ARCHIVED, options.filter)
        assertTrue(options.isArchiveStream())
        // 归档流不接分类条件：normalized 会清空分类，避免留下看不见也点不掉的筛选。
        assertNull(options.categoryId)
    }

    @Test fun pinArchiveAndRestoreReachRepository() = runTest(dispatcher) {
        val repository = Repository()
        val entry = Entry("24af1fc8-c846-4689-9027-d1a619a9126f", "读书", "", LocalDate.of(2026, 3, 21), EntryKind.TODO)
        repository.records.value = mapOf(entry.id to entry)
        val state = AppViewModel(repository, SavedStateHandle(), clock)
        state.setPinned(entry, true)
        advanceUntilIdle()
        assertEquals(listOf(entry.id to true), repository.pins)
        state.archive(entry)
        advanceUntilIdle()
        assertEquals(listOf(entry.id to ArchiveReason.MANUAL), repository.archivedCalls)
        state.restore(entry)
        advanceUntilIdle()
        assertEquals(listOf(entry.id), repository.restoreCalls)
        assertFalse(state.busy.value)
        assertNull(state.error.value)
    }

    @Test fun deletingSelectedCategoryClearsOnlyThatFilter() = runTest(dispatcher) {
        val repository = Repository()
        val state = AppViewModel(repository, SavedStateHandle(), clock)
        state.updateOptions(EntryKind.DATE, EntryOptions(categoryId = "c1"))
        state.updateOptions(EntryKind.TODO, EntryOptions(categoryId = "c2"))
        assertEquals("c1", state.dateOptions.value.categoryId)
        state.deleteCategory("c1")
        advanceUntilIdle()
        assertNull(state.dateOptions.value.categoryId)
        assertEquals("c2", state.todoOptions.value.categoryId)
    }

    @Test fun categoryValidationErrorIsSurfaced() = runTest(dispatcher) {
        val repository = Repository().apply { categoryFailure = "已存在同名分类" }
        val state = AppViewModel(repository, SavedStateHandle(), clock)
        state.createCategory("工作")
        advanceUntilIdle()
        assertEquals("已存在同名分类", state.error.value)
        assertTrue(repository.categoryRows.value.isEmpty())
        assertFalse(state.busy.value)
    }

    @Test fun messagesQueueDeduplicateAndDismiss() = runTest(dispatcher) {
        val repository = Repository()
        val entry = Entry("24af1fc8-c846-4689-9027-d1a619a9126f", "读书", "", LocalDate.of(2026, 3, 21), EntryKind.TODO)
        repository.records.value = mapOf(entry.id to entry)
        val state = AppViewModel(repository, SavedStateHandle(), clock)
        assertTrue(state.messages.value.isEmpty())
        state.setPinned(entry, true)
        advanceUntilIdle()
        assertEquals(listOf("已置顶"), state.messages.value.map { it.text })
        state.setPinned(entry, false)
        advanceUntilIdle()
        assertEquals(listOf("已置顶", "已取消置顶"), state.messages.value.map { it.text })
        state.notify("已置顶") // 同文案去重，不重复堆积。
        assertEquals(2, state.messages.value.size)
        val first = state.messages.value.first()
        state.dismissMessage(first.id)
        assertEquals(listOf("已取消置顶"), state.messages.value.map { it.text })
        assertFalse(state.busy.value)
    }

    @Test fun failedOperationsDoNotEmitSuccessMessage() = runTest(dispatcher) {
        val repository = Repository().apply { fail = true }
        val state = AppViewModel(repository, SavedStateHandle(), clock)
        state.deleteCategory("missing")
        advanceUntilIdle()
        assertTrue(state.messages.value.isEmpty())
        assertNotNull(state.error.value)
    }

    @Test fun settingsPersistAndKeepSettingsReachable() = runTest(dispatcher) {
        val store = MemorySettingsStore()
        val state = AppViewModel(Repository(), SavedStateHandle(), clock, store)
        state.updateSettings(AppSettings(themeMode = ThemeMode.DARK, fontScale = FontScale.LARGE))
        assertEquals(ThemeMode.DARK, state.settings.value.themeMode)
        assertEquals(ThemeMode.DARK, store.load().themeMode)
        state.updateSettings(state.settings.value.toggleEntry(NavEntry.TASKS))
        assertFalse(state.settings.value.isVisible(NavEntry.TASKS))
        assertTrue(state.settings.value.isVisible(NavEntry.SETTINGS))
        assertEquals(state.settings.value, store.load())
    }

    @Test fun selectionTogglesAndBatchDeleteClearsIt() = runTest(dispatcher) {
        val repository = Repository()
        val entry = Entry("24af1fc8-c846-4689-9027-d1a619a9126f", "读书", "", LocalDate.of(2026, 3, 21), EntryKind.TODO)
        repository.records.value = mapOf(entry.id to entry)
        val state = AppViewModel(repository, SavedStateHandle(), clock)
        state.toggleSelection(entry.id)
        assertEquals(listOf(entry.id), state.selection.value)
        state.toggleSelection(entry.id)
        assertTrue(state.selection.value.isEmpty())
        state.selectAll(listOf(entry.id, entry.id))
        assertEquals(listOf(entry.id), state.selection.value)
        state.batchDelete()
        advanceUntilIdle()
        assertEquals(listOf(listOf(entry.id)), repository.deleteManyCalls)
        assertTrue(state.selection.value.isEmpty())
        assertFalse(repository.records.value.containsKey(entry.id))
    }

    @Test fun batchAssignCategoryUsesTargetAndClearsSelection() = runTest(dispatcher) {
        val repository = Repository()
        val entry = Entry("24af1fc8-c846-4689-9027-d1a619a9126f", "读书", "", LocalDate.of(2026, 3, 21), EntryKind.TODO)
        repository.records.value = mapOf(entry.id to entry)
        val state = AppViewModel(repository, SavedStateHandle(), clock)
        state.selectAll(listOf(entry.id))
        state.batchAssignCategory("c1")
        advanceUntilIdle()
        assertEquals(listOf(listOf(entry.id) to "c1"), repository.assignCalls)
        assertTrue(state.selection.value.isEmpty())
        assertEquals("c1", repository.records.value.getValue(entry.id).categoryId)
    }

    @Test fun selectionSurvivesSavedStateRestore() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val first = AppViewModel(Repository(), handle, clock)
        first.toggleSelection("24af1fc8-c846-4689-9027-d1a619a9126f")
        val restored = AppViewModel(Repository(),
            SavedStateHandle(mapOf("selection" to handle.get<ArrayList<String>>("selection"))), clock)
        assertEquals(listOf("24af1fc8-c846-4689-9027-d1a619a9126f"), restored.selection.value)
    }

    @Test fun messagesDeduplicateCapAndDismiss() = runTest(dispatcher) {
        val state = AppViewModel(Repository(), SavedStateHandle(), clock)
        state.notify("已置顶")
        state.notify("已置顶") // 同文案去重，不重复堆积。
        state.notify("已归档，可在“已归档”里恢复")
        assertEquals(2, state.messages.value.size)
        state.notify("第三条")
        state.notify("第四条")
        assertEquals(3, state.messages.value.size)
        assertEquals("第四条", state.messages.value.last().text)
        val first = state.messages.value.first()
        state.dismissMessage(first.id)
        assertFalse(state.messages.value.any { it.id == first.id })
        state.notify("") // 空文案不产生消息。
        assertEquals(2, state.messages.value.size)
    }

    @Test fun deleteShowsSuccessMessageOnlyAfterSuccess() = runTest(dispatcher) {
        val repository = Repository().apply { fail = true }
        val entry = Entry("24af1fc8-c846-4689-9027-d1a619a9126f", "读书", "", LocalDate.of(2026, 3, 21), EntryKind.TODO)
        repository.records.value = mapOf(entry.id to entry)
        val state = AppViewModel(repository, SavedStateHandle(), clock)
        state.delete(entry.id)
        advanceUntilIdle()
        assertNotNull(state.error.value)
        assertTrue(state.messages.value.isEmpty())
        repository.fail = false
        state.delete(entry.id)
        advanceUntilIdle()
        assertNull(state.error.value)
        assertEquals(listOf("已删除这条记录"), state.messages.value.map { it.text })
    }

    @Test fun calendarToolsRoundTrip() {
        val date = LocalDate.of(2026, 3, 21)
        val grid = CalendarDates.monthGrid(date)
        assertEquals(42, grid.size)
        assertEquals(LocalDate.of(2026, 3, 21), grid.first { it == date })
        assertTrue(grid.first().dayOfWeek == java.time.DayOfWeek.MONDAY)
        // 网格覆盖本月全部日期，首尾允许跨月补齐。
        assertTrue(grid.contains(LocalDate.of(2026, 3, 1)))
        assertTrue(grid.contains(LocalDate.of(2026, 3, 31)))
        assertEquals(LocalDate.of(2026, 2, 1), CalendarDates.shiftMonth(date, -1))
        assertEquals(LocalDate.of(2026, 4, 1), CalendarDates.shiftMonth(date, 1))
    }

}
