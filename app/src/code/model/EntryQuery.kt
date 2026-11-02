package local.daybook.model

import java.time.LocalDate

/** 查询状态保存为少量字符串；筛选在数据库执行，与分页加载数量无关。 */
enum class EntryFilter { ALL, TODAY, FUTURE, PAST, OPEN, DONE, OVERDUE }
enum class EntrySort { DATE_ASC, DATE_DESC, TITLE }

data class EntryOptions(
    val search: String = "",
    val filter: EntryFilter = EntryFilter.ALL,
    val sort: EntrySort = EntrySort.DATE_ASC,
) {
    fun encode(): ArrayList<String> = arrayListOf(search, filter.name, sort.name)

    fun normalized(kind: EntryKind): EntryOptions {
        val allowed = if (kind == EntryKind.DATE) setOf(EntryFilter.ALL, EntryFilter.TODAY, EntryFilter.FUTURE, EntryFilter.PAST)
            else setOf(EntryFilter.ALL, EntryFilter.TODAY, EntryFilter.OPEN, EntryFilter.DONE, EntryFilter.OVERDUE)
        return copy(search = search.take(80), filter = if (filter in allowed) filter else EntryFilter.ALL)
    }

    companion object {
        fun decode(values: List<String>?): EntryOptions = runCatching {
            require(values != null && values.size == 3)
            EntryOptions(values[0].take(80), EntryFilter.valueOf(values[1]), EntrySort.valueOf(values[2]))
        }.getOrDefault(EntryOptions())
    }
}

data class EntryQuery(val options: EntryOptions = EntryOptions(), val reference: LocalDate = LocalDate.now())

data class EntrySummary(
    val dateCount: Long = 0,
    val todoCount: Long = 0,
    val completedCount: Long = 0,
    val overdueCount: Long = 0,
    val upcomingDateCount: Long = 0,
) {
    fun completionFraction(): Float = if (todoCount == 0L) 0f else
        (completedCount.toDouble() / todoCount).coerceIn(0.0, 1.0).toFloat()
}

/** 按计划日期聚合，而不是完成日期；不伪造当前模型没有存储的完成时刻。 */
data class DayBucket(val epochDay: Long, val dateCount: Long, val todoCount: Long, val completedCount: Long)
