package ua.readshelf

import androidx.compose.ui.window.ComposeUIViewController
import ua.readshelf.data.local.NativeSqlDriverFactory

fun MainViewController() = ComposeUIViewController { App(NativeSqlDriverFactory()) }