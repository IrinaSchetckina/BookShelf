package ua.readshelf.ui.reading

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ua.readshelf.domain.reading.TrackedBook
import ua.readshelf.presentation.reading.TotalPagesProblem

/**
 * Where the reader sets a book's length. Open Library does not report it reliably,
 * and without it the book shows no progress. Leaving the field empty is valid: length unknown.
 */
@Composable
internal fun BookDetailsForm(
    book: TrackedBook,
    problem: TotalPagesProblem?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable(book.bookKey) { mutableStateOf(book.totalPages?.toString().orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(book.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { input -> text = input.filter(Char::isDigit) },
                    label = { Text("Total pages") },
                    supportingText = { Text("Leave empty if you don't know it yet.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                problem?.let {
                    Text(text = it.message(), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun TotalPagesProblem.message(): String = when (this) {
    TotalPagesProblem.NotAPositiveNumber -> "Enter a number of pages above zero, or leave it empty."
    TotalPagesProblem.SaveFailed -> "Could not save the length. Try again."
}
