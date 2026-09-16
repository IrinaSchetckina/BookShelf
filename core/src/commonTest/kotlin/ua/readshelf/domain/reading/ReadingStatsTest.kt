package ua.readshelf.domain.reading

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Real Open Library works (see the book-fixtures skill); lengths are set here, not fetched.
private const val DUNE_KEY = "/works/OL893414W"
private const val HOBBIT_KEY = "/works/OL27482W"
private const val DUNE_PAGES = 300

private val TODAY = LocalDate(2026, 9, 16)

private fun daysAgo(days: Int): LocalDate = TODAY.minus(days, DateTimeUnit.DAY)

private fun session(
    id: String,
    fromPage: Int,
    toPage: Int,
    day: LocalDate = TODAY,
    bookKey: String = DUNE_KEY,
): ReadingSession =
    ReadingSession(id = id, bookKey = bookKey, fromPage = fromPage, toPage = toPage, day = day, recordedAt = null)

private data class Snapshot(val today: Int, val week: Int, val total: Int, val bookmark: Int, val progress: Int?)

private fun snapshot(sessions: List<ReadingSession>): Snapshot =
    Snapshot(
        today = pagesOn(sessions, TODAY),
        week = pagesInWeek(sessions, TODAY),
        total = pagesTotal(sessions),
        bookmark = bookmarkOf(sessions, DUNE_KEY),
        progress = progressPercent(sessions, DUNE_KEY, DUNE_PAGES),
    )

class ReadingStatsTest {

    @Test
    fun sessionFromBookmarkCountsPagesBetweenBoundaries() {
        val earlier = session("s1", fromPage = 0, toPage = 92, day = daysAgo(1))
        val latest = session("s2", fromPage = 92, toPage = 118)

        val sessions = listOf(earlier, latest)

        assertEquals(26, latest.pages)
        assertEquals(118, bookmarkOf(sessions, DUNE_KEY))
    }

    @Test
    fun twoSessionsOnSameDayAddUp() {
        val sessions = listOf(
            session("s1", fromPage = 92, toPage = 118),
            session("s2", fromPage = 118, toPage = 140),
        )

        val today = pagesOn(sessions, TODAY)

        assertEquals(48, today)
        assertEquals(140, bookmarkOf(sessions, DUNE_KEY))
    }

    @Test
    fun editedSessionIsReflectedInEveryStatistic() {
        val earlier = session("s1", fromPage = 0, toPage = 92, day = daysAgo(1))
        val original = session("s2", fromPage = 92, toPage = 118)

        val edited = snapshot(listOf(earlier, original.copy(toPage = 130)))

        assertEquals(Snapshot(today = 38, week = 130, total = 130, bookmark = 130, progress = 43), edited)
    }

    @Test
    fun deletingSessionRestoresStatisticsFromBeforeIt() {
        val earlier = session("s1", fromPage = 0, toPage = 92, day = daysAgo(1))
        val added = session("s2", fromPage = 92, toPage = 118)
        val before = snapshot(listOf(earlier))

        val afterDelete = snapshot(listOf(earlier, added).filterNot { it.id == added.id })

        assertEquals(before, afterDelete)
    }

    @Test
    fun weekIncludesSixDaysAgoButNotSeven() {
        val sessions = listOf(
            session("s1", fromPage = 0, toPage = 10, day = daysAgo(7)),
            session("s2", fromPage = 10, toPage = 30, day = daysAgo(6)),
            session("s3", fromPage = 30, toPage = 35),
        )

        val week = pagesInWeek(sessions, TODAY)

        assertEquals(25, week)
    }

    @Test
    fun todayIgnoresEarlierDays() {
        val sessions = listOf(
            session("s1", fromPage = 0, toPage = 50, day = daysAgo(1)),
            session("s2", fromPage = 50, toPage = 60),
        )

        val today = pagesOn(sessions, TODAY)

        assertEquals(10, today)
    }

    @Test
    fun totalSpansEveryBookAndDay() {
        val sessions = listOf(
            session("s1", fromPage = 0, toPage = 40, day = daysAgo(30)),
            session("s2", fromPage = 0, toPage = 25, bookKey = HOBBIT_KEY),
        )

        val total = pagesTotal(sessions)

        assertEquals(65, total)
    }

    @Test
    fun bookmarkIsZeroBeforeFirstSession() {
        val bookmark = bookmarkOf(emptyList(), DUNE_KEY)

        assertEquals(0, bookmark)
    }

    @Test
    fun bookmarkIgnoresOtherBooks() {
        val sessions = listOf(session("s1", fromPage = 0, toPage = 250, bookKey = HOBBIT_KEY))

        val bookmark = bookmarkOf(sessions, DUNE_KEY)

        assertEquals(0, bookmark)
    }

    // Covers the bookmark half of AC-8 ahead of its slice; the AC itself is not counted as done.
    @Test
    fun rereadingEarlierPagesDoesNotMoveBookmarkBack() {
        val sessions = listOf(
            session("s1", fromPage = 0, toPage = 118, day = daysAgo(1)),
            session("s2", fromPage = 10, toPage = 30),
        )

        val bookmark = bookmarkOf(sessions, DUNE_KEY)

        assertEquals(118, bookmark)
        assertEquals(20, pagesOn(sessions, TODAY))
    }

    @Test
    fun progressIsHiddenWhenLengthIsUnknown() {
        val sessions = listOf(session("s1", fromPage = 0, toPage = 118))

        val progress = progressPercent(sessions, DUNE_KEY, totalPages = null)

        assertNull(progress)
    }

    @Test
    fun progressIsHiddenForNonPositiveLength() {
        val sessions = listOf(session("s1", fromPage = 0, toPage = 118))

        val progress = progressPercent(sessions, DUNE_KEY, totalPages = 0)

        assertNull(progress)
    }

    @Test
    fun progressIsWholePercentOfBookReached() {
        val sessions = listOf(session("s1", fromPage = 0, toPage = 118))

        val progress = progressPercent(sessions, DUNE_KEY, DUNE_PAGES)

        assertEquals(39, progress)
    }

    @Test
    fun progressRoundsDownSoUnfinishedBookNeverShowsHundred() {
        val sessions = listOf(session("s1", fromPage = 0, toPage = 299))

        val progress = progressPercent(sessions, DUNE_KEY, DUNE_PAGES)

        assertEquals(99, progress)
    }

    @Test
    fun progressIsCappedWhenLengthWasCorrectedDownwards() {
        val sessions = listOf(session("s1", fromPage = 0, toPage = 320))

        val progress = progressPercent(sessions, DUNE_KEY, DUNE_PAGES)

        assertEquals(100, progress)
    }
}
