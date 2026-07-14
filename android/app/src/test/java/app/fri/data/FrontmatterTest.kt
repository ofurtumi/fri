package app.fri.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class FrontmatterTest {

    private val fullDraft = PostDraft(
        title = "Amma's 90 ára afmæli",
        date = "2026-07-12",
        trip = "helgarferd-2026-07",
        location = "Akureyri - Reykjavík",
        lat = 64.1457,
        lng = -21.9232,
        excerpt = "Héldum veislu fyrir ömmu",
        body = "Dagurinn byrjaði bara vel.\n\nOg endaði líka vel.",
        weather = Weather(12, "rain", 39),
    )

    @Test
    fun `full draft round-trips through markdown`() {
        val md = fullDraft.toMarkdown(
            imagePaths = listOf("./slug/photo-1.jpg", "./slug/photo-2.jpg"),
            videoPaths = listOf("./slug/clip-1.mp4"),
        )
        val parsed = parsePostMarkdown("slug", md)
        assertEquals(fullDraft, parsed.draft)
        assertEquals(listOf("./slug/photo-1.jpg", "./slug/photo-2.jpg"), parsed.photos)
        assertEquals(listOf("./slug/clip-1.mp4"), parsed.videos)
    }

    @Test
    fun `minimal draft round-trips`() {
        val draft = fullDraft.copy(trip = "", excerpt = "", weather = null)
        val parsed = parsePostMarkdown("slug", draft.toMarkdown(emptyList()))
        assertEquals(draft, parsed.draft)
        assertEquals(emptyList<String>(), parsed.photos)
        assertEquals(emptyList<String>(), parsed.videos)
        assertNull(parsed.draft.weather)
    }

    @Test
    fun `title apostrophe escaping survives`() {
        val draft = fullDraft.copy(title = "It's a 'weird' title")
        val parsed = parsePostMarkdown("slug", draft.toMarkdown(emptyList()))
        assertEquals("It's a 'weird' title", parsed.draft.title)
    }

    @Test
    fun `unknown top-level key is rejected`() {
        val md = """
            ---
            title: 'X'
            date: 2026-07-12
            location: Y
            lat: 64.0
            lng: -21.0
            draft: true
            ---

            body
        """.trimIndent()
        assertThrows(IllegalArgumentException::class.java) {
            parsePostMarkdown("slug", md)
        }
    }

    @Test
    fun `weather without windKmh defaults to zero`() {
        val md = """
            ---
            title: 'X'
            date: 2026-07-12
            location: Y
            lat: 64.0
            lng: -21.0
            weather:
              temp: 10
              description: fog
            ---

            body
        """.trimIndent()
        val parsed = parsePostMarkdown("slug", md)
        assertEquals(Weather(10, "fog", 0), parsed.draft.weather)
    }

    @Test
    fun `crlf input parses`() {
        val md = fullDraft.toMarkdown(emptyList()).replace("\n", "\r\n")
        assertEquals(fullDraft, parsePostMarkdown("slug", md).draft)
    }
}
