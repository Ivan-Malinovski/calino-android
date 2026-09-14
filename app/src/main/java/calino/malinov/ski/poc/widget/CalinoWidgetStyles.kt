package calino.malinov.ski.poc.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.ColorFilter
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import calino.malinov.ski.poc.R
import calino.malinov.ski.poc.util.CalinoTimeFormat

/**
 * The two shapes an agenda row can take.
 *
 * They are separate widgets rather than a setting, so the launcher's picker is
 * where the choice is made and both can sit on the same home screen. Everything
 * *above* the row -- which records appear, in what order, how many fit -- is
 * shared: see [CalinoAgendaWidget], which is the same shell for both. Only the
 * row, and how many of them a given size affords, differ here.
 */
internal enum class WidgetStyle {

    /**
     * A printed day sheet. A right-aligned time column so the eye scans one
     * edge, a hairline between rows instead of a bar beside each one, and
     * colour demoted to a dot -- seven calendars beside seven bars reads as a
     * paint chart rather than as a day.
     */
    Ledger,

    /**
     * The app's own event pills, on the home screen. Each row is a chip filled
     * with a whisper of its record's colour, which buys a second line for the
     * location at the cost of roughly half the rows.
     */
    Cards,
    ;

    /** Today plus the next `dayCount - 1` days at [size]. */
    fun dayCountFor(size: DpSize): Int = when {
        size.height < MediumHeight -> 1
        size.height < LargeHeight -> 3
        else -> 5
    }

    /**
     * The row ceiling at [size].
     *
     * This bounds how much agenda is *built*, not what fits: the rows go into
     * a `LazyColumn`, so anything past the widget's edge scrolls rather than
     * being lost. [Cards] takes two lines and a fill where [Ledger] takes one
     * line and a hairline, so it wants a lower ceiling for the same reach --
     * roughly two thirds the days' worth of rows in the same box.
     */
    fun maxRowsFor(size: DpSize): Int = when (this) {
        Ledger -> when {
            size.height < MediumHeight -> 3
            size.height < LargeHeight -> 8
            else -> 16
        }
        Cards -> when {
            size.height < MediumHeight -> 2
            size.height < LargeHeight -> 5
            else -> 10
        }
    }

    /**
     * The gap between the widget's edge and its rows.
     *
     * One value for both, arrived at from opposite directions: the card
     * carries 9dp of its own and would float in a wider margin, and the
     * ledger's first column is small text that reads better close to the edge
     * than centred in whitespace.
     */
    val canvasPadding: Dp get() = 10.dp

    /**
     * Extra start padding for the header, on top of [canvasPadding].
     *
     * The canvas is rounded at 20dp, and a heading set flush to the text inset
     * sits inside that curve rather than beside it, which reads as crowding the
     * corner. Both styles resolve to the same absolute inset so that a ledger
     * and a cards widget side by side start their headings on one line, even
     * though their rows do not.
     */
    val headerIndent: Dp get() = HeaderInset - canvasPadding

    private companion object {
        val MediumHeight = 180.dp
        val LargeHeight = 280.dp

        /** Clear of the canvas's 20dp corner, in both styles. */
        val HeaderInset = 20.dp
    }
}

/**
 * One ledger row.
 *
 * [ruled] draws the hairline above; the first row in a block passes false, so a
 * rule never sits directly under a day heading where it would read as an
 * underline rather than as a separator.
 */
@Composable
internal fun LedgerRow(
    row: WidgetAgendaRow,
    ruled: Boolean,
    wide: Boolean,
    timeFormat: CalinoTimeFormat,
    onClick: Action,
) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        if (ruled) {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(CalinoWidgetColors.line),
                contentAlignment = Alignment.Center,
            ) {}
        }
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
                .clickable(onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.timeLabel ?: if (row.allDay) "All day" else "Due",
                // Fixed width plus end alignment is what a proportional font
                // gives instead of tabular figures: the column has one edge
                // even though "9:30" and "11:00" are different widths.
                modifier = GlanceModifier.width(timeColumnWidth(timeFormat)),
                style = TextStyle(
                    color = if (row.isNext) CalinoWidgetColors.accent else CalinoWidgetColors.ink2,
                    fontSize = 10.5.sp,
                    fontWeight = if (row.isNext) FontWeight.Medium else FontWeight.Normal,
                    textAlign = TextAlign.End,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.width(5.dp))
            RowMarker(row)
            Spacer(GlanceModifier.width(5.dp))
            Text(
                text = row.title,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = if (row.done) CalinoWidgetColors.ink3 else CalinoWidgetColors.ink,
                    fontSize = 13.sp,
                    fontWeight = if (row.isNext) FontWeight.Bold else FontWeight.Normal,
                    textDecoration = if (row.done) TextDecoration.LineThrough else TextDecoration.None,
                ),
                maxLines = 1,
            )
            // The location is the first thing to go: at a narrow width the
            // title is what the row is for, and a truncated street name helps
            // nobody.
            if (wide && row.location != null) {
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    text = row.location,
                    style = TextStyle(color = CalinoWidgetColors.ink3, fontSize = 11.sp),
                    maxLines = 1,
                )
            }
        }
    }
}

