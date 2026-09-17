package ua.readshelf.presentation.reading

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.toInstant
import ua.readshelf.domain.Book
import ua.readshelf.domain.reading.ReadingSession
import ua.readshelf.domain.reading.ReadingSessionRepository
import ua.readshelf.domain.reading.SessionDraft
import ua.readshelf.domain.reading.SessionRejection
import ua.readshelf.domain.reading.TrackedBook
import ua.readshelf.domain.reading.TrackedBookRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

// A fixed offset needs no time-zone database, so these tests also run on js and wasmJs.
private val KYIV_SUMMER = UtcOffset(hours = 3).asTimeZone()

private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Instant =
    LocalDateTime(year, month, day, hour, minute).toInstant(KYIV_SUMMER)

private val TODAY = LocalDate(2026, 9, 16)
private val YESTERDAY = LocalDate(2026, 9, 15)
private val TEN_PAST_TEN_PM = at(2026, 9, 16, 22, 10)

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
private val DUNE_SEARCH_RESULT = Book(
    id = DUNE.bookKey,
    title = DUNE.title,
    authors = DUNE.authors,
    firstPublishYear = 1965,
    coverUrl = DUNE.coverUrl,
)

private class FixedClock(var instant: Instant) : Clock {
    override fun now(): Instant = instant
}

private class FakeReadingSessionRepository(initial: List<ReadingSession> = emptyList()) : ReadingSessionRepository {
    val sessions = MutableStateFlow(initial)
    var writes = 0

    /** When set, writes suspend until it completes, so the in-flight state can be observed. */
    var gate: CompletableDeferred<Unit>? = null

    /** When set, writes fail with it instead of storing anything. */
    var failure: Throwable? = null

    private var nextId = initial.size

    override fun observeAll(): Flow<List<ReadingSession>> = sessions

    override suspend fun add(draft: SessionDraft): ReadingSession {
        val session = ReadingSession("s${++nextId}", draft.bookKey, draft.fromPage, draft.toPage, draft.day, draft.recordedAt)
        write { sessions.update { (it + session).sortedBy(ReadingSession::day) } }
        return session
    }

    override suspend fun update(session: ReadingSession) =
        write { sessions.update { list -> list.map { if (it.id == session.id) session else it } } }

    override suspend fun delete(id: String) = write { sessions.update { list -> list.filterNot { it.id == id } } }

    private suspend fun write(block: () -> Unit) {
        writes++
        gate?.await()
        failure?.let { throw it }
        block()
    }
}

private class FakeTrackedBookRepository(initial: List<TrackedBook>) : TrackedBookRepository {
    val books = MutableStateFlow(initial)
    var failure: Throwable? = null

    override fun observeAll(): Flow<List<TrackedBook>> = books

    override suspend fun upsert(book: TrackedBook) {
        failure?.let { throw it }
        books.update { list -> list.filterNot { it.bookKey == book.bookKey } + book }
    }

    override suspend fun delete(bookKey: String) = books.update { list -> list.filterNot { it.bookKey == bookKey } }
}

