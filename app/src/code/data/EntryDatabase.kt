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
import kotlinx.coroutines.flow.Flow
import local.daybook.model.EntrySummary
import local.daybook.model.DayBucket

@Entity(tableName = "entries", indices = [Index(value = ["kind", "completed", "epochDay", "id"])])
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
)

@Dao
interface EntryDao {
    // 唯一 ID 兜底稳定分页；搜索支持中文子串，% 与 _ 作为普通字符而非通配符。
    @Query(EntrySql.PAGE)
    fun page(kind: String, search: String, filter: String, sort: String, today: Long): PagingSource<Int, EntryRow>

    @Query("SELECT * FROM entries WHERE id=:id")
    fun observe(id: String): Flow<EntryRow?>

    @Query(EntrySql.SUMMARY)
    fun summary(today: Long): Flow<EntrySummary>

    @Query(EntrySql.DISTRIBUTION)
    fun distribution(start: Long, endInclusive: Long): Flow<List<DayBucket>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: EntryRow)

    // 更新不使用 REPLACE，后续增加关联表时避免隐式删除关联记录。
    @Query("UPDATE entries SET title=:title, note=:note, epochDay=:epochDay, countUp=:countUp, includeFirstDay=:includeFirstDay, revision=revision+1 WHERE id=:id")
    suspend fun update(id: String, title: String, note: String, epochDay: Long, countUp: Boolean, includeFirstDay: Boolean): Int

    @Query("SELECT EXISTS(SELECT 1 FROM entries WHERE id=:id)")
    suspend fun exists(id: String): Boolean

    @Query("DELETE FROM entries WHERE id=:id")
    suspend fun delete(id: String)

    @Query("UPDATE entries SET completed=:completed, revision=revision+1 WHERE id=:id AND kind='TODO'")
    suspend fun complete(id: String, completed: Boolean)
}

@Database(entities = [EntryRow::class], version = 1, exportSchema = true)
abstract class EntryDatabase : RoomDatabase() {
    abstract fun entries(): EntryDao
}
