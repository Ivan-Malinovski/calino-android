package calino.malinov.ski.data.caldav

import biweekly.ICalendar
import biweekly.component.VEvent
import biweekly.parameter.ICalParameters
import biweekly.property.DateOrDateTimeProperty
import biweekly.property.DateEnd
import biweekly.property.DateStart
import biweekly.property.ExceptionDates
import biweekly.property.ExceptionRule
import biweekly.property.RecurrenceDates
import biweekly.property.RecurrenceId
import biweekly.property.RecurrenceRule
import biweekly.util.DateTimeComponents
import biweekly.util.ICalDate
import biweekly.util.Recurrence
import java.time.Instant
import java.time.LocalDate
import java.util.Date
import java.util.TimeZone

/**
 * Pure transformations for one CalDAV recurrence group.
 *
 * The app's expanded [calino.malinov.ski.data.model.CalEvent] carries a
 * recurrence target for timed and date-only instances, but this helper still
 * deliberately works on biweekly components. The integration contract is:
 *
 *  * parse one VCALENDAR resource and group all VEVENTs with the same UID;
 *  * pass the parsed ICalendar as [Group.calendar] when it contains a
 *    VTIMEZONE, so a TZID value frame can be recovered even when biweekly has
 *    normalised the property parameters;
 *  * resolve the selected expanded occurrence to a [Target] in the master's
 *    DTSTART value frame (use [Target.from] for a raw master/override, or
 *    [Target.timed] / [Target.allDay] for a generated occurrence); and
 *  * pass a complete, already-edited VEvent as [replacement]. Its DTSTART and
 *    DTEND are the values wanted for the selected occurrence. The helper does
 *    not consult or mutate the app model.
 *
 * The returned groups contain only this UID's components. A THIS or ALL edit
 * is one group write. A FUTURE edit is two group writes: the first group keeps
 * the old UID and the second has a fresh UID. The caller is responsible for
 * wrapping each group in its original VCALENDAR metadata and preserving any
 * foreign components in the resource. The helper is pure and does not mutate
 * [Group.calendar]; when Biweekly serializes a returned TZID property, the
 * caller must associate the new property instance with the original timezone
 * assignment in its writer context (or use an equivalent timezone-aware
 * serializer), because biweekly keeps timezone assignments by property
 * identity.
 */
object RecurrenceEdit {

    enum class Scope {
        THIS,
        FUTURE,
        ALL,
    }

    /** The recurrence slot the user acted on, retaining its iCalendar value form. */
    data class Target(val value: ICalDate) {
        init {
            require(value.hasTime() || value.rawComponents != null) {
                "A recurrence target must carry a date or date-time value"
            }
        }

        companion object {
            /** Uses RECURRENCE-ID for an override and DTSTART for the master. */
            fun from(event: VEvent): Target? =
                event.recurrenceId?.value?.let(::ICalDate)?.let(::Target)
                    ?: event.dateStart?.value?.let(::ICalDate)?.let(::Target)

            /** A generated timed occurrence represented as an absolute instant. */
            fun timed(instant: Instant): Target =
                Target(ICalDate(Date.from(instant), true))

            /** A generated all-day occurrence represented without a timezone. */
            fun allDay(date: LocalDate): Target =
                Target(date.toDateOnly())
        }
    }

    /**
     * The master and detached overrides sharing one UID in one CalDAV resource.
     * Instances are validated at construction so a result cannot accidentally
     * attach an override from another series.
     */
    data class Group(
        val master: VEvent,
        val overrides: List<VEvent> = emptyList(),
        val calendar: ICalendar? = null,
    ) {
        init {
            require(master.recurrenceId == null) {
                "A recurrence group master cannot carry RECURRENCE-ID"
            }
            val uid = master.uid?.value
            require(!uid.isNullOrEmpty()) { "A recurrence group needs a master UID" }
            require(master.dateStart?.value != null) { "A recurrence group needs DTSTART" }
            require(overrides.all { it.uid?.value == uid && it.recurrenceId != null }) {
                "Every override must share the master UID and have RECURRENCE-ID"
            }
        }

        val events: List<VEvent>
            get() = listOf(master) + overrides

        companion object {
            /** Extracts one UID group from a parsed calendar without mutating it. */
            fun from(calendar: ICalendar, uid: String): Group =
                from(calendar.events.filter { it.uid?.value == uid }, calendar)

            /** Groups a parsed component list; [calendar] is optional timezone context. */
            fun from(events: Iterable<VEvent>, calendar: ICalendar? = null): Group {
                val matching = events.toList()
                require(matching.isNotEmpty()) { "No VEVENTs were supplied" }
                val masters = matching.filter { it.recurrenceId == null }
                require(masters.size == 1) {
                    "A recurrence group must contain exactly one master"
                }
                return Group(
                    master = masters.single(),
                    overrides = matching.filter { it.recurrenceId != null },
                    calendar = calendar,
                )
            }
        }
    }

