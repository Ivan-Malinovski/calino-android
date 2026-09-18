package calino.malinov.ski

import android.content.Context
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import calino.malinov.ski.platform.AndroidCalendarId
import calino.malinov.ski.platform.AndroidCalendarSource
import calino.malinov.ski.platform.CalinoAccounts
import calino.malinov.ski.state.SharedPreferencesPreferenceStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The import opt-in, and the predicate that stops it eating its own tail.
 *
 * The preference half is the same string-set round trip the projected set
 * needs, against real `SharedPreferences` for the same reason:
 * `getStringSet` hands back the live stored set, and saving a set derived
 * from it is the documented way to lose it.
 *
 * The discovery half is the one that matters. Calino publishes calendars
 * into the same provider it reads, so a discovery query that failed to
 * exclude its own account type would import the projection, re-project it,
 * and double every event on each pass. Asserting a Calino-typed calendar is
 * absent is the only test that would look different if that predicate were
 * dropped.
 */
@RunWith(AndroidJUnit4::class)
class ImportedCalendarPreferenceTest {

    /**
     * Without this the provider query throws, `availableCalendars` swallows
     * it, and every discovery assertion below passes against an empty list --
     * including the one that is supposed to fail if the loop guard is gone.
     */
    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.READ_CALENDAR,
        android.Manifest.permission.WRITE_CALENDAR,
    )

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = SharedPreferencesPreferenceStore(context)

    @Before fun clear() {
        store.saveImportedCalendarIds(emptySet())
        store.saveImportedReminderCalendarIds(emptySet())
    }

    @After fun tidy() {
        clear()
        removeTestCalendars()
    }

    // ------------------------------------------------------------ the opt-in

    @Test fun nothingIsImportedByDefault() {
        assertEquals(emptySet<String>(), store.loadImportedCalendarIds())
        assertEquals(emptySet<String>(), store.loadImportedReminderCalendarIds())
    }

    @Test fun theOptInSurvivesAReload() {
        val ids = setOf(AndroidCalendarId.calendar(11), AndroidCalendarId.calendar(12))
        store.saveImportedCalendarIds(ids)

        assertEquals(ids, SharedPreferencesPreferenceStore(context).loadImportedCalendarIds())
    }

    @Test fun droppingOneLeavesTheRest() {
        val first = AndroidCalendarId.calendar(11)
        val second = AndroidCalendarId.calendar(12)
        store.saveImportedCalendarIds(setOf(first, second))

        // Derived from what was just read, which is exactly the aliasing
        // case that makes this worth testing against the real store.
        store.saveImportedCalendarIds(store.loadImportedCalendarIds() - first)

        assertEquals(setOf(second), store.loadImportedCalendarIds())
    }

    @Test fun theReminderSetIsSeparateAndSurvivesToo() {
        val id = AndroidCalendarId.calendar(11)
        store.saveImportedCalendarIds(setOf(id))
        store.saveImportedReminderCalendarIds(setOf(id))

        val reloaded = SharedPreferencesPreferenceStore(context)
        assertEquals(setOf(id), reloaded.loadImportedCalendarIds())
        assertEquals(setOf(id), reloaded.loadImportedReminderCalendarIds())
    }

    // -------------------------------------------------------- the loop guard

    @Test fun aForeignCalendarIsDiscovered() {
        val rowId = insertCalendar(ForeignType, "someone@example.invalid", "Foreign test")

        val found = AndroidCalendarSource.availableCalendars(context)

        assertTrue(
            "expected the foreign calendar among ${found.map { it.name }}",
            found.any { it.rowId == rowId },
        )
    }

    @Test fun calinosOwnCalendarIsNeverDiscovered() {
        val ours = CalinoAccounts.accountType(context)
        val rowId = insertCalendar(ours, "calino-import-test", "Calino's own")

        val found = AndroidCalendarSource.availableCalendars(context)

        // If this ever passes a row through, Calino imports its own
        // projection and the event count doubles on every pass.
        assertFalse(
            "Calino's own calendar must not be importable",
            found.any { it.rowId == rowId },
        )
        assertTrue(found.none { it.accountType == ours })
    }

    @Test fun readingACalendarThatWasNotAskedForReturnsNothing() {
        val rowId = insertCalendar(ForeignType, "someone@example.invalid", "Foreign test")

        val other = AndroidCalendarId.calendar(rowId + 9_999)
        val import = AndroidCalendarSource.read(context, setOf(other))

        assertTrue(import.isEmpty)
    }

    // ------------------------------------------------------------- provider

    private fun insertCalendar(type: String, name: String, display: String): Long {
        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, name)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, type)
            .build()
        val values = android.content.ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, name)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, type)
            put(CalendarContract.Calendars.NAME, display)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, display)
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0x3366CC)
            put(
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.CAL_ACCESS_OWNER,
            )
            put(CalendarContract.Calendars.OWNER_ACCOUNT, name)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.VISIBLE, 1)
        }
        val inserted = context.contentResolver.insert(uri, values)
        return inserted!!.lastPathSegment!!.toLong()
    }

    private fun removeTestCalendars() {
        for (type in listOf(ForeignType, CalinoAccounts.accountType(context))) {
            val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, type)
                .build()
            runCatching {
                context.contentResolver.delete(
                    uri,
                    "${CalendarContract.Calendars.ACCOUNT_NAME} IN (?, ?)",
                    arrayOf("someone@example.invalid", "calino-import-test"),
                )
            }
        }
    }

    private companion object {
        /**
         * A type no installed app claims, standing in for Google or Exchange.
         * It must not be Calino's, which is the whole point of the guard.
         */
        const val ForeignType = "calino.test.foreign"
    }
}
