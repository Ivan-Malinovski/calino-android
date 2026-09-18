package calino.malinov.ski

import android.accounts.Account
import android.accounts.AccountManager
import android.os.Bundle
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import calino.malinov.ski.data.model.Availability
import calino.malinov.ski.data.model.CalDavAccount
import calino.malinov.ski.data.model.CalDavCalendar
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.Reminder
import calino.malinov.ski.data.repository.CalinoCalendar
import calino.malinov.ski.data.repository.CalinoSnapshot
import calino.malinov.ski.platform.CalendarProjection
import calino.malinov.ski.platform.CalinoAccounts
import calino.malinov.ski.platform.ProviderIdentity
import java.time.LocalDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The outbound projection against the real calendar provider.
 *
 * Runs under a dedicated Calino account id, and every assertion is scoped by
 * our own account type, so the test cannot see or disturb anything else on the
 * device. Teardown removes the account, which takes its calendars with it.
 */
@RunWith(AndroidJUnit4::class)
class CalendarProviderTest {

    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.READ_CALENDAR,
        android.Manifest.permission.WRITE_CALENDAR,
    )

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val accountId = "provider-test-account"
    private val calendarId = "https://dav.invalid/cal/provider-test/"

    private lateinit var account: Account

    @Before fun setUp() {
        val manager = AccountManager.get(context)
        account = Account("provider-test@dav.invalid", CalinoAccounts.accountType(context))
        manager.addAccountExplicitly(
            account,
            null,
            Bundle().apply { putString(CalinoAccounts.UserDataAccountId, accountId) },
        )
    }

    @After fun tearDown() {
        CalendarProjection.clearAccount(context, accountId)
        AccountManager.get(context).removeAccountExplicitly(account)
    }

    @Test fun projectionCreatesTheCalendarAndItsEvents() {
        val result = project(listOf(event()))

        assertEquals(1, result.calendars)
        assertEquals(1, result.written)
        assertNotNull(ourCalendarRowId())

        val row = ourEvents().single()
        assertEquals("e1", row.syncId)
        assertEquals("Standup", row.title)
        // Our own writes must never look like a foreign edit; that is what
        // keeps Step 3's ingest from reading back what Step 2 just wrote.
        assertEquals(0, row.dirty)
    }

    @Test fun anUnchangedPassWritesNothing() {
        project(listOf(event()))
        val second = project(listOf(event()))

        // The anti-churn property. Rewriting an unchanged event re-creates its
        // reminder rows, and the provider then re-arms an alarm that never
        // moved.
        assertEquals(0, second.written)
        assertEquals(0, second.removed)
    }

    @Test fun anEditReplacesTheRowInPlace() {
        project(listOf(event()))
        val edited = project(listOf(event(title = "Standup, moved")))

        assertEquals(1, edited.written)
        assertEquals("Standup, moved", ourEvents().single().title)
    }

    @Test fun aRemovedEventLeavesNoRow() {
        project(listOf(event()))
        val emptied = project(emptyList())

        assertEquals(1, emptied.removed)
        assertTrue(ourEvents().isEmpty())
    }

    @Test fun remindersAreProjectedAsAlerts() {
        project(listOf(event(reminders = listOf(10))))

        val eventRowId = ourEvents().single().id
        context.contentResolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            arrayOf(CalendarContract.Reminders.MINUTES, CalendarContract.Reminders.METHOD),
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(eventRowId.toString()),
            null,
        )!!.use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(10, cursor.getInt(0))
            assertEquals(CalendarContract.Reminders.METHOD_ALERT, cursor.getInt(1))
            assertEquals(1, cursor.count)
        }
    }

    @Test fun aDuplicatedRowIsHealedByTheNextPass() {
        project(listOf(event()))
        val original = ourEvents().single()
        // A second row under the same `_SYNC_ID`, which is what two
        // projection passes racing used to leave behind on device. The pass
        // is serialised now; this asserts the provider can still be brought
        // back into shape when it already holds one.
        context.contentResolver.insert(
            CalendarContract.Events.CONTENT_URI.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Events.ACCOUNT_NAME, account.name)
                .appendQueryParameter(CalendarContract.Events.ACCOUNT_TYPE, account.type)
                .build(),
            android.content.ContentValues().apply {
                put(CalendarContract.Events._SYNC_ID, original.syncId)
                put(CalendarContract.Events.CALENDAR_ID, ourCalendarRowId())
                put(CalendarContract.Events.TITLE, original.title)
                put(CalendarContract.Events.DTSTART, 1_779_285_600_000L)
                put(CalendarContract.Events.DTEND, 1_779_288_300_000L)
                put(CalendarContract.Events.EVENT_TIMEZONE, "Europe/Copenhagen")
            },
        )
        assertEquals(2, ourEvents().size)

        project(listOf(event()))

        assertEquals(1, ourEvents().size)
    }

    @Test fun clearingRemovesEveryOwnedCalendar() {
        project(listOf(event()))
        CalendarProjection.clearAccount(context, accountId)

        assertEquals(null, ourCalendarRowId())
    }

    // --------------------------------------------------------------- helpers

    private fun project(events: List<CalEvent>): CalendarProjection.Result {
        val projection = ProviderIdentity.project(
            snapshot = CalinoSnapshot(
                events = events,
                tasks = emptyList(),
                journals = emptyList(),
                calendars = listOf(CalinoCalendar(calendarId, "Provider test", 0xFFC2697F)),
            ),
            accounts = listOf(
                CalDavAccount(
                    id = accountId,
                    displayName = "Provider test",
                    serverUrl = "https://dav.invalid/",
                    username = "provider-test",
                    calendars = listOf(CalDavCalendar(calendarId, "Provider test", 0xFFC2697F)),
                ),
            ),
            optedIn = setOf(calendarId),
        )
        return requireNotNull(CalendarProjection.sync(context, projection))
    }

    private fun event(
        title: String = "Standup",
        reminders: List<Int> = emptyList(),
    ) = CalEvent(
        id = "e1",
        title = title,
        color = 0xFFC2697F,
        // Fixed, not now(): a drifting second would change the content hash
        // and the anti-churn assertion would pass or fail by the clock.
        start = LocalDateTime.of(2026, 5, 19, 14, 0),
        durationMinutes = 45,
        calendarId = calendarId,
        availability = Availability.Busy,
        reminders = reminders.map { Reminder(it) },
        uid = "e1",
        href = "https://dav.invalid/cal/provider-test/e1.ics",
        etag = "\"one\"",
    )

    private fun ourCalendarRowId(): Long? = context.contentResolver.query(
        CalendarContract.Calendars.CONTENT_URI,
        arrayOf(CalendarContract.Calendars._ID),
        "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND " +
            "${CalendarContract.Calendars.ACCOUNT_NAME} = ?",
        arrayOf(CalinoAccounts.accountType(context), account.name),
        null,
    )!!.use { if (it.moveToFirst()) it.getLong(0) else null }

    private data class EventRow(val id: Long, val syncId: String?, val title: String?, val dirty: Int)

    private fun ourEvents(): List<EventRow> {
        val calendarRowId = ourCalendarRowId() ?: return emptyList()
        return context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events._SYNC_ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DIRTY,
            ),
            "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DELETED} = 0",
            arrayOf(calendarRowId.toString()),
            null,
        )!!.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(EventRow(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getInt(3)))
                }
            }
        }
    }
}
