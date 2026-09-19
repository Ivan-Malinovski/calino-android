package calino.malinov.ski

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentValues
import android.content.Context
import android.os.Bundle
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import calino.malinov.ski.data.model.Availability
import calino.malinov.ski.data.model.CalDavAccount
import calino.malinov.ski.data.model.CalDavCalendar
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.repository.CalinoCalendar
import calino.malinov.ski.data.repository.CalinoSnapshot
import calino.malinov.ski.platform.AndroidCalendarId
import calino.malinov.ski.platform.AndroidCalendarSource
import calino.malinov.ski.platform.CalendarProjection
import calino.malinov.ski.platform.CalinoAccounts
import calino.malinov.ski.platform.ProviderIdentity
import java.time.LocalDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Both directions at once: Calino publishing into the provider while also
 * reading the device's own calendars out of it.
 *
 * Neither feature's own tests cover this, and it is the combination that
 * could hurt most. Calino writes to the same table it reads, so a missing
 * guard does not merely show one wrong row -- the projection re-reads its own
 * output, republishes it, and the event count grows on every pass until the
 * calendar is unusable.
 *
 * Driving it here rather than by hand is deliberate. The failure is only
 * visible across repeated passes, and "I tapped through it once and the
 * number looked right" is not an assertion anybody can re-run.
 */
