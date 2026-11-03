package local.daybook.model

import java.time.LocalDate

/** UI 与数据库通过稳定 UUID 关联，日期使用日历日而不是毫秒差。 */
data class Entry(
    val id: String,
    val title: String,
    val note: String,
    val date: LocalDate,
    val kind: EntryKind,
    val countUp: Boolean = false,
    val includeFirstDay: Boolean = false,
    val completed: Boolean = false,
    val categoryId: String? = null,
    val pinned: Boolean = false,
    /** 归档时间以 UTC 毫秒保存，便于独立归档列表排序；null 表示未归档。 */
    val archivedAt: Long? = null,
    val archiveReason: ArchiveReason? = null,
    /** 内容图标与颜色，null 表示使用类型默认外观。 */
    val icon: EntryIcon? = null,
    val color: EntryColor? = null,
) {
    fun isArchived(): Boolean = archivedAt != null
}

enum class EntryKind { DATE, TODO }

/** 归档不等于删除：原因只用于解释来源，恢复时不清除历史记录。 */
enum class ArchiveReason { MANUAL, AUTO_EXPIRED }

interface EntryRepository {
    fun entries(kind: EntryKind, query: EntryQuery = EntryQuery()): kotlinx.coroutines.flow.Flow<androidx.paging.PagingData<Entry>>
    fun observe(id: String): kotlinx.coroutines.flow.Flow<Entry?>
    fun summary(today: LocalDate): kotlinx.coroutines.flow.Flow<EntrySummary>
    fun distribution(start: LocalDate, endInclusive: LocalDate): kotlinx.coroutines.flow.Flow<List<DayBucket>>
    fun categories(): kotlinx.coroutines.flow.Flow<List<Category>>
    suspend fun save(entry: Entry)
    suspend fun delete(id: String)
    suspend fun deleteMany(ids: List<String>)
    suspend fun setCompleted(id: String, completed: Boolean)
    suspend fun setPinned(id: String, pinned: Boolean)
    suspend fun archive(id: String, reason: ArchiveReason)
    suspend fun restore(id: String)
    suspend fun createCategory(name: String)
    suspend fun renameCategory(id: String, name: String)
    suspend fun moveCategory(id: String, offset: Int)
    suspend fun deleteCategory(id: String)
    suspend fun assignCategoryMany(ids: List<String>, categoryId: String?)
}
