package ua.readshelf.data.local

import kotlin.test.Test
import kotlin.test.assertEquals

class AuthorsColumnAdapterTest {

    // Catalogues often invert names; a comma-joined column would split this one into three.
    @Test
    fun nameWithCommasRoundTrips() {
        val authors = listOf("King, Martin Luther, Jr.", "Frank Herbert")

        val restored = AuthorsColumnAdapter.decode(AuthorsColumnAdapter.encode(authors))

        assertEquals(authors, restored)
    }

    @Test
    fun emptyListRoundTrips() {
        val restored = AuthorsColumnAdapter.decode(AuthorsColumnAdapter.encode(emptyList()))

        assertEquals(emptyList(), restored)
    }
}
