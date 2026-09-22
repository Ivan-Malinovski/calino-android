package calino.malinov.ski.data.caldav

import calino.malinov.ski.data.model.CalDavAccount
import calino.malinov.ski.data.model.CalDavCalendar
import calino.malinov.ski.data.repository.CalDavAccountPersistence
import calino.malinov.ski.data.repository.CalDavAccountStore
import calino.malinov.ski.data.repository.CalDavRepository
import calino.malinov.ski.data.repository.RepositorySyncResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CalDavConnectionManagerTest {
    @Test
    fun `missing saved credentials produce an actionable outcome and remove stale sources`() = runBlocking {
        val account = CalDavAccount(
            id = "account-1",
            displayName = "Work account",
            serverUrl = "https://calendar.example.test/",
            username = "person",
            calendars = listOf(
                CalDavCalendar(
                    id = "https://calendar.example.test/cal/work/",
                    name = "Work",
                    color = 0xFF11A602,
                ),
            ),
        )
        val accountStore = CalDavAccountStore(object : CalDavAccountPersistence {
            override fun load() = listOf(account)
            override fun save(accounts: List<CalDavAccount>) = Unit
        })
        val credentials = InMemoryCredentialStore().apply {
            save(account.id, DavCredentials(account.username, "temporary-secret"))
        }
        val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = CalDavRepository(
            fetcher = CalDavFetcher(DavHttp()),
            scope = repositoryScope,
            cache = CalendarCache.None,
        )
        val manager = CalDavConnectionManager(
            accountStore = accountStore,
            credentialStore = credentials,
            repository = repository,
            discovery = CalDavDiscovery(),
            scope = managerScope,
            cardDiscovery = CardDavDiscovery(),
        )
        try {
            manager.restoreFromCache()
            credentials.clear(account.id)

            val result = manager.rediscoverAccounts(setOf(account.id)) { true }

            assertEquals(AccountRediscoveryResult.CredentialsUnavailable("Work account"), result)
            assertEquals(RepositorySyncResult.NoSources, repository.synchronize())
            assertEquals(
                "a missing credential must not leave the previously seeded source usable",
                false,
                repository.snapshot().calendars.any { it.id == account.calendars.single().id },
            )
        } finally {
            repositoryScope.cancel()
            managerScope.cancel()
        }
    }
}
