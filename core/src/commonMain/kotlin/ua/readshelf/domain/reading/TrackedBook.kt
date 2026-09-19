package ua.readshelf.domain.reading

/**
 * A book the reader logs sessions against. Kept locally, independent of the catalogue:
 * [totalPages] comes from the reader because Open Library does not report it reliably.
 */
data class TrackedBook(
    val bookKey: String,
    val title: String,
    val authors: List<String>,
    val coverUrl: String?,
    /** Null when the reader has not entered it; progress is hidden in that case. */
    val totalPages: Int?,
)
