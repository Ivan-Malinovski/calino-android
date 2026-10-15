package calino.malinov.ski.data.jmap

import calino.malinov.ski.data.caldav.DavCredentials
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JmapSchedulingTest {
    private val fake = FakeJmapServer()
    private val credentials = DavCredentials("ann@example.com", "secret")

    @After
    fun tearDown() = fake.stop()

    private fun discover(identities: JSONObject?, session: (JSONObject) -> Unit = {}) = runBlocking {
        fake.sessionOverride = session
        fake.respond = { name, _, _ ->
            when (name) {
                "CalendarEvent/get" -> jsonObject("state" to "s", "list" to org.json.JSONArray())
                "Calendar/get" -> jsonObject("list" to jsonArray(listOf(jsonObject("id" to "c1", "name" to "Work"))))
                "ParticipantIdentity/get" -> identities ?: jsonObject("@error" to jsonObject("type" to "unknownMethod"))
                else -> null
            }
        }
        fake.start()
        JmapDiscovery().discoverAccount(fake.baseUrl, credentials)
    }

    @Test
    fun identitiesBecomeTheAccountsMailtoAddressesAndEnableScheduling() {
        val account = discover(
            jsonObject(
                "list" to jsonArray(
                    listOf(
                        jsonObject("id" to "i1", "name" to "Ann", "sendTo" to jsonObject("imip" to "mailto:ann@example.org")),
                        jsonObject("id" to "i2", "sendTo" to jsonObject("web" to "https://example.org/x")),
                    ),
                ),
            ),
        )
        assertTrue(account.autoSchedule)
        assertEquals(setOf("mailto:ann@example.org", "mailto:ann@example.com"), account.calendarUserAddresses)
    }

    @Test
    fun aServerWithoutSchedulingOffersNoInvitations() {
        val account = discover(null)
        assertFalse(account.autoSchedule)
        assertTrue(account.calendarUserAddresses.isEmpty())
    }

    @Test
    fun theCapabilityFlagAloneEnablesSchedulingWithTheLoginAsAddress() {
        val account = discover(null) { session ->
            session.getJSONObject("accounts").getJSONObject("a").getJSONObject("accountCapabilities")
                .put(JmapCalendars, jsonObject("maySendSchedulingMessages" to true))
        }
        assertTrue(account.autoSchedule)
        assertEquals(setOf("mailto:ann@example.com"), account.calendarUserAddresses)
    }
}
