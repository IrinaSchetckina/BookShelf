package ua.readshelf

import kotlinx.coroutines.test.runTest
import ua.readshelf.auth.AuthConfig
import ua.readshelf.auth.AuthResult
import ua.readshelf.auth.AuthService
import ua.readshelf.auth.InMemoryUserRepository
import ua.readshelf.auth.JwtService
import ua.readshelf.auth.PasswordHasher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Records what login actually asked it to verify, which is what the timing depends on. */
private class RecordingPasswordHasher : PasswordHasher {
    val verifiedHashes = mutableListOf<String>()
    val threadNames = mutableListOf<String>()

    override fun hash(rawPassword: String): String {
        threadNames += Thread.currentThread().name
        return "hash:$rawPassword"
    }

    override fun verify(rawPassword: String, hash: String): Boolean {
        threadNames += Thread.currentThread().name
        verifiedHashes += hash
        return hash == "hash:$rawPassword"
    }
}

private fun authService(hasher: PasswordHasher) = AuthService(
    userRepository = InMemoryUserRepository(),
    passwordHasher = hasher,
    jwtService = JwtService(AuthConfig(secret = "test-secret")),
)

class AuthServiceTest {

    @Test
    fun `verifies exactly once whether or not the email exists`() = runTest {
        val hasher = RecordingPasswordHasher()
        val service = authService(hasher)
        service.register("reader@example.com", "password1")

        hasher.verifiedHashes.clear()
        service.login("reader@example.com", "wrong-password")
        val forKnownEmail = hasher.verifiedHashes.size

        hasher.verifiedHashes.clear()
        service.login("nobody@example.com", "wrong-password")
        val forUnknownEmail = hasher.verifiedHashes.size

        // An unknown address that skipped the hash check would answer faster, and
        // that alone would tell an attacker the address is not registered.
        assertEquals(1, forKnownEmail)
        assertEquals(1, forUnknownEmail)
    }

    @Test
    fun `verifies an unknown email against a placeholder, not a stored hash`() = runTest {
        val hasher = RecordingPasswordHasher()
        val service = authService(hasher)
        service.register("reader@example.com", "password1")

        hasher.verifiedHashes.clear()
        val result = service.login("nobody@example.com", "password1")

        assertIs<AuthResult.InvalidCredentials>(result)
        assertEquals(1, hasher.verifiedHashes.size)
        assertTrue(
            hasher.verifiedHashes.single() != "hash:password1",
            "an unknown email must not be checked against a real user's hash",
        )
    }

    @Test
    fun `hashes away from the thread that called it`() = runTest {
        val hasher = RecordingPasswordHasher()
        val service = authService(hasher)
        val callerThread = Thread.currentThread().name

        service.register("reader@example.com", "password1")
        service.login("reader@example.com", "password1")

        // Ktor's handler pool is sized to the core count. Hashing inline would let a
        // burst of anonymous logins starve every other request on the server.
        // The constructor's placeholder hash runs at startup and is not counted here.
        val duringRequests = hasher.threadNames.drop(1)
        assertTrue(
            duringRequests.isNotEmpty() && duringRequests.none { it == callerThread },
            "hashing ran on the calling thread: $duringRequests",
        )
    }

    @Test
    fun `answers unknown email and wrong password identically`() = runTest {
        val service = authService(RecordingPasswordHasher())
        service.register("reader@example.com", "password1")

        assertEquals(
            service.login("nobody@example.com", "password1"),
            service.login("reader@example.com", "wrong-password"),
        )
    }

    @Test
    fun `signs in with the right password`() = runTest {
        val service = authService(RecordingPasswordHasher())
        service.register("reader@example.com", "password1")

        val result = service.login("READER@Example.com", "password1")

        assertIs<AuthResult.Success>(result)
        assertEquals("reader@example.com", result.user.email)
    }
}
