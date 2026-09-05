package ua.readshelf.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ua.readshelf.domain.SearchBooksUseCase

class SearchViewModel(
    private val searchBooks: SearchBooksUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun onSearch() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) {
            _state.update { it.copy(status = SearchUiState.Status.Idle) }
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update { it.copy(status = SearchUiState.Status.Loading) }
            val status = runCatching { searchBooks(query) }.fold(
                onSuccess = { books ->
                    if (books.isEmpty()) {
                        SearchUiState.Status.Empty
                    } else {
                        SearchUiState.Status.Success(books)
                    }
                },
                onFailure = { error ->
                    SearchUiState.Status.Error(
                        error.message ?: "Could not reach the ReadShelf server",
                    )
                },
            )
            _state.update { it.copy(status = status) }
        }
    }
}
