package calino.malinov.ski.qa

import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.Contact
import calino.malinov.ski.data.model.ContactAddressBook
import calino.malinov.ski.data.model.JournalEntry
import calino.malinov.ski.data.repository.CalinoCalendar
import calino.malinov.ski.data.repository.CalinoSnapshot
import calino.malinov.ski.data.search.CalinoSearchGroups
import calino.malinov.ski.data.search.CalinoSearchRecordType
import calino.malinov.ski.data.search.CalinoSearchResult
import calino.malinov.ski.data.search.appSearchRecords
import calino.malinov.ski.data.search.contactSearchId
import calino.malinov.ski.data.search.eventSearchId
import calino.malinov.ski.data.search.journalSearchId
import calino.malinov.ski.data.search.resolveCurrentSearchResult
import calino.malinov.ski.data.search.searchCalino
import calino.malinov.ski.data.search.taskSearchId
import calino.malinov.ski.data.search.withSparseSearchFallback
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalinoAppSearchRecordsTest {
    private val today = LocalDate.of(2026, 5, 18)
    private val visibleId = "https://dav.test/cal/visible/"
    private val hiddenId = "https://dav.test/cal/hidden/"
    private val taskHiddenId = "https://dav.test/cal/no-tasks/"

    @Test fun `index mapping excludes hidden calendars hidden tasks and disabled sources`() {
        val snapshot = snapshot()

        val enabled = appSearchRecords(snapshot, journalsEnabled = true, contactsEnabled = true)
        assertEquals(
            setOf(
                eventSearchId(snapshot.events.first()),
                taskSearchId(snapshot.tasks.first()),
                journalSearchId(snapshot.journals.first()),
                contactSearchId(snapshot.contacts.first()),
            ),
            enabled.map { it.stableId }.toSet(),
        )

        val unavailable = appSearchRecords(snapshot, journalsEnabled = false, contactsEnabled = false)
        assertEquals(
            setOf(eventSearchId(snapshot.events.first()), taskSearchId(snapshot.tasks.first())),
            unavailable.map { it.stableId }.toSet(),
        )

        val contactText = enabled.single { it.stableId == contactSearchId(snapshot.contacts.first()) }.searchText
        assertTrue(contactText.contains("river road"))
        assertFalse(contactText.contains("private raw vcard"))
        assertFalse(contactText.contains("account-1"))
        assertFalse(contactText.contains("book-enabled"))
        assertFalse(enabled.single { it.stableId == eventSearchId(snapshot.events.first()) }.searchText.contains("dav.test"))
    }

    @Test fun `document identity scopes recurring occurrences by calendar and stays stable on edits`() {
        val masterId = "series-uid"
        val first = event("$masterId#2026-05-18", "Series title", visibleId).copy(uid = masterId)
        val second = event("$masterId#2026-05-25", "Series title", visibleId).copy(uid = masterId)
        val anotherCalendar = first.copy(calendarId = hiddenId)

        assertNotEquals(eventSearchId(first), eventSearchId(second))
        assertNotEquals(eventSearchId(first), eventSearchId(anotherCalendar))
        assertEquals(eventSearchId(first), eventSearchId(first.copy(title = "Updated series title")))
    }

    @Test fun `sparse AppSearch groups retain typo and metadata matches from current snapshot`() {
        val searchable = snapshot().copy(
            events = listOf(event("metadata-event", "Coffee", visibleId).copy(notes = "Design planning notes")),
            tasks = emptyList(),
            journals = emptyList(),
            contacts = emptyList(),
        )
        val expected = searchCalino(searchable, "desgn planing", today)
        assertEquals("metadata-event", expected.events.single().event.id)

        // A prefix-only AppSearch query may return no candidate for this typo.
        val prefixHits = CalinoSearchGroups()
        val candidates = prefixHits.withSparseSearchFallback(
            fallback = expected,
            enabledTypes = setOf(CalinoSearchRecordType.Events),
        )
        val afterFallback = searchCalino(searchable, "desgn planing", today, candidateRecordIds = candidates)
        assertEquals(expected.events.map { it.stableId }, afterFallback.events.map { it.stableId })
    }

    @Test fun `selection resolves fresh objects and rejects removed or newly hidden records`() {
        val before = event("event-1", "Before sync", visibleId)
        val after = before.copy(title = "After sync")
        val offered = CalinoSearchResult.Event(before)
        val currentSnapshot = snapshot().copy(events = listOf(after))

        val resolved = resolveCurrentSearchResult(currentSnapshot, offered, journalsEnabled = true, contactsEnabled = true)
        assertEquals("After sync", (resolved as CalinoSearchResult.Event).event.title)
        assertNull(resolveCurrentSearchResult(snapshot().copy(events = emptyList()), offered, true, true))

        val hiddenSnapshot = currentSnapshot.copy(
            calendars = currentSnapshot.calendars.map { if (it.id == visibleId) it.copy(visible = false) else it },
        )
        assertNull(resolveCurrentSearchResult(hiddenSnapshot, offered, true, true))
    }

    @Test fun `selection rejects records when their availability is disabled`() {
        val source = snapshot()
        val journal = CalinoSearchResult.Journal(source.journals.single { it.id == "visible-journal" })
        val contact = CalinoSearchResult.Contact(source.contacts.first { it.id == "enabled-contact" })
        assertNull(resolveCurrentSearchResult(source, journal, journalsEnabled = false, contactsEnabled = true))
        assertNull(resolveCurrentSearchResult(source, contact, journalsEnabled = true, contactsEnabled = false))
    }

    private fun snapshot(): CalinoSnapshot {
        val calendars = listOf(
            CalinoCalendar(visibleId, "Visible", 0xFF000000),
            CalinoCalendar(hiddenId, "Hidden", 0xFF000000, visible = false),
            CalinoCalendar(taskHiddenId, "Tasks hidden", 0xFF000000, showTasksInViews = false),
        )
        return CalinoSnapshot(
            events = listOf(
                event("visible-event", "Visible event", visibleId),
                event("hidden-event", "Hidden event", hiddenId),
            ),
            tasks = listOf(
                CalTask("visible-task", "Visible task", 0L, today, calendarId = visibleId),
                CalTask("hidden-task", "Hidden task", 0L, today, calendarId = taskHiddenId),
            ),
            journals = listOf(
                JournalEntry("visible-journal", today, "Visible journal", "Visible body", href = "${visibleId}visible.ics"),
                JournalEntry("hidden-journal", today, "Hidden journal", "Hidden body", href = "${hiddenId}hidden.ics"),
            ),
            contacts = listOf(
                Contact(
                    id = "enabled-contact",
                    addressBookId = "book-enabled",
                    accountId = "account-1",
                    displayName = "Ada Example",
                    addresses = listOf(calino.malinov.ski.data.model.ContactAddress(street = "River Road")),
                    rawVCard = "BEGIN:VCARD private raw vcard",
                ),
                Contact(id = "disabled-contact", addressBookId = "book-disabled", accountId = "account-1", displayName = "Hidden Person"),
            ),
            addressBooks = listOf(
                ContactAddressBook(id = "book-enabled", accountId = "account-1", enabled = true),
                ContactAddressBook(id = "book-disabled", accountId = "account-1", enabled = false),
            ),
            calendars = calendars,
        )
    }

    private fun event(id: String, title: String, calendarId: String) = CalEvent(
        id = id,
        title = title,
        color = 0L,
        start = today.atTime(9, 0),
        durationMinutes = 60,
        calendarId = calendarId,
    )
}
