package local.daybook.model

import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

enum class AnalysisPeriod { WEEK, MONTH, YEAR }

data class AnalysisWindow(val period: AnalysisPeriod, val anchor: LocalDate) {
    fun start(): LocalDate = when (period) {
        AnalysisPeriod.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        AnalysisPeriod.MONTH -> anchor.withDayOfMonth(1)
        AnalysisPeriod.YEAR -> anchor.withDayOfYear(1)
    }
    fun endInclusive(): LocalDate = when (period) {
        AnalysisPeriod.WEEK -> start().plusDays(6)
        AnalysisPeriod.MONTH -> start().plusMonths(1).minusDays(1)
        AnalysisPeriod.YEAR -> start().plusYears(1).minusDays(1)
    }
    fun shifted(amount: Long): AnalysisWindow = copy(anchor = when (period) {
        AnalysisPeriod.WEEK -> anchor.plusWeeks(amount)
        AnalysisPeriod.MONTH -> anchor.plusMonths(amount)
        AnalysisPeriod.YEAR -> anchor.plusYears(amount)
    })
}
