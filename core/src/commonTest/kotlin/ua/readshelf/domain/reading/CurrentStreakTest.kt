package ua.readshelf.domain.reading

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// Real Open Library works (see the book-fixtures skill).
private const val DUNE_KEY = "/works/OL893414W"
private const val HOBBIT_KEY = "/works/OL27482W"

private val TODAY = LocalDate(2026, 9, 16)

// A fixed offset needs no time-zone database, so these tests also run on js and wasmJs.
private val KYIV_SUMMER = UtcOffset(hours = 3).asTimeZone()

private var nextId = 0

private fun daysAgo(days: Int): LocalDate = TODAY.minus(days, DateTimeUnit.DAY)

/** A session on [day]; [pages] = 0 gives a session that read nothing. */
private fun session(day: LocalDate, pages: Int = 10, bookKey: String = DUNE_KEY): ReadingSession =
    ReadingSession(
        id = "s${++nextId}",
        bookKey = bookKey,
        fromPage = 100,
        toPage = 100 + pages,
        day = day,
        recordedAt = null,
    )

/** One reading session on each of the given days ago. */
private fun activeOn(vararg days: Int): List<ReadingSession> = days.map { session(daysAgo(it)) }

class CurrentStreakTest {

    // --- Basics ---

    // S1
    @Test
    fun noSessionsMeansNoStreak() {
        val streak = currentStreak(emptyList(), TODAY, maxFreezes = 0)

        assertEquals(0, streak)
    }

    // S2
    @Test
    fun readingOnlyTodayGivesOne() {
        val streak = currentStreak(activeOn(0), TODAY, maxFreezes = 0)

        assertEquals(1, streak)
    }

    // S3
    @Test
    fun todayAndYesterdayGiveTwo() {
        val streak = currentStreak(activeOn(0, 1), TODAY, maxFreezes = 0)

        assertEquals(2, streak)
    }

    // S4: today is a grace day — it neither breaks the streak nor adds to it.
    @Test
    fun emptyTodayIsGraceAndCountingStartsFromYesterday() {
        val streak = currentStreak(activeOn(1, 2, 3), TODAY, maxFreezes = 0)

        assertEquals(3, streak)
    }

    // S5
    @Test
    fun emptyTodayAndYesterdayWithoutFreezesMeansNoStreak() {
        val streak = currentStreak(activeOn(2), TODAY, maxFreezes = 0)

        assertEquals(0, streak)
    }

    // S6
    @Test
    fun firstInactiveDayEndsTheStreak() {
        val streak = currentStreak(activeOn(0, 1, 3), TODAY, maxFreezes = 0)

        assertEquals(2, streak)
    }

    // --- One active day per date ---

    // S7
    @Test
    fun twoSessionsOfOneBookOnOneDayCountOnce() {
        val sessions = listOf(session(TODAY), session(TODAY))

        val streak = currentStreak(sessions, TODAY, maxFreezes = 0)

        assertEquals(1, streak)
    }

    // S8
    @Test
    fun sessionsOfTwoBooksOnOneDayCountOnce() {
        val sessions = listOf(session(TODAY, bookKey = DUNE_KEY), session(TODAY, bookKey = HOBBIT_KEY))

        val streak = currentStreak(sessions, TODAY, maxFreezes = 0)

        assertEquals(1, streak)
    }

    // S9
    @Test
    fun zeroPageSessionDoesNotMakeTodayActive() {
        val sessions = listOf(session(TODAY, pages = 0))

        val streak = currentStreak(sessions, TODAY, maxFreezes = 0)

        assertEquals(0, streak)
    }

    // S10
    @Test
    fun dayWithOnlyAZeroPageSessionBreaksTheStreak() {
        val sessions = activeOn(0, 2) + session(daysAgo(1), pages = 0)

        val streak = currentStreak(sessions, TODAY, maxFreezes = 0)

        assertEquals(1, streak)
    }

    // S29: the threshold is one page, so a single page is enough.
    @Test
    fun onePageMakesTheDayActive() {
        val sessions = listOf(session(TODAY, pages = 1))

        val streak = currentStreak(sessions, TODAY, maxFreezes = 0)

        assertEquals(1, streak)
    }

    // S30: a zero-page session today leaves today inactive, which is grace, not a break.
    @Test
    fun zeroPageTodayIsStillGrace() {
        val sessions = listOf(session(TODAY, pages = 0)) + activeOn(1)

        val streak = currentStreak(sessions, TODAY, maxFreezes = 0)

        assertEquals(1, streak)
    }

    // S32: activity is the day's total, not a property of each session: a zero-page session
    // next to a real one does not spoil the day.
    @Test
    fun zeroPageSessionBesideARealOneLeavesTheDayActive() {
        val sessions = listOf(session(TODAY, pages = 0), session(TODAY, pages = 5))

        val streak = currentStreak(sessions, TODAY, maxFreezes = 0)

        assertEquals(1, streak)
    }

    // S11 — the streak half of AC-13: 23:00 yesterday and 02:00 today are one reading day.
    @Test
    fun lateEveningAndAfterMidnightCountAsOneDay() {
        val evening = ReadingDay.of(LocalDateTime(2026, 9, 15, 23, 0).toInstant(KYIV_SUMMER), KYIV_SUMMER)
        val afterMidnight = ReadingDay.of(LocalDateTime(2026, 9, 16, 2, 0).toInstant(KYIV_SUMMER), KYIV_SUMMER)
        val sessions = listOf(session(evening), session(afterMidnight))

        val streak = currentStreak(sessions, TODAY, maxFreezes = 0)

        assertEquals(1, streak)
    }

    // --- Freezes ---

