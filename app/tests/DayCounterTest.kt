package local.daybook.datetime

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class DayCounterTest {
    private fun counter(instant: String = "2024-03-10T16:00:00Z", zone: String = "America/New_York") =
        DayCounter(Clock.fixed(Instant.parse(instant), ZoneId.of(zone)))

    @Test fun todayIsNotTomorrow() {
        assertEquals("就是今天", counter().describe(LocalDate.parse("2024-03-10"), false, false))
    }
    @Test fun daylightSavingUsesCalendarDays() {
        assertEquals("已过 1 天", counter().describe(LocalDate.parse("2024-03-09"), false, false))
        assertEquals("还有 1 天", counter().describe(LocalDate.parse("2024-03-11"), false, true))
    }
    @Test fun futureCountUpHasNotStarted() {
        assertEquals("尚未开始", counter().describe(LocalDate.parse("2024-03-11"), true, true))
    }
    @Test fun inclusiveChangesStartedCountOnly() {
        assertEquals("第 1 天", counter().describe(LocalDate.parse("2024-03-10"), true, true))
        assertEquals("第 2 天", counter().describe(LocalDate.parse("2024-03-09"), true, true))
    }
    @Test fun leapDayAndYearBoundary() {
        val leap = counter("2024-03-01T12:00:00Z", "UTC")
        assertEquals("已过 1 天", leap.describe(LocalDate.parse("2024-02-29"), false, false))
        val year = counter("2025-01-01T12:00:00Z", "UTC")
        assertEquals("已过 1 天", year.describe(LocalDate.parse("2024-12-31"), false, false))
    }
    @Test fun sameInstantRespectsTimeZone() {
        val instant = "2024-01-01T01:00:00Z"
        assertEquals("还有 1 天", counter(instant, "America/Los_Angeles")
            .describe(LocalDate.parse("2024-01-01"), false, false))
        assertEquals("就是今天", counter(instant, "Asia/Shanghai")
            .describe(LocalDate.parse("2024-01-01"), false, false))
    }
}
