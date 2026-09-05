package ua.readshelf.presentation

import ua.readshelf.domain.Book

data class SearchUiState(
    val query: String = "",
    val status: Status = Status.Idle,
) {
    sealed interface Status {
        /** Nothing searched yet. */
        data object Idle : Status

        data object Loading : Status

        data class Success(val books: List<Book>) : Status

        /** The search ran but the catalogue returned nothing. */
        data object Empty : Status

        data class Error(val message: String) : Status
    }
}
