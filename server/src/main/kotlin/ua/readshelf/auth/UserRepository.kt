package ua.readshelf.auth

/**
 * The one spelling of an address the system stores and looks up by, so that
 * "  User@Example.com " and "user@example.com" are the same account.
 */
fun normalizeEmail(rawEmail: String): String = rawEmail.trim().lowercase()

/**
 * A stored user, password hash included. Server-side only: the domain [ua.readshelf.domain.User]
 * in :core is what the clients get to see.
 */
data class UserRecord(
    val id: String,
    val email: String,
    val passwordHash: String,
)

interface UserRepository {
    suspend fun findByEmail(email: String): UserRecord?

    suspend fun findById(id: String): UserRecord?

    /**
     * Stores a new user, or returns null when [email] is already taken.
     * Checking and inserting is one call on purpose: as two, concurrent
     * registrations of the same address both pass the check.
     */
    suspend fun create(email: String, passwordHash: String): UserRecord?
}
