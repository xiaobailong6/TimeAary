package local.daybook.model

import java.time.LocalDate

/** 查询状态保存为少量字符串；筛选在数据库执行，与分页加载数量无关。 */
enum class EntryFilter { ALL, TODAY, FUTURE, PAST, OPEN, DONE, OVERDUE, ARCHIVED }
enum class EntrySort { DATE_ASC, DATE_DESC, TITLE }

data class EntryOptions(
    val search: String = "",
    val filter: EntryFilter = EntryFilter.ALL,
    val sort: EntrySort = EntrySort.DATE_ASC,
    /** null 表示全部分类；筛选在数据库执行，未分类条目不会进入某个分类结果。 */
    val categoryId: String? = null,
) {
    fun encode(): ArrayList<String> = arrayListOf(search, filter.name, sort.name, categoryId.orEmpty())

    fun normalized(kind: EntryKind): EntryOptions {
        // 归档列表与正常列表用同一套条件，但归档流按归档时间排序，排序选项只影响正常列表。
        val allowed = if (kind == EntryKind.DATE)
            setOf(EntryFilter.ALL, EntryFilter.TODAY, EntryFilter.FUTURE, EntryFilter.PAST, EntryFilter.ARCHIVED)
        else setOf(EntryFilter.ALL, EntryFilter.TODAY, EntryFilter.OPEN, EntryFilter.DONE, EntryFilter.OVERDUE,
            EntryFilter.ARCHIVED)
        val effective = if (filter in allowed) filter else EntryFilter.ALL
        // 归档流只按归档时间排序、不接分类条件，归档视图统一清空分类，避免留下看不见的筛选。
        return copy(search = search.take(80), filter = effective,
            categoryId = if (effective == EntryFilter.ARCHIVED) null
            else categoryId?.trim()?.takeIf { it.isNotEmpty() }?.take(64))
    }

    /** 归档列表是独立分页流；判断集中在模型层，便于单元测试守住分流条件。 */
    fun isArchiveStream(): Boolean = filter == EntryFilter.ARCHIVED

    companion object {
        // 旧版本保存的三项条件仍然可以恢复，缺少分类视为全部分类。
        fun decode(values: List<String>?): EntryOptions = runCatching {
            require(values != null && values.size in 3..4)
            EntryOptions(values[0].take(80), EntryFilter.valueOf(values[1]), EntrySort.valueOf(values[2]),
                values.getOrNull(3)?.trim()?.takeIf { it.isNotEmpty() })
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
    /** 归档条目单独计数，不计入以上任何活动统计。 */
    val archivedCount: Long = 0,
) {
    fun completionFraction(): Float = if (todoCount == 0L) 0f else
        (completedCount.toDouble() / todoCount).coerceIn(0.0, 1.0).toFloat()
}

/** 按计划日期聚合，而不是完成日期；不伪造当前模型没有存储的完成时刻。 */
data class DayBucket(val epochDay: Long, val dateCount: Long, val todoCount: Long, val completedCount: Long)
