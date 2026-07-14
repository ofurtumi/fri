package app.fri.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripsTest {

    private val trip = Trip(
        id = "helgarferd-2026-07",
        name = "Helgarferð norður",
        start = TripStart("Reykjavík", 64.1395, -21.8958),
        startDate = "2026-07-10",
        stats = listOf(TripStat("Fiskar", "Engir")),
    )

    @Test
    fun `trips json round-trips`() {
        val text = TripsRepository.encode(listOf(trip))
        assertEquals(listOf(trip), TripsRepository.parse(text))
        assertTrue("hand-editable file should end with a newline", text.endsWith("\n"))
    }

    @Test
    fun `parses the site's actual trips json shape`() {
        val text = """
            [
              {
                "id": "helgarferd-2026-07",
                "name": "Helgarferð norður",
                "start": { "name": "Reykjavík", "lat": 64.1395, "lng": -21.8958 },
                "startDate": "2026-07-10",
                "stats": [
                  { "label": "Stenmning", "value": "Góð" },
                  { "label": "Fiskar", "value": "Engir" }
                ]
              }
            ]
        """.trimIndent()
        val trips = TripsRepository.parse(text)
        assertEquals(1, trips.size)
        assertEquals("Helgarferð norður", trips[0].name)
        assertEquals(64.1395, trips[0].start.lat, 1e-9)
        assertEquals(2, trips[0].stats.size)
    }

    @Test
    fun `new trip ids slugify and dodge collisions`() {
        assertEquals("sumarfri-a-vestfjordum", TripsRepository.newTripId("Sumarfrí á Vestfjörðum", emptyList()))
        assertEquals("helgarferd-2026-07-2", TripsRepository.newTripId("Helgarferð 2026 07", listOf(trip)))
    }
}
