package ua.readshelf.domain.reading

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals

// Real Open Library works (see the book-fixtures skill); lengths are set here, not fetched.
private val DUNE = TrackedBook(
    bookKey = "/works/OL893414W",
    title = "Dune",
    authors = listOf("Frank Herbert"),
    coverUrl = "https://covers.openlibrary.org/b/id/11481354-M.jpg",
    totalPages = 300,
)
private val HOBBIT = TrackedBook(
    bookKey = "/works/OL27482W",
    title = "The Hobbit",
    authors = listOf("J.R.R. Tolkien"),
    coverUrl = "https://covers.openlibrary.org/b/id/14627509-M.jpg",
    totalPages = null,
)
private val HAIL_MARY = TrackedBook(
    bookKey = "/works/OL21745884W",
    title = "Project Hail Mary",
    authors = listOf("Andy Weir"),
    coverUrl = "https://covers.openlibrary.org/b/id/11200092-M.jpg",
    totalPages = 400,
)

private val TODAY = LocalDate(2026, 9, 16)

private val buildSummary = BuildReadingSummaryUseCase()

private fun session(id: String, book: TrackedBook, fromPage: Int, toPage: Int, daysAgo: Int): ReadingSession =
    ReadingSession(
        id = id,
        bookKey = book.bookKey,
        fromPage = fromPage,
        toPage = toPage,
        day = TODAY.minus(daysAgo, DateTimeUnit.DAY),
        recordedAt = null,
    )

class BuildReadingSummaryUseCaseTest {

    // AC-49, in-scope half only: goal, streak and freezes arrive in slice 2.
    @Test
    fun pageTotalsSpanAllThreeBooks() {
        val sessions = listOf(
            session("s1", DUNE, fromPage = 0, toPage = 118, daysAgo = 0),
            session("s2", HOBBIT, fromPage = 0, toPage = 40, daysAgo = 0),
            session("s3", HAIL_MARY, fromPage = 0, toPage = 60, daysAgo = 3),
            session("s4", HAIL_MARY, fromPage = 60, toPage = 100, daysAgo = 10),
        )

        val summary = buildSummary(sessions, listOf(DUNE, HOBBIT, HAIL_MARY), TODAY)

        assertEquals(158, summary.pagesToday)
        assertEquals(218, summary.pagesThisWeek)
        assertEquals(258, summary.pagesTotal)
    }

    @Test
    fun eachBookGetsItsOwnBookmarkAndProgress() {
        val sessions = listOf(
            session("s1", DUNE, fromPage = 0, toPage = 118, daysAgo = 0),
            session("s2", HOBBIT, fromPage = 0, toPage = 40, daysAgo = 0),
            session("s3", HAIL_MARY, fromPage = 0, toPage = 100, daysAgo = 1),
        )

        val summary = buildSummary(sessions, listOf(DUNE, HOBBIT, HAIL_MARY), TODAY)

        assertEquals(
            listOf(
                BookProgress(DUNE, bookmark = 118, progressPercent = 39),
                BookProgress(HOBBIT, bookmark = 40, progressPercent = null),
                BookProgress(HAIL_MARY, bookmark = 100, progressPercent = 25),
            ),
            summary.books,
        )
    }

    @Test
    fun bookWithoutSessionsStartsAtZero() {
        val summary = buildSummary(emptyList(), listOf(DUNE), TODAY)

        assertEquals(ReadingSummary(0, 0, 0, listOf(BookProgress(DUNE, bookmark = 0, progressPercent = 0))), summary)
    }
}
