package calino.malinov.ski

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentUris
import android.content.ContentValues
import android.os.Bundle
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import calino.malinov.ski.data.model.Availability
import calino.malinov.ski.data.model.CalDavAccount
import calino.malinov.ski.data.model.CalDavCalendar
import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.Contact
import calino.malinov.ski.data.model.JournalEntry
import calino.malinov.ski.data.model.NewContact
import calino.malinov.ski.data.model.NewEvent
import calino.malinov.ski.data.model.NewJournal
import calino.malinov.ski.data.model.NewTask
import calino.malinov.ski.data.model.RecurrenceEditScope
import calino.malinov.ski.data.repository.CalinoCalendar
import calino.malinov.ski.data.repository.CalinoRepository
import calino.malinov.ski.data.repository.CalinoSnapshot
import calino.malinov.ski.data.repository.UndoableChange
import calino.malinov.ski.data.repository.WriteResult
import calino.malinov.ski.platform.CalendarIngest
import calino.malinov.ski.platform.CalendarProjection
import calino.malinov.ski.platform.CalinoAccounts
import calino.malinov.ski.platform.ProviderIdentity
import java.io.Closeable
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Inbound ingest against the real calendar provider.
 *
 * A foreign edit is simulated the only honest way: by writing through the
 * *plain* content URI, exactly as another calendar app would. That is what
 * sets `DIRTY`, and a test that tagged its writes as a sync adapter would
 * assert on a situation that can never arise.
 *
 * The repository is a recorder rather than the real one. What is under test
 * here is the routing -- which call is made, with what, and what becomes of
 * the row afterwards -- and standing a CalDAV server up would test the write
 * pipeline instead, which already has its own tests.
 */