    /**
     * Result of an edit or delete. [groups] is the exact set of resources the
     * caller must write; an empty list with [deleted] true means DELETE the
     * original resource.
     */
    data class Result(
        val requestedScope: Scope,
        val effectiveScope: Scope,
        val groups: List<Group>,
        val deleted: Boolean = false,
    ) {
        val components: List<List<VEvent>>
            get() = groups.map { it.events }
    }

    /**
     * Applies an edit to one occurrence, the future half of a series, or the
     * whole series.
     *
     * [newUid] is used only by a genuine FUTURE split. Supplying it from the
     * app's UUID source is preferred; the deterministic default keeps this
     * pure helper convenient in tests and guarantees that the old UID is not
     * reused.
     */
    fun edit(
        group: Group,
        target: Target,
        scope: Scope,
        replacement: VEvent,
        newUid: String = defaultSplitUid(group, target),
    ): Result {
        validateTarget(group, target)
        validateReplacement(group, replacement)

        val effectiveScope = if (scope == Scope.FUTURE && isFirstOccurrence(group, target)) {
            Scope.ALL
        } else {
            scope
        }

        return when (effectiveScope) {
            Scope.THIS -> editThis(group, target, replacement, scope)
            Scope.FUTURE -> editFuture(group, target, replacement, newUid, scope)
            Scope.ALL -> editAll(group, replacement, scope)
        }
    }

    /**
     * Deletes one occurrence, the future half of a series, or the whole
     * series. Deleting THIS uses EXDATE and removes a matching detached
     * override; it never creates an empty override component.
     */
    fun delete(group: Group, target: Target, scope: Scope): Result {
        validateTarget(group, target)
        val effectiveScope = if (scope == Scope.FUTURE && isFirstOccurrence(group, target)) {
            Scope.ALL
        } else {
            scope
        }

        return when (effectiveScope) {
            Scope.THIS -> {
                val master = appendExdate(group, target)
                val overrides = group.overrides
                    .filterNot { matchesTarget(group, it.recurrenceId!!.value, target) }
                    .map(VEvent::copy)
                result(scope, effectiveScope, Group(master, overrides, group.calendar))
            }

            Scope.FUTURE -> {
                val master = truncateMaster(group, target)
                val overrides = group.overrides
                    .filter { isBeforeTarget(group, it.recurrenceId!!.value, target) }
                    .map(VEvent::copy)
                result(scope, effectiveScope, Group(master, overrides, group.calendar))
            }

            Scope.ALL -> Result(
                requestedScope = scope,
                effectiveScope = effectiveScope,
                groups = emptyList(),
                deleted = true,
            )
        }
    }

    private fun editThis(
        group: Group,
        target: Target,
        replacement: VEvent,
        requestedScope: Scope,
    ): Result {
        // A stale EXDATE for the selected day would otherwise suppress the new
        // detached instance. Exact values are removed first; if none matches,
        // a same-day legacy value (often midnight against a timed series) is
        // removed as well.
        val master = removeExdateForEdit(group, target)
        val override = asOverride(group, target, replacement)
        val targetIndex = group.overrides.indexOfFirst {
            matchesTarget(group, it.recurrenceId!!.value, target)
        }
        val overrides = if (targetIndex < 0) {
            group.overrides.map(VEvent::copy) + override
        } else {
            group.overrides.mapIndexed { index, event ->
                if (index == targetIndex) override else event.copy()
            }
        }
        return result(requestedScope, Scope.THIS, Group(master, overrides, group.calendar))
    }

