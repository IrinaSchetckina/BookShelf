package ua.readshelf.di

import org.koin.core.module.Module
import org.koin.dsl.module
import ua.readshelf.data.remote.OpenLibraryClient

/**
 * The backend's dependency graph. Tests pass their own modules to
 * [ua.readshelf.module] to override single definitions from here.
 */
fun serverModule(): Module = module {
    single { OpenLibraryClient() }
}
