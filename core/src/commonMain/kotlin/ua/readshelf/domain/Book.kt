package ua.readshelf.domain

/**
 * A book as the app talks about it. Independent of any wire format:
 * [ua.readshelf.contract.BookDto] is mapped into this type at the edges.
 */
data class Book(
    val id: String,
    val title: String,
    val authors: List<String>,
    val firstPublishYear: Int?,
    val coverUrl: String?,
)
