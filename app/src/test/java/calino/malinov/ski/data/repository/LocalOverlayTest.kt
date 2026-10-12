package calino.malinov.ski.data.repository

import calino.malinov.ski.data.model.Attendee
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.Contact
import calino.malinov.ski.data.model.ContactAddress
import calino.malinov.ski.data.model.ContactIm
import calino.malinov.ski.data.model.ContactLang
import calino.malinov.ski.data.model.ContactRelated
import calino.malinov.ski.data.model.ContactRelatedType
import calino.malinov.ski.data.model.NewContact
import calino.malinov.ski.data.model.NewEvent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    private val wrote = Instant.parse("2026-10-11T10:00:00Z")
    private val readLater = wrote.plusSeconds(60)

    private fun invite(status: String, etag: String?) = CalEvent(
        id = "event-1",
        title = "Planning",
        color = 0xFF2563EB,
        start = LocalDateTime.of(2026, 10, 14, 20, 0),
        durationMinutes = 60,
        attendees = listOf(Attendee("Ilia", "ilia@example.test", participationStatus = status)),
        calendarId = "calendar",
        uid = "event-1",
        href = "/dav/event-1.ics",
        etag = etag,
    )

    private fun overlayHolding(event: CalEvent) = LocalOverlay { wrote }.also { it.putEvent(event) }

    private fun LocalOverlay.reconcileEvents(
        server: CalEvent,
        guardedIds: Set<String> = emptySet(),
        deadLetterIds: Set<String> = emptySet(),
        readStartedAt: Instant = readLater,
    ) = reconcile(
        serverEvents = listOf(server),
        serverTasks = emptyList(),
        serverJournals = emptyList(),
        authoritative = true,
        guardedIds = guardedIds,
        deadLetterIds = deadLetterIds,
        readStartedAt = readStartedAt,
    )

    @Test
    fun `an acknowledged edit steps aside when the server resource changes behind it`() {
        val overlay = overlayHolding(invite("NEEDS-ACTION", etag = "v2"))

        overlay.reconcileEvents(invite("ACCEPTED", etag = "v3"))

        val shown = overlay.applyToEvents(listOf(invite("ACCEPTED", etag = "v3"))).single()
        assertEquals("ACCEPTED", shown.attendees.single().participationStatus)
    }

    @Test
    fun `an acknowledged edit survives a read that started before the write`() {
        val overlay = overlayHolding(invite("NEEDS-ACTION", etag = "v2"))

        overlay.reconcileEvents(invite("NEEDS-ACTION", etag = "v1").copy(title = "Old"), readStartedAt = wrote.minusSeconds(1))

        assertEquals("Planning", overlay.applyToEvents(listOf(invite("NEEDS-ACTION", etag = "v1").copy(title = "Old"))).single().title)
    }

    @Test
    fun `a queued edit is protected from a changed server resource`() {
        val queued = invite("NEEDS-ACTION", etag = "v1").copy(title = "Edited offline")
        val overlay = overlayHolding(queued)

        overlay.reconcileEvents(invite("ACCEPTED", etag = "v3"), guardedIds = setOf("event-1"))

        assertEquals("Edited offline", overlay.applyToEvents(listOf(invite("ACCEPTED", etag = "v3"))).single().title)
    }

    @Test
    fun `a dead-lettered edit stays visible until the person discards it`() {
        val failed = invite("NEEDS-ACTION", etag = "v1").copy(title = "Never synced")
        val overlay = overlayHolding(failed)

        overlay.reconcileEvents(invite("ACCEPTED", etag = "v3"), deadLetterIds = setOf("event-1"))

        assertEquals("Never synced", overlay.applyToEvents(listOf(invite("ACCEPTED", etag = "v3"))).single().title)
        overlay.dropEvent("event-1")
        assertTrue(overlay.applyToEvents(listOf(invite("ACCEPTED", etag = "v3"))).single().title == "Planning")
    }

    @Test
    fun `an unknown tag never proves the layer stale`() {
        val overlay = overlayHolding(invite("NEEDS-ACTION", etag = null).copy(title = "Just saved"))

        overlay.reconcileEvents(invite("ACCEPTED", etag = "v3"))

        assertEquals("Just saved", overlay.applyToEvents(listOf(invite("ACCEPTED", etag = "v3"))).single().title)
    }
}
