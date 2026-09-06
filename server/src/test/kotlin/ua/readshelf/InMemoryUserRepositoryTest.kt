package ua.readshelf

import kotlinx.coroutines.test.runTest
import ua.readshelf.auth.InMemoryUserRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class InMemoryUserRepositoryTest {

    @Test
    fun `finds a created user by email and by id`() = runTest {
        val repository = InMemoryUserRepository()

        val created = assertNotNull(repository.create("reader@example.com", "hash"))

        assertEquals(created, repository.findByEmail("reader@example.com"))
        assertEquals(created, repository.findById(created.id))
    }

    @Test
    fun `treats emails as case and space insensitive`() = runTest {
        val repository = InMemoryUserRepository()
        val created = assertNotNull(repository.create("  Reader@Example.com ", "hash"))

        assertEquals("reader@example.com", created.email)
        assertEquals(created, repository.findByEmail("READER@EXAMPLE.COM"))
    }

    @Test
    fun `refuses to create a second user with the same email`() = runTest {
        val repository = InMemoryUserRepository()
        repository.create("reader@example.com", "hash")

        assertNull(repository.create("READER@example.com", "other-hash"))
    }

    @Test
    fun `returns null for an unknown user`() = runTest {
        val repository = InMemoryUserRepository()

        assertNull(repository.findByEmail("nobody@example.com"))
        assertNull(repository.findById("no-such-id"))
    }
}
