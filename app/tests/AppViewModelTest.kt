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

    private class Repository : EntryRepository {
        val saved = mutableListOf<Entry>()
        var fail = false
        val queries = mutableListOf<Pair<EntryKind, EntryQuery>>()
        val records = MutableStateFlow<Map<String, Entry>>(emptyMap())
        var readFail = false
        override fun entries(kind: EntryKind, query: EntryQuery) = flowOf(PagingData.empty<Entry>()).also { queries.add(kind to query) }
        override fun observe(id: String) = records.map { it[id] }
        override fun summary(today: LocalDate) = records.map {
            if (readFail) throw java.io.IOException("unavailable")
            EntrySummary(todoCount = it.values.count { entry -> entry.kind == EntryKind.TODO }.toLong())
        }
        override fun distribution(start: LocalDate, endInclusive: LocalDate) = flowOf(emptyList<DayBucket>())
        override suspend fun save(entry: Entry) {
            if (fail) throw java.io.IOException("storage unavailable")
            saved.add(entry)
            records.value = records.value + (entry.id to entry)
        }
        override suspend fun delete(id: String) { records.value = records.value - id }
        override suspend fun setCompleted(id: String, completed: Boolean) {
            records.value[id]?.let { records.value = records.value + (id to it.copy(completed = completed)) }
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
        assertEquals(entry, (state.detail.value as ReadState.Ready).value)
        state.complete(entry)
        runCurrent()
        assertTrue((state.detail.value as ReadState.Ready).value!!.completed)
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
        assertTrue(state.summary.value is ReadState.Ready)
    }

    @Test fun stoppedDetailSubscriptionDropsCachedRecord() = runTest(dispatcher) {
        val repository = Repository()
        val entry = Entry("24af1fc8-c846-4689-9027-d1a619a9126f", "读书", "", LocalDate.of(2026, 3, 21), EntryKind.TODO)
        repository.records.value = mapOf(entry.id to entry)
        val state = AppViewModel(repository, SavedStateHandle(), clock)
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.detail.collect() }
        state.openDetail(entry.id)
        runCurrent()
        assertTrue(state.detail.value is ReadState.Ready)
        observer.cancel()
        runCurrent()
        assertTrue(state.detail.value is ReadState.Loading)
        repository.records.value = mapOf(entry.id to entry.copy(completed = true))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.detail.collect() }
        runCurrent()
        assertTrue((state.detail.value as ReadState.Ready).value!!.completed)
    }
}
