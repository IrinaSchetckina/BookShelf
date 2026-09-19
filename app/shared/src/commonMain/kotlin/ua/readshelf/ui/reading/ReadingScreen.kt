package ua.readshelf.ui.reading

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import ua.readshelf.domain.reading.BookProgress
import ua.readshelf.domain.reading.ReadingSession
import ua.readshelf.domain.reading.ReadingSummary
import ua.readshelf.presentation.reading.ReadingUiState
import ua.readshelf.presentation.reading.ReadingViewModel

/**
 * The reading tracker: page totals, the tracked books, the session form and the session history.
 * Everything here is backed by local storage, so there is no loading indicator.
 */
@Composable
fun ReadingScreen(
    viewModel: ReadingViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var lengthDialogBookKey by rememberSaveable { mutableStateOf<String?>(null) }

    ReadingScreenContent(
        state = state,
        onLogSession = viewModel::selectBook,
        onEditLength = { bookKey ->
            viewModel.dismissTotalPagesProblem()
            lengthDialogBookKey = bookKey
        },
        onEditSession = viewModel::startEdit,
        onDeleteSession = viewModel::onDelete,
        formActions = SessionFormActions(
            onFromPageChange = viewModel::onFromPageChange,
            onToPageChange = viewModel::onToPageChange,
            onDayChange = viewModel::onDayChange,
            onSave = viewModel::onSave,
            onCancelEdit = viewModel::cancelEdit,
        ),
        modifier = modifier,
    )

    val dialogBook = state.summary?.books?.firstOrNull { it.book.bookKey == lengthDialogBookKey }
    if (dialogBook != null) {
        BookDetailsForm(
            book = dialogBook.book,
            problem = state.totalPagesProblem,
            onSave = { text ->
                viewModel.onSaveTotalPages(dialogBook.book.bookKey, text)
                // Invalid input is reported synchronously; keep the dialog open so it can be fixed.
                if (viewModel.state.value.totalPagesProblem == null) lengthDialogBookKey = null
            },
            onDismiss = {
                viewModel.dismissTotalPagesProblem()
                lengthDialogBookKey = null
            },
        )
    }
}

@Composable
private fun ReadingScreenContent(
    state: ReadingUiState,
    onLogSession: (bookKey: String) -> Unit,
    onEditLength: (bookKey: String) -> Unit,
    onEditSession: (sessionId: String) -> Unit,
    onDeleteSession: (sessionId: String) -> Unit,
    formActions: SessionFormActions,
    modifier: Modifier = Modifier,
) {
    val summary = state.summary ?: return
    val today = state.today ?: return
    val titles = summary.books.associate { it.book.bookKey to it.book.title }
    val formBook = summary.books.firstOrNull { it.book.bookKey == state.form.bookKey }?.book

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { TotalsCard(summary) }

        if (summary.books.isEmpty()) {
            item {
                Text(
                    text = "Track a book from search to start logging your reading.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            item { SectionTitle("Books") }
            items(summary.books, key = { it.book.bookKey }) { progress ->
                BookProgressRow(
                    progress = progress,
                    isSelected = progress.book.bookKey == state.form.bookKey,
                    onLogSession = { onLogSession(progress.book.bookKey) },
                    onEditLength = { onEditLength(progress.book.bookKey) },
                )
            }
        }

        if (formBook != null) {
            item(key = "form") {
                SessionForm(
                    bookTitle = formBook.title,
                    form = state.form,
                    today = today,
                    actions = formActions,
                )
            }
        }

        if (state.sessions.isNotEmpty()) {
            item { SectionTitle("Sessions") }
            items(state.sessions, key = { it.id }) { session ->
                SessionRow(
                    session = session,
                    bookTitle = titles[session.bookKey] ?: session.bookKey,
                    today = today,
                    isBeingEdited = session.id == state.form.editingId,
                    onEdit = { onEditSession(session.id) },
                    onDelete = { onDeleteSession(session.id) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun TotalsCard(summary: ReadingSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Total(label = "Today", pages = summary.pagesToday)
            Total(label = "Last 7 days", pages = summary.pagesThisWeek)
            Total(label = "All time", pages = summary.pagesTotal)
        }
    }
}

@Composable
private fun Total(label: String, pages: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = pages.toString(), style = MaterialTheme.typography.headlineMedium)
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun BookProgressRow(
    progress: BookProgress,
    isSelected: Boolean,
    onLogSession: () -> Unit,
    onEditLength: () -> Unit,
) {
    val colors = if (isSelected) {
        CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    } else {
        CardDefaults.outlinedCardColors()
    }
    Card(modifier = Modifier.fillMaxWidth(), colors = colors) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = progress.book.title, style = MaterialTheme.typography.titleMedium)
            if (progress.book.authors.isNotEmpty()) {
                Text(text = progress.book.authors.joinToString(), style = MaterialTheme.typography.bodyMedium)
            }
            val percent = progress.progressPercent
            val totalPages = progress.book.totalPages
            if (percent != null && totalPages != null) {
                Text("Page ${progress.bookmark} of $totalPages · $percent %", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text("Page ${progress.bookmark} · length not set", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onLogSession) { Text("Log session") }
                TextButton(onClick = onEditLength) { Text(if (totalPages == null) "Set length" else "Edit length") }
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: ReadingSession,
    bookTitle: String,
    today: LocalDate,
    isBeingEdited: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = bookTitle, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "Pages ${session.fromPage}–${session.toPage} · ${session.pages} read",
                style = MaterialTheme.typography.bodyMedium,
            )
            val time = session.recordedAt?.let { formatTime(it, TimeZone.currentSystemDefault()) }
            Text(
                text = listOfNotNull(dayLabel(session.day, today), time).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(onClick = onEdit, enabled = !isBeingEdited) { Text(if (isBeingEdited) "Editing" else "Edit") }
        TextButton(onClick = onDelete) { Text("Delete") }
    }
}