/** One card. */
@Composable
internal fun CardRow(row: WidgetAgendaRow, onClick: Action) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(bottom = 5.dp),
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(CalinoWidgetColors.recordTint(row.color))
                .cornerRadius(11.dp)
                .padding(horizontal = 9.dp, vertical = 7.dp)
                .clickable(onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Full-strength beside its own tint: the fill says which calendar
            // at a glance, the rail is what makes it legible when two records
            // in neighbouring hues sit next to each other. Every row keeps it,
            // tasks included, so that one card's title starts where the next
            // one's does.
            Box(
                modifier = GlanceModifier
                    .width(3.dp)
                    .height(if (row.location != null) 26.dp else 16.dp)
                    .background(CalinoWidgetColors.record(row.color))
                    .cornerRadius(2.dp),
                contentAlignment = Alignment.Center,
            ) {}
            Spacer(GlanceModifier.width(8.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = row.title,
                    style = TextStyle(
                        color = if (row.done) CalinoWidgetColors.ink3 else CalinoWidgetColors.ink,
                        fontSize = 12.5.sp,
                        fontWeight = if (row.isNext) FontWeight.Bold else FontWeight.Medium,
                        textDecoration = if (row.done) TextDecoration.LineThrough else TextDecoration.None,
                    ),
                    maxLines = 1,
                )
                row.location?.let {
                    Text(
                        text = it,
                        style = TextStyle(color = CalinoWidgetColors.ink2, fontSize = 10.5.sp),
                        maxLines = 1,
                    )
                }
            }
            Spacer(GlanceModifier.width(8.dp))
            // A task's bare "Due" is dropped rather than set beside the
            // checkbox: the control already says the row is a task, and the
            // word adds nothing next to it. A real due *time* still shows.
            if (row.kind != WidgetRowKind.Task || row.timeLabel != null) {
                Text(
                    text = row.timeLabel ?: if (row.allDay) "All day" else "Due",
                    style = TextStyle(
                        color = if (row.isNext) CalinoWidgetColors.accent else CalinoWidgetColors.ink2,
                        fontSize = 10.5.sp,
                        fontWeight = if (row.isNext) FontWeight.Medium else FontWeight.Normal,
                        textAlign = TextAlign.End,
                    ),
                    maxLines = 1,
                )
            }
            if (row.kind == WidgetRowKind.Task) {
                if (row.timeLabel != null) Spacer(GlanceModifier.width(4.dp))
                TaskMarker(row)
            }
        }
    }
}

/**
 * An event's dot or a task's checkbox, in one slot so that titles start at the
 * same place either way.
 *
 * The slot is what makes the checkbox tappable: the mark itself is small, and
 * a 5dp target is not a control. Padding inside the slot turns the whole thing
 * into the hit area without moving the title.
 */
@Composable
private fun RowMarker(row: WidgetAgendaRow) {
    if (row.kind == WidgetRowKind.Task) {
        TaskMarker(row)
    } else {
        Box(
            modifier = GlanceModifier.size(MarkerSlot),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = GlanceModifier
                    .size(5.dp)
                    .background(CalinoWidgetColors.record(row.color))
                    .cornerRadius(3.dp),
                contentAlignment = Alignment.Center,
            ) {}
        }
    }
}

/**
 * A task's checkbox. Tapping it completes or reopens the task.
 *
 * It takes the record's colour while open and drops to [CalinoWidgetColors.ink3]
 * once done, so a finished row recedes as a whole rather than keeping a bright
 * mark beside struck-through text.
 */
@Composable
internal fun TaskMarker(row: WidgetAgendaRow) {
    Box(
        modifier = GlanceModifier
            .size(MarkerSlot)
            .clickable(toggleTask(row)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(
                if (row.done) R.drawable.ic_widget_task_done else R.drawable.ic_widget_task_open,
            ),
            contentDescription = if (row.done) "Reopen ${row.title}" else "Complete ${row.title}",
            modifier = GlanceModifier.size(14.dp),
            colorFilter = ColorFilter.tint(
                if (row.done) CalinoWidgetColors.ink3 else CalinoWidgetColors.record(row.color),
            ),
        )
    }
}

/**
 * The marker's slot, which is also its tap target.
 *
 * Under the 48dp the guidelines ask for, and knowingly: this sits in a row
 * whose other half opens the task, so the slot has to stay a marker's width or
 * the layout becomes a checkbox with a title attached. The row's own height
 * carries the rest of the target.
 */
private val MarkerSlot = 20.dp

/**
 * How wide the ledger's time column has to be.
 *
 * Measured rather than estimated, at 10.5sp on the platform font: "12:30 AM"
 * is 44.2dp, and on a 24-hour clock the widest label is not a time at all but
 * an overdue task's "Aug 30" at 32.4dp, with "All day" just behind it at 31.2
 * and "08:00" only 25.5. Hence 46 and 34, each a little over its widest label
 * and nothing more.
 *
 * The column is end-aligned, so every dp of slack here arrives as dead space on
 * the *left* and pushes the whole row away from the edge. That is worth being
 * exact about: it was the single biggest source of the ledger's indent.
 */
private fun timeColumnWidth(format: CalinoTimeFormat): Dp =
    if (format == CalinoTimeFormat.TwentyFourHour) 34.dp else 46.dp
