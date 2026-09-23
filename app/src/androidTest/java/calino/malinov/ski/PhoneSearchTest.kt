package calino.malinov.ski

import android.app.SearchManager
import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import calino.malinov.ski.platform.search.PhoneSearchAccess
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Calino in the phone's search. The listing (what Samsung Finder's "Apps to
 * search in" is built from) is permanent; the Settings switch decides whether
 * the suggestion provider answers at all.
 */
@RunWith(AndroidJUnit4::class)
class PhoneSearchTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val searchManager = context.getSystemService(SearchManager::class.java)
    private val query = Uri.parse("content://${context.packageName}.search/${SearchManager.SUGGEST_URI_PATH_QUERY}/Design")

    @After fun turnOff() = PhoneSearchAccess.setEnabled(context, false)

    @Test fun calinoIsListedAsGloballySearchableWithItsOwnAuthority() {
        val info = searchManager.searchablesInGlobalSearch.single { it.searchActivity.packageName == context.packageName }
        assertEquals("${context.packageName}.search", info.suggestAuthority)
    }

    /**
     * Once published, a provider stays reachable in a live process even after
     * its component is disabled, so it must also refuse by itself.
     */
    @Test fun switchedOffTheProviderAnswersNothing() {
        PhoneSearchAccess.setEnabled(context, true)
        context.contentResolver.query(query, null, null, null, null)?.close()
        PhoneSearchAccess.setEnabled(context, false)
        val cursor = context.contentResolver.query(query, null, null, null, null)
        assertTrue(cursor == null || cursor.use { it.count == 0 })
    }

    /** The device tests run on the May 2026 fixtures, which are not the person's calendar. */
    @Test fun switchedOnSampleDataIsStillNeverSuggested() {
        PhoneSearchAccess.setEnabled(context, true)
        val cursor = context.contentResolver.query(query, null, null, null, null)
        assertNotNull(cursor)
        cursor!!.use { assertEquals(0, it.count) }
    }
}
