package local.daybook.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import local.daybook.model.Entry
import local.daybook.model.EntryKind
import local.daybook.model.EntryRepository

class AppViewModel(
    private val repository: EntryRepository,
    private val savedState: SavedStateHandle,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    val dates = repository.entries(EntryKind.DATE).cachedIn(viewModelScope)
    val todos = repository.entries(EntryKind.TODO).cachedIn(viewModelScope)
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private val mutableDraft = MutableStateFlow(EntryDraft.decode(savedState["draft"]))
    val draft = mutableDraft.asStateFlow()

    fun clearError() { mutableError.value = null }

    fun openEditor(kind: EntryKind, entry: Entry? = null) {
        if (mutableBusy.value) return
        clearError()
        storeDraft(entry?.let { EntryDraft.from(it) }
            ?: EntryDraft.create(kind).copy(date = LocalDate.now(clock).toString()))
    }

    fun updateDraft(draft: EntryDraft) {
        if (!mutableBusy.value) storeDraft(draft)
    }

    fun closeEditor() {
        if (!mutableBusy.value) { storeDraft(null); clearError() }
    }

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
            // 更新统一状态而不是调用旧 Activity 的回调，旋转后仍会正确退出编辑页。
            storeDraft(null)
        }
    }

    fun delete(id: String) = execute { repository.delete(id) }
    fun complete(entry: Entry) = execute { repository.setCompleted(entry.id, !entry.completed) }

    private fun storeDraft(draft: EntryDraft?) {
        savedState["draft"] = draft?.encode()
        mutableDraft.value = draft
    }

    private fun execute(operation: suspend () -> Unit) {
        // 防止重复点击；取消异常继续传播，不吞掉结构化并发的取消信号。
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
}
