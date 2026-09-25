package calino.malinov.ski.data.caldav

import biweekly.component.DaylightSavingsTime
import biweekly.component.Observance
import biweekly.component.StandardTime
import biweekly.component.VTimezone
import biweekly.property.RecurrenceDates
import biweekly.util.DayOfWeek as ICalDayOfWeek
import biweekly.util.Frequency
import biweekly.util.ICalDate
import biweekly.util.Recurrence
import biweekly.util.UtcOffset
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.zone.ZoneOffsetTransition
import java.time.zone.ZoneOffsetTransitionRule
import java.util.Date

/**
 * Builds an RFC 5545 `VTIMEZONE` for an IANA zone from the device's tzdb.
 *
 * Offline on purpose: no timezone server, no tzurl.org. Minified the way
 * DAVx5's ical4android does it -- only the observances needed from [from]
 * onward -- so a new event does not carry a zone's whole history.
 *
 * Shape:
 *  * the observance in effect at [from];
 *  * every fixed historical transition after it (tzdb's explicit list);
 *  * for a zone that still changes offset, one recurring STANDARD and one
 *    DAYLIGHT observance from tzdb's transition rules, so an open-ended series
 *    stays covered (RFC 5545 3.6.5).
 *
 * Observance DTSTARTs are local times in the offset *before* the onset, per
 * RFC 5545 3.6.5, which is exactly [ZoneOffsetTransition.getDateTimeBefore].
 */
object VTimezoneBuilder {

    /** Years a rule is checked over before it is written as a single RRULE. */
    private const val RuleCheckYears = 28

    /** Years spelled out as RDATEs when a rule has no RRULE form. */
    private const val RdateYears = 40

    fun build(zone: ZoneId, from: Instant, tzid: String = zone.id): VTimezone {
        val rules = zone.rules
        val vtimezone = VTimezone(tzid)
        val lastFixed = rules.transitions.lastOrNull()?.instant

        val previous = rules.previousTransition(from.plusSeconds(1))
        if (previous != null) {
            vtimezone.add(observance(previous.isDaylight(rules), previous))
        } else {
            val offset = rules.getOffset(from)
            vtimezone.add(
                fixedObservance(
                    daylight = rules.isDaylightSavings(from),
                    start = LocalDateTime.of(1970, 1, 1, 0, 0),
                    offsetFrom = offset,
                    offsetTo = offset,
                ),
            )
        }

        if (lastFixed != null && lastFixed > from) {
            var transition = rules.nextTransition(from)
            while (transition != null && transition.instant <= lastFixed) {
                vtimezone.add(observance(transition.isDaylight(rules), transition))
                transition = rules.nextTransition(transition.instant)
            }
        }

        val anchor = maxOf(from, lastFixed ?: from)
        rules.transitionRules.forEach { rule -> vtimezone.add(recurringObservance(rule, anchor, rules)) }
        return vtimezone
    }

    private fun VTimezone.add(observance: Observance) {
        when (observance) {
            is DaylightSavingsTime -> addDaylightSavingsTime(observance)
            is StandardTime -> addStandardTime(observance)
        }
    }

    private fun ZoneOffsetTransition.isDaylight(rules: java.time.zone.ZoneRules): Boolean =
        rules.isDaylightSavings(instant)

    private fun observance(daylight: Boolean, transition: ZoneOffsetTransition): Observance =
        fixedObservance(daylight, transition.dateTimeBefore, transition.offsetBefore, transition.offsetAfter)

    private fun fixedObservance(
        daylight: Boolean,
        start: LocalDateTime,
        offsetFrom: ZoneOffset,
        offsetTo: ZoneOffset,
    ): Observance = (if (daylight) DaylightSavingsTime() else StandardTime()).also {
        it.setDateStart(local(start))
        it.setTimezoneOffsetFrom(offsetFrom.toUtcOffset())
        it.setTimezoneOffsetTo(offsetTo.toUtcOffset())
    }

