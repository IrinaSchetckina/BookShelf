package ua.readshelf.domain

interface BookRepository {
    /**
     * Searches the public catalogue through the ReadShelf backend.
     * Throws when the backend is unreachable or answers with an error.
     */
    suspend fun search(query: String, limit: Int = DEFAULT_SEARCH_LIMIT): List<Book>

    companion object {
        const val DEFAULT_SEARCH_LIMIT: Int = 20
    }
}
