package calino.malinov.ski.poc.data.caldav

import biweekly.Biweekly
import biweekly.ICalendar
import biweekly.component.ICalComponent
import biweekly.component.VEvent
import biweekly.component.VJournal
import biweekly.component.VTodo
import biweekly.property.ExceptionDates
import biweekly.property.ExceptionRule
import biweekly.property.ICalProperty
import biweekly.property.RecurrenceDates
import biweekly.property.RecurrenceId
import biweekly.property.RecurrenceRule
import calino.malinov.ski.poc.data.model.CalEvent
import calino.malinov.ski.poc.data.model.CalTask
import calino.malinov.ski.poc.data.model.JournalEntry
import java.time.LocalDate
import java.time.Instant
import biweekly.util.ICalDate

/**
 * Rewrites Calino's components inside a resource the server already holds.
 *
 * A CalDAV resource is not ours. It carries properties no client models -- an
 * `ORGANIZER`, a `CLASS`, alarms another app set, `X-` properties, and often a
 * `VTIMEZONE` whose definitions the times depend on. Rebuilding the file from
 * the app's model would drop every one of them, and the person would never see
 * it happen: the event still looks right in Calino, and quietly lost half of
 * itself everywhere else.
 *
 * So an edit is a patch. The server's own bytes are parsed, the one component
 * we mean to change is found and rewritten in place by [ICalWriter], and
 * everything else is left exactly as it arrived.
 *
 * **Every failure returns null** rather than throwing. Null means "could not
 * patch this safely" and the caller falls back to building the resource from
 * scratch. A save that loses unmodelled properties is bad; a save that does not
 * happen at all is worse.
 */
class ICalPatcher(private val writer: ICalWriter = ICalWriter()) {

    /**
     * Rewrites [events] into [originalIcs].
     *
     * [events] is a list because a recurring series and its detached overrides
     * share one resource (RFC 4791 §4.1) and can only be written together.
     * Components matching a supplied event are rewritten; components carrying
     * any other UID -- another object sharing the resource -- are untouched.
     */
    fun patchEvents(originalIcs: String, events: List<CalEvent>, now: Instant): String? =
        patch(originalIcs) { calendar ->
            events.forEach { event ->
                val uid = event.uid ?: return@patch null
                val existing = calendar.events.firstOrNull { it.matches(uid, event) }
                val written = writer.writeEvent(event, existing, now)
                if (existing == null) calendar.addEvent(written)
            }
            calendar
        }

    fun patchTask(originalIcs: String, task: CalTask, now: Instant): String? =
        patch(originalIcs) { calendar ->
            val uid = task.uid ?: return@patch null
            val existing = calendar.todos.firstOrNull { it.uid?.value == uid }
            val written = writer.writeTask(task, existing, now)
            if (existing == null) calendar.addComponent(written)
            calendar
        }

    /**
     * VTODO recurrence is a resource-level operation, just like VEVENT
     * recurrence. The app has no task recurrence editor yet, so a task sharing
     * its href with an override must never be rewritten or deleted as if it
     * were a standalone task (that would discard the rest of the series).
     *
     * `null` means the resource could not be parsed; callers should treat that
     * as an ordinary unsafe-cache condition rather than claiming it is a
     * non-recurring task.
     */
    fun taskRequiresGroupWrite(originalIcs: String, uid: String): Boolean? {
        val calendar = parseSingle(originalIcs) ?: return null
        val matching = calendar.todos.filter { it.uid?.value == uid }
        if (matching.isEmpty()) return false
        return matching.size > 1 || matching.any { task ->
            task.getProperty(RecurrenceRule::class.java) != null ||
                task.getProperty(RecurrenceId::class.java) != null ||
                task.getProperties(RecurrenceDates::class.java).isNotEmpty() ||
                task.getProperties(ExceptionDates::class.java).isNotEmpty() ||
                task.getProperties(ExceptionRule::class.java).isNotEmpty()
        }
    }

    fun patchJournal(originalIcs: String, entry: JournalEntry, now: Instant): String? =
        patch(originalIcs) { calendar ->
            val uid = entry.uid ?: return@patch null
            val existing = calendar.journals.firstOrNull { it.uid?.value == uid }
            val written = writer.writeJournal(entry, existing, now)
            if (existing == null) calendar.addComponent(written)
            calendar
        }

    /** Replaces every VEVENT in one UID recurrence group with [result]. */
    fun patchRecurrence(
        originalIcs: String,
        uid: String,
        result: RecurrenceEdit.Result,
    ): String? = patch(originalIcs) { calendar ->
        val existing = calendar.events.filter { it.uid?.value == uid }
        if (existing.isEmpty()) return@patch null
        existing.forEach(calendar::removeComponent)
        result.groups.flatMap { it.events }.forEach(calendar::addEvent)
        calendar
    }

