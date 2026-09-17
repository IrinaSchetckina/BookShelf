package ua.readshelf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import ua.readshelf.data.local.SqlDriverFactory
import ua.readshelf.di.AppContainer
import ua.readshelf.di.ReadingStorage
import ua.readshelf.presentation.reading.ReadingViewModel
import ua.readshelf.ui.SearchScreen
import ua.readshelf.ui.reading.ReadingScreen

/** [sqlDriverFactory] comes from the platform entry point: Android needs a Context to open a file. */
@Composable
fun App(sqlDriverFactory: SqlDriverFactory) {
    MaterialTheme {
        Surface {
            val storage by produceState<StorageState>(StorageState.Opening, sqlDriverFactory) {
                value = try {
                    StorageState.Ready(AppContainer.openReadingStorage(sqlDriverFactory))
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    StorageState.Failed(error.message)
                }
            }
            AppTabs(storage = storage, modifier = Modifier.safeContentPadding())
        }
    }
}

private sealed interface StorageState {
    /** Local and quick, so nothing is drawn for it: no spinner. */
    data object Opening : StorageState

    data class Ready(val storage: ReadingStorage) : StorageState

    data class Failed(val reason: String?) : StorageState
}

private enum class AppTab(val title: String) {
    Search("Search"),
    Reading("Reading"),
}

@Composable
private fun AppTabs(storage: StorageState, modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableStateOf(AppTab.Search) }
    // One tracker view model for both tabs, so "Track" in search and the tracker share state.
    val readingViewModel: ReadingViewModel? = (storage as? StorageState.Ready)?.let { ready ->
        viewModel { AppContainer.readingViewModel(ready.storage) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab.ordinal) {
            AppTab.entries.forEach { entry ->
                Tab(selected = tab == entry, onClick = { tab = entry }, text = { Text(entry.title) })
            }
        }
        when (tab) {
            AppTab.Search -> SearchTab(readingViewModel)
            AppTab.Reading -> when {
                readingViewModel != null -> ReadingScreen(viewModel = readingViewModel)
                storage is StorageState.Failed -> StorageUnavailable()
                else -> Unit
            }
        }
    }
}

@Composable
private fun SearchTab(readingViewModel: ReadingViewModel?) {
    if (readingViewModel == null) {
        SearchScreen()
        return
    }
    val readingState by readingViewModel.state.collectAsStateWithLifecycle()
    val trackedKeys = readingState.summary?.books.orEmpty().map { it.book.bookKey }.toSet()
    SearchScreen(trackedBookKeys = trackedKeys, onTrack = readingViewModel::startTracking)
}

@Composable
private fun StorageUnavailable() {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(
            // On the web the database allows one app tab at a time; that is the likely cause there.
            text = "Your reading log could not be opened. If ReadShelf is open in another tab, " +
                "close it and reload this page.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
