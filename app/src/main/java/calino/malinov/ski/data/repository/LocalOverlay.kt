package calino.malinov.ski.data.repository

import calino.malinov.ski.data.model.CalEvent
import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.Contact
import calino.malinov.ski.data.model.NewContact
import calino.malinov.ski.data.model.JournalEntry
import calino.malinov.ski.data.model.NewEvent
import calino.malinov.ski.data.model.NewJournal
import calino.malinov.ski.data.model.NewTask
import java.util.UUID

/**
 * Local edits layered over server data until the next authoritative answer
 * confirms them. A successful CalDAV write is reflected here immediately so
 * the UI does not wait for a second REPORT; [reconcile] removes that temporary
 * layer once the server sends the same record back.
 */
internal class LocalOverlay {

    private val events = LinkedHashMap<String, CalEvent>()
    private val deletedEvents = mutableSetOf<String>()
    private val tasks = LinkedHashMap<String, CalTask>()
    private val deletedTasks = mutableSetOf<String>()
    private val journals = LinkedHashMap<String, JournalEntry>()
    private val deletedJournals = mutableSetOf<String>()
    private val contacts = LinkedHashMap<String, Contact>()
    private val deletedContacts = mutableSetOf<String>()

    fun clear() {
        events.clear()
        deletedEvents.clear()
        tasks.clear()
        deletedTasks.clear()
        journals.clear()
        deletedJournals.clear()
        contacts.clear()
        deletedContacts.clear()
    }

    fun clearContacts() {
        contacts.clear()
        deletedContacts.clear()
    }

    /** Drops one pending layer when a person discards its dead-lettered write. */
    fun dropEvent(id: String) {
        events.remove(id)
        deletedEvents.remove(id)
    }

    fun dropTask(id: String) {
        tasks.remove(id)
        deletedTasks.remove(id)
    }

    fun dropJournal(id: String) {
        journals.remove(id)
        deletedJournals.remove(id)
    }

    fun dropContact(id: String) {
        contacts.remove(id)
        deletedContacts.remove(id)
    }

    fun applyToEvents(base: List<CalEvent>): List<CalEvent> =
        merge(base, events) { it.id }.without(deletedEvents) { it.id }

    fun applyToTasks(base: List<CalTask>): List<CalTask> =
        merge(base, tasks) { it.id }.without(deletedTasks) { it.id }

    fun applyToJournals(base: List<JournalEntry>): List<JournalEntry> =
        merge(base, journals) { it.id }.without(deletedJournals) { it.id }

    fun applyToContacts(base: List<Contact>): List<Contact> =
        merge(base, contacts) { it.id }.without(deletedContacts) { it.id }

    /**
     * Identity matters here, not just contents. The UI caches month indices
     * and measured text against the list it was handed, and an unconditional
     * filter handed it a fresh list on every publish -- dozens of times during
     * a sync -- throwing all of that away each time for an overlay that was
     * usually empty.
     */
    private fun <T> List<T>.without(deleted: Set<String>, id: (T) -> String): List<T> =
        if (deleted.isEmpty()) this else filterNot { id(it) in deleted }

    /** Server records first, with local edits replacing matches in place. */
    private fun <T> merge(base: List<T>, edits: Map<String, T>, id: (T) -> String): List<T> {
        if (edits.isEmpty()) return base
        val replaced = base.map { edits[id(it)] ?: it }
        val seen = base.map(id).toSet()
        return replaced + edits.values.filterNot { id(it) in seen }
    }

    fun newEvent(input: NewEvent): CalEvent =
        eventFrom(newId("event"), input)

    fun addEvent(input: NewEvent): CalEvent = newEvent(input).also(::putEvent)

    fun updateEvent(id: String, input: NewEvent, existing: List<CalEvent>): CalEvent {
        val current = events[id] ?: existing.firstOrNull { it.id == id } ?: error("Unknown event: $id")
        return eventFrom(id, input)
            // Server identity survives a local edit so a later write path can
            // still address the resource it came from.
            .copy(uid = current.uid, href = current.href, etag = current.etag)
            .also(::putEvent)
    }

    fun newTask(input: NewTask): CalTask =
        taskFrom(newId("task"), input, done = false)

    fun addTask(input: NewTask): CalTask = newTask(input).also(::putTask)

    fun updateTask(id: String, input: NewTask, done: Boolean, existing: List<CalTask>): CalTask {
        val current = tasks[id] ?: existing.firstOrNull { it.id == id } ?: error("Unknown task: $id")
        return taskFrom(id, input, done)
            .copy(uid = current.uid, href = current.href, etag = current.etag)
            .also(::putTask)
    }

