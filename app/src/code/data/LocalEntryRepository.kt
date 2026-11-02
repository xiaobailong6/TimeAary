package local.daybook.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.map
import androidx.room.withTransaction
import java.time.LocalDate
import kotlinx.coroutines.flow.map
import local.daybook.model.Entry
import local.daybook.model.EntryKind
import local.daybook.model.EntryRepository

class LocalEntryRepository(private val database: EntryDatabase) : EntryRepository {
    private val dao = database.entries()

    override fun entries(kind: EntryKind) = Pager(
        PagingConfig(pageSize = 30, initialLoadSize = 30, enablePlaceholders = false, maxSize = 150)
    ) { dao.page(kind.name) }.flow.map { page ->
        page.map { row ->
            Entry(row.id, row.title, row.note, LocalDate.ofEpochDay(row.epochDay),
                EntryKind.valueOf(row.kind), row.countUp, row.includeFirstDay, row.completed)
        }
    }

    override suspend fun save(entry: Entry) {
        require(entry.title.isNotBlank() && entry.title.length <= 80)
        require(entry.note.length <= 4000)
        require(entry.date.year in 1900..2200)
        // 检查与写入置于同一事务；后续可加入修订冲突校验而不改变 UI 合约。
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
}
