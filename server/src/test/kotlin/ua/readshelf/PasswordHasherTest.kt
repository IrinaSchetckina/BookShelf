package ua.readshelf

import ua.readshelf.auth.BcryptPasswordHasher
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val TEST_COST = 4

class PasswordHasherTest {

    private val hasher = BcryptPasswordHasher(cost = TEST_COST)

    @Test
    fun `never stores the raw password`() {
        val hash = hasher.hash("password1")

        assertNotEquals("password1", hash)
        assertFalse(hash.contains("password1"))
    }

    @Test
    fun `accepts the correct password`() {
        val hash = hasher.hash("password1")

        assertTrue(hasher.verify("password1", hash))
    }

    @Test
    fun `rejects a wrong password`() {
        val hash = hasher.hash("password1")

        assertFalse(hasher.verify("password2", hash))
    }

    @Test
    fun `produces a different hash for the same password`() {
        assertNotEquals(hasher.hash("password1"), hasher.hash("password1"))
    }
}
