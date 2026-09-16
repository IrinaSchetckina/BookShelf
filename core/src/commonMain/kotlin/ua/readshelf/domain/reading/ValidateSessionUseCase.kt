package ua.readshelf.domain.reading

import kotlinx.datetime.LocalDate

class ValidateSessionUseCase {

    /**
     * Returns the first reason [draft] cannot be saved, or null when it can.
     * [today] is the current reading day; [totalPages] is null when the book length is unknown.
     */
    operator fun invoke(draft: SessionDraft, today: LocalDate, totalPages: Int?): SessionRejection? =
        when {
            draft.fromPage < 0 -> SessionRejection.NegativeFrom
            draft.toPage <= draft.fromPage -> SessionRejection.NotForward
            draft.day > today -> SessionRejection.DayInFuture
            totalPages != null && draft.toPage > totalPages -> SessionRejection.BeyondTotalPages
            else -> null
        }
}
