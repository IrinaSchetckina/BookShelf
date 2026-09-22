package ua.readshelf.domain.reading

import kotlinx.datetime.LocalDate

/** Freeze budget for the current streak when the caller does not pass one. */
const val DEFAULT_MAX_FREEZES: Int = 2

/**
 * Length of the current reading streak: consecutive active days (at least one page read)
 * counted back from [today], where up to [maxFreezes] inactive days may be bridged.
 *
 * Signature stub for the TDD red step; the rules live in CurrentStreakTest.
 */
fun currentStreak(
    sessions: List<ReadingSession>,
    today: LocalDate,
    maxFreezes: Int = DEFAULT_MAX_FREEZES,
): Int = throw NotImplementedError("currentStreak is not implemented yet")
