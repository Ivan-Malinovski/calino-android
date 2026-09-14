package calino.malinov.ski.qa

import calino.malinov.ski.data.model.ContactDraft
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContactDraftTest {

    @Test
    fun blankDraft_doesNotCommit() {
        assertNull(ContactDraft("draft-contact").commit(displayName = "  ", note = "\n"))
    }

    @Test
    fun commit_trimsValuesAndKeepsAddressBookIdentity() {
        val result = ContactDraft("draft-contact", "neighbors").commit(
            displayName = "  Ada Lovelace  ",
            email = "  ada@example.com ",
            phone = "  +45 12 34 56  ",
            note = "  Cake at six.\n",
        )

        requireNotNull(result)
        assertEquals("Ada Lovelace", result.displayName)
        assertEquals("ada@example.com", result.emails.single().value)
        assertEquals("+45 12 34 56", result.phones.single().value)
        assertEquals("Cake at six.", result.note)
        assertEquals("neighbors", result.addressBookId)
    }

    @Test
    fun contactIdentity_survivesEditThroughRepository() = runBlocking {
        val repository = calino.malinov.ski.data.repository.FixtureRepository()
        val created = (repository.addContact(ContactDraft("unused", "fixture-contacts").commit(displayName = "New Neighbor")!!) as calino.malinov.ski.data.repository.WriteResult.Applied).record
        val updated = (repository.updateContact(created.id, created.copy(displayName = "Updated Neighbor").let {
            calino.malinov.ski.data.model.NewContact(
                displayName = it.displayName,
                addressBookId = it.addressBookId,
            )
        }) as calino.malinov.ski.data.repository.WriteResult.Applied).record

        assertEquals(created.id, updated.id)
        assertEquals("Updated Neighbor", updated.displayName)
        assertEquals(created.id, repository.contacts().single { it.id == created.id }.id)
    }
}
