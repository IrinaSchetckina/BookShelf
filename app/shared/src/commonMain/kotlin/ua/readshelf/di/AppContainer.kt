package ua.readshelf.di

import io.ktor.client.HttpClient
import ua.readshelf.data.BookRepositoryImpl
import ua.readshelf.data.remote.ReadShelfApi
import ua.readshelf.domain.BookRepository
import ua.readshelf.domain.SearchBooksUseCase
import ua.readshelf.presentation.SearchViewModel

/**
 * Hand-rolled dependency wiring: heavyweight dependencies are lazy singletons,
 * ViewModels are created per caller. Everything is injected through constructors,
 * so swapping this for a DI container later is mechanical.
 */
object AppContainer {

    private val httpClient: HttpClient by lazy { ReadShelfApi.defaultHttpClient() }

    private val api: ReadShelfApi by lazy { ReadShelfApi(httpClient) }

    private val bookRepository: BookRepository by lazy { BookRepositoryImpl(api) }

    private val searchBooksUseCase: SearchBooksUseCase by lazy { SearchBooksUseCase(bookRepository) }

    fun searchViewModel(): SearchViewModel = SearchViewModel(searchBooksUseCase)
}