@RunWith(AndroidJUnit4::class)
class BothDirectionsTest {

    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.READ_CALENDAR,
        android.Manifest.permission.WRITE_CALENDAR,
    )

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val accountId = "both-directions-account"
    private val calendarId = "https://dav.invalid/cal/both-directions/"

    private lateinit var account: Account
    private var foreignRowId: Long = -1

    @Before fun setUp() {
        account = Account("both-directions@dav.invalid", CalinoAccounts.accountType(context))
        AccountManager.get(context).addAccountExplicitly(
            account,
            null,
            Bundle().apply { putString(CalinoAccounts.UserDataAccountId, accountId) },
        )
        foreignRowId = insertForeignCalendar()
        insertForeignEvent("Dentist")
    }

    @After fun tearDown() {
        CalendarProjection.clearAccount(context, accountId)
        AccountManager.get(context).removeAccountExplicitly(account)
        removeForeignCalendar()
    }

    @Test fun calinoNeverOffersItsOwnProjectionAsSomethingToImport() {
        project(listOf(event("e1"), event("e2")))

        val importable = AndroidCalendarSource.availableCalendars(context)

        // The foreign one is found, which is what stops the next assertion
        // passing merely because nothing was found at all.
        assertTrue(
            "expected the foreign calendar among ${importable.map { it.name }}",
            importable.any { it.rowId == foreignRowId },
        )
        assertFalse(
            "Calino must not offer to import what it just published",
            importable.any { it.accountType == CalinoAccounts.accountType(context) },
        )
    }

    @Test fun importingReadsTheForeignCalendarAndNotTheProjectedOne() {
        project(listOf(event("e1"), event("e2")))

        val import = AndroidCalendarSource.read(
            context,
            setOf(AndroidCalendarId.calendar(foreignRowId)),
        )

        assertEquals(1, import.calendars.size)
        assertTrue(import.events.any { it.title == "Dentist" })
        // Nothing Calino published may come back in through the front door.
        assertTrue(
            "imported ${import.events.map { it.title }}",
            import.events.none { it.title == "Standup" },
        )
    }

    @Test fun repeatedPassesWithBothDirectionsOnReachAFixedPoint() {
        val events = listOf(event("e1"), event("e2"), event("e3"))
        val counts = mutableListOf<Triple<Int, Int, Int>>()

        repeat(4) {
            project(events)
            // Read between passes, exactly as the running app does: the
            // content observer fires on our own projection writes too.
            val import = AndroidCalendarSource.read(
                context,
                setOf(AndroidCalendarId.calendar(foreignRowId)),
            )
            counts += Triple(projectedRowCount(), foreignRowCount(), import.events.size)
        }

        // Every pass must agree with the first. A growing first number is the
        // echo; a growing third is the import re-reading the projection.
        assertEquals(
            "counts drifted across passes: $counts",
            List(4) { counts.first() },
            counts,
        )
        assertEquals(3, counts.first().first)
        assertEquals(1, counts.first().second)
    }

    @Test fun theProjectedRowsStayUniqueAcrossPasses() {
        val events = listOf(event("e1"), event("e2"), event("e3"))
        repeat(3) { project(events) }

        val syncIds = projectedSyncIds()

        // One row per record. Duplicates under one _SYNC_ID are what the
        // projection race produced before it was serialised, and a second
        // source touching the same table is a fresh way to reintroduce it.
        assertEquals(3, syncIds.size)
        assertEquals(syncIds.distinct().size, syncIds.size)
    }

    // ------------------------------------------------------------- outbound

    private fun project(events: List<CalEvent>): CalendarProjection.Result {
        val projection = ProviderIdentity.project(
            snapshot = CalinoSnapshot(
                events = events,
                tasks = emptyList(),
                journals = emptyList(),
                calendars = listOf(CalinoCalendar(calendarId, "Both directions", 0xFFC2697F)),
            ),
            accounts = listOf(
                CalDavAccount(
                    id = accountId,
                    displayName = "Both directions",
                    serverUrl = "https://dav.invalid/",
                    username = "both-directions",
                    calendars = listOf(CalDavCalendar(calendarId, "Both directions", 0xFFC2697F)),
                ),
            ),
            optedIn = setOf(calendarId),
        )
        return CalendarProjection.sync(context, projection, writable = true)!!
    }

    private fun event(id: String) = CalEvent(
        id = id,
        title = "Standup",
        color = 0xFFC2697F,
        // Fixed rather than now(): a drifting second changes the content hash
        // and would make the fixed-point assertion pass or fail by the clock.
        start = LocalDateTime.of(2026, 9, 19, 14, 0),
        durationMinutes = 45,
        calendarId = calendarId,
        availability = Availability.Busy,
        uid = id,
        href = "https://dav.invalid/cal/both-directions/$id.ics",
        etag = "\"$id\"",
    )

    private fun ourCalendarRowId(): Long? = context.contentResolver.query(
        CalendarContract.Calendars.CONTENT_URI,
        arrayOf(CalendarContract.Calendars._ID),
        "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND " +
            "${CalendarContract.Calendars.ACCOUNT_NAME} = ?",
        arrayOf(CalinoAccounts.accountType(context), account.name),
        null,
    )!!.use { if (it.moveToFirst()) it.getLong(0) else null }

    private fun projectedSyncIds(): List<String> {
        val rowId = ourCalendarRowId() ?: return emptyList()
        return context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._SYNC_ID),
            "${CalendarContract.Events.CALENDAR_ID} = ? AND " +
                "${CalendarContract.Events.DELETED} = 0",
            arrayOf(rowId.toString()),
            null,
        )!!.use {
            buildList { while (it.moveToNext()) add(it.getString(0).orEmpty()) }
        }
    }

    private fun projectedRowCount(): Int = projectedSyncIds().size

    // -------------------------------------------------------------- inbound

    /**
     * A calendar under `ACCOUNT_TYPE_LOCAL`, standing in for Google or
     * Exchange.
     *
     * Local rather than a made-up account type on purpose: the provider
     * deletes calendars whose account is not registered with AccountManager
     * the moment the account list changes, and adding Calino's own account is
     * exactly such a change. A `com.google` row with no Google account behind
     * it disappears mid-test, which looks alarmingly like the projection
     * having eaten it.
     */
    private fun insertForeignCalendar(): Long {
        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ForeignAccount)
            .appendQueryParameter(
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.ACCOUNT_TYPE_LOCAL,
            )
            .build()
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, ForeignAccount)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, "Phone calendar")
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Phone calendar")
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0x3366CC)
            put(
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.CAL_ACCESS_OWNER,
            )
            put(CalendarContract.Calendars.OWNER_ACCOUNT, ForeignAccount)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.VISIBLE, 1)
        }
        return context.contentResolver.insert(uri, values)!!.lastPathSegment!!.toLong()
    }

    private fun insertForeignEvent(title: String) {
        val start = System.currentTimeMillis() + 3_600_000L
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, foreignRowId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, start + 3_600_000L)
            put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
        }
        context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
    }

    private fun foreignRowCount(): Int = context.contentResolver.query(
        CalendarContract.Events.CONTENT_URI,
        arrayOf(CalendarContract.Events._ID),
        "${CalendarContract.Events.CALENDAR_ID} = ? AND " +
            "${CalendarContract.Events.DELETED} = 0",
        arrayOf(foreignRowId.toString()),
        null,
    )!!.use { it.count }

    private fun removeForeignCalendar() {
        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ForeignAccount)
            .appendQueryParameter(
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.ACCOUNT_TYPE_LOCAL,
            )
            .build()
        runCatching { context.contentResolver.delete(uri, null, null) }
    }

    private companion object {
        const val ForeignAccount = "both-directions-phone"
    }
}
