package calino.malinov.ski.qa

import calino.malinov.ski.data.model.CalDavAccount
import calino.malinov.ski.data.model.CalDavCalendar
import calino.malinov.ski.data.model.WebcalSubscription
import calino.malinov.ski.data.repository.CalinoCalendar
import calino.malinov.ski.data.repository.CalinoSnapshot
import calino.malinov.ski.ui.components.sidebarCalendarRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SidebarCalendarRowsTest {
    @Test
    fun `webcal-only overlay is not treated as a fixture calendar`() {
        val snapshot = CalinoSnapshot(
            events = emptyList(),
            tasks = emptyList(),
            journals = emptyList(),
            calendars = listOf(
                CalinoCalendar("webcal:abc", "Katrina", 0xFF5B7FB5, readOnly = true),
            ),
        )
        val rows = sidebarCalendarRows(snapshot, accounts = emptyList())
        assertEquals(1, rows.size)
        assertEquals(WebcalSubscription.AccountId, rows.single().accountId)
        assertEquals("Katrina", rows.single().calendar.name)
    }

    @Test
    fun `webcal rows sit beside CalDAV rows`() {
        val account = CalDavAccount(
            id = "dav",
            displayName = "Radicale",
            serverUrl = "https://dav.example",
            username = "joseph",
            calendars = listOf(CalDavCalendar("https://dav.example/personal/", "Personal", 0xFFC2697F)),
        )
        val snapshot = CalinoSnapshot(
            events = emptyList(),
            tasks = emptyList(),
            journals = emptyList(),
            calendars = listOf(
                CalinoCalendar("https://dav.example/personal/", "Personal", 0xFFC2697F),
                CalinoCalendar("webcal:abc", "Work", 0xFF5B7FB5, readOnly = true),
            ),
        )
        val rows = sidebarCalendarRows(snapshot, listOf(account))
        assertEquals(listOf("dav", WebcalSubscription.AccountId), rows.map { it.accountId })
        assertEquals(listOf("Personal", "Work"), rows.map { it.calendar.name })
    }

    @Test
    fun `fixture names only apply when there is no live calendar`() {
        val snapshot = CalinoSnapshot(
            events = emptyList(),
            tasks = emptyList(),
            journals = emptyList(),
            calendars = listOf(CalinoCalendar("personal", "Personal", 0xFFC2697F)),
        )
        val rows = sidebarCalendarRows(
            snapshot,
            accounts = emptyList(),
            fixtureCalendarNames = mapOf("personal" to "Renamed"),
        )
        assertEquals(null, rows.single().accountId)
        assertEquals("Renamed", rows.single().calendar.name)
        assertTrue(rows.single().calendar.id == "personal")
    }
}
