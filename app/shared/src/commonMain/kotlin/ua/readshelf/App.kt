package ua.readshelf

import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import ua.readshelf.ui.SearchScreen

@Composable
@Preview
fun App() {
    MaterialTheme {
        Surface {
            SearchScreen(modifier = Modifier.safeContentPadding())
        }
    }
}
