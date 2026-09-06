package ua.readshelf.auth

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Keeps users in memory, so they are gone on restart. Good enough until the
 * PostgreSQL implementation lands; the routes talk to [UserRepository] only.
 *
 * Keyed by [normalizeEmail] of the address, applied here rather than trusted
 * from the caller, so lookups stay case-insensitive whoever calls them.
 */
class InMemoryUserRepository(
    private val generateId: () -> String = { UUID.randomUUID().toString() },
) : UserRepository {

    private val mutex = Mutex()
    private val usersByEmail = mutableMapOf<String, UserRecord>()

    override suspend fun findByEmail(email: String): UserRecord? = mutex.withLock {
        usersByEmail[normalizeEmail(email)]
    }

    override suspend fun findById(id: String): UserRecord? = mutex.withLock {
        usersByEmail.values.firstOrNull { it.id == id }
    }

    override suspend fun create(email: String, passwordHash: String): UserRecord? = mutex.withLock {
        val normalized = normalizeEmail(email)
        if (usersByEmail.containsKey(normalized)) return@withLock null

        val user = UserRecord(id = generateId(), email = normalized, passwordHash = passwordHash)
        usersByEmail[normalized] = user
        user
    }
}
