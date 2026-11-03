package local.daybook.datetime

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

/** UI 选择日历日只使用 UTC 编码，不通过设备时区改变日期。 */
object CalendarDates {
    fun parseSupported(text: String): LocalDate? = runCatching {
        LocalDate.parse(text).takeIf { it.year in 1900..2200 }
    }.getOrNull()

    fun toPickerMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun fromPickerMillis(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** 月视图固定 6 行 7 列，跨月日期由调用方淡化显示，不改变日历日含义。 */
    fun monthStart(date: LocalDate): LocalDate = date.withDayOfMonth(1)

    fun monthGridStart(date: LocalDate): LocalDate = weekStart(monthStart(date))

    fun monthGrid(date: LocalDate): List<LocalDate> {
        val start = monthGridStart(date)
        return List(42) { offset -> start.plusDays(offset.toLong()) }
    }

    fun shiftMonth(date: LocalDate, amount: Long): LocalDate = monthStart(date).plusMonths(amount)
}
