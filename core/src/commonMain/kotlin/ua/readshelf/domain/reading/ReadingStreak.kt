package ua.readshelf.domain.reading

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** Freeze budget for the current streak when the caller does not pass one. */
const val DEFAULT_MAX_FREEZES: Int = 2

/**
 * Length of the current reading streak, counted back from [today]:
 * - a day is active when at least one page was read on it; several sessions or books on one
 *   day make one active day;
 * - an empty [today] is a grace day: it neither breaks the streak nor adds to it, and counting
 *   starts from yesterday;
 * - each active day adds one;
 * - an inactive day is frozen while any of the [maxFreezes] budget is left: it adds nothing
 *   but keeps the chain going;
 * - the first inactive day with no freezes left ends the streak.
 *
 * The budget belongs to this call, not to stored state: the result is derived from [sessions]
 * alone, like every other reading statistic. Sessions after [today] are ignored.
 */
fun currentStreak(
    sessions: List<ReadingSession>,
    today: LocalDate,
    maxFreezes: Int = DEFAULT_MAX_FREEZES,
): Int {
    require(maxFreezes >= 0) { "maxFreezes must not be negative, was $maxFreezes" }

    val activeDays = sessions
        .filter { it.day <= today }
        .groupBy { it.day }
        .filterValues { daySessions -> daySessions.sumOf { it.pages } >= 1 }
        .keys
    // Walking past the earliest active day could only spend freezes, never add to the count.
    val earliest = activeDays.minOrNull() ?: return 0

    var streak = 0
    var freezesLeft = maxFreezes
    var day = if (today in activeDays) today else today.minus(1, DateTimeUnit.DAY)
    while (day >= earliest) {
        when {
            day in activeDays -> streak++
            freezesLeft > 0 -> freezesLeft--
            else -> break
        }
        day = day.minus(1, DateTimeUnit.DAY)
    }
    return streak
}
