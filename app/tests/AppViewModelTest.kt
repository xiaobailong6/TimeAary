package local.daybook.ui

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
        override fun entries(kind: EntryKind) = flowOf(PagingData.empty<Entry>())
        override suspend fun save(entry: Entry) {
            if (fail) throw java.io.IOException("storage unavailable")
            saved.add(entry)
        }
        override suspend fun delete(id: String) = Unit
        override suspend fun setCompleted(id: String, completed: Boolean) = Unit
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
}
