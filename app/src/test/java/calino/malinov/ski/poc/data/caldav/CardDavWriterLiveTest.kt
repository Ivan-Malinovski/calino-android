package calino.malinov.ski.poc.data.caldav

import calino.malinov.ski.poc.data.model.ContactEmail
import calino.malinov.ski.poc.data.model.Contact
import calino.malinov.ski.poc.data.model.ContactType
import calino.malinov.ski.poc.data.model.NewContact
import java.nio.file.Files
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in write round trip against the throwaway Radicale instance. */
class CardDavWriterLiveTest {

    @Test
    fun `Radicale accepts conditional CardDAV create update and delete`() = runBlocking {
        val url = System.getenv("CALINO_CARDDAV_URL") ?: System.getenv("CALINO_CALDAV_URL")
        val user = System.getenv("CALINO_CARDDAV_USER") ?: System.getenv("CALINO_CALDAV_USER")
        val password = System.getenv("CALINO_CARDDAV_PASS") ?: System.getenv("CALINO_CALDAV_PASS")
        assumeTrue(
            "Set CALINO_CARDDAV_URL/_USER/_PASS or the CALINO_CALDAV_* equivalents.",
            !url.isNullOrBlank() && !user.isNullOrBlank() && !password.isNullOrBlank(),
        )

        val credentials = DavCredentials(user!!, password!!)
        val account = CardDavDiscovery().discoverAccount(url!!, credentials)
        val book = account.addressBooks.firstOrNull { !it.readOnly }
        assumeTrue("The live account has no writable address book.", book != null)
        val writableBook = book!!
        val cache = FileCalendarCache(Files.createTempDirectory("calino-carddav-write").toFile())
        cache.saveAddressBook(CachedAddressBook(writableBook.url, Instant.now(), emptyList()))
        val writer = CardDavWriter(cache = cache)
        val uid = "calino-carddav-write-${UUID.randomUUID()}"
        var created: CardResource? = null
        var latest: Contact? = null
        var latestHref: String? = null
        var latestEtag: String? = null

        try {
            created = writer.createContact(
                writableBook,
                credentials,
                NewContact(
                    displayName = "Calino write probe",
                    givenName = "Calino",
                    familyName = "Write Probe",
                    emails = listOf(ContactEmail("calino-write-probe@example.invalid", ContactType.Other, true)),
                    addressBookId = writableBook.url,
                ),
                uid = uid,
            )
            assertTrue(created!!.href.startsWith(writableBook.url))
            assertEquals(uid, VCardMapper().map(created!!.vcf, writableBook.url, "live")!!.uid)

            val mapped = VCardMapper().map(
                created!!.vcf,
                addressBookId = writableBook.url,
                accountId = "live-write-test",
                href = created!!.href,
                etag = created!!.etag,
            )
            assertNotNull("the created vCard should map back", mapped)
            val mappedContact = requireNotNull(mapped)
            latest = mappedContact
            latestHref = created!!.href
            latestEtag = created!!.etag

            latest = null
            val updated = writer.updateContact(
                writableBook,
                credentials,
                mappedContact.copy(displayName = "Calino write probe updated"),
            )
            latestHref = updated.href
            latestEtag = updated.etag
            val updatedContact = VCardMapper().map(
                updated.vcf,
                addressBookId = writableBook.url,
                accountId = "live-write-test",
                href = updated.href,
                etag = updated.etag,
            )
            val latestContact = requireNotNull(updatedContact)
            assertEquals("Calino write probe updated", latestContact.displayName)
            latest = latestContact

            writer.deleteContact(writableBook, credentials, latestContact)
            latest = null
            latestHref = null
            latestEtag = null
        } finally {
            // If an assertion or network error interrupts the normal delete,
            // make one best-effort conditional cleanup attempt.
            runCatching {
                latest?.let { writer.deleteContact(writableBook, credentials, it) }
                    ?: latestHref?.let { writer.delete(writableBook, credentials, it, latestEtag) }
                    ?: created?.let { writer.delete(writableBook, credentials, it.href, it.etag) }
            }
        }
    }
}
