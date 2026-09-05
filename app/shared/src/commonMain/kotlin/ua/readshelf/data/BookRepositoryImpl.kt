package ua.readshelf.data

import ua.readshelf.contract.toDomain
import ua.readshelf.data.remote.ReadShelfApi
import ua.readshelf.domain.Book
import ua.readshelf.domain.BookRepository

class BookRepositoryImpl(private val api: ReadShelfApi) : BookRepository {

    override suspend fun search(query: String, limit: Int): List<Book> =
        api.search(query, limit).books.toDomain()
}
