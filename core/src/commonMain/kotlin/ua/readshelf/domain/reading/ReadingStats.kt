package ua.readshelf.domain.reading

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

// Every statistic is derived from the session list on demand. Nothing is stored,
// so editing or deleting a session can never leave a total out of step.

/** Length of the rolling week: today plus the six reading days before it. */
const val WEEK_DAYS: Int = 7

fun pagesOn(sessions: List<ReadingSession>, day: LocalDate): Int =
    sessions.filter { it.day == day }.sumOf { it.pages }

fun pagesInWeek(sessions: List<ReadingSession>, today: LocalDate): Int {
    val start = today.minus(WEEK_DAYS - 1, DateTimeUnit.DAY)
    return sessions.filter { it.day in start..today }.sumOf { it.pages }
}

fun pagesTotal(sessions: List<ReadingSession>): Int = sessions.sumOf { it.pages }

/**
 * The furthest page reached in the book, or 0 before the first session.
 * The maximum, not the latest session's end: re-reading an earlier chapter must not move the bookmark back.
 */
fun bookmarkOf(sessions: List<ReadingSession>, bookKey: String): Int =
    sessions.filter { it.bookKey == bookKey }.maxOfOrNull { it.toPage } ?: 0

/**
 * Whole percent of the book reached, or null when its length is unknown.
 * Rounded down, so the reader never sees 100 % with pages still left;
 * capped at 100 for a book whose length was later corrected downwards.
 */
fun progressPercent(sessions: List<ReadingSession>, bookKey: String, totalPages: Int?): Int? {
    if (totalPages == null || totalPages <= 0) return null
    return (bookmarkOf(sessions, bookKey) * 100 / totalPages).coerceAtMost(100)
}
