package ua.readshelf.domain

class SearchBooksUseCase(private val repository: BookRepository) {

    /**
     * Returns an empty list for a blank query instead of hitting the network.
     */
    suspend operator fun invoke(
        query: String,
        limit: Int = BookRepository.DEFAULT_SEARCH_LIMIT,
    ): List<Book> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return repository.search(trimmed, limit)
    }
}
