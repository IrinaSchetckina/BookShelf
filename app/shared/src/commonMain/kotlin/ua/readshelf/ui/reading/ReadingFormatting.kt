package ua.readshelf.ui.reading

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import ua.readshelf.domain.reading.SessionRejection
import kotlin.time.Instant

private val clockTime = LocalTime.Format {
    hour()
    char(':')
    minute()
}

internal fun formatTime(instant: Instant, zone: TimeZone): String =
    instant.toLocalDateTime(zone).time.format(clockTime)

internal fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "Today"
    today.minus(1, DateTimeUnit.DAY) -> "Yesterday"
    else -> day.toString()
}

internal fun streakLabel(days: Int): String = if (days == 1) "day in a row" else "days in a row"

// The date picker speaks in UTC midnights, independent of the device time zone.
internal fun LocalDate.toUtcEpochMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

internal fun utcEpochMillisToDate(millis: Long): LocalDate =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date

internal fun SessionRejection.message(): String = when (this) {
    SessionRejection.NegativeFrom -> "The start page cannot be below zero."
    SessionRejection.NotForward -> "The end page must be after the start page."
    SessionRejection.DayInFuture -> "A session cannot be dated in the future."
    SessionRejection.BeyondTotalPages -> "The end page is past the last page of the book."
}