    fun putEvent(event: CalEvent): CalEvent = event.also {
        events[it.id] = it
        deletedEvents.remove(it.id)
    }

    fun deleteEvent(id: String) {
        events.remove(id)
        deletedEvents += id
    }

    fun putTask(task: CalTask): CalTask = task.also {
        tasks[it.id] = it
        deletedTasks.remove(it.id)
    }

    fun deleteTask(id: String) {
        tasks.remove(id)
        deletedTasks += id
    }

    fun newJournal(input: NewJournal): JournalEntry = JournalEntry(
            id = newId("journal"),
            date = input.date,
            title = input.title,
            body = input.body,
        )

    fun addJournal(input: NewJournal): JournalEntry = newJournal(input).also { journals[it.id] = it }

    fun updateJournal(id: String, input: NewJournal, existing: List<JournalEntry>): JournalEntry {
        val current = journals[id] ?: existing.firstOrNull { it.id == id } ?: error("Unknown journal: $id")
        return current.copy(date = input.date, title = input.title, body = input.body)
            .also(::putJournal)
    }

    fun putJournal(entry: JournalEntry): JournalEntry = entry.also {
        journals[it.id] = it
        deletedJournals.remove(it.id)
    }

    fun deleteJournal(id: String) {
        journals.remove(id)
        deletedJournals += id
    }

    fun addContact(input: NewContact): Contact = contactFrom(newId("contact"), input)
        .also { contacts[it.id] = it }

    /**
     * These IDs become CalDAV/CardDAV UIDs and resource names. They therefore
     * have to remain unique across process restarts, not merely within one
     * in-memory overlay.
     */
    private fun newId(kind: String): String = "local-$kind-${UUID.randomUUID()}"

    fun putContact(contact: Contact): Contact = contact.also {
        contacts[it.id] = it
        deletedContacts.remove(it.id)
    }

    fun updateContact(id: String, input: NewContact, existing: List<Contact>): Contact {
        val current = contacts[id] ?: existing.firstOrNull { it.id == id } ?: error("Unknown contact: $id")
        // The editor intentionally exposes only a subset of vCard fields.
        // Preserve every other field in the model while replacing the fields
        // the editor owns; otherwise CardDavWriter would see an apparently
        // empty ROLE/ADR/IMPP/LANG/RELATED/etc. and delete it from the raw
        // server card on an unrelated name change.
        return current.copy(
            addressBookId = input.addressBookId,
            displayName = input.displayName.trim(),
            givenName = input.givenName.trim(),
            familyName = input.familyName.trim(),
            organization = input.organization.trim(),
            department = input.department.trim(),
            title = input.title.trim(),
            nickname = input.nickname.trim(),
            emails = input.emails,
            phones = input.phones,
            urls = input.urls,
            birthday = input.birthday,
            anniversary = input.anniversary,
            note = input.note.trim(),
            categories = input.categories,
        )
            .also(::putContact)
    }

    fun deleteContact(id: String) {
        contacts.remove(id)
        deletedContacts += id
    }

    fun removeContactEvents(id: String) {
        val markers = setOf("calino:contact:$id", "calino:contact:$id:anniversary")
        events.entries.removeIf { it.value.url in markers }
    }

    /**
     * Drops only overlays which the server has now confirmed. A partial
     * component read may confirm a changed record, but it must not prove that
     * a missing record was deleted, hence the [authoritative] guard for
     * tombstones.
     */
    fun reconcile(
        serverEvents: List<CalEvent>,
        serverTasks: List<CalTask>,
        serverJournals: List<JournalEntry>,
        serverContacts: List<Contact> = emptyList(),
        authoritative: Boolean,
        guardedIds: Set<String> = emptySet(),
        guardedContactIds: Set<String> = emptySet(),
    ) {
        events.entries.removeIf { (_, local) ->
            local.id !in guardedIds &&
                serverEvents.firstOrNull { it.id == local.id }?.sameContent(local) == true
        }
        tasks.entries.removeIf { (_, local) ->
            local.id !in guardedIds &&
                serverTasks.firstOrNull { it.id == local.id }?.sameContent(local) == true
        }
        journals.entries.removeIf { (_, local) ->
            local.id !in guardedIds &&
                serverJournals.firstOrNull { it.id == local.id }?.sameContent(local) == true
        }
        contacts.entries.removeIf { (_, local) ->
            local.id !in guardedContactIds &&
                serverContacts.firstOrNull { it.sameIdentity(local) }?.sameContent(local) == true
        }
        if (authoritative) {
            deletedEvents.removeIf { id -> id !in guardedIds && serverEvents.none { it.id == id } }
            deletedTasks.removeIf { id -> id !in guardedIds && serverTasks.none { it.id == id } }
            deletedJournals.removeIf { id -> id !in guardedIds && serverJournals.none { it.id == id } }
            deletedContacts.removeIf { id ->
                id !in guardedContactIds && serverContacts.none { it.id == id || it.uid == id }
            }
        }
    }