    private fun editAll(
        group: Group,
        replacement: VEvent,
        requestedScope: Scope,
    ): Result {
        val master = asMaster(group, replacement, sequence = null)
        // ALL is a master update. Detached overrides remain detached and keep
        // their per-instance edits; the caller writes the whole group together.
        // Clearing recurrence removes detached overrides as well. Keeping them
        // would leave orphaned one-off events after the master stops recurring.
        val overrides = if (replacement.recurrenceRule == null) {
            emptyList()
        } else {
            group.overrides.map(VEvent::copy)
        }
        return result(requestedScope, Scope.ALL, Group(master, overrides, group.calendar))
    }

    private fun editFuture(
        group: Group,
        target: Target,
        replacement: VEvent,
        newUid: String,
        requestedScope: Scope,
    ): Result {
        val oldUid = requireNotNull(group.master.uid?.value)
        require(newUid.isNotBlank() && newUid != oldUid) {
            "A future recurrence split needs a fresh UID"
        }

        val oldMaster = truncateMaster(group, target)
        val oldOverrides = group.overrides
            .filter { isBeforeTarget(group, it.recurrenceId!!.value, target) }
            .map(VEvent::copy)

        val newMaster = asMaster(group, replacement, sequence = 0).also {
            it.setUid(newUid)
            // EXDATE belongs to the old series. The replacement may have been
            // built from a master or from an occurrence, so neither old
            // exclusions nor old exception rules may leak into the new series.
            it.removeProperties(ExceptionDates::class.java)
            it.removeProperties(ExceptionRule::class.java)
            it.removeProperties(RecurrenceDates::class.java)
            it.setRecurrenceRule(recurrenceForNewSeries(group, target, replacement))
        }

        // An override at the split slot is represented by the new master. The
        // later overrides retain their detached edits, but must move to the
        // new UID *and the corresponding slot in the new series*. If the
        // replacement changes DTSTART (a common "move this and following"
        // edit), retaining the old RECURRENCE-ID leaves those overrides
        // orphaned because RECURRENCE-ID names a slot in the new master's
        // recurrence set, not the old one.
        val newOverrides = group.overrides
            .filter { !isBeforeTarget(group, it.recurrenceId!!.value, target) }
            .filterNot { matchesTarget(group, it.recurrenceId!!.value, target) }
            .map { event ->
                event.copy().also {
                    it.setUid(newUid)
                    val migratedId = migrateOverrideSlot(
                        oldGroup = group,
                        newGroup = Group(newMaster, emptyList(), group.calendar),
                        splitTarget = target,
                        override = it,
                    ) ?: throw IllegalArgumentException(
                        "A detached recurrence could not be mapped into the new future series",
                    )
                    it.setRecurrenceId(migratedId)
                }
            }

        return Result(
            requestedScope = requestedScope,
            effectiveScope = Scope.FUTURE,
            groups = listOf(
                Group(oldMaster, oldOverrides, group.calendar),
                Group(newMaster, newOverrides, group.calendar),
            ),
        )
    }

    private fun asMaster(group: Group, replacement: VEvent, sequence: Int?): VEvent =
        replacement.copy().also { event ->
            event.setUid(requireNotNull(group.master.uid?.value))
            event.removeProperties(RecurrenceId::class.java)
            preserveTemporalForm(group, event)
            sequence?.let { event.setSequence(it) }
        }

    private fun asOverride(group: Group, target: Target, replacement: VEvent): VEvent =
        replacement.copy().also { event ->
            event.setUid(requireNotNull(group.master.uid?.value))
            event.removeProperties(RecurrenceRule::class.java)
            event.removeProperties(RecurrenceDates::class.java)
            event.removeProperties(ExceptionDates::class.java)
            event.removeProperties(ExceptionRule::class.java)
            event.removeProperties(RecurrenceId::class.java)
            preserveTemporalForm(group, event)
            event.setRecurrenceId(recurrenceIdFor(group, target))
        }

