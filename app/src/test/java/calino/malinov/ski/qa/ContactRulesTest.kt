package calino.malinov.ski.qa

import calino.malinov.ski.data.model.Contact
import calino.malinov.ski.data.model.ContactEmail
import calino.malinov.ski.data.model.ContactPhone
import calino.malinov.ski.data.model.ContactPhoneType
import calino.malinov.ski.data.model.ContactType
import calino.malinov.ski.data.model.contactAge
import calino.malinov.ski.data.model.daysUntilNextContactDate
import calino.malinov.ski.data.model.deriveDisplayName
import calino.malinov.ski.data.model.groupContactsByAlpha
import calino.malinov.ski.data.model.primaryContactEmail
import calino.malinov.ski.data.model.primaryContactPhone
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContactRulesTest {

    private fun contact(id: String, name: String) = Contact(id, "book", displayName = name)

    @Test
    fun alphaSections_putNonLettersInHashAndHashLast() {
        val groups = groupContactsByAlpha(
            listOf(contact("z", "Zoe"), contact("hash", "# Rooftop"), contact("a", "Ada"), contact("digit", "9 Lives")),
        )

        assertEquals(listOf("A", "Z", "#"), groups.map { it.first })
        assertEquals(listOf("# Rooftop", "9 Lives"), groups.last().second.map { it.displayName })
    }

    @Test
    fun displayName_fallsBackInTheExpectedOrder() {
        assertEquals("Given Family", deriveDisplayName("", "Given", "Family", "Org", "mail@example.com"))
        assertEquals("Org", deriveDisplayName(null, null, null, "Org", "mail@example.com"))
        assertEquals("mail@example.com", deriveDisplayName(null, null, null, null, "mail@example.com"))
        assertEquals("Unknown", deriveDisplayName(null, null, null, null, null))
    }

    @Test
    fun primarySelection_prefersMarkedValueOtherwiseFirst() {
        val emails = listOf(
            ContactEmail("first@example.com", ContactType.Home),
            ContactEmail("main@example.com", ContactType.Work, isPrimary = true),
        )
        val phones = listOf(
            ContactPhone("111", ContactPhoneType.Home),
            ContactPhone("222", ContactPhoneType.Cell),
        )

        assertEquals("main@example.com", emails.primaryContactEmail()?.value)
        assertEquals("111", phones.primaryContactPhone()?.value)
        assertNull(emptyList<ContactEmail>().primaryContactEmail())
    }

    @Test
    fun dateRules_areLocalAndCrossLeapDayAndYearBoundary() {
        val leapBirthday = LocalDate.of(2000, 2, 29)
        assertEquals(25, contactAge(leapBirthday, LocalDate.of(2026, 2, 28)))
        assertEquals(26, contactAge(leapBirthday, LocalDate.of(2026, 3, 1)))
        assertEquals(1L, daysUntilNextContactDate(LocalDate.of(2000, 1, 1), LocalDate.of(2025, 12, 31)))
        assertEquals(1L, daysUntilNextContactDate(leapBirthday, LocalDate.of(2025, 2, 28)))
        assertEquals(0L, daysUntilNextContactDate(leapBirthday, LocalDate.of(2024, 2, 29)))
    }
}
