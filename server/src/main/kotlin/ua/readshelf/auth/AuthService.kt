package ua.readshelf.auth

import ua.readshelf.domain.User

/**
 * Registration and login rules, kept out of the routes so those stay thin.
 */
class AuthService(
    private val userRepository: UserRepository,
    private val passwordHasher: PasswordHasher,
    private val jwtService: JwtService,
) {
    /**
     * Hashed once at startup so [login] can spend the same time verifying an
     * address nobody registered as it does verifying a real one. Built with the
     * configured hasher rather than hardcoded, so its cost factor always matches
     * the real hashes it stands in for.
     */
    private val absentUserHash: String = passwordHasher.hash(ABSENT_USER_PASSWORD)

    suspend fun register(email: String, password: String): AuthResult {
        val normalizedEmail = normalizeEmail(email)
        validateCredentials(normalizedEmail, password)?.let { return it }

        val user = userRepository.create(normalizedEmail, passwordHasher.hash(password))
            ?: return AuthResult.EmailAlreadyTaken

        return AuthResult.Success(user.toDomain(), jwtService.issueToken(user))
    }

    suspend fun login(email: String, password: String): AuthResult {
        val normalizedEmail = normalizeEmail(email)
        if (normalizedEmail.isEmpty() || password.isEmpty()) {
            return AuthResult.ValidationFailed("Email and password must not be blank")
        }

        val user = userRepository.findByEmail(normalizedEmail)

        // Verify runs exactly once whether or not the address exists. Skipping it
        // for an unknown address would answer noticeably faster, and that timing
        // difference tells an attacker who is registered just as plainly as
        // separate error messages would.
        val passwordMatches = passwordHasher.verify(password, user?.passwordHash ?: absentUserHash)
        if (user == null || !passwordMatches) {
            return AuthResult.InvalidCredentials
        }

        return AuthResult.Success(user.toDomain(), jwtService.issueToken(user))
    }

    private fun validateCredentials(normalizedEmail: String, password: String): AuthResult.ValidationFailed? = when {
        !isPlausibleEmail(normalizedEmail) ->
            AuthResult.ValidationFailed("Email address is not valid")

        password.length < MIN_PASSWORD_LENGTH ->
            AuthResult.ValidationFailed("Password must be at least $MIN_PASSWORD_LENGTH characters long")

        password.toByteArray().size > MAX_PASSWORD_BYTES ->
            AuthResult.ValidationFailed("Password must not be longer than $MAX_PASSWORD_BYTES bytes")

        else -> null
    }

    companion object {
        /** Never a real password: it only exists to give [absentUserHash] something to hash. */
        private const val ABSENT_USER_PASSWORD = "absent-user-placeholder"

        const val MIN_PASSWORD_LENGTH: Int = 8

        /**
         * BCrypt itself stops at 72 bytes and silently ignores the rest, so a
         * longer password would not mean what the reader thinks it means.
         */
        const val MAX_PASSWORD_BYTES: Int = 72
    }
}

sealed interface AuthResult {
    data class Success(val user: User, val token: String) : AuthResult
    data class ValidationFailed(val message: String) : AuthResult
    data object EmailAlreadyTaken : AuthResult
    data object InvalidCredentials : AuthResult
}

fun UserRecord.toDomain(): User = User(id = id, email = email)

/**
 * Deliberately loose: a strict RFC-shaped pattern rejects addresses that work,
 * and the only real proof that an address exists is sending mail to it.
 */
private fun isPlausibleEmail(email: String): Boolean {
    if (email.count { it == '@' } != 1) return false
    val (local, domain) = email.split("@")
    return local.isNotEmpty() &&
        domain.length >= MIN_DOMAIN_LENGTH &&
        "." in domain.drop(1).dropLast(1) &&
        email.none { it.isWhitespace() }
}

private const val MIN_DOMAIN_LENGTH = 3