    /**
     * A model writer may have produced UTC values even though the original
     * series used a TZID. Convert the absolute value back to that series frame
     * before attaching the edited component, and copy the original parameters
     * (`VALUE=DATE` or `TZID=...`) onto the replacement property.
     */
    private fun preserveTemporalForm(group: Group, event: VEvent) {
        val startTemplate = requireNotNull(group.master.dateStart)
        // A TZID parameter on a timed replacement is the writer's deliberate
        // choice of zone (the person picked one), not a lost frame: keep it.
        // RECURRENCE-ID still follows the master's frame (RFC 5545 3.8.4.4).
        fun chosenZone(property: DateOrDateTimeProperty?): Boolean =
            !isAllDay(group) && property?.value?.hasTime() == true && property.getParameter("TZID") != null
        event.dateStart?.takeUnless(::chosenZone)?.let { original ->
            val value = normalizedValue(group, original.value)
            val property = DateStart(value)
            property.setParameters(parametersForDate(group, startTemplate))
            event.removeProperties(DateStart::class.java)
            event.addProperty(property)
        }

        event.dateEnd?.takeUnless(::chosenZone)?.let { original ->
            val template = group.master.dateEnd ?: startTemplate
            val value = normalizedValue(group, original.value)
            val property = DateEnd(value)
            property.setParameters(parametersForDate(group, template))
            event.removeProperties(DateEnd::class.java)
            event.addProperty(property)
        }
    }

    private fun normalizedValue(group: Group, value: ICalDate): ICalDate =
        if (isAllDay(group)) {
            localDateOf(group, value).toDateOnly()
        } else {
            valueInMasterFrame(group, value)
        }

    private fun truncateMaster(group: Group, target: Target): VEvent {
        val rule = requireNotNull(group.master.recurrenceRule) {
            "A FUTURE recurrence operation needs an RRULE"
        }
        val until = if (isAllDay(group)) {
            localDateOf(group, target.value).minusDays(1).toDateOnly()
        } else {
            ICalDate(
                Date.from(Instant.ofEpochMilli(target.value.time).minusSeconds(1)),
                true,
            )
        }
        val truncatedRule = RecurrenceRule(
            Recurrence.Builder(rule.value)
                .until(until)
                .count(null)
                .build(),
        )
        return group.master.copy().also { it.setRecurrenceRule(truncatedRule) }
    }

    /**
     * Keeps a user's explicit replacement rule. If the replacement came from
     * an occurrence and consequently has no RRULE, the old rule is inherited.
     * A FUTURE operation is therefore always a series operation; use ALL with
     * a rule-less replacement to turn a series into a one-off.
     */
    private fun recurrenceForNewSeries(
        group: Group,
        target: Target,
        replacement: VEvent,
    ): RecurrenceRule? {
        val original = group.master.recurrenceRule
        val requested = replacement.recurrenceRule ?: original ?: return null
        val originalCount = original?.value?.count
        val requestedCount = requested.value.count
        val before = if (originalCount != null && requestedCount == originalCount) {
            recurrenceSlotsBefore(group, target)
        } else {
            null
        }
        if (originalCount == null || requestedCount != originalCount || before == null) {
            return RecurrenceRule(requested.value)
        }

        val remaining = originalCount - before
        require(remaining > 0) {
            "The selected occurrence is outside the master's COUNT"
        }
        return RecurrenceRule(
            Recurrence.Builder(requested.value)
                .count(remaining)
                .build(),
        )
    }

    private fun recurrenceSlotsBefore(group: Group, target: Target): Int? {
        val iterator = group.master.getDateIterator(seriesTimeZone(group))
        var before = 0
        repeat(MaxRecurrenceWalk) {
            if (!iterator.hasNext()) return null
            val generated = iterator.next()
            val comparison = if (isAllDay(group)) {
                generated.toInstant().atZone(seriesTimeZone(group).toZoneId()).toLocalDate()
                    .compareTo(localDateOf(group, target.value))
            } else {
                generated.time.compareTo(target.value.time)
            }
            when {
                comparison < 0 -> before++
                comparison == 0 -> return before
                else -> return null
            }
        }
        return null
    }

    /**
     * Moves a later detached override to the same ordinal slot in the new
     * FUTURE series. Ordinals are intentional here: a future edit is allowed
     * to change the start time/date and even the rule's interval, while an
     * override still represents the user's exception to the corresponding
     * occurrence rather than to an absolute old timestamp.
     */
    private fun migrateOverrideSlot(
        oldGroup: Group,
        newGroup: Group,
        splitTarget: Target,
        override: VEvent,
    ): RecurrenceId? {
        val oldValue = override.recurrenceId?.value ?: return null
        val offset = recurrenceOffset(oldGroup, splitTarget, oldValue) ?: return null
        val newValue = recurrenceValueAt(newGroup, offset) ?: return null
        return recurrenceIdFor(newGroup, Target(newValue))
    }

