package ua.readshelf.domain.reading

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

// A fixed offset needs no time-zone database, so these run on js and wasmJs too.
// The daylight-saving case needs a real zone and lives in jvmTest.
private val KYIV_WINTER = UtcOffset(hours = 2).asTimeZone()

private fun readingDayAt(year: Int, month: Int, day: Int, hour: Int, minute: Int): LocalDate =
    ReadingDay.of(LocalDateTime(year, month, day, hour, minute).toInstant(KYIV_WINTER), KYIV_WINTER)

class ReadingDayTest {

    @Test
    fun sessionAtHalfPastOneBelongsToPreviousDay() {
        val day = readingDayAt(2026, 9, 16, 1, 30)

        assertEquals(LocalDate(2026, 9, 15), day)
    }

    @Test
    fun sessionAtHalfPastFourBelongsToSameDay() {
        val day = readingDayAt(2026, 9, 16, 4, 30)

        assertEquals(LocalDate(2026, 9, 16), day)
    }

    @Test
    fun cutoffStartsNewDayExactlyAtFour() {
        val lastMinute = readingDayAt(2026, 9, 16, 3, 59)
        val firstMinute = readingDayAt(2026, 9, 16, 4, 0)

        assertEquals(LocalDate(2026, 9, 15), lastMinute)
        assertEquals(LocalDate(2026, 9, 16), firstMinute)
    }

    @Test
    fun lateEveningAndAfterMidnightAreOneReadingDay() {
        val evening = readingDayAt(2026, 9, 15, 23, 0)
        val afterMidnight = readingDayAt(2026, 9, 16, 2, 0)

        assertEquals(LocalDate(2026, 9, 15), evening)
        assertEquals(evening, afterMidnight)
    }

    @Test
    fun sessionBeforeCutoffOnFirstOfMonthBelongsToPreviousMonth() {
        val day = readingDayAt(2026, 10, 1, 0, 15)

        assertEquals(LocalDate(2026, 9, 30), day)
    }
}
