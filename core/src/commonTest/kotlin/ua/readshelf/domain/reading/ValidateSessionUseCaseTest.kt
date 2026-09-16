package ua.readshelf.domain.reading

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Real Open Library work (see the book-fixtures skill); its length is set here, not fetched.
private const val DUNE_KEY = "/works/OL893414W"
private const val DUNE_PAGES = 300

private val TODAY = LocalDate(2026, 9, 16)

private val validate = ValidateSessionUseCase()

private fun draft(fromPage: Int, toPage: Int, day: LocalDate = TODAY): SessionDraft =
    SessionDraft(bookKey = DUNE_KEY, fromPage = fromPage, toPage = toPage, day = day, recordedAt = null)

class ValidateSessionUseCaseTest {

    @Test
    fun acceptsSessionThatMovesForward() {
        val rejection = validate(draft(fromPage = 92, toPage = 118), TODAY, DUNE_PAGES)

        assertNull(rejection)
    }

    @Test
    fun rejectsSessionThatEndsWhereItStarted() {
        val rejection = validate(draft(fromPage = 118, toPage = 118), TODAY, DUNE_PAGES)

        assertEquals(SessionRejection.NotForward, rejection)
    }

    @Test
    fun rejectsSessionThatEndsBeforeItStarted() {
        val rejection = validate(draft(fromPage = 118, toPage = 90), TODAY, DUNE_PAGES)

        assertEquals(SessionRejection.NotForward, rejection)
    }

    @Test
    fun rejectsNegativeStartPage() {
        val rejection = validate(draft(fromPage = -1, toPage = 10), TODAY, DUNE_PAGES)

        assertEquals(SessionRejection.NegativeFrom, rejection)
    }

    @Test
    fun acceptsSessionStartingAtPageZero() {
        val rejection = validate(draft(fromPage = 0, toPage = 10), TODAY, DUNE_PAGES)

        assertNull(rejection)
    }

    @Test
    fun rejectsSessionDatedTomorrow() {
        val tomorrow = TODAY.plus(1, DateTimeUnit.DAY)

        val rejection = validate(draft(fromPage = 92, toPage = 118, day = tomorrow), TODAY, DUNE_PAGES)

        assertEquals(SessionRejection.DayInFuture, rejection)
    }

    @Test
    fun acceptsSessionDatedInThePast() {
        val twoDaysAgo = TODAY.minus(2, DateTimeUnit.DAY)

        val rejection = validate(draft(fromPage = 92, toPage = 118, day = twoDaysAgo), TODAY, DUNE_PAGES)

        assertNull(rejection)
    }

    @Test
    fun rejectsEndPagePastLastPage() {
        val rejection = validate(draft(fromPage = 290, toPage = 301), TODAY, DUNE_PAGES)

        assertEquals(SessionRejection.BeyondTotalPages, rejection)
    }

    @Test
    fun acceptsEndingOnLastPage() {
        val rejection = validate(draft(fromPage = 290, toPage = 300), TODAY, DUNE_PAGES)

        assertNull(rejection)
    }

    @Test
    fun acceptsAnyEndPageWhenLengthIsUnknown() {
        val rejection = validate(draft(fromPage = 290, toPage = 5000), TODAY, totalPages = null)

        assertNull(rejection)
    }
}
