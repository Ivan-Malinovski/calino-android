package calino.malinov.ski

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.Contact
import calino.malinov.ski.data.model.ContactAddressBook
import calino.malinov.ski.data.model.JournalEntry
import calino.malinov.ski.data.repository.CalinoCalendar
import calino.malinov.ski.data.repository.CalinoSnapshot
import calino.malinov.ski.data.search.CalinoAppSearchIndex
import calino.malinov.ski.data.search.eventSearchId
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivateAppSearchIndexTest {
    @Test fun localIndexUpdatesDocumentsAndRemovesHiddenRecords() = runBlocking {
        val calendarId = "https://dav.test/calendar/"
        val calendar = CalinoCalendar(calendarId, "Work", 0xFF000000)
        val event = CalEvent(
            id = "event-uid",
            title = "Coffee planning",
            color = 0xFF000000,
            start = LocalDate.of(2026, 5, 19).atTime(9, 0),
            durationMinutes = 30,
            calendarId = calendarId,
        )
        val snapshot = CalinoSnapshot(
            events = listOf(event),
            tasks = emptyList(),
            journals = emptyList(),
            calendars = listOf(calendar),
        )
        val index = CalinoAppSearchIndex(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            databaseName = "test-${UUID.randomUUID()}",
        )
        try {
            val id = eventSearchId(event)
            assertTrue(id in index.searchRecords("coffee", snapshot, journalsEnabled = false, contactsEnabled = false))

            val updated = event.copy(title = "Tea planning")
            val updatedSnapshot = snapshot.copy(events = listOf(updated))
            assertTrue(id in index.searchRecords("tea", updatedSnapshot, journalsEnabled = false, contactsEnabled = false))
            assertFalse(id in index.searchRecords("coffee", updatedSnapshot, journalsEnabled = false, contactsEnabled = false))

            val hiddenSnapshot = updatedSnapshot.copy(
                calendars = listOf(calendar.copy(visible = false)),
            )
            assertFalse(id in index.searchRecords("tea", hiddenSnapshot, journalsEnabled = false, contactsEnabled = false))
        } finally {
            index.close()
        }
    }

    @Test fun disablingSourcesAndRemovingAnAccountDeleteTheirDocuments() = runBlocking {
        val calendarId = "https://dav.test/account/calendar/"
        val calendar = CalinoCalendar(calendarId, "Personal", 0xFF000000)
        val contactBook = ContactAddressBook(
            id = "https://dav.test/account/addressbook/",
            accountId = "account-1",
            enabled = true,
        )
        val snapshot = CalinoSnapshot(
            events = emptyList(),
            tasks = emptyList(),
            journals = listOf(
                JournalEntry(
                    id = "journal-uid",
                    date = LocalDate.of(2026, 5, 18),
                    title = "Field notebook",
                    body = "Private journal search term",
                    href = "${calendarId}journal.ics",
                ),
            ),
            contacts = listOf(
                Contact(
                    id = "contact-uid",
                    addressBookId = contactBook.id,
                    accountId = contactBook.accountId,
                    displayName = "Ava Member",
                ),
            ),
            addressBooks = listOf(contactBook),
            calendars = listOf(calendar),
        )
        val index = CalinoAppSearchIndex(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            databaseName = "test-${UUID.randomUUID()}",
        )
        try {
            assertTrue("journal" in index.searchRecords("notebook", snapshot, journalsEnabled = true, contactsEnabled = true)
                .map { it.substringBefore(':') })
            assertTrue("contact" in index.searchRecords("ava", snapshot, journalsEnabled = true, contactsEnabled = true)
                .map { it.substringBefore(':') })
            assertTrue(index.searchRecords("dav.test", snapshot, journalsEnabled = true, contactsEnabled = true).isEmpty())

            assertTrue(index.searchRecords("notebook", snapshot, journalsEnabled = false, contactsEnabled = true).isEmpty())
            assertTrue(index.searchRecords("ava", snapshot, journalsEnabled = true, contactsEnabled = false).isEmpty())

            // An empty authoritative snapshot after account removal must clear
            // every document that had been indexed for the account.
            val removedAccount = snapshot.copy(
                journals = emptyList(),
                contacts = emptyList(),
                addressBooks = emptyList(),
                calendars = emptyList(),
            )
            assertTrue(index.searchRecords("notebook", removedAccount, true, true).isEmpty())
            assertTrue(index.searchRecords("ava", removedAccount, true, true).isEmpty())
        } finally {
            index.close()
        }
    }
}
