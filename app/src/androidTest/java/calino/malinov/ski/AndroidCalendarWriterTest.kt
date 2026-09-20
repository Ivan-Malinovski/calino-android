package calino.malinov.ski

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
        val master = insertSeries("Series all")
        val originalStart = eventLong(master, CalendarContract.Events.DTSTART)
        val route = occurrenceRoute(master, index = 2)
        val occurrenceDate = Instant.ofEpochMilli(route.instanceBegin).atZone(ZoneId.systemDefault()).toLocalDate()

        val result = writer.update(
            route,
            NewEvent(
                title = "Whole series",
                date = occurrenceDate,
                startTime = LocalTime.of(9, 0),
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

    private fun insertSeries(title: String): Long {
        val start = LocalDate.now().plusDays(1).atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarRowId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DURATION, "PT30M")
            put(CalendarContract.Events.RRULE, "FREQ=DAILY;COUNT=4")
            put(CalendarContract.Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
        })!!
        return ContentUris.parseId(uri)
    }

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
