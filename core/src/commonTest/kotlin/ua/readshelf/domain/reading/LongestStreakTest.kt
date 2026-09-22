package ua.readshelf.domain.reading

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// Real Open Library works (see the book-fixtures skill).
private const val DUNE_KEY = "/works/OL893414W"
private const val HOBBIT_KEY = "/works/OL27482W"

/** Day 1 of the history; "day N" in the L-criteria of §5.9 is [DAY_ONE] + N − 1. */
private val DAY_ONE = LocalDate(2026, 9, 1)

// The reference day for the currentStreak comparisons (L25), as in CurrentStreakTest.
private val TODAY = LocalDate(2026, 9, 16)

private var nextId = 0

private fun day(n: Int): LocalDate = DAY_ONE.plus(n - 1, DateTimeUnit.DAY)

/** A session on [date]; [pages] = 0 gives a session that read nothing. */
private fun session(date: LocalDate, pages: Int = 10, bookKey: String = DUNE_KEY): ReadingSession =
    ReadingSession(
        id = "s${++nextId}",
        bookKey = bookKey,
        fromPage = 100,
        toPage = 100 + pages,
        day = date,
        recordedAt = null,
    )

/** One reading session on each of the given history days. */
private fun activeOn(vararg days: Int): List<ReadingSession> = days.map { session(day(it)) }

/** One reading session on each of the given days before [TODAY]. */
private fun activeDaysAgo(vararg days: Int): List<ReadingSession> =
    days.map { session(TODAY.minus(it, DateTimeUnit.DAY)) }

class LongestStreakTest {

    // --- Basics ---

    // L1
    @Test
    fun noSessionsMeansNoStreak() {
        val streak = longestStreak(emptyList(), maxFreezes = 0)

        assertEquals(0, streak)
    }

    // L2
    @Test
    fun singleActiveDayGivesOne() {
        val streak = longestStreak(activeOn(1), maxFreezes = 0)

        assertEquals(1, streak)
    }

    // L3
    @Test
    fun threeDaysInARowGiveThree() {
        val streak = longestStreak(activeOn(1, 2, 3), maxFreezes = 0)

        assertEquals(3, streak)
    }

    // L4
    @Test
    fun laterLongerRunWins() {
        val streak = longestStreak(activeOn(1, 2, 5, 6, 7, 8, 9), maxFreezes = 0)

        assertEquals(5, streak)
    }

    // L5: the longest run is not necessarily the latest one.
    @Test
    fun earlierLongerRunWins() {
        val streak = longestStreak(activeOn(1, 2, 3, 4, 5, 8, 9), maxFreezes = 0)

        assertEquals(5, streak)
    }

    // L6
    @Test
    fun tiedRunsGiveTheirCommonLength() {
        val streak = longestStreak(activeOn(1, 2, 3, 6, 7, 8), maxFreezes = 0)

        assertEquals(3, streak)
    }

    // --- Freezes ---

    // L7: a frozen day bridges the gap but does not count.
    @Test
    fun frozenDayBridgesTheGapWithoutCounting() {
        val streak = longestStreak(activeOn(1, 2, 4, 5), maxFreezes = 1)

        assertEquals(4, streak)
    }

    // L8
    @Test
    fun gapLongerThanTheBudgetSplitsTheRuns() {
        val streak = longestStreak(activeOn(1, 2, 5, 6), maxFreezes = 1)

        assertEquals(2, streak)
    }

    // L9: the budget covers the whole streak, not each gap separately.
    @Test
    fun budgetIsSharedAcrossGapsWithinOneStreak() {
        val streak = longestStreak(activeOn(1, 2, 4, 5, 7, 8), maxFreezes = 1)

        assertEquals(4, streak)
    }

    // L10: each streak has its own budget; an earlier streak does not spend a later one's.
    @Test
    fun eachStreakHasItsOwnBudget() {
        val streak = longestStreak(activeOn(1, 2, 3, 5, 6, 20, 21, 23, 24), maxFreezes = 1)

        assertEquals(5, streak)
    }

    // L11: candidate streaks overlap. Day 4 closes {1, 2, 4} and also opens {4, 6, 7, 8, 9};
    // restarting the budget only after a break would miss the second one and return 4.
    @Test
    fun overlappingStreaksAreAllConsidered() {
        val streak = longestStreak(activeOn(1, 2, 4, 6, 7, 8, 9), maxFreezes = 1)

        assertEquals(5, streak)
    }

    // L12
    @Test
    fun freezesAloneDoNotMakeAStreak() {
        val sessions = listOf(session(day(1), pages = 0))

        val streak = longestStreak(sessions, maxFreezes = 3)

        assertEquals(0, streak)
    }

    // L13
    @Test
    fun unusedFreezesAtTheEdgesAddNothing() {
        val streak = longestStreak(activeOn(1), maxFreezes = 3)

        assertEquals(1, streak)
    }

    // --- One active day per date ---

