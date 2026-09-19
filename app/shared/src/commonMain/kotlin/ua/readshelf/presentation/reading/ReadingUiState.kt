package ua.readshelf.presentation.reading

import kotlinx.datetime.LocalDate
import ua.readshelf.domain.reading.ReadingSession
import ua.readshelf.domain.reading.ReadingSummary
import ua.readshelf.domain.reading.SessionRejection

/**
 * Everything the tracker screen shows. All of it comes from local storage,
 * so there is deliberately no loading or network state here.
 */
data class ReadingUiState(
    /** Null only until storage has emitted once. */
    val summary: ReadingSummary? = null,
    /** The reading day the summary was computed for; null until storage has emitted once. */
    val today: LocalDate? = null,
    /** Newest first. */
    val sessions: List<ReadingSession> = emptyList(),
    val form: SessionFormState = SessionFormState(),
    /** Set when a book length could not be saved; cleared by the next attempt. */
    val totalPagesProblem: TotalPagesProblem? = null,
    /** Set when deleting a session or tracking a book failed; cleared when the reader dismisses it. */
    val actionProblem: ActionProblem? = null,
)

data class SessionFormState(
    /** The book the next session is recorded against; null until the reader picks one. */
    val bookKey: String? = null,
    /** Null for a new session, otherwise the id of the session being edited. */
    val editingId: String? = null,
    val fromPage: String = "",
    val toPage: String = "",
    /** Null means the current reading day. */
    val day: LocalDate? = null,
    /** True while the write is in flight, so a double tap cannot store the session twice. */
    val isSaving: Boolean = false,
    val problem: FormProblem? = null,
)

sealed interface FormProblem {
    /** A page field is empty or not a whole number. */
    data object PageNotANumber : FormProblem

    data class Rejected(val reason: SessionRejection) : FormProblem

    /** Storage refused the write; the form keeps what the reader typed. */
    data object SaveFailed : FormProblem
}

/** Failures of one-tap actions that have no form to report into. */
enum class ActionProblem {
    DeleteFailed,
    TrackFailed,
}

enum class TotalPagesProblem {
    /** Not blank, but not a whole number above zero. */
    NotAPositiveNumber,

    SaveFailed,
}
