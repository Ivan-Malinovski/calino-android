package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.data.model.Contact
import calino.malinov.ski.poc.data.model.ContactAddress
import calino.malinov.ski.poc.data.model.ContactEmail
import calino.malinov.ski.poc.data.model.ContactPhone
import calino.malinov.ski.poc.data.model.ContactPhoneType
import calino.malinov.ski.poc.data.model.ContactType
import calino.malinov.ski.poc.state.searchContacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactSearchTest {

    private fun contact(
        id: String,
        name: String,
        book: String = "book-a",
        email: String = "",
        phone: String = "",
        categories: List<String> = emptyList(),
        address: ContactAddress? = null,
    ) = Contact(
        id = id,
        addressBookId = book,
        displayName = name,
        emails = email.takeIf(String::isNotBlank)?.let { listOf(ContactEmail(it, ContactType.Work)) } ?: emptyList(),
        phones = phone.takeIf(String::isNotBlank)?.let { listOf(ContactPhone(it, ContactPhoneType.Cell)) } ?: emptyList(),
        categories = categories,
        addresses = address?.let(::listOf) ?: emptyList(),
    )

    @Test
    fun weightedNameMatch_ranksAboveLowerWeightOrganizationMatch() {
        val results = searchContacts(
            listOf(
                contact("org", "Someone", email = "name@example.com"),
                contact("name", "Name Person"),
            ),
            query = "name",
        )

        assertEquals(listOf("name", "org"), results.map { it.id })
    }

    @Test
    fun digitsQuery_matchesPhoneWithSeparatorsRemoved() {
        val results = searchContacts(
            listOf(contact("phone", "Neighbor", phone = "+45 (20) 26-18-15"), contact("other", "Other")),
            query = "2026",
        )

        assertEquals("phone", results.single().id)
    }

    @Test
    fun filters_applyAddressBookTagAndAddressFields() {
        val results = searchContacts(
            listOf(
                contact("a", "Ada", categories = listOf("Neighbors"), address = ContactAddress(city = "Copenhagen")),
                contact("b", "Bob", book = "book-b", categories = listOf("Work"), address = ContactAddress(city = "Aarhus")),
            ),
            query = "copenhagen",
            addressBookId = "book-a",
            tag = "Neighbors",
        )

        assertTrue(results.map { it.id } == listOf("a"))
        assertTrue(searchContacts(results, query = "", addressBookId = "book-b").isEmpty())
    }
}
