package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.data.caldav.VCardMapper
import calino.malinov.ski.poc.data.model.ContactPhoneType
import calino.malinov.ski.poc.data.model.ContactType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VCardMapperTest {

    private val mapper = VCardMapper()

    @Test
    fun mapsVcard30AndAppleLabels_withPrimaryAndOpaqueLines() {
        val contact = mapper.map(
            """
            BEGIN:VCARD
            VERSION:3.0
            UID:ada-1
            N:Lovelace;Ada;;;
            FN:Ada Lovelace
            item1.EMAIL;TYPE=INTERNET,HOME:ada@example.com
            item1.X-ABLabel:Personal
            item2.TEL;TYPE=CELL,PREF:+45 20 26 18 15
            item2.X-ABLabel:Mobile
            BDAY:19880524
            PHOTO;ENCODING=b;TYPE=JPEG:aGVsbG8=
            X-CUSTOM:preserve-me
            END:VCARD
            """.trimIndent(),
            addressBookId = "book",
            accountId = "account",
            href = "https://example.test/book/ada.vcf",
            etag = "abc",
        )

        requireNotNull(contact)
        assertEquals("Ada Lovelace", contact.displayName)
        assertEquals("email=${contact.emails.single()}", ContactType.Home, contact.emails.single().type)
        assertTrue(contact.emails.single().isPrimary)
        assertEquals(ContactPhoneType.Cell, contact.phones.single().type)
        assertTrue(contact.phones.single().isPrimary)
        assertEquals(LocalDate.of(1988, 5, 24), contact.birthday)
        assertTrue(contact.photo!!.startsWith("data:image/jpeg;base64,"))
        assertTrue(contact.opaqueLines.any { it.startsWith("X-CUSTOM:") })
        assertEquals("abc", contact.etag)
    }

    @Test
    fun mapsVcard40_prefTypeAndDateWithoutUtcConversion() {
        val contact = mapper.map(
            """
            ﻿BEGIN:VCARD
            VERSION:4.0
            N:;;;;
            EMAIL;TYPE=work:work@example.com
            TEL;TYPE=cell:+45 11 22 33 44
            FN:
            ORG:Neighborhood Co-op
            BDAY:2000-01-01
            END:VCARD
            """.trimIndent(),
            addressBookId = "book",
            accountId = "account",
        )

        requireNotNull(contact)
        assertEquals("Neighborhood Co-op", contact.displayName)
        assertEquals(ContactType.Work, contact.emails.single().type)
        assertEquals(ContactPhoneType.Cell, contact.phones.single().type)
        assertEquals(LocalDate.of(2000, 1, 1), contact.birthday)
    }

    @Test
    fun displayNameFallback_usesNameThenOrganizationThenEmail() {
        val org = mapper.map(
            "BEGIN:VCARD\nVERSION:3.0\nN:Family;Given;;;\nORG:Org\nEND:VCARD\n",
            "book",
            "account",
        )
        val email = mapper.map(
            "BEGIN:VCARD\nVERSION:3.0\nEMAIL:fallback@example.com\nEND:VCARD\n",
            "book",
            "account",
        )

        assertEquals("Given Family", org!!.displayName)
        assertEquals("fallback@example.com", email!!.displayName)
    }

    @Test
    fun rejectsAResourceContainingMultipleVCards() {
        val contact = mapper.map(
            """
            BEGIN:VCARD
            VERSION:3.0
            UID:first
            FN:First
            END:VCARD
            BEGIN:VCARD
            VERSION:3.0
            UID:second
            FN:Second
            END:VCARD
            """.trimIndent(),
            addressBookId = "book",
            accountId = "account",
        )

        assertNull(contact)
    }
}
