package ua.readshelf.domain.reading

/** What the tracker screen shows. Page totals span every book, not just one. */
data class ReadingSummary(
    val pagesToday: Int,
    val pagesThisWeek: Int,
    val pagesTotal: Int,
    val books: List<BookProgress>,
)

data class BookProgress(
    val book: TrackedBook,
    val bookmark: Int,
    /** Null when the book length is unknown. */
    val progressPercent: Int?,
)
