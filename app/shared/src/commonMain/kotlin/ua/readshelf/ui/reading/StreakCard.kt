package ua.readshelf.ui.reading

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The current reading streak. A zero is shown as a plain number, never as a failure (spec §1).
 * The freeze indicator is deferred: v1 does not expose how many freezes are left.
 */
@Composable
internal fun StreakCard(days: Int, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = days.toString(), style = MaterialTheme.typography.displayMedium)
            Text(text = streakLabel(days), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
