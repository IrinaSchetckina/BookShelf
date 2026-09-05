package ua.readshelf.contract

import kotlinx.serialization.Serializable

/**
 * Wire format of the ReadShelf `/search` endpoint, shared by :server and the clients.
 */
@Serializable
data class SearchResponseDto(
    val query: String,
    val total: Int,
    val books: List<BookDto>,
)

@Serializable
data class BookDto(
    val id: String,
    val title: String,
    val authors: List<String> = emptyList(),
    val firstPublishYear: Int? = null,
    val coverUrl: String? = null,
)

@Serializable
data class ErrorResponseDto(val message: String)
