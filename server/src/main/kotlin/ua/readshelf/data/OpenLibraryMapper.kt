package ua.readshelf.data

import ua.readshelf.contract.BookDto
import ua.readshelf.data.remote.OpenLibraryDocDto

private const val COVER_URL_TEMPLATE = "https://covers.openlibrary.org/b/id/%s-M.jpg"

/** Documents without a title carry nothing we can show, so they are dropped by [toBookDtos]. */
fun OpenLibraryDocDto.toBookDtoOrNull(): BookDto? {
    val title = title?.takeIf { it.isNotBlank() } ?: return null
    return BookDto(
        id = key,
        title = title,
        authors = authorName,
        firstPublishYear = firstPublishYear,
        coverUrl = coverId?.let { COVER_URL_TEMPLATE.format(it) },
    )
}

fun List<OpenLibraryDocDto>.toBookDtos(): List<BookDto> = mapNotNull { it.toBookDtoOrNull() }
