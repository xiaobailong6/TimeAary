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
)

enum class EntryKind { DATE, TODO }

interface EntryRepository {
    fun entries(kind: EntryKind): kotlinx.coroutines.flow.Flow<androidx.paging.PagingData<Entry>>
    suspend fun save(entry: Entry)
    suspend fun delete(id: String)
    suspend fun setCompleted(id: String, completed: Boolean)
}