    // S12: a frozen day neither adds to the count nor breaks the chain.
    @Test
    fun frozenDayBridgesTheGapWithoutCounting() {
        val streak = currentStreak(activeOn(0, 1, 3, 4), TODAY, maxFreezes = 1)

        assertEquals(4, streak)
    }

    // S13
    @Test
    fun streakEndsWhenTheFreezeBudgetRunsOut() {
        val streak = currentStreak(activeOn(0, 1, 4), TODAY, maxFreezes = 1)

        assertEquals(2, streak)
    }

    // S14
    @Test
    fun twoFreezesBridgeTwoEmptyDaysInARow() {
        val streak = currentStreak(activeOn(0, 1, 4, 5), TODAY, maxFreezes = 2)

        assertEquals(4, streak)
    }

    // S15: the budget covers the whole current stretch, not each gap separately.
    @Test
    fun freezeBudgetIsSharedAcrossSeparateGaps() {
        val streak = currentStreak(activeOn(0, 1, 3, 4, 6), TODAY, maxFreezes = 1)

        assertEquals(4, streak)
    }

    // S16
    @Test
    fun graceTodayAndAFrozenYesterdayKeepTheStreak() {
        val streak = currentStreak(activeOn(2, 3, 4), TODAY, maxFreezes = 1)

        assertEquals(3, streak)
    }

    // S17: grace for today costs nothing from the budget.
    @Test
    fun graceTodayDoesNotSpendAFreeze() {
        val streak = currentStreak(activeOn(1), TODAY, maxFreezes = 0)

        assertEquals(1, streak)
    }

    // S18
    @Test
    fun freezesAloneDoNotMakeAStreak() {
        val sessions = listOf(session(daysAgo(1), pages = 0))

        val streak = currentStreak(sessions, TODAY, maxFreezes = 3)

        assertEquals(0, streak)
    }

    // S19
    @Test
    fun unusedFreezesAtTheOldEndAddNothing() {
        val streak = currentStreak(activeOn(0), TODAY, maxFreezes = 3)

        assertEquals(1, streak)
    }

    // S31: the walk is bounded by the earliest active day, not by the budget.
    @Test
    fun hugeFreezeBudgetStillEnds() {
        val sessions = activeOn(0, 3650)

        val streak = currentStreak(sessions, TODAY, maxFreezes = Int.MAX_VALUE)

        assertEquals(2, streak)
    }

    // S20
    @Test
    fun negativeFreezeBudgetIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            currentStreak(activeOn(0), TODAY, maxFreezes = -1)
        }
    }

    // --- Default budget of two ---

    // S27
    @Test
    fun defaultBudgetBridgesTwoEmptyDays() {
        val streak = currentStreak(activeOn(0, 1, 4), TODAY)

        assertEquals(3, streak)
    }

    // S28
    @Test
    fun defaultBudgetDoesNotBridgeThreeEmptyDays() {
        val streak = currentStreak(activeOn(0, 1, 5), TODAY)

        assertEquals(2, streak)
    }

    // --- Calendar and input order ---

    // S21: day arithmetic around a clock change — adjacent dates stay adjacent. The input is plain
    // LocalDate, so no time zone is involved here: sessions near 04:00 on the night of the change
    // are placed by ReadingDay, and that is covered by ReadingDayDaylightSavingTest.
    @Test
    fun streakRunsAcrossTheSpringClockChange() {
        val sessions = listOf(28, 29, 30).map { session(LocalDate(2026, 3, it)) }

        val streak = currentStreak(sessions, LocalDate(2026, 3, 30), maxFreezes = 0)

        assertEquals(3, streak)
    }

    // S22: as S21, around the autumn clock change; date arithmetic only.
    @Test
    fun streakRunsAcrossTheAutumnClockChange() {
        val sessions = listOf(24, 25, 26).map { session(LocalDate(2026, 10, it)) }

        val streak = currentStreak(sessions, LocalDate(2026, 10, 26), maxFreezes = 0)

        assertEquals(3, streak)
    }

    // S23
    @Test
    fun streakRunsAcrossTheNewYear() {
        val sessions = listOf(session(LocalDate(2026, 12, 31)), session(LocalDate(2027, 1, 1)))

        val streak = currentStreak(sessions, LocalDate(2027, 1, 1), maxFreezes = 0)

        assertEquals(2, streak)
    }

    // S24: guards against counting from the latest session instead of from today. A walk that
    // starts at today never reaches later days, so this does not isolate the date filter itself.
    @Test
    fun sessionsAfterTodayAreIgnored() {
        val sessions = listOf(session(TODAY), session(TODAY.plus(1, DateTimeUnit.DAY)))

        val streak = currentStreak(sessions, TODAY, maxFreezes = 0)

        assertEquals(1, streak)
    }

    // S33: with only future sessions there is nothing to count, even with freezes to spend.
    @Test
    fun onlyFutureSessionsGiveNoStreak() {
        val sessions = listOf(session(TODAY.plus(1, DateTimeUnit.DAY)))

        val streak = currentStreak(sessions, TODAY, maxFreezes = 2)

        assertEquals(0, streak)
    }

    // S25
    @Test
    fun orderOfSessionsDoesNotMatter() {
        val sessions = activeOn(3, 0, 2, 1)

        val streak = currentStreak(sessions, TODAY, maxFreezes = 0)

        assertEquals(4, streak)
    }

    // S26
    @Test
    fun hundredDaysInARowGiveHundred() {
        val sessions = (0 until 100).map { session(daysAgo(it)) }

        val streak = currentStreak(sessions, TODAY, maxFreezes = 0)

        assertEquals(100, streak)
    }
}
