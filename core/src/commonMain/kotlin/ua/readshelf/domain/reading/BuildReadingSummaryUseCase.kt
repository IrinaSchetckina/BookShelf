package ua.readshelf.domain.reading

import kotlinx.datetime.LocalDate

class BuildReadingSummaryUseCase {

    /** [today] is the current reading day, supplied by the caller so the result is reproducible. */
    operator fun invoke(
        sessions: List<ReadingSession>,
        books: List<TrackedBook>,
        today: LocalDate,
    ): ReadingSummary =
        ReadingSummary(
            pagesToday = pagesOn(sessions, today),
            pagesThisWeek = pagesInWeek(sessions, today),
            pagesTotal = pagesTotal(sessions),
            books = books.map { book ->
                BookProgress(
                    book = book,
                    bookmark = bookmarkOf(sessions, book.bookKey),
                    progressPercent = progressPercent(sessions, book.bookKey, book.totalPages),
                )
            },
        )
}
