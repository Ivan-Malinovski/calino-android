package calino.malinov.ski.qa

import calino.malinov.ski.data.caldav.CardDavDiscovery
import calino.malinov.ski.data.caldav.CalDavErrorCode
import calino.malinov.ski.data.caldav.CalDavException
import calino.malinov.ski.data.caldav.DavCredentials
import calino.malinov.ski.data.caldav.DavHttp
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CardDavDiscoveryTest {
    private lateinit var server: MockWebServer
    private val credentials = DavCredentials("test-user", "test-pass")

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun multiStatus(body: String) = MockResponse()
        .setResponseCode(207)
        .setBody(body)
        .setHeader("Content-Type", "application/xml; charset=utf-8")

    @Test
    fun discoversIndependentAddressBookHome_andFiltersCalendarCollections() = runBlocking {
        // The well-known probe is deliberately unsupported; the following
        // principal walk proves the CardDAV home need not sit beside CalDAV.
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(multiStatus(
            """<multistatus xmlns="DAV:"><response><href>/principals/test-user/</href><propstat><prop>
                <current-user-principal><href>/principals/test-user/</href></current-user-principal>
            </prop></propstat></response></multistatus>""",
        ))
        server.enqueue(multiStatus(
            """<multistatus xmlns="DAV:" xmlns:card="urn:ietf:params:xml:ns:carddav"><response><href>/principals/test-user/</href><propstat><prop>
                <card:addressbook-home-set><href>/dav/test-user/contacts/</href></card:addressbook-home-set>
            </prop></propstat></response></multistatus>""",
        ))
        server.enqueue(multiStatus(
            """<multistatus xmlns="DAV:" xmlns:card="urn:ietf:params:xml:ns:carddav" xmlns:cs="http://calendarserver.org/ns/">
                <response><href>/dav/test-user/contacts/</href><propstat><prop><resourcetype><collection/></resourcetype><displayname>Home</displayname></prop><status>HTTP/1.1 200 OK</status></propstat></response>
                <response><href>/dav/test-user/contacts/friends/</href><propstat><prop><resourcetype><collection/><card:addressbook/></resourcetype><displayname>Friends</displayname><card:addressbook-description>Neighbors</card:addressbook-description><cs:getctag>ctag-1</cs:getctag><sync-token>token-1</sync-token></prop><status>HTTP/1.1 200 OK</status></propstat></response>
                <response><href>/dav/test-user/contacts/calendar/</href><propstat><prop><resourcetype><collection/><card:addressbook/><calendar xmlns="urn:ietf:params:xml:ns:caldav"/></resourcetype><displayname>Calendar-shaped</displayname></prop><status>HTTP/1.1 200 OK</status></propstat></response>
            </multistatus>""",
        ))

        val account = CardDavDiscovery(DavHttp()).discoverAccount(server.url("/").toString(), credentials)

        assertTrue(account.homeSetUrl.endsWith("/dav/test-user/contacts/"))
        assertEquals(listOf("Friends"), account.addressBooks.map { it.displayName })
        assertEquals("ctag-1", account.addressBooks.single().ctag)
        assertEquals("token-1", account.addressBooks.single().syncToken)
        assertFalse(account.addressBooks.single().readOnly)
        assertTrue(server.takeRequest().path!!.contains("/.well-known/carddav"))
    }

    @Test
    fun missingPrivilegeMetadata_meansWritable() = runBlocking {
        server.enqueue(multiStatus(
            """<multistatus xmlns="DAV:" xmlns:card="urn:ietf:params:xml:ns:carddav"><response><href>/book/</href><propstat><prop>
                <resourcetype><collection/><card:addressbook/></resourcetype><displayname>Book</displayname>
            </prop></propstat></response></multistatus>""",
        ))

        val books = CardDavDiscovery(DavHttp()).listAddressBooks(server.url("/").toString(), credentials)
        assertFalse(books.single().readOnly)
    }

    @Test
    fun rejectsPrincipalHrefOnAnotherOrigin() = runBlocking {
        val other = MockWebServer().also { it.start() }
        try {
            server.enqueue(MockResponse().setResponseCode(404))
            server.enqueue(multiStatus(
                """<multistatus xmlns="DAV:"><response><href>/</href><propstat><prop>
                    <current-user-principal><href>${other.url("/principal/")}</href></current-user-principal>
                </prop><status>HTTP/1.1 200 OK</status></propstat></response></multistatus>""",
            ))

            val error = runCatching {
                CardDavDiscovery(DavHttp()).discoverAccount(server.url("/").toString(), credentials)
            }.exceptionOrNull()

            assertTrue("expected a rejected DAV href, got $error", error is CalDavException)
            assertEquals(CalDavErrorCode.NotCalDav, (error as CalDavException).code)
            assertEquals(0, other.requestCount)
            assertEquals(2, server.requestCount)
        } finally {
            other.shutdown()
        }
    }
}
