package ua.readshelf.domain.reading

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * A reading day runs from [CUTOFF_HOUR]:00 to [CUTOFF_HOUR]:00 the next morning, local time,
 * so a chapter finished after midnight still belongs to the evening it started.
 */
object ReadingDay {

    const val CUTOFF_HOUR: Int = 4

    /**
     * Compares wall-clock hours rather than subtracting four hours from [instant]:
     * on a daylight-saving night those two differ, and the reader thinks in wall-clock time.
     */
    fun of(instant: Instant, zone: TimeZone): LocalDate {
        val local = instant.toLocalDateTime(zone)
        return if (local.hour < CUTOFF_HOUR) local.date.minus(1, DateTimeUnit.DAY) else local.date
    }
}
