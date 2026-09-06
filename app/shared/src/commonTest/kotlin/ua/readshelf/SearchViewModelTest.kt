package ua.readshelf

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import ua.readshelf.domain.Book
import ua.readshelf.domain.BookRepository
import ua.readshelf.domain.SearchBooksUseCase
import ua.readshelf.presentation.SearchUiState
import ua.readshelf.presentation.SearchViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private class FakeBookRepository(
    private val result: Result<List<Book>>,
    /** When set, [search] suspends until it completes, so Loading can be observed. */
    private val gate: CompletableDeferred<Unit>? = null,
) : BookRepository {
    var lastQuery: String? = null

    override suspend fun search(query: String, limit: Int): List<Book> {
        lastQuery = query
        gate?.await()
        return result.getOrThrow()
    }
}

private fun book(id: String) = Book(
    id = id,
    title = "Dune",
    authors = listOf("Frank Herbert"),
    firstPublishYear = 1965,
    coverUrl = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    /** viewModelScope runs on Dispatchers.Main, which the test dispatcher replaces. */
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModelWith(repository: BookRepository) =
        SearchViewModel(SearchBooksUseCase(repository))

    @Test
    fun emitsLoadingThenSuccess() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeBookRepository(Result.success(listOf(book("/works/OL1W"))), gate)
        val viewModel = viewModelWith(repository)

        viewModel.onQueryChange("dune")
        viewModel.onSearch()
        runCurrent()
        assertIs<SearchUiState.Status.Loading>(viewModel.state.value.status)

        gate.complete(Unit)
        testScheduler.advanceUntilIdle()
        val success = assertIs<SearchUiState.Status.Success>(viewModel.state.value.status)
        assertEquals(1, success.books.size)
        assertEquals("dune", repository.lastQuery)
    }

    @Test
    fun emitsEmptyWhenNothingFound() = runTest(testDispatcher) {
        val viewModel = viewModelWith(FakeBookRepository(Result.success(emptyList())))

        viewModel.onQueryChange("no such book")
        viewModel.onSearch()
        testScheduler.advanceUntilIdle()

        assertIs<SearchUiState.Status.Empty>(viewModel.state.value.status)
    }

    @Test
    fun emitsErrorWhenRepositoryFails() = runTest(testDispatcher) {
        val repository = FakeBookRepository(Result.failure(IllegalStateException("backend down")))
        val viewModel = viewModelWith(repository)

        viewModel.onQueryChange("dune")
        viewModel.onSearch()
        testScheduler.advanceUntilIdle()

        val error = assertIs<SearchUiState.Status.Error>(viewModel.state.value.status)
        assertEquals("backend down", error.message)
    }

    @Test
    fun ignoresBlankQuery() = runTest(testDispatcher) {
        val repository = FakeBookRepository(Result.success(listOf(book("/works/OL1W"))))
        val viewModel = viewModelWith(repository)

        viewModel.onQueryChange("   ")
        viewModel.onSearch()
        testScheduler.advanceUntilIdle()

        assertIs<SearchUiState.Status.Idle>(viewModel.state.value.status)
        assertEquals(null, repository.lastQuery)
    }
}