    /**
     * Re-applies a queued resource to a fresh server resource.
     *
     * The queue stores the raw resource that the local edit was based on as
     * well as the locally edited bytes. When the original ETag has gone stale,
     * replacing the fresh resource with those bytes would resurrect foreign
     * properties from the old snapshot. This three-way merge compares
     * properties by their iCalendar property class: properties unchanged by
     * the local edit stay exactly as the server now has them, while properties
     * changed locally win. Components newly added or removed by the local
     * operation are handled by UID plus RECURRENCE-ID identity.
     *
     * Components outside [component] and UIDs outside [uids] are never
     * touched. That is important for shared resources containing another
     * client's event, task, journal, or VTIMEZONE.
     */
    fun rebaseResource(
        currentIcs: String,
        localIcs: String,
        baseIcs: String,
        component: String,
        uids: Set<String>,
    ): String? = runCatching {
        val current = parseSingle(currentIcs) ?: return@runCatching null
        val local = parseSingle(localIcs) ?: return@runCatching null
        val base = parseSingle(baseIcs) ?: return@runCatching null
        val kind = component.uppercase()
        if (kind !in setOf("VEVENT", "VTODO", "VJOURNAL") || uids.isEmpty()) {
            return@runCatching null
        }

        val baseIndex = indexComponents(base, kind, uids)
        val localIndex = indexComponents(local, kind, uids)
        val currentIndex = indexComponents(current, kind, uids)
        val keys = (baseIndex.keys + localIndex.keys).toSet()
        keys.forEach { key ->
            val baseComponent = baseIndex[key]
            val localComponent = localIndex[key]
            val currentComponent = currentIndex[key]
            when {
                localComponent == null -> {
                    // The local operation removed a component that existed in
                    // its base. Do not remove a server-added component under a
                    // different identity.
                    if (baseComponent != null && currentComponent != null) {
                        current.removeComponent(currentComponent)
                    }
                }
                baseComponent == null -> {
                    // A local recurrence split or newly added component wins
                    // over a same-key server addition, but is added only to
                    // the requested component kind.
                    currentComponent?.let(current::removeComponent)
                    current.addComponent(localComponent.copy())
                }
                currentComponent == null -> {
                    // The remote copy disappeared. Local queued work remains
                    // the requested winner, subject to the same UID scope.
                    current.addComponent(localComponent.copy())
                }
                else -> {
                    current.removeComponent(currentComponent)
                    current.addComponent(mergeComponent(baseComponent, localComponent, currentComponent))
                }
            }
        }
        write(current)
    }.getOrNull()

    /** Removes one UID recurrence group while preserving unrelated components. */
    fun removeRecurrence(
        originalIcs: String,
        uid: String,
    ): PatchRemoval? {
        val calendar = parseSingle(originalIcs) ?: return null
        val doomed = calendar.events.filter { it.uid?.value == uid }
        if (doomed.isEmpty()) return null
        doomed.forEach(calendar::removeComponent)
        val remaining = calendar.events.size + calendar.todos.size + calendar.journals.size
        return if (remaining == 0) PatchRemoval.Emptied else PatchRemoval.Patched(write(calendar))
    }

    /**
     * Deletes one component from a shared resource, leaving the rest in place.
     *
     * Returns null when [originalIcs] cannot be patched, and [Emptied] when the
     * component was the last one Calino could find -- the caller should then
     * `DELETE` the resource rather than `PUT` an empty calendar back.
     */
    fun removeComponent(originalIcs: String, uid: String, component: String? = null): PatchRemoval? {
        val calendar = parseSingle(originalIcs) ?: return null
        // The typed accessors, not the raw component multimap: only VEVENT,
        // VTODO and VJOURNAL are Calino's to remove. A VTIMEZONE is shared
        // scaffolding that the remaining components may still depend on.
        val scheduled = calendar.events + calendar.todos + calendar.journals
        val doomed = scheduled.filter {
            it.uidValue() == uid &&
                (component == null || it.componentName() == component.uppercase())
        }
        if (doomed.isEmpty()) return null
        doomed.forEach { calendar.removeComponent(it) }

        val remaining = scheduled.size - doomed.size
        return if (remaining == 0) PatchRemoval.Emptied else PatchRemoval.Patched(write(calendar))
    }

    sealed interface PatchRemoval {
        data class Patched(val ics: String) : PatchRemoval
        /** Nothing schedulable is left; delete the resource instead of writing it. */
        data object Emptied : PatchRemoval
    }

