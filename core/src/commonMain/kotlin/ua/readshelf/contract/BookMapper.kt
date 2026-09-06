package ua.readshelf.contract

import ua.readshelf.domain.Book

fun BookDto.toDomain(): Book = Book(
    id = id,
    title = title,
    authors = authors,
    firstPublishYear = firstPublishYear,
    coverUrl = coverUrl,
)

fun List<BookDto>.toDomain(): List<Book> = map { it.toDomain() }
