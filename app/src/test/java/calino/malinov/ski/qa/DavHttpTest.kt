package calino.malinov.ski.qa

import calino.malinov.ski.data.caldav.CalDavErrorCode
import calino.malinov.ski.data.caldav.CalDavException
import calino.malinov.ski.data.caldav.DavCredentials
import calino.malinov.ski.data.caldav.DavHttp
import calino.malinov.ski.data.caldav.DavPrecondition
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DavHttpTest {

    private val credentials = DavCredentials("test-user", "test-pass")

    @Test
    fun `a cross-origin redirect is rejected before forwarding credentials or body`() = runBlocking {
        val server = MockWebServer().also { it.start() }
        val other = MockWebServer().also { it.start() }
        try {
            server.enqueue(
                MockResponse().setResponseCode(307)
                    .setHeader("Location", other.url("/capture.ics").toString()),
            )

            val error = runCatching {
                DavHttp().put(
                    url = server.url("/calendar/item.ics").toString(),
                    credentials = credentials,
                    body = "BEGIN:VCALENDAR\nSECRET-CALENDAR-DATA\nEND:VCALENDAR",
                    contentType = DavHttp.CalendarMediaType,
                    precondition = DavPrecondition.New,
                )
            }.exceptionOrNull()

            assertTrue("expected a rejected redirect, got $error", error is CalDavException)
            assertEquals(CalDavErrorCode.NotCalDav, (error as CalDavException).code)
            assertEquals(0, other.requestCount)
            val original = server.takeRequest()
            assertEquals("PUT", original.method)
            assertEquals("Basic dGVzdC11c2VyOnRlc3QtcGFzcw==", original.getHeader("Authorization"))
            assertTrue(original.body.readUtf8().contains("SECRET-CALENDAR-DATA"))
        } finally {
            server.shutdown()
            other.shutdown()
        }
    }

    @Test
    fun `same-origin redirects preserve DAV method body and authentication`() = runBlocking {
        val server = MockWebServer().also { it.start() }
        try {
            server.enqueue(MockResponse().setResponseCode(301).setHeader("Location", "/dav/root"))
            server.enqueue(
                MockResponse().setResponseCode(207)
                    .setBody("<d:multistatus xmlns:d=\"DAV:\"/>")
                    .setHeader("Content-Type", "application/xml; charset=utf-8"),
            )
            val body = "<d:propfind xmlns:d=\"DAV:\"><d:prop><d:displayname/></d:prop></d:propfind>"

            val response = DavHttp().request(
                method = "PROPFIND",
                url = server.url("/.well-known/caldav").toString(),
                credentials = credentials,
                body = body,
            )

            assertEquals(207, response.status)
            assertEquals(server.url("/dav/root").toString(), response.url)
            assertEquals(2, server.requestCount)
            val first = server.takeRequest()
            val second = server.takeRequest()
            listOf(first, second).forEach { request ->
                assertEquals("PROPFIND", request.method)
                assertEquals(body, request.body.readUtf8())
                assertEquals("Basic dGVzdC11c2VyOnRlc3QtcGFzcw==", request.getHeader("Authorization"))
            }
        } finally {
            server.shutdown()
        }
    }
}
