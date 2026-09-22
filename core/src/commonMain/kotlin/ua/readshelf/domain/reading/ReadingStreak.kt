package ua.readshelf.domain.reading

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
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
 * [today] must be the current reading day, `ReadingDay.of(now, zone)`, not the calendar date:
 * between midnight and 04:00 the calendar date is a day ahead and would break a live streak.
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

    val activeDays = activeDays(sessions.filter { it.day <= today })
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

/**
 * Length of the longest reading streak in the whole history of [sessions], with the same days
 * and freezes as [currentStreak]:
 * - a day is active when at least one page was read on it;
 * - a streak runs from one active day to another; each active day inside adds one, each
 *   inactive day inside is frozen and adds nothing;
 * - every streak has its own budget of [maxFreezes]: an earlier streak does not spend a later
 *   one's, and streaks may overlap.
 *
 * There is no "today" here, so there is no grace day and no session is ignored for its date;
 * a caller that wants only the past filters [sessions] first. The result is never shorter than
 * [currentStreak] over the same sessions and budget.
 */
fun longestStreak(
    sessions: List<ReadingSession>,
    maxFreezes: Int = DEFAULT_MAX_FREEZES,
): Int {
    require(maxFreezes >= 0) { "maxFreezes must not be negative, was $maxFreezes" }

    val days = activeDays(sessions).sorted()

    // Sliding window over the sorted active days: [first, last] is a streak while the inactive
    // days inside it fit the budget. Each day enters and leaves the window once.
    var longest = 0
    var first = 0
    for (last in days.indices) {
        while (inactiveDaysBetween(days, first, last) > maxFreezes) first++
        longest = maxOf(longest, last - first + 1)
    }
    return longest
}

/** Days on which at least one page was read; several sessions on one day give one day. */
private fun activeDays(sessions: List<ReadingSession>): Set<LocalDate> =
    sessions
        .groupBy { it.day }
        .filterValues { daySessions -> daySessions.sumOf { it.pages } >= 1 }
        .keys

/** Inactive days strictly inside the stretch from `days[first]` to `days[last]`. */
private fun inactiveDaysBetween(days: List<LocalDate>, first: Int, last: Int): Int =
    days[first].daysUntil(days[last]) + 1 - (last - first + 1)
