package ua.readshelf.domain.reading

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

// Needs a real time-zone database, which only the JVM target has without extra setup.
private val KYIV = TimeZone.of("Europe/Kyiv")

class ReadingDayDaylightSavingTest {

    /**
     * On 29 March 2026 Kyiv clocks jump from 03:00 to 04:00, so local 04:30 is only
     * three and a half real hours after midnight. Subtracting four hours from the
     * instant would file this session under 28 March.
     */
    @Test
    fun halfPastFourAfterSpringForwardBelongsToSameDay() {
        val instant = LocalDateTime(2026, 3, 29, 4, 30).toInstant(KYIV)

        val day = ReadingDay.of(instant, KYIV)

        assertEquals(LocalDate(2026, 3, 29), day)
    }

    /** On 25 October 2026 the clocks fall back from 04:00 to 03:00; 03:30 happens twice. */
    @Test
    fun repeatedHourAfterFallBackStaysOnPreviousDay() {
        val instant = LocalDateTime(2026, 10, 25, 3, 30).toInstant(KYIV)

        val day = ReadingDay.of(instant, KYIV)

        assertEquals(LocalDate(2026, 10, 24), day)
    }
}