@RunWith(AndroidJUnit4::class)
class CalendarIngestProviderTest {

    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.READ_CALENDAR,
        android.Manifest.permission.WRITE_CALENDAR,
    )

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val accountId = "ingest-test-account"
    private val calendarId = "https://dav.invalid/cal/ingest-test/"

    private lateinit var account: Account
    private lateinit var repository: RecordingRepository

    @Before fun setUp() {
        account = Account("ingest-test@dav.invalid", CalinoAccounts.accountType(context))
        AccountManager.get(context).addAccountExplicitly(
            account,
            null,
            Bundle().apply { putString(CalinoAccounts.UserDataAccountId, accountId) },
        )
        repository = RecordingRepository(listOf(event()))
    }

    @After fun tearDown() {
        CalendarProjection.clearAccount(context, accountId)
        AccountManager.get(context).removeAccountExplicitly(account)
    }

    @Test fun aCleanProviderOffersNothingToIngest() {
        project()

        assertTrue(CalendarIngest.collect(context, account).isEmpty())
    }

    @Test fun aForeignEditIsReadBackAsAnUpdate() {
        project()
        foreignEdit(rowId(), "Renamed in another app")

        val report = ingest()

        assertEquals(1, report.updated)
        val (id, input) = repository.updated.single()
        assertEquals("e1", id)
        assertEquals("Renamed in another app", input.title)
    }

    @Test fun anIngestedEditKeepsWhatTheProviderNeverHeld() {
        project()
        foreignEdit(rowId(), "Renamed in another app")

        ingest()

        // The provider has no column for any of these, so they can only have
        // come from the record Calino already holds.
        val input = repository.updated.single().second
        assertEquals("uid-1", input.uid)
        assertEquals("\"one\"", input.etag)
        assertEquals(listOf("work"), input.categories)
        assertEquals("FREQ=WEEKLY", input.recurrence)
    }

    @Test fun anIngestedRowIsNoLongerDirty() {
        project()
        foreignEdit(rowId(), "Renamed in another app")
        ingest()

        assertEquals(0, column(rowId(), CalendarContract.Events.DIRTY)?.toInt())
    }

    @Test fun anIngestedRowIsRewrittenByTheNextProjection() {
        project()
        val before = rowId()
        foreignEdit(before, "Renamed in another app")
        ingest()

        // The hash is cleared on ingest precisely so this pass is not skipped.
        assertNull(column(before, ProviderIdentity.Columns.Hash))
        val second = project()
        assertEquals(1, second.written)
        // Calino refused nothing and changed nothing, so the row comes back
        // holding what Calino holds -- the foreign title is gone.
        assertEquals("Standup", titleOf(rowId()))
    }

    @Test fun aRejectedEditIsRevertedRatherThanLeftStanding() {
        repository.reject = true
        project()
        foreignEdit(rowId(), "Renamed in another app")

        val report = ingest()
        project()

        assertEquals(1, report.rejected)
        assertEquals("Standup", titleOf(rowId()))
    }

    @Test fun aForeignDeleteBecomesADeleteAndTakesTheRowWithIt() {
        project()
        foreignDelete(rowId())

        val report = ingest()

        assertEquals(1, report.deleted)
        assertEquals(listOf("e1"), repository.deleted.map { it.first })
        assertEquals(RecurrenceEditScope.All, repository.deleted.single().second)
        assertNull(rowId())
    }

    @Test fun aRefusedDeleteLeavesTheTombstoneForTheNextPass() {
        repository.reject = true
        project()
        foreignDelete(rowId())

        val report = ingest()

        assertEquals(1, report.rejected)
        assertEquals(1, CalendarIngest.collect(context, account).size)
    }

    @Test fun aForeignInsertBecomesAnAdd() {
        project()
        foreignInsert("Dentist")

        val report = ingest()

        assertEquals(1, report.added)
        val input = repository.added.single()
        assertEquals("Dentist", input.title)
        assertEquals(calendarId, input.calendarId)
        // The foreign row carries no Calino identity, so it is dropped and
        // the next projection inserts the canonical one.
        assertEquals(1, ourRowCount())
    }

    @Test fun aDirtyRowNamingNothingCalinoHoldsIsLeftAlone() {
        project()
        val orphan = rowId()
        foreignEdit(orphan, "Edited after the account went away")
        repository.events = emptyList()

        val report = ingest()

        assertEquals(1, report.unresolved)
        assertTrue(repository.updated.isEmpty())
    }

    @Test fun ingestReachesNoCalendarButOurOwn() {
        // A second account of our own type, projected and then edited. The
        // account passed to collect must not see it: if this scoping slips,
        // the same slip reaches Google and Exchange rows.
        project()
        foreignEdit(rowId(), "Renamed in another app")

        val stranger = Account("someone-else@dav.invalid", CalinoAccounts.accountType(context))
        assertTrue(CalendarIngest.collect(context, stranger).isEmpty())
    }

    // --------------------------------------------------------------- helpers

    private fun ingest(): CalendarIngest.Report = runBlocking {
        CalendarIngest.apply(context, account, repository, CalendarIngest.collect(context, account))
    }

    private fun project(): CalendarProjection.Result {
        val projection = ProviderIdentity.project(
            snapshot = CalinoSnapshot(
                events = repository.events,
                tasks = emptyList(),
                journals = emptyList(),
                calendars = listOf(CalinoCalendar(calendarId, "Ingest test", 0xFFC2697F)),
            ),
            accounts = listOf(
                CalDavAccount(
                    id = accountId,
                    displayName = "Ingest test",
                    serverUrl = "https://dav.invalid/",
                    username = "ingest-test",
                    calendars = listOf(CalDavCalendar(calendarId, "Ingest test", 0xFFC2697F)),
                ),
            ),
            optedIn = setOf(calendarId),
        )
        // Writable, because a foreign app cannot edit a CAL_ACCESS_READ
        // calendar and there would be nothing to ingest.
        return requireNotNull(CalendarProjection.sync(context, projection, writable = true))
    }

    private fun event() = CalEvent(
        id = "e1",
        title = "Standup",
        color = 0xFFC2697F,
        start = LocalDateTime.of(2026, 5, 19, 14, 0),
        durationMinutes = 45,
        calendarId = calendarId,
        availability = Availability.Busy,
        categories = listOf("work"),
        recurrence = "FREQ=WEEKLY",
        uid = "uid-1",
        href = "https://dav.invalid/cal/ingest-test/e1.ics",
        etag = "\"one\"",
    )

    /** Exactly what another calendar app does: a plain-URI write, which dirties the row. */
    private fun foreignEdit(rowId: Long?, title: String) {
        val updated = context.contentResolver.update(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, requireNotNull(rowId)),
            ContentValues().apply { put(CalendarContract.Events.TITLE, title) },
            null,
            null,
        )
        assertEquals(1, updated)
    }

    private fun foreignDelete(rowId: Long?) {
        context.contentResolver.delete(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, requireNotNull(rowId)),
            null,
            null,
        )
    }

    private fun foreignInsert(title: String) {
        val start = LocalDateTime.of(2026, 5, 20, 9, 0)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        context.contentResolver.insert(
            CalendarContract.Events.CONTENT_URI,
            ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarRowId())
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DTSTART, start)
                put(CalendarContract.Events.DTEND, start + 30 * 60_000)
                put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
            },
        )
    }

    private fun calendarRowId(): Long? = context.contentResolver.query(
        CalendarContract.Calendars.CONTENT_URI,
        arrayOf(CalendarContract.Calendars._ID),
        "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND " +
            "${CalendarContract.Calendars.ACCOUNT_NAME} = ?",
        arrayOf(CalinoAccounts.accountType(context), account.name),
        null,
    )!!.use { if (it.moveToFirst()) it.getLong(0) else null }

    private fun rowId(): Long? = ourRows().firstOrNull()

    private fun ourRowCount(): Int = ourRows().size

    private fun ourRows(): List<Long> {
        val calendar = calendarRowId() ?: return emptyList()
        return context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DELETED} = 0",
            arrayOf(calendar.toString()),
            null,
        )!!.use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) } }
    }

    private fun titleOf(rowId: Long?): String? = column(rowId, CalendarContract.Events.TITLE)

    private fun column(rowId: Long?, name: String): String? = context.contentResolver.query(
        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, requireNotNull(rowId)),
        arrayOf(name),
        null,
        null,
        null,
    )!!.use { if (it.moveToFirst()) it.getString(0) else null }
}

