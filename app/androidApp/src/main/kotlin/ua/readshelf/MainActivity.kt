package ua.readshelf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import ua.readshelf.data.local.AndroidSqlDriverFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // The application context: the database outlives this activity.
        val sqlDriverFactory = AndroidSqlDriverFactory(applicationContext)
        setContent {
            App(sqlDriverFactory)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(AndroidSqlDriverFactory(LocalContext.current))
}