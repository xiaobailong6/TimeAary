package local.daybook.datetime

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CalendarDatesTest {
    @Test fun pickerEncodingUsesUtcMidnight() {
        assertEquals(0L, CalendarDates.toPickerMillis(LocalDate.of(1970, 1, 1)))
        assertEquals(LocalDate.of(1970, 1, 1), CalendarDates.fromPickerMillis(0L))
    }

    @Test fun supportedBoundsAndLeapDateRoundTrip() {
        listOf("1900-01-01", "2024-02-29", "2200-12-31").forEach {
            val date = LocalDate.parse(it)
            assertEquals(date, CalendarDates.fromPickerMillis(CalendarDates.toPickerMillis(date)))
        }
    }

    @Test fun invalidAndUnsupportedDatesAreRejected() {
        listOf("2026-02-30", "2026-13-01", "1899-12-31", "2201-01-01", "2026-", "").forEach {
            assertNull(CalendarDates.parseSupported(it))
        }
    }

    @Test fun weekStartsOnMondayAcrossYearBoundary() {
        assertEquals(LocalDate.of(2025, 12, 29), CalendarDates.weekStart(LocalDate.of(2026, 1, 1)))
        assertEquals(LocalDate.of(2025, 12, 29), CalendarDates.weekStart(LocalDate.of(2026, 1, 4)))
    }

    @Test fun mondayStaysInItsOwnWeek() {
        val monday = LocalDate.of(2026, 3, 16)
        assertEquals(monday, CalendarDates.weekStart(monday))
    }

    @Test fun monthGridAlwaysHasSixRowsAndStartsOnMonday() {
        listOf("2026-02-17", "2024-02-29", "2026-01-01", "2100-12-31").forEach { text ->
            val month = LocalDate.parse(text)
            val grid = CalendarDates.monthGrid(month)
            assertEquals(42, grid.size)
            assertEquals(CalendarDates.weekStart(CalendarDates.monthStart(month)), grid.first())
            assertTrue(grid.contains(month.withDayOfMonth(1)))
            assertTrue(grid.contains(month.withDayOfMonth(month.lengthOfMonth())))
        }
    }

    @Test fun shiftMonthKeepsDayOfMonthWithinTargetMonth() {
        val january = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 2, 1), CalendarDates.shiftMonth(january, 1).withDayOfMonth(1))
        assertEquals(LocalDate.of(2025, 12, 1), CalendarDates.shiftMonth(january, -1).withDayOfMonth(1))
        assertEquals(LocalDate.of(2027, 1, 1), CalendarDates.shiftMonth(january, 12).withDayOfMonth(1))
    }

    @Test fun gridCoversMonthSpanningWeeksAcrossYearBoundary() {
        val grid = CalendarDates.monthGrid(LocalDate.of(2026, 1, 15))
        // 2026-01-01 是周四，网格必须从 2025-12-29 的周一开始。
        assertEquals(LocalDate.of(2025, 12, 29), grid.first())
        assertTrue(grid.contains(LocalDate.of(2026, 1, 31)))
    }

}
