package calino.malinov.ski.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.design.CalinoTypography
import calino.malinov.ski.state.LocalTimeFormat
import calino.malinov.ski.util.formatCalinoDuration
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private val HeroDayFormat = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.US)
private val HeroAllDayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.US)
private val HeroMonthFormat = DateTimeFormatter.ofPattern("MMM", Locale.US)

/**
 * The when-block, given the weight the thing it says deserves.
 *
 * An event is mostly its time, and a metadata row spelling "Time: 14:00 – 15:00"
 * in body text gives that the same voice as a reminder offset. Here the clock
 * face is the display numeral and everything around it -- the day, the span --
 * is the quiet part, which is also the reading order someone glancing at the
 * card actually wants.
 *
 * One composable serves the detail card and the editor, because two surfaces
 * that state the same fact in different type is exactly how the old date/time
 * row and the editor's start/end columns drifted apart.
 *
 * Every handler is nullable: a null one leaves that piece as plain text rather
 * than an inert button, so a read-only host shows no affordance it cannot
 * honour.
 */
@Composable
fun WhenHero(
    startDate: LocalDate,
    startTime: LocalTime?,
    endDate: LocalDate?,
    endTime: LocalTime?,
    accent: Color,
    // Stated rather than inferred from a null start time: an event being
    // written has no time yet and is not all-day, and inferring it left a new
    // event showing the all-day block with no way to give it a time.
    allDay: Boolean,
    modifier: Modifier = Modifier,
    onStartDate: (() -> Unit)? = null,
    onStartTime: (() -> Unit)? = null,
    onEndDate: (() -> Unit)? = null,
    onEndTime: (() -> Unit)? = null,
    onAllDay: (() -> Unit)? = null,
) {
    if (allDay) {
        AllDayHero(startDate, endDate, accent, modifier, onStartDate, onAllDay)
    } else {
        TimedHero(startDate, startTime, endDate, endTime, accent, modifier, onStartDate, onStartTime, onEndDate, onEndTime, onAllDay)
    }
}

@Composable
private fun TimedHero(
    startDate: LocalDate,
    startTime: LocalTime?,
    endDate: LocalDate?,
    endTime: LocalTime?,
    accent: Color,
    modifier: Modifier,
    onStartDate: (() -> Unit)?,
    onStartTime: (() -> Unit)?,
    onEndDate: (() -> Unit)?,
    onEndTime: (() -> Unit)?,
    onAllDay: (() -> Unit)?,
) {
    val finish = endDate ?: startDate
    // Both faces are hung from the top rather than centred, so a day line that
    // wraps on one side cannot slide the numerals out of line with each other.
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        HeroClock(
            time = startTime,
            date = startDate,
            align = Alignment.Start,
            label = "Start",
            placeholder = "Add time",
            onTime = onStartTime,
            onDate = onStartDate,
            modifier = Modifier.weight(1f),
        )
        SpanRule(
            label = startTime?.let { from ->
                endTime?.let { formatCalinoDuration(spanMinutes(startDate, from, finish, it)) }
            } ?: "—",
            accent = accent,
            onAllDay = onAllDay,
        )
        HeroClock(
            time = endTime,
            date = finish,
            align = Alignment.End,
            label = "End",
            onTime = onEndTime,
            onDate = onEndDate,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The span in minutes, taking a midnight rollover into account: an end time
 * earlier than the start on the same day is tomorrow, not a negative duration.
 */
private fun spanMinutes(startDate: LocalDate, start: LocalTime, endDate: LocalDate, end: LocalTime): Int {
    val minutes = Duration.between(startDate.atTime(start), endDate.atTime(end)).toMinutes()
    return (if (minutes < 0) minutes + Duration.ofDays(1).toMinutes() else minutes).toInt()
}

@Composable
private fun RowScope.HeroClock(
    time: LocalTime?,
    date: LocalDate?,
    align: Alignment.Horizontal,
    label: String,
    onTime: (() -> Unit)?,
    onDate: (() -> Unit)?,
    modifier: Modifier,
    placeholder: String = "—",
) {
    val format = LocalTimeFormat
    // The 12-hour clock's meridiem is not part of the numeral: at display size
    // it doubles the column's width and pulls the two faces out of alignment.
    val text = time?.let { format.format(it) } ?: placeholder
    val split = if (time == null) -1 else text.lastIndexOf(' ')
    val numerals = if (split > 0) text.take(split) else text
    val meridiem = if (split > 0) text.drop(split + 1) else null
    Column(modifier, horizontalAlignment = align) {
        Row(
            Modifier
                .heightIn(min = 40.dp)
                .then(if (onTime != null) Modifier.clickable(role = Role.Button, onClick = onTime) else Modifier)
                .semantics { contentDescription = "$label time, $text" },
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                numerals,
                style = if (time == null) {
                    CalinoTypography.displayMedium.copy(fontSize = 22.sp, lineHeight = 40.sp)
                } else {
                    CalinoTypography.displayLarge.copy(fontSize = 32.sp, lineHeight = 40.sp)
                },
                color = if (time == null) CalinoColors.Ink3 else CalinoColors.Ink,
                maxLines = 1,
                softWrap = false,
            )
            meridiem?.let {
                Text(
                    it,
                    style = CalinoTypography.labelSmall,
                    color = CalinoColors.Ink2,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
            }
        }
        date?.let {
            Text(
                it.format(HeroDayFormat).uppercase(Locale.US),
                style = CalinoTypography.labelSmall,
                color = CalinoColors.Ink3,
                textAlign = if (align == Alignment.End) TextAlign.End else TextAlign.Start,
                modifier = Modifier
                    .heightIn(min = 28.dp)
                    .then(if (onDate != null) Modifier.clickable(role = Role.Button, onClick = onDate) else Modifier)
                    .padding(top = 2.dp)
                    .semantics { contentDescription = "$label date, ${it.format(HeroDayFormat)}" },
            )
        }
    }
}

/**
 * The bridge between the two anchors: a label over a hairline that spans the
 * gap the same way the event spans the day. It is also where the all-day
 * switch lives, so the timed and all-day heroes have the same silhouette and
 * the toggle never costs a metadata row of its own.
 */
@Composable
private fun SpanRule(
    label: String,
    accent: Color,
    onAllDay: (() -> Unit)?,
    modifier: Modifier = Modifier,
    action: String = "Make all day",
    // Nudges the rule down against faces that are hung from the top. A row
    // that centres its anchors instead needs none, or the rule drifts below
    // them and lands on the block's bottom edge.
    topPadding: Dp = 12.dp,
) {
    Column(
        modifier
            .width(72.dp)
            .then(if (onAllDay != null) Modifier.clickable(role = Role.Button, onClick = onAllDay) else Modifier)
            .semantics { contentDescription = if (onAllDay != null) action else "Duration, $label" }
            .padding(top = topPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = label,
            style = CalinoTypography.bodySmall,
            color = CalinoColors.Ink2,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.fillMaxWidth().height(7.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 3.dp).height(1.dp).background(CalinoColors.Ink.copy(alpha = .18f)))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.size(7.dp).background(accent, CircleShape))
                Box(Modifier.size(7.dp).background(accent, CircleShape))
            }
        }
    }
}