private fun session(id: String, book: TrackedBook, fromPage: Int, toPage: Int, day: LocalDate, recordedAt: Instant? = null) =
    ReadingSession(id, book.bookKey, fromPage, toPage, day, recordedAt)

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val clock = FixedClock(TEN_PAST_TEN_PM)

    /** viewModelScope runs on Dispatchers.Main, which the test dispatcher replaces. */
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModelWith(
        sessions: FakeReadingSessionRepository,
        books: FakeTrackedBookRepository = FakeTrackedBookRepository(listOf(DUNE, HOBBIT)),
    ): ReadingViewModel =
        ReadingViewModel(sessions, books, clock = clock, zone = KYIV_SUMMER).also { advanceUntilIdle() }

    private fun TestScope.saveSession(viewModel: ReadingViewModel, toPage: Int) {
        viewModel.onToPageChange(toPage.toString())
        viewModel.onSave()
        advanceUntilIdle()
    }

    @Test
    fun summaryReflectsStoredSessions() = runTest(testDispatcher) {
        val sessions = FakeReadingSessionRepository(
            listOf(
                session("s1", DUNE, fromPage = 0, toPage = 92, day = YESTERDAY),
                session("s2", DUNE, fromPage = 92, toPage = 118, day = TODAY),
            ),
        )

        val summary = viewModelWith(sessions).state.value.summary

        assertEquals(26, summary?.pagesToday)
        assertEquals(118, summary?.pagesTotal)
        assertEquals(39, summary?.books?.first { it.book == DUNE }?.progressPercent)
    }

    @Test
    fun sessionsAreListedNewestFirst() = runTest(testDispatcher) {
        val sessions = FakeReadingSessionRepository(
            listOf(
                session("s1", DUNE, fromPage = 0, toPage = 92, day = YESTERDAY),
                session("s2", DUNE, fromPage = 92, toPage = 118, day = TODAY),
            ),
        )

        val listed = viewModelWith(sessions).state.value.sessions

        assertEquals(listOf("s2", "s1"), listed.map { it.id })
    }

    @Test
    fun selectingBookStartsFromItsBookmark() = runTest(testDispatcher) {
        val sessions = FakeReadingSessionRepository(listOf(session("s1", DUNE, fromPage = 0, toPage = 92, day = YESTERDAY)))
        val viewModel = viewModelWith(sessions)

        viewModel.selectBook(DUNE.bookKey)

        assertEquals("92", viewModel.state.value.form.fromPage)
    }

    @Test
    fun newSessionIsStampedWithCurrentTime() = runTest(testDispatcher) {
        val sessions = FakeReadingSessionRepository(listOf(session("s1", DUNE, fromPage = 0, toPage = 92, day = YESTERDAY)))
        val viewModel = viewModelWith(sessions)
        viewModel.selectBook(DUNE.bookKey)

        saveSession(viewModel, toPage = 118)

        val stored = sessions.sessions.value.last()
        assertEquals(TEN_PAST_TEN_PM, stored.recordedAt)
        assertEquals(TODAY, stored.day)
        assertEquals(26, stored.pages)
    }

    @Test
    fun sessionAfterMidnightCountsForPreviousDay() = runTest(testDispatcher) {
        clock.instant = at(2026, 9, 17, 1, 30)
        val sessions = FakeReadingSessionRepository()
        val viewModel = viewModelWith(sessions)
        viewModel.selectBook(DUNE.bookKey)

        saveSession(viewModel, toPage = 20)

        assertEquals(TODAY, sessions.sessions.value.single().day)
        assertEquals(20, viewModel.state.value.summary?.pagesToday)
    }

    @Test
    fun sessionEnteredForEarlierDayHasUnknownTime() = runTest(testDispatcher) {
        val sessions = FakeReadingSessionRepository()
        val viewModel = viewModelWith(sessions)
        viewModel.selectBook(DUNE.bookKey)
        viewModel.onDayChange(YESTERDAY)

        saveSession(viewModel, toPage = 40)

        val stored = sessions.sessions.value.single()
        assertEquals(YESTERDAY, stored.day)
        assertNull(stored.recordedAt)
    }

    @Test
    fun formKeepsInputUntilWriteCompletes() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        val sessions = FakeReadingSessionRepository().apply { this.gate = gate }
        val viewModel = viewModelWith(sessions)
        viewModel.selectBook(DUNE.bookKey)
        viewModel.onToPageChange("118")

        viewModel.onSave()
        runCurrent()

        assertEquals("118", viewModel.state.value.form.toPage)
        assertTrue(viewModel.state.value.form.isSaving)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals("", viewModel.state.value.form.toPage)
        assertEquals("118", viewModel.state.value.form.fromPage)
        assertFalse(viewModel.state.value.form.isSaving)
    }

    @Test
    fun secondTapWhileSavingStoresNothingExtra() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        val sessions = FakeReadingSessionRepository().apply { this.gate = gate }
        val viewModel = viewModelWith(sessions)
        viewModel.selectBook(DUNE.bookKey)
        viewModel.onToPageChange("118")
        viewModel.onSave()
        runCurrent()

        viewModel.onSave()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, sessions.writes)
    }

    @Test
    fun summaryUpdatesRightAfterSave() = runTest(testDispatcher) {
        val sessions = FakeReadingSessionRepository(listOf(session("s1", DUNE, fromPage = 0, toPage = 92, day = YESTERDAY)))
        val viewModel = viewModelWith(sessions)
        viewModel.selectBook(DUNE.bookKey)

        saveSession(viewModel, toPage = 118)

        val summary = viewModel.state.value.summary
        assertEquals(26, summary?.pagesToday)
        assertEquals(118, summary?.pagesThisWeek)
        assertEquals(39, summary?.books?.first { it.book == DUNE }?.progressPercent)
    }

    @Test
    fun sessionThatDoesNotMoveForwardIsRejectedWithoutWriting() = runTest(testDispatcher) {
        val sessions = FakeReadingSessionRepository(listOf(session("s1", DUNE, fromPage = 0, toPage = 118, day = YESTERDAY)))
        val viewModel = viewModelWith(sessions)
        viewModel.selectBook(DUNE.bookKey)

        saveSession(viewModel, toPage = 118)

        assertEquals(FormProblem.Rejected(SessionRejection.NotForward), viewModel.state.value.form.problem)
        assertEquals(0, sessions.writes)
    }

    @Test
    fun pagePastKnownLengthIsRejected() = runTest(testDispatcher) {
        val sessions = FakeReadingSessionRepository()
        val viewModel = viewModelWith(sessions)
        viewModel.selectBook(DUNE.bookKey)

        saveSession(viewModel, toPage = 301)

        assertEquals(FormProblem.Rejected(SessionRejection.BeyondTotalPages), viewModel.state.value.form.problem)
        assertEquals(0, sessions.writes)
    }

    @Test
    fun anyPageIsAcceptedWhenLengthIsUnknown() = runTest(testDispatcher) {
        val sessions = FakeReadingSessionRepository()
        val viewModel = viewModelWith(sessions)
        viewModel.selectBook(HOBBIT.bookKey)

        saveSession(viewModel, toPage = 5000)

        assertNull(viewModel.state.value.form.problem)
        assertEquals(1, sessions.writes)
    }

    @Test
    fun pageThatIsNotANumberIsReportedWithoutWriting() = runTest(testDispatcher) {
        val sessions = FakeReadingSessionRepository()
        val viewModel = viewModelWith(sessions)
        viewModel.selectBook(DUNE.bookKey)
        viewModel.onToPageChange("12a")

        viewModel.onSave()
        advanceUntilIdle()

        assertEquals(FormProblem.PageNotANumber, viewModel.state.value.form.problem)
        assertEquals(0, sessions.writes)
    }

    @Test
    fun failedWriteKeepsInputAndReportsIt() = runTest(testDispatcher) {
        val sessions = FakeReadingSessionRepository().apply { failure = IllegalStateException("disk full") }
        val viewModel = viewModelWith(sessions)
        viewModel.selectBook(DUNE.bookKey)

        saveSession(viewModel, toPage = 118)

        val form = viewModel.state.value.form
        assertEquals(FormProblem.SaveFailed, form.problem)
        assertEquals("118", form.toPage)
        assertFalse(form.isSaving)
    }

    @Test
    fun editedSessionIsUpdatedInPlace() = runTest(testDispatcher) {
        val original = session("s1", DUNE, fromPage = 92, toPage = 118, day = TODAY, recordedAt = TEN_PAST_TEN_PM)
        val sessions = FakeReadingSessionRepository(listOf(original))
        val viewModel = viewModelWith(sessions)
        viewModel.startEdit(original.id)

        saveSession(viewModel, toPage = 130)

        assertEquals(listOf(original.copy(toPage = 130)), sessions.sessions.value)
        assertEquals(38, viewModel.state.value.summary?.pagesToday)
        assertNull(viewModel.state.value.form.editingId)
    }

    @Test
    fun editMovedToAnotherDayLosesItsTime() = runTest(testDispatcher) {
        val original = session("s1", DUNE, fromPage = 92, toPage = 118, day = TODAY, recordedAt = TEN_PAST_TEN_PM)
        val sessions = FakeReadingSessionRepository(listOf(original))
        val viewModel = viewModelWith(sessions)
        viewModel.startEdit(original.id)
        viewModel.onDayChange(YESTERDAY)

        saveSession(viewModel, toPage = 118)

        assertEquals(listOf(original.copy(day = YESTERDAY, recordedAt = null)), sessions.sessions.value)
    }

    @Test
    fun deletedSessionDisappearsFromSummary() = runTest(testDispatcher) {
        val kept = session("s1", DUNE, fromPage = 0, toPage = 92, day = YESTERDAY)
        val removed = session("s2", DUNE, fromPage = 92, toPage = 118, day = TODAY)
        val viewModel = viewModelWith(FakeReadingSessionRepository(listOf(kept, removed)))

        viewModel.onDelete(removed.id)
        advanceUntilIdle()

        assertEquals(listOf(kept), viewModel.state.value.sessions)
        assertEquals(0, viewModel.state.value.summary?.pagesToday)
        assertEquals(92, viewModel.state.value.summary?.pagesTotal)
    }

    @Test
    fun enteringLengthShowsProgress() = runTest(testDispatcher) {
        val sessions = FakeReadingSessionRepository(listOf(session("s1", HOBBIT, fromPage = 0, toPage = 118, day = TODAY)))
        val viewModel = viewModelWith(sessions)

        viewModel.onSaveTotalPages(HOBBIT.bookKey, "300")
        advanceUntilIdle()

        assertEquals(39, viewModel.state.value.summary?.books?.first { it.book.bookKey == HOBBIT.bookKey }?.progressPercent)
    }

    @Test
    fun clearingLengthHidesProgress() = runTest(testDispatcher) {
        val sessions = FakeReadingSessionRepository(listOf(session("s1", DUNE, fromPage = 0, toPage = 118, day = TODAY)))
        val viewModel = viewModelWith(sessions)

        viewModel.onSaveTotalPages(DUNE.bookKey, "  ")
        advanceUntilIdle()

        val dune = viewModel.state.value.summary?.books?.first { it.book.bookKey == DUNE.bookKey }
        assertNull(dune?.book?.totalPages)
        assertNull(dune?.progressPercent)
    }

    @Test
    fun lengthThatIsNotPositiveIsReportedAndNotStored() = runTest(testDispatcher) {
        val books = FakeTrackedBookRepository(listOf(DUNE))
        val viewModel = viewModelWith(FakeReadingSessionRepository(), books)

        viewModel.onSaveTotalPages(DUNE.bookKey, "0")
        advanceUntilIdle()

        assertEquals(TotalPagesProblem.NotAPositiveNumber, viewModel.state.value.totalPagesProblem)
        assertEquals(listOf(DUNE), books.books.value)
    }

    @Test
    fun trackingAlreadyTrackedBookKeepsItsLength() = runTest(testDispatcher) {
        val books = FakeTrackedBookRepository(listOf(DUNE))
        val viewModel = viewModelWith(FakeReadingSessionRepository(), books)

        viewModel.startTracking(DUNE_SEARCH_RESULT)
        advanceUntilIdle()

        assertEquals(listOf(DUNE), books.books.value)
    }

    @Test
    fun trackingNewBookStartsWithUnknownLength() = runTest(testDispatcher) {
        val books = FakeTrackedBookRepository(emptyList())
        val viewModel = viewModelWith(FakeReadingSessionRepository(), books)

        viewModel.startTracking(DUNE_SEARCH_RESULT)
        advanceUntilIdle()

        assertEquals(listOf(DUNE.copy(totalPages = null)), books.books.value)
    }
}