/**
 * A repository that records what ingest asked of it.
 *
 * Only the four event entry points ingest is allowed to touch are
 * implemented. Everything else throws on purpose: a test that starts failing
 * here is telling us ingest has grown a path the review doc did not sanction.
 */
private class RecordingRepository(var events: List<CalEvent>) : CalinoRepository {

    var reject = false

    val added = mutableListOf<NewEvent>()
    val updated = mutableListOf<Pair<String, NewEvent>>()
    val deleted = mutableListOf<Triple<String, RecurrenceEditScope, LocalDate?>>()

    override fun snapshot(): CalinoSnapshot = CalinoSnapshot(
        events = events,
        tasks = emptyList(),
        journals = emptyList(),
    )

    override fun observe(listener: (CalinoSnapshot) -> Unit): Closeable {
        listener(snapshot())
        return Closeable {}
    }

    override suspend fun addEvent(input: NewEvent): WriteResult<CalEvent> {
        added += input
        return if (reject) WriteResult.Rejected("no") else WriteResult.Applied(events.first())
    }

    override suspend fun updateEvent(id: String, input: NewEvent): WriteResult<CalEvent> {
        updated += id to input
        return if (reject) WriteResult.Rejected("no") else WriteResult.Applied(events.first())
    }

    override suspend fun deleteEvent(
        id: String,
        scope: RecurrenceEditScope,
        occurrenceDate: LocalDate?,
    ): WriteResult<Unit> {
        deleted += Triple(id, scope, occurrenceDate)
        return if (reject) WriteResult.Rejected("no") else WriteResult.Applied(Unit)
    }

    private fun unreachable(): Nothing = error("Ingest must not reach this entry point")

    override suspend fun addTask(input: NewTask): WriteResult<CalTask> = unreachable()
    override suspend fun updateTask(id: String, input: NewTask, done: Boolean): WriteResult<CalTask> = unreachable()
    override suspend fun deleteTask(id: String, scope: RecurrenceEditScope): WriteResult<Unit> = unreachable()
    override suspend fun addJournal(input: NewJournal): WriteResult<JournalEntry> = unreachable()
    override suspend fun updateJournal(id: String, input: NewJournal): WriteResult<JournalEntry> = unreachable()
    override suspend fun deleteJournal(id: String): WriteResult<Unit> = unreachable()
    override suspend fun addContact(input: NewContact): WriteResult<Contact> = unreachable()
    override suspend fun updateContact(id: String, input: NewContact): WriteResult<Contact> = unreachable()
    override suspend fun deleteContact(id: String): WriteResult<Unit> = unreachable()
    override fun addLocalEvent(input: NewEvent): CalEvent = unreachable()
    override suspend fun setTaskDone(id: String, done: Boolean): WriteResult<UndoableChange> = unreachable()
    override suspend fun rescheduleTask(id: String, due: LocalDate?): WriteResult<UndoableChange> = unreachable()
    override suspend fun undo(change: UndoableChange): WriteResult<Unit> = unreachable()
}