    /**
     * One observance for a tzdb rule, as an RRULE when a single yearly rule
     * reproduces every onset over [RuleCheckYears], otherwise as RDATEs.
     */
    private fun recurringObservance(
        rule: ZoneOffsetTransitionRule,
        anchor: Instant,
        rules: java.time.zone.ZoneRules,
    ): Observance {
        var year = anchor.atOffset(ZoneOffset.UTC).year
        var first = rule.createTransition(year)
        while (first.instant <= anchor) first = rule.createTransition(++year)
        val onsets = (0 until RdateYears).map { rule.createTransition(year + it).dateTimeBefore }
        val observance = fixedObservance(
            daylight = rules.isDaylightSavings(first.instant),
            start = first.dateTimeBefore,
            offsetFrom = first.offsetBefore,
            offsetTo = first.offsetAfter,
        )
        val recurrence = yearlyRule(onsets.take(RuleCheckYears))
        if (recurrence != null) {
            observance.setRecurrenceRule(recurrence)
        } else {
            onsets.drop(1).forEach { onset ->
                observance.addRecurrenceDates(RecurrenceDates().also { it.dates.add(local(onset)) })
            }
        }
        return observance
    }

    /**
     * The `FREQ=YEARLY` rule matching every onset, or null.
     *
     * Candidates are derived from the first onset's actual date rather than
     * from the tzdb rule's fields, so a `24:00` rule (which lands on the
     * following day) or a UTC/standard time definition cannot produce an
     * RRULE that fires on the wrong day.
     */
    private fun yearlyRule(onsets: List<LocalDateTime>): Recurrence? {
        val first = onsets.first()
        if (onsets.any { it.monthValue != first.monthValue || it.toLocalTime() != first.toLocalTime() }) return null
        val weekday = first.dayOfWeek.toICal()
        val lengthOfMonth = first.toLocalDate().lengthOfMonth()
        val nth = (first.dayOfMonth - 1) / 7 + 1
        val fromEnd = -((lengthOfMonth - first.dayOfMonth) / 7 + 1)
        val candidates = listOf(
            { b: Recurrence.Builder -> b.byDay(nth, weekday) } to { d: LocalDateTime ->
                d.dayOfWeek == first.dayOfWeek && (d.dayOfMonth - 1) / 7 + 1 == nth
            },
            { b: Recurrence.Builder -> b.byDay(fromEnd, weekday) } to { d: LocalDateTime ->
                d.dayOfWeek == first.dayOfWeek &&
                    -((d.toLocalDate().lengthOfMonth() - d.dayOfMonth) / 7 + 1) == fromEnd
            },
            { b: Recurrence.Builder -> b.byMonthDay(first.dayOfMonth) } to { d: LocalDateTime ->
                d.dayOfMonth == first.dayOfMonth
            },
        )
        val (apply, _) = candidates.firstOrNull { (_, matches) -> onsets.all(matches) } ?: return null
        val builder = Recurrence.Builder(Frequency.YEARLY).byMonth(first.monthValue)
        apply(builder)
        return builder.build()
    }

    private fun java.time.DayOfWeek.toICal(): ICalDayOfWeek = when (this) {
        java.time.DayOfWeek.MONDAY -> ICalDayOfWeek.MONDAY
        java.time.DayOfWeek.TUESDAY -> ICalDayOfWeek.TUESDAY
        java.time.DayOfWeek.WEDNESDAY -> ICalDayOfWeek.WEDNESDAY
        java.time.DayOfWeek.THURSDAY -> ICalDayOfWeek.THURSDAY
        java.time.DayOfWeek.FRIDAY -> ICalDayOfWeek.FRIDAY
        java.time.DayOfWeek.SATURDAY -> ICalDayOfWeek.SATURDAY
        java.time.DayOfWeek.SUNDAY -> ICalDayOfWeek.SUNDAY
    }

    private fun ZoneOffset.toUtcOffset(): UtcOffset = UtcOffset(totalSeconds * 1000L)

    /**
     * A floating local date-time, as observance DTSTART and RDATE require.
     * The [Date] part is only a carrier; biweekly writes the raw components.
     */
    private fun local(value: LocalDateTime): ICalDate = ICalDate(
        Date.from(value.toInstant(ZoneOffset.UTC)),
        biweekly.util.DateTimeComponents(
            value.year, value.monthValue, value.dayOfMonth,
            value.hour, value.minute, value.second, false,
        ),
        true,
    )
}
