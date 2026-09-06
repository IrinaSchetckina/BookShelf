package ua.readshelf

import org.koin.core.module.Module
import org.koin.dsl.module
import ua.readshelf.auth.AuthConfig
import ua.readshelf.auth.BcryptPasswordHasher
import ua.readshelf.auth.PasswordHasher

const val TEST_BCRYPT_COST: Int = 4

/**
 * Every test application needs this: without it the graph falls back to
 * [ua.readshelf.auth.authConfigFromEnv], which refuses to build without
 * JWT_SECRET exported. The hasher is real BCrypt, only at a cost that does not
 * dominate the run.
 */
fun testAuthModule(): Module = module {
    single { AuthConfig(secret = "test-secret") }
    single<PasswordHasher> { BcryptPasswordHasher(cost = TEST_BCRYPT_COST) }
}
