package ua.readshelf.ui.reading

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import ua.readshelf.presentation.reading.FormProblem
import ua.readshelf.presentation.reading.SessionFormState

class SessionFormActions(
    val onFromPageChange: (String) -> Unit,
    val onToPageChange: (String) -> Unit,
    val onDayChange: (LocalDate?) -> Unit,
    val onSave: () -> Unit,
    val onCancelEdit: () -> Unit,
)

@Composable
internal fun SessionForm(
    bookTitle: String,
    form: SessionFormState,
    today: LocalDate,
    actions: SessionFormActions,
) {
    var isPickingDay by rememberSaveable { mutableStateOf(false) }
    val isEditing = form.editingId != null
    val day = form.day ?: today

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = if (isEditing) "Edit session · $bookTitle" else "New session · $bookTitle",
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PageField(
                    label = "From page",
                    value = form.fromPage,
                    onValueChange = actions.onFromPageChange,
                    imeAction = ImeAction.Next,
                    onDone = {},
                    modifier = Modifier.weight(1f),
                )
                PageField(
                    label = "To page",
                    value = form.toPage,
                    onValueChange = actions.onToPageChange,
                    imeAction = ImeAction.Done,
                    onDone = actions.onSave,
                    modifier = Modifier.weight(1f),
                )
            }
            TextButton(onClick = { isPickingDay = true }) {
                Text("Reading day: ${dayLabel(day, today)}")
            }
            form.problem?.let { problem ->
                Text(
                    text = problem.message(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = actions.onSave, enabled = form.toPage.isNotBlank() && !form.isSaving) {
                    Text(if (isEditing) "Save changes" else "Save session")
                }
                if (isEditing) {
                    OutlinedButton(onClick = actions.onCancelEdit, enabled = !form.isSaving) { Text("Cancel") }
                }
            }
        }
    }

    if (isPickingDay) {
        ReadingDayPicker(
            selected = day,
            today = today,
            onPick = { picked ->
                actions.onDayChange(picked.takeUnless { it == today })
                isPickingDay = false
            },
            onDismiss = { isPickingDay = false },
        )
    }
}

@Composable
private fun PageField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    imeAction: ImeAction,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        // Digits only; anything else is still validated by the view model.
        onValueChange = { text -> onValueChange(text.filter(Char::isDigit)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingDayPicker(
    selected: LocalDate,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val todayMillis = today.toUtcEpochMillis()
    val pastOnly = remember(todayMillis) {
        object : SelectableDates {
            // Sessions cannot be dated after the current reading day (spec §5.1).
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis
        }
    }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = selected.toUtcEpochMillis(),
        selectableDates = pastOnly,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { pickerState.selectedDateMillis?.let { onPick(utcEpochMillisToDate(it)) } }) {
                Text("OK")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = pickerState)
    }
}

private fun FormProblem.message(): String = when (this) {
    FormProblem.PageNotANumber -> "Enter both pages as whole numbers."
    FormProblem.SaveFailed -> "Could not save the session. Your input is kept, try again."
    is FormProblem.Rejected -> reason.message()
}