    private inline fun patch(originalIcs: String, edit: (ICalendar) -> ICalendar?): String? =
        runCatching {
            val calendar = parseSingle(originalIcs) ?: return null
            write(edit(calendar) ?: return null)
        }.getOrNull()

    /**
     * Parses [originalIcs] to exactly one VCALENDAR.
     *
     * More than one concatenated block is refused rather than guessed at: the
     * mapper tolerates them on the way in, but there is no way to tell which
     * block an edit belongs in, and picking wrong would move an event between
     * objects. Falling back to a rebuild is the safe answer.
     */
    private fun parseSingle(originalIcs: String): ICalendar? {
        val cleaned = originalIcs.removePrefix("\uFEFF").trim()
        if (cleaned.isEmpty()) return null
        val calendars = runCatching { Biweekly.parse(cleaned).all() }.getOrNull() ?: return null
        return calendars.singleOrNull()
    }

    private fun write(calendar: ICalendar): String = ICalWriter.write(calendar)

    private companion object {

        fun indexComponents(
            calendar: ICalendar,
            kind: String,
            uids: Set<String>,
        ): Map<String, ICalComponent> {
            val components = when (kind) {
                "VEVENT" -> calendar.events
                "VTODO" -> calendar.todos
                "VJOURNAL" -> calendar.journals
                else -> emptyList()
            }
                .filter { it.uidValue() in uids }
            val grouped = components.groupBy { it.identityKey() }
            require(grouped.keys.none { it == null || grouped[it]!!.size > 1 }) {
                "A queued resource has ambiguous component identity"
            }
            return grouped.mapNotNull { (key, values) -> key?.let { it to values.single() } }.toMap()
        }

        fun mergeComponent(
            base: ICalComponent,
            local: ICalComponent,
            current: ICalComponent,
        ): ICalComponent {
            require(base.javaClass == local.javaClass && local.javaClass == current.javaClass) {
                "A queued resource changed component type"
            }
            val merged = current.copy()
            val baseProperties = base.getProperties().asMap()
            val localProperties = local.getProperties().asMap()
            val propertyClasses = (baseProperties.keys + localProperties.keys).toSet()
            propertyClasses.forEach { propertyClass ->
                val baseValues = baseProperties[propertyClass].orEmpty()
                val localValues = localProperties[propertyClass].orEmpty()
                if (baseValues != localValues) {
                    @Suppress("UNCHECKED_CAST")
                    val typedClass = propertyClass as Class<ICalProperty>
                    merged.removeProperties(typedClass)
                    localValues.forEach { merged.addProperty(it.copy()) }
                }
            }
            return merged
        }

        fun ICalComponent.identityKey(): String? {
            val uid = uidValue() ?: return null
            val recurrence = when (this) {
                is VEvent -> recurrenceKey(recurrenceId?.value)
                is VTodo -> recurrenceKey(recurrenceId?.value)
                is VJournal -> recurrenceKey(recurrenceId?.value)
                else -> "master"
            }
            return "$uid|$recurrence"
        }

        fun recurrenceKey(value: ICalDate?): String = when {
            value == null -> "master"
            !value.hasTime() -> value.rawComponents?.let { raw ->
                "date:${raw.year.toString().padStart(4, '0')}${raw.month.toString().padStart(2, '0')}${raw.date.toString().padStart(2, '0')}"
            } ?: "date:${value.time}"
            else -> "time:${value.time}"
        }

        fun ICalComponent.uidValue(): String? = when (this) {
            is VEvent -> uid?.value
            is VTodo -> uid?.value
            is VJournal -> uid?.value
            else -> null
        }

        fun ICalComponent.componentName(): String = when (this) {
            is VEvent -> "VEVENT"
            is VTodo -> "VTODO"
            is VJournal -> "VJOURNAL"
            else -> ""
        }

        /**
         * Component identity inside a resource is UID *plus* RECURRENCE-ID: the
         * master and each detached override share the UID and are told apart
         * only by the occurrence they replace.
         */
        fun VEvent.matches(uid: String, event: CalEvent): Boolean {
            if (this.uid?.value != uid) return false
            val existing = recurrenceId?.value
            if (event.recurrenceDate != null) {
                val raw = existing?.rawComponents ?: return false
                val date = runCatching { LocalDate.of(raw.year, raw.month, raw.date) }.getOrNull()
                return !existing.hasTime() && date == event.recurrenceDate
            }
            if (event.recurrenceId != null) {
                return existing?.hasTime() == true &&
                    runCatching { existing.toInstant() }.getOrNull() == event.recurrenceId
            }
            return existing == null
        }
    }
}
