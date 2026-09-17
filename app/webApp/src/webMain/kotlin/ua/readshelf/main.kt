package ua.readshelf

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import ua.readshelf.data.local.WebWorkerSqlDriverFactory

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val sqlDriverFactory = WebWorkerSqlDriverFactory()
    ComposeViewport {
        App(sqlDriverFactory)
    }
}