    // L14
    @Test
    fun sessionsOfTwoBooksOnOneDayCountOnce() {
        val sessions = listOf(
            session(day(1), bookKey = DUNE_KEY),
            session(day(1), bookKey = HOBBIT_KEY),
            session(day(2)),
        )

        val streak = longestStreak(sessions, maxFreezes = 0)

        assertEquals(2, streak)
    }

    // L15
    @Test
    fun dayWithOnlyAZeroPageSessionBreaksTheStreak() {
        val sessions = activeOn(1, 3) + session(day(2), pages = 0)

        val streak = longestStreak(sessions, maxFreezes = 0)

        assertEquals(1, streak)
    }

    // L16: activity is the day's total; one page is enough.
    @Test
    fun onePageBesideAZeroPageSessionMakesTheDayActive() {
        val sessions = listOf(session(day(1), pages = 0), session(day(1), pages = 1))

        val streak = longestStreak(sessions, maxFreezes = 0)

        assertEquals(1, streak)
    }

    // --- Invalid budget ---

    // L17
    @Test
    fun negativeFreezeBudgetIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            longestStreak(activeOn(1), maxFreezes = -1)
        }
    }

    // L17, empty input: an early return for "no sessions" must not hide an invalid call.
    @Test
    fun negativeFreezeBudgetIsRejectedWithoutSessions() {
        assertFailsWith<IllegalArgumentException> {
            longestStreak(emptyList(), maxFreezes = -1)
        }
    }

    // --- Calendar, order and scale ---

    // L18
    @Test
    fun orderOfSessionsDoesNotMatter() {
        val streak = longestStreak(activeOn(3, 1, 2), maxFreezes = 0)

        assertEquals(3, streak)
    }

    // L19: adjacent dates across a year and a month end (2027 is not a leap year).
    @Test
    fun streakRunsAcrossTheNewYearAndTheEndOfFebruary() {
        val newYear = listOf(session(LocalDate(2026, 12, 31)), session(LocalDate(2027, 1, 1)))
        val february = listOf(session(LocalDate(2027, 2, 28)), session(LocalDate(2027, 3, 1)))

        assertEquals(2, longestStreak(newYear, maxFreezes = 0))
        assertEquals(2, longestStreak(february, maxFreezes = 0))
    }

    // L20: a huge budget bridges a ten-year gap without overflowing.
    @Test
    fun hugeFreezeBudgetBridgesAHugeGap() {
        val sessions = activeOn(1, 3651)

        val streak = longestStreak(sessions, maxFreezes = Int.MAX_VALUE)

        assertEquals(2, streak)
    }

    // L21: ten years of daily reading.
    @Test
    fun tenYearsInARowGiveTheirLength() {
        val sessions = (1..3650).map { session(day(it)) }

        val streak = longestStreak(sessions, maxFreezes = 0)

        assertEquals(3650, streak)
    }

    // L24: there is no "today" here, so no session is ignored for being late.
    @Test
    fun sessionsOfAnyDateCount() {
        val sessions = listOf(session(LocalDate(2030, 1, 1)), session(LocalDate(2030, 1, 2)))

        val streak = longestStreak(sessions, maxFreezes = 0)

        assertEquals(2, streak)
    }

    // --- Default budget of two ---

    // L22
    @Test
    fun defaultBudgetBridgesTwoEmptyDays() {
        val streak = longestStreak(activeOn(1, 4))

        assertEquals(2, streak)
    }

    // L23
    @Test
    fun defaultBudgetDoesNotBridgeThreeEmptyDays() {
        val streak = longestStreak(activeOn(1, 5))

        assertEquals(1, streak)
    }

    // --- Relation to currentStreak ---

    // L25: the current streak is one of the streaks, so the longest is never shorter.
    // Data sets of S14, S15 and S16, seen from their own today and from ten days later,
    // when the current streak is gone but the record stays.
    @Test
    fun longestIsNeverShorterThanTheCurrentStreak() {
        val cases = listOf(
            Triple(activeDaysAgo(0, 1, 4, 5), 2, 4),
            Triple(activeDaysAgo(0, 1, 3, 4, 6), 1, 4),
            Triple(activeDaysAgo(2, 3, 4), 1, 3),
        )
        val tenDaysLater = TODAY.plus(10, DateTimeUnit.DAY)

        for ((sessions, maxFreezes, expected) in cases) {
            val longest = longestStreak(sessions, maxFreezes)

            assertEquals(expected, longest, "mF=$maxFreezes")
            for (today in listOf(TODAY, tenDaysLater)) {
                val current = currentStreak(sessions, today, maxFreezes)
                assertTrue(longest >= current, "longest $longest < current $current on $today")
            }
        }
    }

    // L26: currentStreak ignores the day after today (S24), longestStreak
    // does not, so here the two differ.
    @Test
    fun sessionAfterTodayCountsOnlyTowardsTheLongest() {
        val sessions = listOf(session(TODAY), session(TODAY.plus(1, DateTimeUnit.DAY)))

        assertEquals(1, currentStreak(sessions, TODAY, maxFreezes = 0))
        assertEquals(2, longestStreak(sessions, maxFreezes = 0))
    }
}
