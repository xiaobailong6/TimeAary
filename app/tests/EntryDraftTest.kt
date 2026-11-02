package local.daybook.ui

import local.daybook.model.Entry
import local.daybook.model.EntryKind
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class EntryDraftTest {
    @Test fun newDraftHasUniqueIdentity() {
        val first = EntryDraft.create(EntryKind.DATE)
        val second = EntryDraft.create(EntryKind.DATE)
        assertNotEquals(first.id, second.id)
        assertEquals(EntryKind.DATE, first.kind)
    }

    @Test fun draftRestoresEveryFieldIncludingInvalidDateInput() {
        val draft = EntryDraft.create(EntryKind.DATE).copy(title = "生日", note = "多行\n备注",
            date = "2026-02-3", countUp = true, includeFirstDay = true)
        assertEquals(draft, EntryDraft.decode(draft.encode()))
    }

    @Test fun editingPreservesIdentityAndMode() {
        val entry = Entry("24af1fc8-c846-4689-9027-d1a619a9126f", "日期", "备注",
            LocalDate.parse("2024-02-29"), EntryKind.DATE, true, true)
        val draft = EntryDraft.from(entry)
        assertEquals(entry.id, draft.id)
        assertEquals("2024-02-29", draft.date)
        assertTrue(draft.countUp)
        assertTrue(draft.includeFirstDay)
    }

    @Test fun malformedSavedDraftIsRejected() {
        assertNull(EntryDraft.decode(null))
        assertNull(EntryDraft.decode(listOf("partial")))
        val encoded = EntryDraft.create(EntryKind.TODO).encode()
        encoded[1] = "UNKNOWN"
        assertNull(EntryDraft.decode(encoded))
    }
}
