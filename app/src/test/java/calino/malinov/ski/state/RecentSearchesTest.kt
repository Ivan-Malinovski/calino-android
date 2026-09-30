package calino.malinov.ski.state

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentSearchesTest {
    @Test fun newestFirstAndCaseInsensitivelyDeduplicated() {
        val recents = withRecentSearch(listOf("dentist", "project review"), "  Dentist ")
        assertEquals(listOf("Dentist", "project review"), recents)
    }

    @Test fun blankQueriesAreIgnored() {
        val recents = listOf("dentist")
        assertEquals(recents, withRecentSearch(recents, "   "))
    }

    @Test fun keepsOnlyTheLastFive() {
        val recents = listOf("a", "b", "c", "d", "e")
        assertEquals(listOf("f", "a", "b", "c", "d"), withRecentSearch(recents, "f"))
    }

    @Test fun inMemoryStoreRoundTrips() {
        val store = CalinoPreferenceStore.InMemory
        store.saveRecentSearches(listOf("x"))
        assertEquals(listOf("x"), store.loadRecentSearches())
        store.saveRecentSearches(emptyList())
    }
}
