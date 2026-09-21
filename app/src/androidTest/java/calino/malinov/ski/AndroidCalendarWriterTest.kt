package calino.malinov.ski

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import calino.malinov.ski.data.model.NewEvent
import calino.malinov.ski.data.model.RecurrenceEditScope
import calino.malinov.ski.data.model.Reminder
import calino.malinov.ski.data.repository.WriteResult
import calino.malinov.ski.platform.AndroidCalendarId
import calino.malinov.ski.platform.AndroidCalendarSource
import calino.malinov.ski.platform.AndroidCalendarWriter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Provider characterization for ordinary-app writes to foreign calendars. */
@RunWith(AndroidJUnit4::class)
class AndroidCalendarWriterTest {
    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.READ_CALENDAR,
        android.Manifest.permission.WRITE_CALENDAR,
    )

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val writer = AndroidCalendarWriter(context)
    private val account = "writer-${System.nanoTime()}@example.invalid"
    private var calendarRowId = -1L
    private lateinit var source: AndroidCalendarSource.ImportableCalendar

    @Before fun setUp() {
        calendarRowId = insertCalendar()
        source = AndroidCalendarSource.availableCalendars(context).first { it.rowId == calendarRowId }
        assertTrue(source.canWrite)
    }

    @After fun tearDown() {
        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, AccountType)
            .build()
        context.contentResolver.delete(uri, null, null)
    }

    @Test fun providerBatchRollsBackEarlierOperationsWhenAChildFails() {
        val rowId = insertOneOff("Before batch")
        val operations = arrayListOf(
            ContentProviderOperation.newUpdate(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, rowId))
                .withValue(CalendarContract.Events.TITLE, "Must roll back")
                .withExpectedCount(1)
                .build(),
            ContentProviderOperation.newUpdate(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, Long.MAX_VALUE))
                .withValue(CalendarContract.Events.TITLE, "Missing")
                .withExpectedCount(1)
                .build(),
        )

        assertTrue(runCatching {
            context.contentResolver.applyBatch(CalendarContract.AUTHORITY, operations)
        }.isFailure)
        assertEquals("Before batch", eventColumn(rowId, CalendarContract.Events.TITLE))
    }

    @Test fun createAndUpdatePreserveUnsupportedReminderRows() = runBlocking {
        val date = LocalDate.now().plusDays(2)
        val created = writer.create(
            NewEvent(
                title = "Provider write",
                date = date,
                startTime = LocalTime.NOON,
                durationMinutes = 45,
                allDay = false,
                calendarId = source.id,
                reminders = listOf(Reminder(15)),
            ),
            source,
            setOf(source.id),
        )
        assertTrue("$created", created is WriteResult.Applied)
        val event = (created as WriteResult.Applied).record
        val rowId = requireNotNull(AndroidCalendarId.eventRow(event.id)).first
        context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, rowId)
            put(CalendarContract.Reminders.MINUTES, 7)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_DEFAULT)
        })

        val imported = AndroidCalendarSource.read(context, setOf(source.id), setOf(source.id))
        assertEquals(
            "unsupported reminder methods must not become modeled alerts",
            listOf(15),
            imported.events.first { AndroidCalendarId.eventRow(it.id)?.first == rowId }
                .reminders.map { it.minutesBefore },
        )
        val route = imported.routes.values.first { it.eventRowId == rowId }
        val updated = writer.update(
            route,
            NewEvent(
                title = "Changed",
                date = date,
                startTime = LocalTime.NOON,
                durationMinutes = 45,
                allDay = false,
                calendarId = source.id,
                reminders = listOf(Reminder(5)),
            ),
            RecurrenceEditScope.All,
            setOf(source.id),
        )
        assertTrue("$updated", updated is WriteResult.Applied)
        assertEquals(setOf(5 to CalendarContract.Reminders.METHOD_ALERT, 7 to CalendarContract.Reminders.METHOD_DEFAULT), reminders(rowId).toSet())
        val reread = AndroidCalendarSource.read(context, setOf(source.id), setOf(source.id))
        assertEquals(
            listOf(5),
            reread.events.first { AndroidCalendarId.eventRow(it.id)?.first == rowId }
                .reminders.map { it.minutesBefore },
        )
    }

    @Test fun generatedOccurrenceUpdateCreatesAnExceptionWithOriginalIdentity() = runBlocking {
        val master = insertSeries("Series update")
        val route = occurrenceRoute(master, index = 1)
        val date = Instant.ofEpochMilli(route.instanceBegin).atZone(ZoneId.systemDefault()).toLocalDate()

        val result = writer.update(
            route,
            NewEvent(
                title = "Detached",
                date = date.plusDays(1),
                startTime = LocalTime.of(11, 0),
                durationMinutes = 30,
                allDay = false,
                calendarId = source.id,
                reminders = listOf(Reminder(10)),
                recurrenceScope = RecurrenceEditScope.This,
            ),
            RecurrenceEditScope.This,
            setOf(source.id),
        )

        assertTrue("$result", result is WriteResult.Applied)
        val exception = exceptionFor(master, route.originalInstanceTime)
        assertNotNull(exception)
        assertEquals(master, exception!!.originalId)
        assertEquals(route.originalInstanceTime, exception.originalInstanceTime)
        assertEquals(false, exception.originalAllDay)
        assertEquals("Detached", exception.title)
    }

    @Test fun entireSeriesTargetsMasterWithoutMovingItToTheSelectedOccurrence() = runBlocking {
        val master = insertSeries("Series all", timeZone = "America/New_York")
        val originalStart = eventLong(master, CalendarContract.Events.DTSTART)
        val route = occurrenceRoute(master, index = 2)
        val occurrence = Instant.ofEpochMilli(route.instanceBegin).atZone(ZoneId.systemDefault()).toLocalDateTime()
        assertEquals("America/New_York", route.masterBaseline.timeZone)

        val result = writer.update(
            route,
            NewEvent(
                title = "Whole series",
                date = occurrence.toLocalDate(),
                startTime = occurrence.toLocalTime(),
                durationMinutes = 30,
                allDay = false,
                calendarId = source.id,
                recurrenceScope = RecurrenceEditScope.All,
            ),
            RecurrenceEditScope.All,
            setOf(source.id),
        )

        assertTrue("$result", result is WriteResult.Applied)
        assertEquals(originalStart, eventLong(master, CalendarContract.Events.DTSTART))
        assertEquals("Whole series", eventColumn(master, CalendarContract.Events.TITLE))
        assertEquals("FREQ=DAILY;COUNT=4", eventColumn(master, CalendarContract.Events.RRULE))
        assertEquals("America/New_York", eventColumn(master, CalendarContract.Events.EVENT_TIMEZONE))
    }

    @Test fun staleGeneratedSlotDoesNotCreateADuplicateException() = runBlocking {
        val master = insertSeries("Duplicate guard")
        val route = occurrenceRoute(master, index = 1)
        insertExceptionRow(route, CalendarContract.Events.STATUS_CANCELED)
        val occurrence = Instant.ofEpochMilli(route.instanceBegin).atZone(ZoneId.systemDefault()).toLocalDateTime()

        val result = writer.update(
            route,
            NewEvent(
                title = "Must not duplicate",
                date = occurrence.toLocalDate(),
                startTime = occurrence.toLocalTime(),
                durationMinutes = 30,
                allDay = false,
                calendarId = source.id,
                recurrenceScope = RecurrenceEditScope.This,
            ),
            RecurrenceEditScope.This,
            setOf(source.id),
        )

        assertTrue("$result", result is WriteResult.Rejected)
        assertEquals(1, exceptionCount(master, route.originalInstanceTime))
    }

    @Test fun unsupportedEditorFieldsAreRejectedBeforeMutation() = runBlocking {
        val master = insertSeries("Unsupported")
        val route = occurrenceRoute(master, index = 0)
        val occurrence = Instant.ofEpochMilli(route.instanceBegin).atZone(ZoneId.systemDefault()).toLocalDateTime()

        val result = writer.update(
            route,
            NewEvent(
                title = "Should not land",
                date = occurrence.toLocalDate(),
                startTime = occurrence.toLocalTime(),
                durationMinutes = 30,
                allDay = false,
                calendarId = source.id,
                categories = listOf("Private"),
            ),
            RecurrenceEditScope.All,
            setOf(source.id),
        )

        assertTrue("$result", result is WriteResult.Rejected)
        assertEquals("Unsupported", eventColumn(master, CalendarContract.Events.TITLE))
    }

    @Test fun deletingOneGeneratedOccurrenceCreatesACanceledTombstone() = runBlocking {
        val master = insertSeries("Series delete")
        val route = occurrenceRoute(master, index = 2)

        val result = writer.delete(route, RecurrenceEditScope.This, setOf(source.id))

        assertTrue("$result", result is WriteResult.Applied)
        val exception = exceptionFor(master, route.originalInstanceTime)
        assertNotNull(exception)
        assertEquals(CalendarContract.Events.STATUS_CANCELED, exception!!.status)
    }

    @Test fun deletedStateParticipatesInTheStaleBaseline() = runBlocking {
        val master = insertSeries("Deleted elsewhere")
        val route = occurrenceRoute(master, index = 0)
        assertFalse(route.masterBaseline.deleted)
        val syncUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, master).buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Events.ACCOUNT_NAME, account)
            .appendQueryParameter(CalendarContract.Events.ACCOUNT_TYPE, AccountType)
            .build()
        context.contentResolver.update(
            syncUri,
            ContentValues().apply { put(CalendarContract.Events.DELETED, 1) },
            null,
            null,
        )
        val occurrence = Instant.ofEpochMilli(route.instanceBegin).atZone(ZoneId.systemDefault()).toLocalDateTime()

        val result = writer.update(
            route,
            NewEvent(
                title = "Must stay deleted",
                date = occurrence.toLocalDate(),
                startTime = occurrence.toLocalTime(),
                durationMinutes = 30,
                allDay = false,
                calendarId = source.id,
            ),
            RecurrenceEditScope.All,
            setOf(source.id),
        )

        assertTrue("$result", result is WriteResult.Rejected)
    }

    @Test fun staleBaselineIsRejectedWithoutOverwritingProviderChange() = runBlocking {
        val master = insertSeries("Original")
        val route = occurrenceRoute(master, index = 0)
        context.contentResolver.update(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, master),
            ContentValues().apply { put(CalendarContract.Events.TITLE, "Changed elsewhere") },
            null,
            null,
        )

        val result = writer.update(
            route,
            NewEvent(
                title = "Calino edit",
                date = Instant.ofEpochMilli(route.instanceBegin).atZone(ZoneId.systemDefault()).toLocalDate(),
                startTime = LocalTime.of(9, 0),
                durationMinutes = 30,
                allDay = false,
                calendarId = source.id,
                recurrenceScope = RecurrenceEditScope.All,
            ),
            RecurrenceEditScope.All,
            setOf(source.id),
        )

        assertTrue("$result", result is WriteResult.Rejected)
        assertEquals("Changed elsewhere", eventColumn(master, CalendarContract.Events.TITLE))
    }

    private fun occurrenceRoute(master: Long, index: Int): AndroidCalendarSource.ProviderEvent {
        val imported = AndroidCalendarSource.read(context, setOf(source.id), setOf(source.id))
        return imported.routes.values.filter { it.masterRowId == master }
            .sortedBy { it.originalInstanceTime }[index]
    }

    private fun insertCalendar(): Long {
        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, AccountType)
            .build()
        val inserted = context.contentResolver.insert(uri, ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, account)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, AccountType)
            put(CalendarContract.Calendars.NAME, "writer-test")
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Writer test")
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0x3366cc)
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, account)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.ALLOWED_REMINDERS, "0,1")
        })!!
        return ContentUris.parseId(inserted)
    }

    private fun insertOneOff(title: String): Long {
        val start = LocalDate.now().plusDays(1).atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return ContentUris.parseId(context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarRowId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, start + 30 * 60_000L)
            put(CalendarContract.Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
        })!!)
    }

    private fun insertSeries(title: String, timeZone: String = ZoneId.systemDefault().id): Long {
        val start = LocalDate.now().plusDays(1).atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarRowId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DURATION, "PT30M")
            put(CalendarContract.Events.RRULE, "FREQ=DAILY;COUNT=4")
            put(CalendarContract.Events.EVENT_TIMEZONE, timeZone)
        })!!
        return ContentUris.parseId(uri)
    }

    private fun insertExceptionRow(route: AndroidCalendarSource.ProviderEvent, status: Int): Long {
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarRowId)
            put(CalendarContract.Events.TITLE, "Existing exception")
            put(CalendarContract.Events.DTSTART, route.instanceBegin)
            route.instanceEnd?.let { put(CalendarContract.Events.DTEND, it) }
            put(CalendarContract.Events.EVENT_TIMEZONE, route.masterBaseline.timeZone)
            put(CalendarContract.Events.ORIGINAL_ID, route.masterRowId)
            put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, route.originalInstanceTime)
            put(CalendarContract.Events.ORIGINAL_ALL_DAY, if (route.originalAllDay) 1 else 0)
            put(CalendarContract.Events.STATUS, status)
        })!!
        return ContentUris.parseId(uri)
    }

    private fun exceptionCount(master: Long, slot: Long): Int =
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            "${CalendarContract.Events.ORIGINAL_ID} = ? AND ${CalendarContract.Events.ORIGINAL_INSTANCE_TIME} = ?",
            arrayOf(master.toString(), slot.toString()),
            null,
        )?.use { it.count } ?: 0

    private data class ExceptionRow(
        val originalId: Long,
        val originalInstanceTime: Long,
        val originalAllDay: Boolean,
        val status: Int?,
        val title: String?,
    )

    private fun exceptionFor(master: Long, slot: Long): ExceptionRow? {
        val columns = arrayOf(
            CalendarContract.Events.ORIGINAL_ID,
            CalendarContract.Events.ORIGINAL_INSTANCE_TIME,
            CalendarContract.Events.ORIGINAL_ALL_DAY,
            CalendarContract.Events.STATUS,
            CalendarContract.Events.TITLE,
        )
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            columns,
            "${CalendarContract.Events.ORIGINAL_ID} = ? AND ${CalendarContract.Events.ORIGINAL_INSTANCE_TIME} = ?",
            arrayOf(master.toString(), slot.toString()),
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            return ExceptionRow(
                originalId = cursor.getLong(0),
                originalInstanceTime = cursor.getLong(1),
                originalAllDay = cursor.getInt(2) == 1,
                status = if (cursor.isNull(3)) null else cursor.getInt(3),
                title = cursor.getString(4),
            )
        }
        return null
    }

    private fun reminders(eventId: Long): List<Pair<Int, Int>> {
        val rows = mutableListOf<Pair<Int, Int>>()
        context.contentResolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            arrayOf(CalendarContract.Reminders.MINUTES, CalendarContract.Reminders.METHOD),
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
            null,
        )?.use { cursor -> while (cursor.moveToNext()) rows += cursor.getInt(0) to cursor.getInt(1) }
        return rows
    }

    private fun eventLong(rowId: Long, column: String): Long? =
        context.contentResolver.query(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, rowId),
            arrayOf(column), null, null, null,
        )?.use { cursor -> if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null }

    private fun eventColumn(rowId: Long, column: String): String? =
        context.contentResolver.query(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, rowId),
            arrayOf(column), null, null, null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    private companion object {
        const val AccountType = "calino.test.writer"
    }
}
