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
        store.saveWritableImportedCalendarIds(emptySet())
    }

    @After fun tidy() {
        clear()
        removeTestCalendars()
    }

    // ------------------------------------------------------------ the opt-in

    @Test fun nothingIsImportedByDefault() {
        assertEquals(emptySet<String>(), store.loadImportedCalendarIds())
        assertEquals(emptySet<String>(), store.loadImportedReminderCalendarIds())
        assertEquals(emptySet<String>(), store.loadWritableImportedCalendarIds())
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

    @Test fun theWriteSetIsSeparateAndAnExplicitEmptyChoiceSurvivesToo() {
        val id = AndroidCalendarId.calendar(11)
        store.saveImportedCalendarIds(setOf(id))
        store.saveWritableImportedCalendarIds(setOf(id))

        val reloaded = SharedPreferencesPreferenceStore(context)
        assertEquals(setOf(id), reloaded.loadImportedCalendarIds())
        assertEquals(setOf(id), reloaded.loadWritableImportedCalendarIds())

        reloaded.saveWritableImportedCalendarIds(emptySet())
        val disabled = SharedPreferencesPreferenceStore(context)
        assertTrue(disabled.hasWritableImportedCalendarPreference())
        assertEquals(emptySet<String>(), disabled.loadWritableImportedCalendarIds())
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
        val rowId = insertCalendar(ForeignType, ForeignAccount, "Foreign test")

        val found = AndroidCalendarSource.availableCalendars(context)

        assertTrue(
            "expected the foreign calendar among ${found.map { it.name }}",
            found.any { it.rowId == rowId },
        )
    }

    @Test fun calinosOwnCalendarIsNeverDiscovered() {
        val ours = CalinoAccounts.accountType(context)
        val rowId = insertCalendar(ours, OwnAccount, "Calino's own")

        val found = AndroidCalendarSource.availableCalendars(context)

        // If this ever passes a row through, Calino imports its own
        // projection and the event count doubles on every pass.
        assertFalse(
            "Calino's own calendar must not be importable",
            found.any { it.rowId == rowId },
        )
        assertTrue(found.none { it.accountType == ours })
    }

    /**
     * The test that was missing, and whose absence let a broken query ship.
     *
     * Everything else here asserts that something is *not* found, which an
     * empty result satisfies for the wrong reason. `read` swallows provider
     * exceptions on purpose -- a revoked permission must not crash the app --
     * so a projection the provider rejects turns the whole import into
     * silence rather than an error. Only an assertion that real events come
     * back can tell the two apart.
     */
    @Test fun anImportedCalendarYieldsItsEventsAndExpandsItsSeries() {
        val rowId = insertCalendar(ForeignType, ForeignAccount, "Foreign test")
        val calendarId = AndroidCalendarId.calendar(rowId)
        val begin = System.currentTimeMillis() + 3_600_000L
        insertEvent(rowId, "Dentist", begin, begin + 3_600_000L)
        insertRecurringAllDay(rowId, "Bin day", begin, "FREQ=WEEKLY;COUNT=4")

        val import = AndroidCalendarSource.read(context, setOf(calendarId))

        assertEquals(1, import.calendars.size)
        assertTrue("read opt-in alone must remain read-only", import.calendars.single().readOnly)
        val writableImport = AndroidCalendarSource.read(context, setOf(calendarId), setOf(calendarId))
        assertFalse("explicit write opt-in should expose editor access", writableImport.calendars.single().readOnly)
        assertTrue(
            "expected the one-off event among ${import.events.map { it.title }}",
            import.events.any { it.title == "Dentist" },
        )
        // Instances expands the series for us; four occurrences, one row each.
        assertEquals(4, import.events.count { it.title == "Bin day" })
        assertTrue(import.events.all { it.calendarId == calendarId })
    }

    @Test fun readingACalendarThatWasNotAskedForReturnsNothing() {
        val rowId = insertCalendar(ForeignType, ForeignAccount, "Foreign test")

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

    /**
     * The provider only honours a sync-adapter delete when the URI names
     * *both* the account name and its type. Supplying the type alone leaves
     * the rows in place, which is how an earlier version of this test seeded
     * the emulator with calendars that outlived it.
     */
    private fun insertEvent(calendarRowId: Long, title: String, start: Long, end: Long) {
        val values = android.content.ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarRowId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, end)
            put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
        }
        context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
    }

    private fun insertRecurringAllDay(
        calendarRowId: Long,
        title: String,
        start: Long,
        rule: String,
    ) {
        val values = android.content.ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarRowId)
            put(CalendarContract.Events.TITLE, title)
            // All-day rows are anchored at UTC midnight; the provider rejects
            // anything else.
            put(CalendarContract.Events.DTSTART, start / 86_400_000L * 86_400_000L)
            put(CalendarContract.Events.ALL_DAY, 1)
            put(CalendarContract.Events.DURATION, "P1D")
            put(CalendarContract.Events.RRULE, rule)
            put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
        }
        context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
    }

    private fun removeTestCalendars() {
        val accounts = listOf(
            ForeignType to ForeignAccount,
            CalinoAccounts.accountType(context) to OwnAccount,
        )
        for ((type, name) in accounts) {
            val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, name)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, type)
                .build()
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
    }

    private companion object {
        /**
         * A type no installed app claims, standing in for Google or Exchange.
         * It must not be Calino's, which is the whole point of the guard.
         */
        const val ForeignType = "calino.test.foreign"
        const val ForeignAccount = "someone@example.invalid"
        const val OwnAccount = "calino-import-test"
    }
}
