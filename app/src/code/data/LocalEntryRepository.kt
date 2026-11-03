package local.daybook.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.map
import androidx.room.withTransaction
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import local.daybook.model.*

class LocalEntryRepository(
    private val database: EntryDatabase,
    private val clock: Clock = Clock.systemUTC(),
) : EntryRepository {
    private val dao = database.entries()
    private val categoryDao = database.categories()

    override fun entries(kind: EntryKind, query: EntryQuery) = Pager(
        PagingConfig(pageSize = 30, initialLoadSize = 30, enablePlaceholders = false, maxSize = 150)
    ) {
        val options = query.options.normalized(kind)
        // 归档列表是独立分页流，不共享活动列表的筛选与排序口径。
        if (options.isArchiveStream()) {
            dao.archivedPage(kind.name, options.search.trim())
        } else {
            dao.page(kind.name, options.search.trim(), options.filter.name, options.sort.name,
                options.categoryId.orEmpty(), query.reference.toEpochDay())
        }
    }.flow.map { page -> page.map { it.toEntry() } }

    override fun observe(id: String) = dao.observe(id).map { it?.toEntry() }
    override fun summary(today: LocalDate) = dao.summary(today.toEpochDay())
    override fun distribution(start: LocalDate, endInclusive: LocalDate): Flow<List<DayBucket>> {
        // 一次最多一年聚合点，避免错误区间读取整个数据库历史。
        require(ChronoUnit.DAYS.between(start, endInclusive) in 0..365)
        return dao.distribution(start.toEpochDay(), endInclusive.toEpochDay())
    }
    override fun categories(): Flow<List<Category>> = categoryDao.observe()
        .map { rows -> rows.map { Category(it.id, it.name, it.position) } }

    override suspend fun save(entry: Entry) {
        require(entry.title.isNotBlank() && entry.title.length <= 80)
        require(entry.note.length <= 4000)
        require(entry.date.year in 1900..2200)
        database.withTransaction {
            entry.categoryId?.let { require(categoryDao.exists(it)) { "分类不存在，可能已被删除" } }
            if (dao.exists(entry.id)) {
                // 编辑不改写置顶与归档状态，避免一次保存悄悄改变列表归属。
                dao.update(entry.id, entry.title.trim(), entry.note, entry.date.toEpochDay(),
                    entry.countUp, entry.includeFirstDay, entry.categoryId,
                    entry.icon?.name, entry.color?.name)
            } else {
                dao.insert(EntryRow(entry.id, entry.title.trim(), entry.note, entry.date.toEpochDay(),
                    entry.kind.name, entry.countUp, entry.includeFirstDay, entry.completed, 1,
                    entry.categoryId, entry.pinned, entry.archivedAt, entry.archiveReason?.name,
                    entry.icon?.name, entry.color?.name))
            }
        }
    }

    override suspend fun delete(id: String) = dao.delete(id)

    override suspend fun deleteMany(ids: List<String>) {
        if (ids.isEmpty()) return
        database.withTransaction {
            // 分批绑定，避免一次传入过多 ID 触发 SQLite 变量上限。
            ids.distinct().chunked(BATCH).forEach { batch -> dao.deleteMany(batch) }
        }
    }

    override suspend fun assignCategoryMany(ids: List<String>, categoryId: String?) {
        if (ids.isEmpty()) return
        database.withTransaction {
            categoryId?.let { require(categoryDao.exists(it)) { "分类不存在，可能已被删除" } }
            ids.distinct().chunked(BATCH).forEach { batch -> dao.assignCategoryMany(batch, categoryId) }
        }
    }
    override suspend fun setCompleted(id: String, completed: Boolean) = dao.complete(id, completed)

    override suspend fun setPinned(id: String, pinned: Boolean) {
        require(dao.setPinned(id, pinned) > 0) { "记录不存在，可能已被删除" }
    }

    override suspend fun archive(id: String, reason: ArchiveReason) {
        // 归档时间取注入时钟，回归测试不依赖真实时间；重复归档由 SQL 条件挡住。
        require(dao.archiveEntry(id, clock.millis(), reason.name) > 0) { "记录不存在或已经归档" }
    }

    override suspend fun restore(id: String) {
        require(dao.restoreEntry(id) > 0) { "记录不存在，可能已被删除" }
    }

    override suspend fun createCategory(name: String) {
        val clean = requireCategoryName(name)
        database.withTransaction {
            require(categoryDao.count() < Category.LIMIT) { "分类最多 ${Category.LIMIT} 个" }
            require(!categoryDao.nameTaken(clean, "")) { "已存在同名分类" }
            categoryDao.insert(CategoryEntity(UUID.randomUUID().toString(), clean, categoryDao.nextPosition()))
        }
    }

    override suspend fun renameCategory(id: String, name: String) {
        val clean = requireCategoryName(name)
        database.withTransaction {
            require(categoryDao.exists(id)) { "分类不存在，可能已被删除" }
            require(!categoryDao.nameTaken(clean, id)) { "已存在同名分类" }
            categoryDao.rename(id, clean)
        }
    }

    override suspend fun moveCategory(id: String, offset: Int) {
        require(offset == -1 || offset == 1) { "排序方向无效" }
        database.withTransaction {
            val current = categoryDao.find(id) ?: throw IllegalArgumentException("分类不存在，可能已被删除")
            val neighbour = if (offset < 0) categoryDao.previous(current.position) else categoryDao.next(current.position)
            if (neighbour != null) {
                categoryDao.setPosition(neighbour.id, current.position)
                categoryDao.setPosition(current.id, neighbour.position)
            }
        }
    }

    override suspend fun deleteCategory(id: String) {
        database.withTransaction {
            // 只把条目迁移回未分类，事件本身有任何历史都不删除。
            require(categoryDao.exists(id)) { "分类不存在，可能已被删除" }
            categoryDao.clearCategory(id)
            categoryDao.deleteCategory(id)
        }
    }

    private fun requireCategoryName(name: String): String {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "分类名称不能为空" }
        require(clean.length <= Category.NAME_LIMIT) { "分类名称最多 ${Category.NAME_LIMIT} 个字符" }
        return clean
    }

    private fun EntryRow.toEntry() = Entry(id, title, note, LocalDate.ofEpochDay(epochDay),
        EntryKind.valueOf(kind), countUp, includeFirstDay, completed,
        categoryId?.takeIf { it.isNotBlank() }, pinned, archivedAt,
        archiveReason?.let { value -> ArchiveReason.entries.firstOrNull { it.name == value } },
        parseEntryIcon(icon), parseEntryColor(color))
}

/** 单次批量语句最多绑定的 ID 数量，远低于 SQLite 默认变量上限。 */
private const val BATCH = 400
