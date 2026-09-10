package calino.malinov.ski.poc.data.caldav

import calino.malinov.ski.poc.data.model.Contact
import calino.malinov.ski.poc.data.model.ContactEmail
import calino.malinov.ski.poc.data.model.ContactPhone
import calino.malinov.ski.poc.data.model.ContactPhoneType
import calino.malinov.ski.poc.data.model.ContactType
import calino.malinov.ski.poc.data.model.NewContact
import ezvcard.Ezvcard
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VCardWriterTest {
    private val writer = VCardWriter()
    private val timestamp = Instant.parse("2026-09-10T12:00:00Z")

    @Test
    fun patchReplacesModeledValuesAndPreservesForeignPropertiesAndParameters() {
        val original = """BEGIN:VCARD
VERSION:3.0
UID:ada:1
N;X-NAME-PARAM=keep:Lovelace;Ada;;;
FN:Old Name
EMAIL;TYPE=HOME;X-EMAIL-PARAM=keep:ada@example.com
X-SERVER-ONLY;X-CUSTOM-PARAM=keep:do not lose
PHOTO;VALUE=uri:https://example.test/ada.jpg
END:VCARD
"""
        val contact = Contact(
            id = "ada:1",
            addressBookId = "book",
            givenName = "Ada",
            familyName = "Lovelace",
            displayName = "Ada Updated",
            emails = listOf(ContactEmail("ada@example.com", ContactType.Home, isPrimary = true)),
            uid = "ada:1",
        )

        val patched = writer.patch(original, contact, now = timestamp)

        requireNotNull(patched)
        assertTrue(patched.contains("FN:Ada Updated"))
        assertFalse(patched.contains("FN:Old Name"))
        assertTrue(patched.contains("X-SERVER-ONLY;X-CUSTOM-PARAM=keep:do not lose"))
        assertTrue(patched.contains("PHOTO;TYPE=jpeg;VALUE=uri:https://example.test/ada.jpg"))
        assertTrue(patched.contains("X-NAME-PARAM=keep"))
        assertTrue(patched.contains("REV:"))
    }

    @Test
    fun newContactWritesIdentityAndCommonFieldsAsReadableVcard() {
        val vcf = writer.write(
            NewContact(
                displayName = "Ada Lovelace",
                givenName = "Ada",
                familyName = "Lovelace",
                organization = "Analytical Engines",
                emails = listOf(ContactEmail("ada@example.com", ContactType.Work, isPrimary = true)),
                phones = listOf(ContactPhone("+45 20 00 00 00", ContactPhoneType.Cell)),
            ),
            uid = "ada:1",
            now = timestamp,
        )

        val card = Ezvcard.parse(vcf).first()
        assertEquals("3.0", card.version.version)
        assertEquals("ada:1", card.uid.value)
        assertEquals("Ada Lovelace", card.formattedName.value)
        assertEquals("Lovelace", card.structuredName.family)
        assertEquals("ada@example.com", card.emails.single().value)
        assertEquals("+45 20 00 00 00", card.telephoneNumbers.single().text)
        assertTrue(vcf.contains("PRODID:-//Calino//Calino Android//EN"))
    }

    @Test
    fun malformedOrMultipleCardsAreNotPatched() {
        val contact = Contact(id = "ada:1", addressBookId = "book", uid = "ada:1")
        assertEquals(null, writer.patch("not a vcard", contact, now = timestamp))
        assertEquals(
            null,
            writer.patch(
                "BEGIN:VCARD\nVERSION:3.0\nFN:one\nEND:VCARD\n" +
                    "BEGIN:VCARD\nVERSION:3.0\nFN:two\nEND:VCARD\n",
                contact,
                now = timestamp,
            ),
        )
    }
}
