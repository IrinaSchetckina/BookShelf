package ua.readshelf.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.readshelf.di.AppContainer
import ua.readshelf.domain.Book
import ua.readshelf.presentation.SearchUiState
import ua.readshelf.presentation.SearchViewModel

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = viewModel { AppContainer.searchViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SearchScreenContent(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onSearch = viewModel::onSearch,
        modifier = modifier,
    )
}

@Composable
private fun SearchScreenContent(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = { Text("Search books") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onSearch, enabled = state.query.isNotBlank()) {
                Text("Search")
            }
        }

        Box(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {
            when (val status = state.status) {
                SearchUiState.Status.Idle -> CenteredMessage("Find a book to add to your shelf")
                SearchUiState.Status.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                SearchUiState.Status.Empty -> CenteredMessage("Nothing found for \"${state.query}\"")
                is SearchUiState.Status.Error -> CenteredMessage(status.message)
                is SearchUiState.Status.Success -> BookList(status.books)
            }
        }
    }
}

@Composable
private fun BookList(books: List<Book>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(books, key = { it.id }) { book ->
            BookRow(book)
            HorizontalDivider()
        }
    }
}

@Composable
private fun BookRow(book: Book) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(text = book.title, style = MaterialTheme.typography.titleMedium)
        val subtitle = listOfNotNull(
            book.authors.takeIf { it.isNotEmpty() }?.joinToString(),
            book.firstPublishYear?.toString(),
        ).joinToString(" · ")
        if (subtitle.isNotEmpty()) {
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun BoxScope.CenteredMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.align(Alignment.Center),
    )
}