    private fun eventFrom(id: String, input: NewEvent): CalEvent = CalEvent(
        id = id,
        title = input.title,
        color = input.color,
        start = if (input.allDay) null else input.date.atTime(input.startTime ?: java.time.LocalTime.of(9, 0)),
        durationMinutes = if (input.allDay) null else (input.durationMinutes ?: 60),
        allDay = input.allDay,
        date = if (input.allDay) input.date else null,
        endDate = if (input.allDay) input.endDate else null,
        recurrence = input.recurrence,
        location = input.location,
        notes = input.notes,
        attendees = input.attendees,
        calendarId = input.calendarId,
        availability = input.availability,
        categories = input.categories,
        reminders = input.reminders,
        travelTimeMinutes = input.travelTimeMinutes,
        relatedTo = input.relatedTo,
        uid = input.uid,
        url = input.url,
    )

    private fun taskFrom(id: String, input: NewTask, done: Boolean): CalTask = CalTask(
        id = id,
        title = input.title,
        color = input.color,
        due = input.due,
        dueTime = input.dueTime,
        startDate = input.startDate,
        startTime = input.startTime,
        done = done || input.percentComplete >= 100 || input.status.equals("COMPLETED", ignoreCase = true),
        priority = input.priority,
        percentComplete = if (done || input.percentComplete >= 100 || input.status.equals("COMPLETED", ignoreCase = true)) 100 else input.percentComplete.coerceIn(0, 99),
        status = if (done || input.percentComplete >= 100 || input.status.equals("COMPLETED", ignoreCase = true)) "COMPLETED" else input.status ?: if (input.percentComplete > 0) "IN-PROCESS" else "NEEDS-ACTION",
        completedAt = if (done || input.percentComplete >= 100 || input.status.equals("COMPLETED", ignoreCase = true)) input.completedAt ?: java.time.Instant.now() else null,
        category = input.category,
        notes = input.notes,
        reminder = input.reminder,
        calendarId = input.calendarId,
        parentTaskId = input.parentTaskId,
        recurrence = input.recurrence,
        recurrenceId = input.recurrenceId,
        recurrenceDate = input.recurrenceDate,
        sequence = input.sequence,
        recurrenceChanged = input.recurrenceChanged,
        recurrenceScope = input.recurrenceScope,
    )

    private fun contactFrom(id: String, input: NewContact): Contact = Contact(
        id = id,
        addressBookId = input.addressBookId,
        displayName = input.displayName.trim(),
        givenName = input.givenName.trim(),
        familyName = input.familyName.trim(),
        organization = input.organization.trim(),
        department = input.department.trim(),
        title = input.title.trim(),
        nickname = input.nickname.trim(),
        emails = input.emails,
        phones = input.phones,
        urls = input.urls,
        birthday = input.birthday,
        anniversary = input.anniversary,
        note = input.note.trim(),
        categories = input.categories,
    )

    private fun CalEvent.sameContent(other: CalEvent): Boolean =
        copy(uid = null, href = null, etag = null) == other.copy(uid = null, href = null, etag = null)

    private fun CalTask.sameContent(other: CalTask): Boolean =
        copy(uid = null, href = null, etag = null) == other.copy(uid = null, href = null, etag = null)

    private fun JournalEntry.sameContent(other: JournalEntry): Boolean =
        copy(uid = null, href = null, etag = null) == other.copy(uid = null, href = null, etag = null)

    private fun Contact.sameIdentity(other: Contact): Boolean =
        id == other.id ||
            (uid != null && uid == other.uid) ||
            (href != null && href == other.href)

    private fun Contact.sameContent(other: Contact): Boolean =
        copy(uid = null, href = null, etag = null, rawVCard = null) ==
            other.copy(uid = null, href = null, etag = null, rawVCard = null)
}
