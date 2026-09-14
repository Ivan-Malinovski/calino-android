package calino.malinov.ski.data.repository

import calino.malinov.ski.data.model.Contact
import calino.malinov.ski.data.model.ContactAddress
import calino.malinov.ski.data.model.ContactIm
import calino.malinov.ski.data.model.ContactLang
import calino.malinov.ski.data.model.ContactRelated
import calino.malinov.ski.data.model.ContactRelatedType
import calino.malinov.ski.data.model.NewContact
import calino.malinov.ski.data.model.NewEvent
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalOverlayTest {

    @Test
    fun `new event ids do not repeat when the process overlay is recreated`() {
        val input = NewEvent(
            title = "Across launches",
            date = LocalDate.of(2026, 9, 11),
            startTime = LocalTime.of(10, 0),
        )

        val firstLaunchId = LocalOverlay().newEvent(input).id
        val secondLaunchId = LocalOverlay().newEvent(input).id

        assertNotEquals(firstLaunchId, secondLaunchId)
    }

    @Test
    fun `editing the reduced contact form preserves fields it does not expose`() {
        val original = Contact(
            id = "person-1",
            addressBookId = "book",
            accountId = "account",
            displayName = "Original Name",
            givenName = "Original",
            familyName = "Name",
            role = "Archivist",
            addresses = listOf(ContactAddress(street = "1 Archive Road", city = "Copenhagen")),
            ims = listOf(ContactIm("ada@example.com", protocol = "xmpp")),
            gender = "F",
            langs = listOf(ContactLang("da")),
            related = listOf(ContactRelated("urn:uuid:friend", ContactRelatedType.Friend)),
            isGroup = true,
            memberUids = listOf("member-1"),
            uid = "person-1",
            href = "https://dav.example.test/contacts/person-1.vcf",
            etag = "v1",
        )
        val overlay = LocalOverlay()
        overlay.putContact(original)

        val updated = overlay.updateContact(
            id = original.id,
            input = NewContact(
                displayName = "Updated Name",
                givenName = "Updated",
                familyName = "Name",
                addressBookId = original.addressBookId,
            ),
            existing = emptyList(),
        )

        assertEquals("Updated Name", updated.displayName)
        assertEquals("Updated", updated.givenName)
        assertEquals(original.role, updated.role)
        assertEquals(original.addresses, updated.addresses)
        assertEquals(original.ims, updated.ims)
        assertEquals(original.gender, updated.gender)
        assertEquals(original.langs, updated.langs)
        assertEquals(original.related, updated.related)
        assertEquals(original.memberUids, updated.memberUids)
        assertEquals(original.href, updated.href)
        assertEquals(original.etag, updated.etag)
    }
}
