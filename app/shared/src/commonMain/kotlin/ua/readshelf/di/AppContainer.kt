package ua.readshelf.di

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ua.readshelf.data.BookRepositoryImpl
import ua.readshelf.data.local.ReadingSessionRepositoryImpl
import ua.readshelf.data.local.SqlDriverFactory
import ua.readshelf.data.local.TrackedBookRepositoryImpl
import ua.readshelf.data.local.createReadShelfDatabase
import ua.readshelf.data.remote.ReadShelfApi
import ua.readshelf.domain.BookRepository
import ua.readshelf.domain.SearchBooksUseCase
import ua.readshelf.presentation.SearchViewModel
import ua.readshelf.presentation.reading.ReadingViewModel

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

    private var storageOpener: ReadingStorageOpener? = null

    fun searchViewModel(): SearchViewModel = SearchViewModel(searchBooksUseCase)

    /**
     * Opens local storage once per process; later calls share the same open and ignore
     * [driverFactory]. Suspends because the web driver opens its database in a worker.
     * See [ReadingStorageOpener] for why cancelling a caller does not cancel the open.
     */
    suspend fun openReadingStorage(driverFactory: SqlDriverFactory): ReadingStorage {
        val opener = storageOpener ?: ReadingStorageOpener(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            open = {
                val database = createReadShelfDatabase(driverFactory)
                ReadingStorage(
                    sessions = ReadingSessionRepositoryImpl(database),
                    books = TrackedBookRepositoryImpl(database),
                )
            },
        ).also { storageOpener = it }
        return opener.get()
    }

    fun readingViewModel(storage: ReadingStorage): ReadingViewModel =
        ReadingViewModel(sessionRepository = storage.sessions, bookRepository = storage.books)
}