    /** Number of recurrence steps from [target] to [value], inclusive of target. */
    private fun recurrenceOffset(group: Group, target: Target, value: ICalDate): Int? {
        val iterator = group.master.getDateIterator(seriesTimeZone(group))
        var targetSeen = false
        var offset = 0
        repeat(MaxRecurrenceWalk) {
            if (!iterator.hasNext()) return null
            val generated = iterator.next()
            val generatedValue = if (isAllDay(group)) {
                generated.toInstant().atZone(seriesTimeZone(group).toZoneId()).toLocalDate().toDateOnly()
            } else {
                ICalDate(generated, true)
            }
            if (!targetSeen) {
                if (matchesTarget(group, generatedValue, target)) {
                    targetSeen = true
                    offset = 0
                    if (matchesTarget(group, value, target)) return offset
                }
            } else {
                offset++
                if (matchesTarget(group, generatedValue, Target(value))) return offset
            }
        }
        return null
    }

    /** Returns the [offset]-th slot in a new series, in the master's value frame. */
    private fun recurrenceValueAt(group: Group, offset: Int): ICalDate? {
        if (offset < 0) return null
        val iterator = group.master.getDateIterator(seriesTimeZone(group))
        var generated: Date? = null
        repeat(offset + 1) {
            if (!iterator.hasNext()) return null
            generated = iterator.next()
        }
        val date = generated ?: return null
        return if (isAllDay(group)) {
            date.toInstant().atZone(seriesTimeZone(group).toZoneId()).toLocalDate().toDateOnly()
        } else {
            valueInMasterFrame(group, ICalDate(date, true))
        }
    }

    private fun appendExdate(group: Group, target: Target): VEvent {
        val event = group.master.copy()
        val properties = group.master.getProperties(ExceptionDates::class.java)
        val alreadyExcluded = properties.any { property ->
            property.values.any { value ->
                if (isAllDay(group)) {
                    localDateOf(group, value) == localDateOf(group, target.value)
                } else {
                    matchesTarget(group, value, target)
                }
            }
        }
        if (alreadyExcluded) return event

        val targetValue = valueInMasterFrame(group, target.value)
        val property = properties.firstOrNull { it.values.firstOrNull()?.hasTime() == targetValue.hasTime() }
            ?.let(::ExceptionDates)
            ?: ExceptionDates().also {
                it.setParameters(parametersForDate(group, group.master.dateStart!!))
            }
        property.values.add(targetValue)
        event.removeProperties(ExceptionDates::class.java)
        event.addProperty(property)
        return event
    }

    private fun removeExdateForEdit(group: Group, target: Target): VEvent {
        val event = group.master.copy()
        val properties = group.master.getProperties(ExceptionDates::class.java)
        if (properties.isEmpty()) return event

        val exactExists = properties.any { property ->
            property.values.any { value -> matchesTarget(group, value, target) }
        }
        event.removeProperties(ExceptionDates::class.java)
        properties.forEach { original ->
            val copy = ExceptionDates(original)
            copy.values.removeAll { value ->
                matchesTarget(group, value, target) ||
                    (!exactExists && localDateOf(group, value) == localDateOf(group, target.value))
            }
            if (copy.values.isNotEmpty()) event.addProperty(copy)
        }
        return event
    }

    private fun recurrenceIdFor(group: Group, target: Target): RecurrenceId {
        val template = requireNotNull(group.master.dateStart)
        val property = RecurrenceId(valueInMasterFrame(group, target.value))
        property.setParameters(parametersForDate(group, template))
        property.removeParameter("RANGE")
        return property
    }

    private fun parametersForDate(group: Group, template: DateOrDateTimeProperty): ICalParameters =
        ICalParameters(template.parameters).also { parameters ->
            if (!template.value!!.hasTime()) {
                parameters.setValue(biweekly.ICalDataType.DATE)
            } else if (template.value!!.rawComponents?.isUtc != true) {
                seriesTimezoneId(group)?.let(parameters::setTimezoneId)
                parameters.removeAll("VALUE")
            } else {
                parameters.removeAll("TZID")
                parameters.removeAll("VALUE")
            }
        }

