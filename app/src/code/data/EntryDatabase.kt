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
    // 相同日期以 UUID 兜底排序，分页不因并列日期发生漂移。
    @Query("SELECT * FROM entries WHERE kind = :kind ORDER BY completed, epochDay, id")
    fun page(kind: String): PagingSource<Int, EntryRow>

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
