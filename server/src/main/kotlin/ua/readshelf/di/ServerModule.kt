package ua.readshelf.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.dsl.onClose
import ua.readshelf.auth.AuthConfig
import ua.readshelf.auth.AuthService
import ua.readshelf.auth.BcryptPasswordHasher
import ua.readshelf.auth.InMemoryUserRepository
import ua.readshelf.auth.JwtService
import ua.readshelf.auth.PasswordHasher
import ua.readshelf.auth.UserRepository
import ua.readshelf.auth.authConfigFromEnv
import ua.readshelf.data.remote.OpenLibraryClient

/**
 * The backend's dependency graph. Tests pass their own modules to
 * [ua.readshelf.module] to override single definitions from here.
 *
 * Anything holding an engine or a pool gets an `onClose`: the Koin plugin stops
 * this graph when the Ktor application stops, and that is the only place these
 * are released.
 */
fun serverModule(): Module = module {
    single { OpenLibraryClient() } onClose { client: OpenLibraryClient? -> client?.close() }
    single { authConfigFromEnv() }
    single<PasswordHasher> { BcryptPasswordHasher() }
    single<UserRepository> { InMemoryUserRepository() }
    single { JwtService(get<AuthConfig>()) }
    single { AuthService(get(), get(), get()) }
}
