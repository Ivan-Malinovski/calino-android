package calino.malinov.ski.qa

import calino.malinov.ski.ui.components.eventLocationUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventLocationTest {

    @Test
    fun `blank locations have no map action target`() {
        assertNull(eventLocationUri(null))
        assertNull(eventLocationUri(""))
        assertNull(eventLocationUri(" \n\t "))
    }

    @Test
    fun `location query is trimmed and percent encoded`() {
        assertEquals(
            "geo:0,0?q=Cafe%20%26%20Co%2FHQ",
            eventLocationUri("  Cafe & Co/HQ  "),
        )
    }

    @Test
    fun `unicode location is encoded as utf8`() {
        assertEquals(
            "geo:0,0?q=Caf%C3%A9%20%F0%9F%93%8D",
            eventLocationUri("Café 📍"),
        )
    }
}
