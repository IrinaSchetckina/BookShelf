package ua.readshelf

import ua.readshelf.data.remote.OpenLibraryDocDto
import ua.readshelf.data.toBookDtos
import kotlin.test.Test
import kotlin.test.assertEquals

class OpenLibraryMapperTest {

    @Test
    fun `drops documents without a usable title`() {
        val docs = listOf(
            OpenLibraryDocDto(key = "/works/OL1W", title = "Dune"),
            OpenLibraryDocDto(key = "/works/OL2W", title = null),
            OpenLibraryDocDto(key = "/works/OL3W", title = "   "),
        )

        val mapped = docs.toBookDtos()

        assertEquals(listOf("/works/OL1W"), mapped.map { it.id })
    }

    @Test
    fun `builds a cover url only when a cover id is present`() {
        val docs = listOf(
            OpenLibraryDocDto(key = "/works/OL1W", title = "With cover", coverId = 42),
            OpenLibraryDocDto(key = "/works/OL2W", title = "No cover"),
        )

        val mapped = docs.toBookDtos()

        assertEquals("https://covers.openlibrary.org/b/id/42-M.jpg", mapped[0].coverUrl)
        assertEquals(null, mapped[1].coverUrl)
    }
}
