package calino.malinov.ski.data.repository

import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.NewEvent
import calino.malinov.ski.platform.AndroidCalendarId
import calino.malinov.ski.platform.AndroidCalendarSource
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The seam where the device's calendars meet Calino's own.
 *
 * Four properties are worth holding down, because each one fails silently
 * rather than loudly: the synchronous first emission (a missed frame), the
 * monotonic revision (a view that never recomposes), the write refusal (an
 * edit that looks accepted and vanishes), and the fact that nothing imported
 * leaks into tasks or journals.
 */
class ImportingRepositoryTest {

    private val importedCalendar = CalinoCalendar(
        id = AndroidCalendarId.calendar(3),
        name = "Work (Exchange)",
        color = 0xFF3366CC,
        readOnly = true,
        components = setOf("VEVENT"),
    )

    private val importedEvent = CalEvent(
        id = AndroidCalendarId.event(9, 1_789_000_000_000L),
        title = "Sprint review",
        color = 0xFF3366CC,
        start = null,
        durationMinutes = null,
        allDay = true,
        calendarId = importedCalendar.id,
        date = LocalDate.of(2026, 9, 18),
    )

    private val import = AndroidCalendarSource.Import(
        calendars = listOf(importedCalendar),
        events = listOf(importedEvent),
    )

    private fun repository(
        primary: CalinoRepository = FixtureRepository(),
        imported: AndroidCalendarSource.Import = import,
    ) = ImportingRepository(primary, imported)

    @Test
    fun importedEventsAndCalendarsJoinThePrimarysOwn() {
        val primary = FixtureRepository()
        val base = primary.snapshot()
        val merged = repository(primary).snapshot()

        assertEquals(base.events.size + 1, merged.events.size)
        assertEquals(base.calendars.size + 1, merged.calendars.size)
        assertTrue(merged.events.any { it.id == importedEvent.id })
        assertTrue(merged.calendars.any { it.id == importedCalendar.id })
    }

    @Test
    fun importedCalendarsComeLastSoTheEditorStillDefaultsToAWritableOne() {
        val merged = repository().snapshot()

        // The editor picks the first writable VEVENT calendar. An imported
        // one is read-only, but ordering it last means the default does not
        // even depend on that check being right.
        assertEquals(importedCalendar.id, merged.calendars.last().id)
        assertTrue(merged.calendars.first().id != importedCalendar.id)
    }

    @Test
    fun nothingImportedReachesTasksJournalsOrContacts() {
        val primary = FixtureRepository()
        val base = primary.snapshot()
        val merged = repository(primary).snapshot()

        // The provider has no table for any of these, so the primary's are
        // the only ones there can be.
        assertEquals(base.tasks, merged.tasks)
        assertEquals(base.journals, merged.journals)
        assertEquals(base.contacts, merged.contacts)
        assertEquals(base.sync, merged.sync)
        assertEquals(base.writeStatus, merged.writeStatus)
    }

    @Test
    fun anObserverIsHandedTheCurrentSnapshotSynchronously() {
        val repository = repository()
        var seen: CalinoSnapshot? = null

        repository.observe { seen = it }

        // Deferring this renders one empty frame; CalDavRepository documents
        // the same contract and the Compose bridge depends on it.
        assertEquals(repository.snapshot(), seen)
    }

    @Test
    fun theRevisionMovesForwardOnEveryChange() {
        val repository = repository(imported = AndroidCalendarSource.Import())
        val first = repository.snapshot().revision

        repository.setImported(import)
        val second = repository.snapshot().revision
        repository.setImported(AndroidCalendarSource.Import())
        val third = repository.snapshot().revision

        // Including when the imported half empties again: a view keyed on the
        // revision has to redraw to lose the events it was showing.
        assertTrue("$first -> $second", second > first)
        assertTrue("$second -> $third", third > second)
    }

    @Test
    fun aRevisionIsNeverReusedAcrossAPrimaryWithItsOwnNumbering() {
        val repository = repository()
        val revisions = buildList {
            repeat(5) {
                repository.setImported(import)
                add(repository.snapshot().revision)
            }
        }

        assertEquals(revisions.distinct(), revisions)
        assertEquals(revisions.sorted(), revisions)
    }

    @Test
    fun theWrapperRepublishesWhenThePrimaryDoes() {
        val primary = FixtureRepository()
        val repository = repository(primary)
        var seen = 0
        repository.observe { seen++ }
        val before = seen

        primary.addLocalEvent(NewEvent(title = "Added", date = LocalDate.of(2026, 9, 18), calendarId = "personal"))

        assertTrue("observer should have been told", seen > before)
    }

    @Test
    fun anEditToAnImportedEventIsRefusedRatherThanAttempted() = runBlocking {
        val repository = repository()

        val update = repository.updateEvent(
            importedEvent.id,
            NewEvent(title = "Moved", date = LocalDate.of(2026, 9, 18), calendarId = importedCalendar.id),
        )
        val delete = repository.deleteEvent(importedEvent.id)
        val add = repository.addEvent(
            NewEvent(title = "New", date = LocalDate.of(2026, 9, 18), calendarId = importedCalendar.id),
        )

        // The owning app is authoritative for these rows. A rejection that a
        // person can read beats a write that quietly does nothing.
        assertTrue(update is WriteResult.Rejected)
        assertTrue(delete is WriteResult.Rejected)
        assertTrue(add is WriteResult.Rejected)
    }

    @Test
    fun aWriteToTheRealRepositoryStillGoesThrough() = runBlocking {
        val repository = repository()

        val added = repository.addEvent(NewEvent(title = "Coffee", date = LocalDate.of(2026, 9, 18), calendarId = "personal"))

        assertTrue("$added", added !is WriteResult.Rejected)
    }

    @Test
    fun anEmptyImportChangesNothingButTheRevision() {
        val primary = FixtureRepository()
        val base = primary.snapshot()
        val merged = repository(primary, AndroidCalendarSource.Import()).snapshot()

        assertEquals(base.events, merged.events)
        assertEquals(base.calendars, merged.calendars)
        assertNotEquals(base.revision, merged.revision)
    }
}