    private fun validateTarget(group: Group, target: Target) {
        val start = requireNotNull(group.master.dateStart?.value)
        require(start.hasTime() == target.value.hasTime()) {
            "The recurrence target must use the master's DTSTART value form"
        }
    }

    private fun validateReplacement(group: Group, replacement: VEvent) {
        val replacementStart = requireNotNull(replacement.dateStart?.value) {
            "The edited VEvent needs DTSTART"
        }
        val masterStart = requireNotNull(group.master.dateStart?.value)
        require(replacementStart.hasTime() == masterStart.hasTime()) {
            "The edited VEvent must keep the recurrence value form"
        }
    }

    private fun isFirstOccurrence(group: Group, target: Target): Boolean =
        if (isAllDay(group)) {
            !localDateOf(group, target.value).isAfter(localDateOf(group, group.master.dateStart!!.value))
        } else {
            target.value.time <= group.master.dateStart!!.value.time
        }

    private fun isBeforeTarget(group: Group, value: ICalDate, target: Target): Boolean =
        if (isAllDay(group)) {
            localDateOf(group, value).isBefore(localDateOf(group, target.value))
        } else {
            value.time < target.value.time
        }

    private fun matchesTarget(group: Group, value: ICalDate, target: Target): Boolean =
        if (isAllDay(group)) {
            localDateOf(group, value) == localDateOf(group, target.value)
        } else {
            value.time == target.value.time
        }

    private fun localDateOf(group: Group, value: ICalDate): LocalDate {
        if (!value.hasTime()) {
            value.rawComponents?.let { raw ->
                return LocalDate.of(raw.year, raw.month, raw.date)
            }
        }

        val raw = value.rawComponents
        val template = group.master.dateStart!!.value
        if (raw != null && !raw.isUtc && template.rawComponents?.isUtc != true) {
            return LocalDate.of(raw.year, raw.month, raw.date)
        }
        return Instant.ofEpochMilli(value.time)
            .atZone(seriesTimeZone(group).toZoneId())
            .toLocalDate()
    }

    /** Converts an absolute target to the master's local/TZID/UTC value frame. */
    private fun valueInMasterFrame(group: Group, value: ICalDate): ICalDate {
        val template = group.master.dateStart!!.value
        if (!template.hasTime()) return localDateOf(group, value).toDateOnly()

        val instant = Instant.ofEpochMilli(value.time)
        val timezone = seriesTimeZone(group).toZoneId()
        val local = instant.atZone(timezone).toLocalDateTime()
        return ICalDate(
            Date.from(instant),
            DateTimeComponents(
                local.year,
                local.monthValue,
                local.dayOfMonth,
                local.hour,
                local.minute,
                local.second,
                template.rawComponents?.isUtc == true,
            ),
            true,
        )
    }

    private fun isAllDay(group: Group): Boolean =
        group.master.dateStart!!.value.hasTime().not()

    private fun seriesTimeZone(group: Group): TimeZone {
        val property = group.master.dateStart!!
        ICalTimezones.timeZoneOf(group.calendar, property)?.let { return it }
        return if (property.value.rawComponents?.isUtc == true) {
            TimeZone.getTimeZone("UTC")
        } else {
            TimeZone.getDefault()
        }
    }

    private fun seriesTimezoneId(group: Group): String? {
        val property = group.master.dateStart!!
        if (property.value.rawComponents?.isUtc == true) return null
        return ICalTimezones.tzidOf(group.calendar, property)?.takeIf(String::isNotBlank)
    }

    private fun result(requested: Scope, effective: Scope, group: Group): Result =
        Result(requested, effective, listOf(group))

    private fun defaultSplitUid(group: Group, target: Target): String {
        val uid = requireNotNull(group.master.uid?.value)
        val suffix = if (isAllDay(group)) {
            localDateOf(group, target.value).toString().replace("-", "")
        } else {
            target.value.time.toString()
        }
        return "$uid-future-$suffix"
    }

    private fun LocalDate.toDateOnly(): ICalDate =
        ICalDate(
            DateTimeComponents(year, monthValue, dayOfMonth, 0, 0, 0, false),
            false,
        )

    private const val MaxRecurrenceWalk = 100_000
}
