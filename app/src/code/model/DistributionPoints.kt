package local.daybook.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class DistributionPoint(val start: LocalDate, val endInclusive: LocalDate,
                             val dateCount: Long, val todoCount: Long, val completedCount: Long) {
    fun total(): Long = dateCount + todoCount
}

/** 年视图按月合并最多 12 点，周/月视图补齐零数据日，不依赖 UI 取全表明细。 */
fun distributionPoints(window: AnalysisWindow, buckets: List<DayBucket>): List<DistributionPoint> {
    val grouped = buckets.groupBy { bucket ->
        val date = LocalDate.ofEpochDay(bucket.epochDay)
        if (window.period == AnalysisPeriod.YEAR) date.withDayOfMonth(1) else date
    }
    val count = if (window.period == AnalysisPeriod.YEAR) 12 else
        (ChronoUnit.DAYS.between(window.start(), window.endInclusive()) + 1).toInt()
    return List(count) { index ->
        val start = if (window.period == AnalysisPeriod.YEAR) window.start().plusMonths(index.toLong())
            else window.start().plusDays(index.toLong())
        val end = if (window.period == AnalysisPeriod.YEAR) start.plusMonths(1).minusDays(1) else start
        val items = grouped[start].orEmpty()
        DistributionPoint(start, end, items.sumOf { it.dateCount }, items.sumOf { it.todoCount }, items.sumOf { it.completedCount })
    }
}
