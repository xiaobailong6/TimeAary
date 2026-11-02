package local.daybook.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.map
import androidx.room.withTransaction
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.map
import local.daybook.model.*

class LocalEntryRepository(private val database: EntryDatabase) : EntryRepository {
    private val dao = database.entries()

    override fun entries(kind: EntryKind, query: EntryQuery) = Pager(
        PagingConfig(pageSize = 30, initialLoadSize = 30, enablePlaceholders = false, maxSize = 150)
    ) {
        val options = query.options.normalized(kind)
        dao.page(kind.name, options.search.trim(), options.filter.name, options.sort.name, query.reference.toEpochDay())
    }.flow.map { page -> page.map { it.toEntry() } }

    override fun observe(id: String) = dao.observe(id).map { it?.toEntry() }
    override fun summary(today: LocalDate) = dao.summary(today.toEpochDay())
    override fun distribution(start: LocalDate, endInclusive: LocalDate): kotlinx.coroutines.flow.Flow<List<DayBucket>> {
        // 一次最多一年聚合点，避免错误区间读取整个数据库历史。
        require(ChronoUnit.DAYS.between(start, endInclusive) in 0..365)
        return dao.distribution(start.toEpochDay(), endInclusive.toEpochDay())
    }

    override suspend fun save(entry: Entry) {
        require(entry.title.isNotBlank() && entry.title.length <= 80)
        require(entry.note.length <= 4000)
        require(entry.date.year in 1900..2200)
        database.withTransaction {
            if (dao.exists(entry.id)) {
                dao.update(entry.id, entry.title.trim(), entry.note, entry.date.toEpochDay(),
                    entry.countUp, entry.includeFirstDay)
            } else {
                dao.insert(EntryRow(entry.id, entry.title.trim(), entry.note, entry.date.toEpochDay(),
                    entry.kind.name, entry.countUp, entry.includeFirstDay, entry.completed, 1))
            }
        }
    }

    override suspend fun delete(id: String) = dao.delete(id)
    override suspend fun setCompleted(id: String, completed: Boolean) = dao.complete(id, completed)

    private fun EntryRow.toEntry() = Entry(id, title, note, LocalDate.ofEpochDay(epochDay),
        EntryKind.valueOf(kind), countUp, includeFirstDay, completed)
}
