package ua.readshelf.presentation.reading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import ua.readshelf.domain.Book
import ua.readshelf.domain.reading.BuildReadingSummaryUseCase
import ua.readshelf.domain.reading.ReadingDay
import ua.readshelf.domain.reading.ReadingSession
import ua.readshelf.domain.reading.ReadingSessionRepository
import ua.readshelf.domain.reading.SessionDraft
import ua.readshelf.domain.reading.TrackedBook
import ua.readshelf.domain.reading.TrackedBookRepository
import ua.readshelf.domain.reading.ValidateSessionUseCase
import ua.readshelf.domain.reading.bookmarkOf
import ua.readshelf.domain.reading.currentStreak
import kotlin.time.Clock

class ReadingViewModel(
    private val sessionRepository: ReadingSessionRepository,
    private val bookRepository: TrackedBookRepository,
    private val validateSession: ValidateSessionUseCase = ValidateSessionUseCase(),
    private val buildSummary: BuildReadingSummaryUseCase = BuildReadingSummaryUseCase(),
    private val clock: Clock = Clock.System,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val _state = MutableStateFlow(ReadingUiState())
    val state: StateFlow<ReadingUiState> = _state.asStateFlow()

    // The latest lists from storage; every statistic is derived from these on each change.
    private var sessions: List<ReadingSession> = emptyList()
    private var books: List<TrackedBook> = emptyList()

    init {
        combine(sessionRepository.observeAll(), bookRepository.observeAll()) { sessions, books ->
            this.sessions = sessions
            this.books = books
            val today = today()
            _state.update {
                it.copy(
                    summary = buildSummary(sessions, books, today),
                    today = today,
                    streak = currentStreak(sessions, today),
                    sessions = sessions.asReversed(),
                )
            }
        }.launchIn(viewModelScope)
    }

    /** Starts tracking a catalogue book. A book already tracked keeps the length the reader entered. */
    fun startTracking(book: Book) {
        if (books.any { it.bookKey == book.id }) return
        viewModelScope.launch {
            // Caught, as every write here: an exception escaping viewModelScope crashes the app on Android.
            runCatching {
                bookRepository.upsert(
                    TrackedBook(
                        bookKey = book.id,
                        title = book.title,
                        authors = book.authors,
                        coverUrl = book.coverUrl,
                        totalPages = null,
                    ),
                )
            }.onFailure { _state.update { it.copy(actionProblem = ActionProblem.TrackFailed) } }
        }
    }

    /** Prepares a new session for [bookKey], starting from where the reader left off. */
    fun selectBook(bookKey: String) {
        _state.update {
            it.copy(form = SessionFormState(bookKey = bookKey, fromPage = bookmarkOf(sessions, bookKey).toString()))
        }
    }

    fun onFromPageChange(text: String) = updateForm { it.copy(fromPage = text, problem = null) }

    fun onToPageChange(text: String) = updateForm { it.copy(toPage = text, problem = null) }

    /** [day] is null for the current reading day. */
    fun onDayChange(day: LocalDate?) = updateForm { it.copy(day = day, problem = null) }

    fun startEdit(sessionId: String) {
        val session = sessions.firstOrNull { it.id == sessionId } ?: return
        _state.update {
            it.copy(
                form = SessionFormState(
                    bookKey = session.bookKey,
                    editingId = session.id,
                    fromPage = session.fromPage.toString(),
                    toPage = session.toPage.toString(),
                    day = session.day,
                ),
            )
        }
    }

    fun cancelEdit() {
        val bookKey = _state.value.form.bookKey ?: return
        selectBook(bookKey)
    }

    fun onSave() {
        val form = _state.value.form
        val bookKey = form.bookKey ?: return
        if (form.isSaving) return

        val fromPage = form.fromPage.trim().toIntOrNull()
        val toPage = form.toPage.trim().toIntOrNull()
        if (fromPage == null || toPage == null) {
            updateForm { it.copy(problem = FormProblem.PageNotANumber) }
            return
        }

        val now = clock.now()
        val today = ReadingDay.of(now, zone)
        val day = form.day ?: today
        val editing = form.editingId?.let { id -> sessions.firstOrNull { it.id == id } }
        val draft = SessionDraft(
            bookKey = bookKey,
            fromPage = fromPage,
            toPage = toPage,
            day = day,
            recordedAt = when {
                // An edit keeps its time unless it moves to another day, where that time would be wrong.
                editing != null -> editing.recordedAt.takeIf { editing.day == day }
                // A session entered for an earlier day was not read now; its time is unknown (spec §5.1).
                day == today -> now
                else -> null
            },
        )
        val totalPages = books.firstOrNull { it.bookKey == bookKey }?.totalPages
        validateSession(draft, today, totalPages)?.let { reason ->
            updateForm { it.copy(problem = FormProblem.Rejected(reason)) }
            return
        }

        updateForm { it.copy(isSaving = true, problem = null) }
        viewModelScope.launch {
            // The form is cleared only after the write returns, so nothing the reader typed
            // is dropped if storage fails or the app is closed mid-write.
            val saved = runCatching {
                if (editing != null) {
                    val updated = editing.copy(
                        fromPage = draft.fromPage,
                        toPage = draft.toPage,
                        day = draft.day,
                        recordedAt = draft.recordedAt,
                    )
                    sessionRepository.update(updated)
                    updated
                } else {
                    sessionRepository.add(draft)
                }
            }
            saved.onSuccess { stored ->
                // Storage may not have re-emitted yet, so apply this save to the known list before
                // reading the bookmark. Taking the maximum with the old list instead would keep a
                // bookmark that an edit has just lowered, and prefill the next session from it.
                val afterSave = sessions.filterNot { it.id == stored.id } + stored
                val bookmark = bookmarkOf(afterSave, bookKey)
                _state.update {
                    it.copy(form = SessionFormState(bookKey = bookKey, fromPage = bookmark.toString()))
                }
            }.onFailure {
                updateForm { it.copy(isSaving = false, problem = FormProblem.SaveFailed) }
            }
        }
    }

    fun onDelete(sessionId: String) {
        viewModelScope.launch {
            runCatching { sessionRepository.delete(sessionId) }
                // Leave an edit of this session open until it is really gone.
                .onSuccess { if (_state.value.form.editingId == sessionId) cancelEdit() }
                .onFailure { _state.update { it.copy(actionProblem = ActionProblem.DeleteFailed) } }
        }
    }

    fun dismissActionProblem() {
        _state.update { it.copy(actionProblem = null) }
    }

    /** A blank [text] marks the length as unknown, which hides progress for the book. */
    fun onSaveTotalPages(bookKey: String, text: String) {
        val book = books.firstOrNull { it.bookKey == bookKey } ?: return
        val trimmed = text.trim()
        val totalPages = if (trimmed.isEmpty()) null else trimmed.toIntOrNull()
        if (trimmed.isNotEmpty() && (totalPages == null || totalPages <= 0)) {
            _state.update { it.copy(totalPagesProblem = TotalPagesProblem.NotAPositiveNumber) }
            return
        }
        _state.update { it.copy(totalPagesProblem = null) }
        viewModelScope.launch {
            runCatching { bookRepository.upsert(book.copy(totalPages = totalPages)) }
                .onFailure { _state.update { it.copy(totalPagesProblem = TotalPagesProblem.SaveFailed) } }
        }
    }

    fun dismissTotalPagesProblem() {
        _state.update { it.copy(totalPagesProblem = null) }
    }

    private fun today(): LocalDate = ReadingDay.of(clock.now(), zone)

    private fun updateForm(transform: (SessionFormState) -> SessionFormState) {
        _state.update { it.copy(form = transform(it.form)) }
    }
}
