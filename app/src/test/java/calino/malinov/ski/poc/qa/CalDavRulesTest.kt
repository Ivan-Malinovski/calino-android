package calino.malinov.ski.poc.qa

import calino.malinov.ski.poc.data.model.CalDavForm
import calino.malinov.ski.poc.data.repository.CalDavAccountStore
import calino.malinov.ski.poc.data.repository.FixtureCalDavClient
import calino.malinov.ski.poc.state.CalDavConnectResult
import calino.malinov.ski.poc.state.CalDavField
import calino.malinov.ski.poc.state.accountId
import calino.malinov.ski.poc.state.canConnect
import calino.malinov.ski.poc.state.defaultDisplayName
import calino.malinov.ski.poc.state.messageFor
import calino.malinov.ski.poc.state.normalizeServerUrl
import calino.malinov.ski.poc.state.serverHost
import calino.malinov.ski.poc.state.validate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The add-CalDAV-account flow's rules, kept out of composables so they can be asserted here. */
class CalDavRulesTest {

    @Test
    fun `bare host gains https`() {
        assertEquals("https://dav.example.com", normalizeServerUrl("dav.example.com"))
    }

    @Test
    fun `trailing slash and surrounding space are dropped`() {
        assertEquals(
            "https://dav.example.com/dav",
            normalizeServerUrl("  https://dav.example.com/dav/  "),
        )
    }

    @Test
    fun `an explicit http scheme is kept`() {
        assertEquals("http://localhost:8080", normalizeServerUrl("http://localhost:8080"))
    }

    @Test
    fun `blank and malformed addresses are rejected`() {
        assertNull(normalizeServerUrl(""))
        assertNull(normalizeServerUrl("   "))
        assertNull(normalizeServerUrl("ftp://dav.example.com"))
        assertNull(normalizeServerUrl("not a host"))
        // A single label is not a host unless it is localhost.
        assertNull(normalizeServerUrl("example"))
    }

    @Test
    fun `host is read back from a normalized url`() {
        assertEquals("dav.example.com", serverHost("https://dav.example.com:8443/dav"))
    }

    @Test
    fun `each missing field reports against itself`() {
        val errors = CalDavForm().validate()
        assertNotNull(errors.messageFor(CalDavField.ServerUrl))
        assertNotNull(errors.messageFor(CalDavField.Username))
        assertNotNull(errors.messageFor(CalDavField.Password))
    }

    @Test
    fun `a malformed url is the only complaint when the rest is filled in`() {
        val errors = CalDavForm(serverUrl = "ftp://x", username = "test-user", password = "pw").validate()
        assertEquals(1, errors.size)
        assertEquals(CalDavField.ServerUrl, errors.single().field)
    }

    @Test
    fun `a complete form can connect`() {
        assertTrue(completeForm().canConnect())
    }

    @Test
    fun `display name falls back to the host and then the username`() {
        assertEquals("dav.example.com", defaultDisplayName(completeForm()))
        assertEquals("Fastmail", defaultDisplayName(completeForm().copy(displayName = " Fastmail ")))
        assertEquals("test-user", defaultDisplayName(CalDavForm(username = "test-user")))
    }

    @Test
    fun `the same login normalizes to the same account id`() {
        assertEquals(
            accountId(completeForm()),
            accountId(completeForm().copy(serverUrl = "HTTPS://Dav.Example.com/", username = "Ivan")),
        )
    }

    @Test
    fun `discovery returns calendars for a reachable host`() = runBlocking {
        val result = fastClient().discover(completeForm())
        val discovered = result as CalDavConnectResult.Discovered
        assertTrue(discovered.calendars.isNotEmpty())
        // The read-only collection is the one that must not offer editing.
        assertTrue(discovered.calendars.any { it.readOnly })
    }

    @Test
    fun `an unreachable host fails`() = runBlocking {
        val result = fastClient().discover(completeForm().copy(serverUrl = "bad.example.com"))
        assertTrue(result is CalDavConnectResult.Failed)
    }

    @Test
    fun `rejected credentials fail`() = runBlocking {
        val result = fastClient().discover(completeForm().copy(password = "wrong"))
        assertTrue(result is CalDavConnectResult.Failed)
    }

    @Test
    fun `the store adds, toggles, and removes an account`() = runBlocking {
        val store = CalDavAccountStore()
        val form = completeForm()
        val discovered = (fastClient().discover(form) as CalDavConnectResult.Discovered).calendars
        val account = store.addAccount(form, discovered)
        assertEquals(1, store.accounts().size)

        store.setCalendarEnabled(account.id, discovered.first().id, false)
        assertEquals(false, store.accounts().single().calendars.first().enabled)

        store.removeAccount(account.id)
        assertTrue(store.accounts().isEmpty())
    }

    @Test
    fun `reconnecting the same login replaces rather than duplicates`() = runBlocking {
        val store = CalDavAccountStore()
        val form = completeForm()
        val discovered = (fastClient().discover(form) as CalDavConnectResult.Discovered).calendars
        store.addAccount(form, discovered)
        store.addAccount(form.copy(serverUrl = "https://dav.example.com/"), discovered)
        assertEquals(1, store.accounts().size)
    }

    @Test
    fun `a stored account carries no password`() = runBlocking {
        val store = CalDavAccountStore()
        val form = completeForm()
        val discovered = (fastClient().discover(form) as CalDavConnectResult.Discovered).calendars
        val account = store.addAccount(form, discovered)
        assertTrue(account.toString().contains(form.username))
        assertTrue(!account.toString().contains(form.password))
    }

    private fun completeForm() = CalDavForm(
        serverUrl = "dav.example.com",
        username = "test-user",
        password = "test-pass",
    )

    private fun fastClient() = FixtureCalDavClient(delayMillis = 0)
}
