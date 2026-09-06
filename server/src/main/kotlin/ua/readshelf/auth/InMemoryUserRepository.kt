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

    // Second index rather than a scan of the first: findById runs on every
    // authenticated request, and a scan would hold the one lock for longer the
    // more users exist, putting logins and registrations behind it.
    private val usersById = mutableMapOf<String, UserRecord>()

    override suspend fun findByEmail(email: String): UserRecord? = mutex.withLock {
        usersByEmail[normalizeEmail(email)]
    }

    override suspend fun findById(id: String): UserRecord? = mutex.withLock {
        usersById[id]
    }

    override suspend fun create(email: String, passwordHash: String): UserRecord? = mutex.withLock {
        val normalized = normalizeEmail(email)
        if (usersByEmail.containsKey(normalized)) return@withLock null

        val user = UserRecord(id = generateId(), email = normalized, passwordHash = passwordHash)
        usersByEmail[normalized] = user
        usersById[user.id] = user
        user
    }
}
