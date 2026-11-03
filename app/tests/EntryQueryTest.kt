package local.daybook.model

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class EntryQueryTest {
    @Test fun optionsRoundTripAndInvalidStateFallback() {
        val options = EntryOptions("中文 %_", EntryFilter.OPEN, EntrySort.TITLE)
        assertEquals(options, EntryOptions.decode(options.encode()))
        assertEquals(EntryOptions(), EntryOptions.decode(null))
        assertEquals(EntryOptions(), EntryOptions.decode(listOf("partial")))
        assertEquals(EntryOptions(), EntryOptions.decode(listOf("query", "UNKNOWN", "TITLE")))
    }
    @Test fun dateAndTodoFiltersCannotLeakIntoOtherType() {
        assertEquals(EntryFilter.ALL, EntryOptions(filter = EntryFilter.DONE).normalized(EntryKind.DATE).filter)
        assertEquals(EntryFilter.ALL, EntryOptions(filter = EntryFilter.FUTURE).normalized(EntryKind.TODO).filter)
        assertEquals(80, EntryOptions(search = "a".repeat(100)).normalized(EntryKind.DATE).search.length)
    }
    @Test fun emptySummaryDoesNotDivideByZero() {
        assertEquals(0f, EntrySummary().completionFraction(), 0.001f)
        assertEquals(0.5f, EntrySummary(todoCount = 4, completedCount = 2).completionFraction(), 0.001f)
        assertEquals(1f, EntrySummary(todoCount = 1, completedCount = 2).completionFraction(), 0.001f)
    }

    @Test fun legacyThreeItemOptionsStillRestore() {
        val restored = EntryOptions.decode(listOf("生日", "TODAY", "DATE_DESC"))
        assertEquals(EntryOptions("生日", EntryFilter.TODAY, EntrySort.DATE_DESC, null), restored)
    }
    @Test fun archivedFilterAndCategoryRoundTrip() {
        val options = EntryOptions("", EntryFilter.ARCHIVED, EntrySort.DATE_ASC, "c1")
        assertEquals(options, EntryOptions.decode(options.encode()))
        assertEquals(EntryFilter.ARCHIVED, options.normalized(EntryKind.TODO).filter)
        assertEquals(EntryFilter.ARCHIVED, options.normalized(EntryKind.DATE).filter)
        // 归档流不接分类条件，normalized 清空分类；普通列表保留分类。
        assertNull(options.normalized(EntryKind.DATE).categoryId)
        assertTrue(options.normalized(EntryKind.TODO).isArchiveStream())
        assertFalse(EntryOptions(categoryId = "c1").isArchiveStream())
        assertEquals("c1", EntryOptions(categoryId = "c1").normalized(EntryKind.DATE).categoryId)
        assertNull(EntryOptions(categoryId = "   ").normalized(EntryKind.DATE).categoryId)
        assertEquals(64, EntryOptions(categoryId = "c".repeat(100)).normalized(EntryKind.DATE).categoryId!!.length)
    }
    @Test fun leapMonthAndYearWindows() {
        val month = AnalysisWindow(AnalysisPeriod.MONTH, LocalDate.of(2024, 2, 20))
        assertEquals(LocalDate.of(2024, 2, 1), month.start())
        assertEquals(LocalDate.of(2024, 2, 29), month.endInclusive())
        val year = AnalysisWindow(AnalysisPeriod.YEAR, month.anchor)
        assertEquals(LocalDate.of(2024, 1, 1), year.start())
        assertEquals(LocalDate.of(2024, 12, 31), year.endInclusive())
    }
    @Test fun weekCrossesYearWithoutDroppingDays() {
        val window = AnalysisWindow(AnalysisPeriod.WEEK, LocalDate.of(2026, 1, 1))
        assertEquals(LocalDate.of(2025, 12, 29), window.start())
        assertEquals(LocalDate.of(2026, 1, 4), window.endInclusive())
        assertEquals(LocalDate.of(2026, 1, 8), window.shifted(1).anchor)
    }
    @Test fun monthlyPointsIncludeEmptyDays() {
        val window = AnalysisWindow(AnalysisPeriod.MONTH, LocalDate.of(2024, 2, 15))
        val points = distributionPoints(window, listOf(DayBucket(LocalDate.of(2024, 2, 29).toEpochDay(), 2, 3, 1)))
        assertEquals(29, points.size)
        assertEquals(0L, points.first().total())
        assertEquals(5L, points.last().total())
        assertEquals(1L, points.last().completedCount)
    }
    @Test fun annualPointsAggregateByMonth() {
        val window = AnalysisWindow(AnalysisPeriod.YEAR, LocalDate.of(2024, 2, 15))
        val buckets = listOf(DayBucket(LocalDate.of(2024, 2, 1).toEpochDay(), 1, 2, 1),
            DayBucket(LocalDate.of(2024, 2, 29).toEpochDay(), 3, 4, 2))
        val points = distributionPoints(window, buckets)
        assertEquals(12, points.size)
        assertEquals(10L, points[1].total())
        assertEquals(3L, points[1].completedCount)
        assertEquals(LocalDate.of(2024, 2, 29), points[1].endInclusive)
    }
}