/**
 * A day, in the same register the clock faces use: the day of the month as the
 * numeral and the rest of the date in the quiet voice beside it.
 *
 * Setting the date in the title's own serif, one size larger than the title,
 * is what made the all-day card read as being about the day rather than about
 * the event. A numeral does not compete with a name.
 */
@Composable
private fun DayAnchor(
    date: LocalDate,
    label: String,
    onDate: (() -> Unit)?,
    modifier: Modifier = Modifier,
    // The closing anchor hugs the measure's right edge the way the closing
    // clock face does; left-aligned in its own half, it stopped short of the
    // edge every other element on the card lines up with.
    align: Alignment.Horizontal = Alignment.Start,
) {
    Row(
        modifier
            .then(if (onDate != null) Modifier.clickable(role = Role.Button, onClick = onDate) else Modifier)
            .semantics { contentDescription = "$label date, ${date.format(HeroAllDayFormat)}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (align == Alignment.End) {
            Arrangement.spacedBy(11.dp, Alignment.End)
        } else {
            Arrangement.spacedBy(11.dp)
        },
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = CalinoTypography.displayLarge.copy(fontSize = 32.sp, lineHeight = 40.sp),
            color = CalinoColors.Ink,
            maxLines = 1,
            softWrap = false,
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            // Short forms, and no year: the same vocabulary the timed hero's
            // day line uses, and the only one that fits two anchors and a rule
            // inside the card's text measure without breaking a word in half.
            Text(
                date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US).uppercase(Locale.US),
                style = CalinoTypography.labelSmall,
                color = CalinoColors.Ink2,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                date.format(HeroMonthFormat).uppercase(Locale.US),
                style = CalinoTypography.labelSmall,
                color = CalinoColors.Ink3,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun AllDayHero(
    startDate: LocalDate,
    endDate: LocalDate?,
    accent: Color,
    modifier: Modifier,
    onStartDate: (() -> Unit)?,
    onAllDay: (() -> Unit)?,
) {
    val finish = endDate?.takeIf { it != startDate }
    if (finish != null) {
        // A span takes the timed hero's silhouette exactly -- anchor, rule,
        // anchor -- so the two cards read as one component in two states.
        val days = ChronoUnit.DAYS.between(startDate, finish).toInt() + 1
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DayAnchor(startDate, "Start", onStartDate, Modifier.weight(1f))
            // Both day anchors lead with their numeral, so the rule needs its
            // own gutter; the timed row gets this for free from the end face
            // being right-aligned.
            SpanRule(
                "$days days",
                accent,
                onAllDay,
                modifier = Modifier.padding(horizontal = 12.dp),
                action = "All day, give it a time",
                topPadding = 0.dp,
            )
            DayAnchor(finish, "End", null, Modifier.weight(1f), align = Alignment.End)
        }
    } else {
        // One day has nothing to put in a second column, so it does not pretend
        // to: the cluster stays left and the all-day tag rides the numeral's
        // own line rather than stacking another mono kicker under the title.
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DayAnchor(startDate, "Start", onStartDate)
            if (onAllDay != null) {
                Row(
                    Modifier
                        .padding(start = 16.dp)
                        .heightIn(min = 44.dp)
                        .clickable(role = Role.Button, onClick = onAllDay)
                        .semantics { contentDescription = "All day, give it a time" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Box(Modifier.size(5.dp).background(accent, CircleShape))
                    Text("ALL DAY", style = CalinoTypography.labelSmall, color = CalinoColors.Ink3)
                }
            }
        }
    }
}

/** The tint block a hero sits on, so the card and the editor share one shell. */
@Composable
fun HeroMasthead(
    tint: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(tint)
            // Starts on the metadata rows' text rather than their icon column:
            // 18dp of row inset, the 22dp icon and the 16dp gap after it. The
            // icons are a gutter, and a title hanging in it reads as indented
            // from the card rather than aligned to anything.
            .padding(horizontal = 56.dp)
            .padding(top = 4.dp, bottom = 8.dp),
    ) { content() }
}
