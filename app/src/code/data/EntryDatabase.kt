package local.daybook.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import local.daybook.model.DayBucket
import local.daybook.model.EntrySummary

@Entity(
    tableName = "entries",
    indices = [
        Index(value = ["kind", "completed", "epochDay", "id"]),
        // 归档流与分类筛选各自走索引，避免百万明细时全表扫描。
        Index(value = ["kind", "archivedAt"]),
        Index(value = ["categoryId"]),
    ],
)
data class EntryRow(
    @PrimaryKey val id: String,
    val title: String,
    val note: String,
    val epochDay: Long,
    val kind: String,
    val countUp: Boolean,
    val includeFirstDay: Boolean,
    val completed: Boolean,
    val revision: Long,
    val categoryId: String?,
    val pinned: Boolean,
    val archivedAt: Long?,
    val archiveReason: String?,
    val icon: String?,
    val color: String?,
)

@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val position: Int,
)

@Dao
interface EntryDao {
    // 唯一 ID 兜底稳定分页；搜索支持中文子串，% 与 _ 作为普通字符而非通配符。
    @Query(EntrySql.PAGE)
    fun page(kind: String, search: String, filter: String, sort: String, categoryId: String, today: Long): PagingSource<Int, EntryRow>

    @Query(EntrySql.ARCHIVE_PAGE)
    fun archivedPage(kind: String, search: String): PagingSource<Int, EntryRow>

    @Query("SELECT * FROM entries WHERE id=:id")
    fun observe(id: String): Flow<EntryRow?>

    @Query(EntrySql.SUMMARY)
    fun summary(today: Long): Flow<EntrySummary>

    @Query(EntrySql.DISTRIBUTION)
    fun distribution(start: Long, endInclusive: Long): Flow<List<DayBucket>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: EntryRow)

    // 更新不使用 REPLACE，后续增加关联表时避免隐式删除关联记录。
    @Query("UPDATE entries SET title=:title, note=:note, epochDay=:epochDay, countUp=:countUp, includeFirstDay=:includeFirstDay, categoryId=:categoryId, icon=:icon, color=:color, revision=revision+1 WHERE id=:id")
    suspend fun update(id: String, title: String, note: String, epochDay: Long, countUp: Boolean, includeFirstDay: Boolean, categoryId: String?, icon: String?, color: String?): Int

    @Query("SELECT EXISTS(SELECT 1 FROM entries WHERE id=:id)")
    suspend fun exists(id: String): Boolean

    @Query("DELETE FROM entries WHERE id=:id")
    suspend fun delete(id: String)

    // 批量操作由调用方分批传入 ID，避免一次绑定过多变量。
    @Query("DELETE FROM entries WHERE id IN (:ids)")
    suspend fun deleteMany(ids: List<String>)

    @Query("UPDATE entries SET categoryId=:categoryId, revision=revision+1 WHERE id IN (:ids)")
    suspend fun assignCategoryMany(ids: List<String>, categoryId: String?): Int

    @Query("UPDATE entries SET completed=:completed, revision=revision+1 WHERE id=:id AND kind='TODO'")
    suspend fun complete(id: String, completed: Boolean)

    @Query("UPDATE entries SET pinned=:pinned, revision=revision+1 WHERE id=:id")
    suspend fun setPinned(id: String, pinned: Boolean): Int

    // 归档保留原日期与备注，只写入归档时间与原因，重复归档被 archivedAt IS NULL 条件挡住。
    @Query("UPDATE entries SET archivedAt=:archivedAt, archiveReason=:reason, revision=revision+1 WHERE id=:id AND archivedAt IS NULL")
    suspend fun archiveEntry(id: String, archivedAt: Long, reason: String): Int

    @Query("UPDATE entries SET archivedAt=NULL, archiveReason=NULL, revision=revision+1 WHERE id=:id")
    suspend fun restoreEntry(id: String): Int
}

@Dao
interface CategoryDao {
    @Query(EntrySql.CATEGORIES)
    fun observe(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id=:id")
    suspend fun find(id: String): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM categories")
    suspend fun nextPosition(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM categories WHERE id=:id)")
    suspend fun exists(id: String): Boolean

    // 唯一索引区分大小写，这里额外做一次忽略大小写的重复名检查。
    @Query("SELECT EXISTS(SELECT 1 FROM categories WHERE lower(name)=lower(:name) AND id<>:id)")
    suspend fun nameTaken(name: String, id: String): Boolean

    @Query("SELECT * FROM categories WHERE position<:position ORDER BY position DESC LIMIT 1")
    suspend fun previous(position: Int): CategoryEntity?

    @Query("SELECT * FROM categories WHERE position>:position ORDER BY position ASC LIMIT 1")
    suspend fun next(position: Int): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: CategoryEntity)

    @Query("UPDATE categories SET name=:name WHERE id=:id")
    suspend fun rename(id: String, name: String): Int

    @Query("UPDATE categories SET position=:position WHERE id=:id")
    suspend fun setPosition(id: String, position: Int): Int

    // 删除分类前把所有条目迁移到未分类，事件本身不删除。
    @Query("UPDATE entries SET categoryId=NULL, revision=revision+1 WHERE categoryId=:id")
    suspend fun clearCategory(id: String)

    @Query("DELETE FROM categories WHERE id=:id")
    suspend fun deleteCategory(id: String)
}

@Database(entities = [EntryRow::class, CategoryEntity::class], version = 3, exportSchema = true)
abstract class EntryDatabase : RoomDatabase() {
    abstract fun entries(): EntryDao
    abstract fun categories(): CategoryDao

    companion object {
        /**
         * 1 → 2：新增分类表与置顶 / 归档字段。仅添加列与索引，不改写已有事实数据；
         * 迁移后所有旧记录保持未分类、未置顶、未归档。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `categories` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name` ON `categories` (`name`)")
                db.execSQL("ALTER TABLE `entries` ADD COLUMN `categoryId` TEXT")
                db.execSQL("ALTER TABLE `entries` ADD COLUMN `pinned` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `entries` ADD COLUMN `archivedAt` INTEGER")
                db.execSQL("ALTER TABLE `entries` ADD COLUMN `archiveReason` TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_entries_kind_archivedAt` ON `entries` (`kind`, `archivedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_entries_categoryId` ON `entries` (`categoryId`)")
            }
        }

        /** 2 → 3：内容图标与主题色，两者都可空，旧记录自动使用类型默认外观。 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `entries` ADD COLUMN `icon` TEXT")
                db.execSQL("ALTER TABLE `entries` ADD COLUMN `color` TEXT")
            }
        }
    }
}
