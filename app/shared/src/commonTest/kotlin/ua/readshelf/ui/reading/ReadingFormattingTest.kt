package ua.readshelf.ui.reading

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

private val TODAY = LocalDate(2026, 9, 16)

class ReadingFormattingTest {

    @Test
    fun todayAndYesterdayGetNames() {
        assertEquals("Today", dayLabel(TODAY, TODAY))
        assertEquals("Yesterday", dayLabel(LocalDate(2026, 9, 15), TODAY))
    }

    @Test
    fun olderDaysShowTheDate() {
        val label = dayLabel(LocalDate(2026, 9, 14), TODAY)

        assertEquals("2026-09-14", label)
    }

    @Test
    fun yesterdayAcrossMonthBoundaryIsStillYesterday() {
        val label = dayLabel(LocalDate(2026, 9, 30), LocalDate(2026, 10, 1))

        assertEquals("Yesterday", label)
    }

    // The date picker works in UTC midnights; a zone-dependent conversion would shift the day.
    @Test
    fun pickerDateSurvivesTheRoundTrip() {
        val restored = utcEpochMillisToDate(TODAY.toUtcEpochMillis())

        assertEquals(TODAY, restored)
    }

    @Test
    fun timeIsShownInTheGivenZone() {
        val zone = UtcOffset(hours = 3).asTimeZone()
        val instant = LocalDateTime(2026, 9, 16, 22, 10).toInstant(zone)

        val time = formatTime(instant, zone)

        assertEquals("22:10", time)
    }

    @Test
    fun singleDigitHoursArePadded() {
        val zone = UtcOffset(hours = 3).asTimeZone()
        val instant = LocalDateTime(2026, 9, 17, 1, 5).toInstant(zone)

        val time = formatTime(instant, zone)

        assertEquals("01:05", time)
    }
}
