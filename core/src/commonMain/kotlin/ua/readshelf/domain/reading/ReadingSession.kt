package ua.readshelf.domain.reading

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * One sitting with a book: the reader moved the bookmark from [fromPage] to [toPage].
 * Consecutive sessions line up edge to edge — the previous [toPage] is the next [fromPage].
 */
data class ReadingSession(
    val id: String,
    val bookKey: String,
    val fromPage: Int,
    val toPage: Int,
    /** The reading day this session counts towards; see [ReadingDay]. */
    val day: LocalDate,
    /** When the session was recorded; null when the time is unknown (entered after the fact). */
    val recordedAt: Instant?,
) {
    /**
     * `toPage - fromPage`, not `+ 1`: sessions share their boundary page,
     * so counting it on both sides would inflate every total.
     */
    val pages: Int get() = toPage - fromPage
}
