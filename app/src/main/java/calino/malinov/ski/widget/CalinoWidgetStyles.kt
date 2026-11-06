package calino.malinov.ski.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
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
import calino.malinov.ski.R
import calino.malinov.ski.util.CalinoTimeFormat

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

    /**
     * The row ceiling for the window.
     *
     * This bounds how much agenda is *built*, and it no longer depends on the
     * widget's size: the rows go into a `LazyColumn`, so how many are visible is
     * the launcher's business and the rest scroll. The ceiling is only there to
     * keep the RemoteViews payload sane on a busy month; past it the footer says
     * how many rows were left out. [Cards] takes two lines and a fill where
     * [Ledger] takes one line and a hairline, so it gets a lower ceiling for the
     * same reach.
     */
    val rowCeiling: Int get() = when (this) {
        Ledger -> 40
        Cards -> 30
    }

    /**
     * The gap between the widget's edge and its rows.
     *
     * One value for both, arrived at from opposite directions: the card
     * carries 9dp of its own and would float in a wider margin, and the
     * ledger's first column is small text that reads better close to the edge
     * than centred in whitespace.
     */
    val canvasPadding: Dp @Composable get() = 10.wdp

    /**
     * Extra start padding for the header, on top of [canvasPadding].
     *
     * The canvas is rounded at 20dp, and a heading set flush to the text inset
     * sits inside that curve rather than beside it, which reads as crowding the
     * corner. Both styles resolve to the same absolute inset so that a ledger
     * and a cards widget side by side start their headings on one line, even
     * though their rows do not.
     */
    val headerIndent: Dp @Composable get() = HeaderInset - canvasPadding

    private companion object {
        /** Clear of the canvas's 20dp corner, in both styles. */
        val HeaderInset: Dp @Composable get() = 20.wdp
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
                    .height(1.wdp)
                    .background(CalinoWidgetColors.line),
                contentAlignment = Alignment.Center,
            ) {}
        }
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 5.wdp)
                .clickable(onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.timeLabel ?: if (row.allDay) androidx.glance.LocalContext.current.getString(R.string.sys_all_day) else androidx.glance.LocalContext.current.getString(R.string.sys_due),
                // Fixed width plus end alignment is what a proportional font
                // gives instead of tabular figures: the column has one edge
                // even though "9:30" and "11:00" are different widths.
                modifier = GlanceModifier.width(timeColumnWidth(timeFormat)),
                style = TextStyle(
                    color = if (row.isNext) CalinoWidgetColors.accent else if (row.ended) CalinoWidgetColors.ink3 else CalinoWidgetColors.ink2,
                    fontSize = 10.5.widgetSp,
                    fontWeight = if (row.isNext) FontWeight.Medium else FontWeight.Normal,
                    textAlign = TextAlign.End,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.width(5.wdp))
            RowMarker(row)
            Spacer(GlanceModifier.width(5.wdp))
            Text(
                text = row.title,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = if (row.done || row.ended) CalinoWidgetColors.ink3 else CalinoWidgetColors.ink,
                    fontSize = 13.widgetSp,
                    fontWeight = if (row.isNext) FontWeight.Bold else FontWeight.Normal,
                    textDecoration = if (row.done) TextDecoration.LineThrough else TextDecoration.None,
                ),
                maxLines = 1,
            )
            // The location is the first thing to go: at a narrow width the
            // title is what the row is for, and a truncated street name helps
            // nobody. It gets a column of its own width rather than whatever
            // it asks for: a wrap-content sibling is measured before the
            // weighted title, so a long address used to take the row and leave
            // the title an ellipsis.
            if (wide && row.location != null) {
                Spacer(GlanceModifier.width(8.wdp))
                Text(
                    text = row.location,
                    modifier = GlanceModifier.width(96.wdp),
                    style = TextStyle(color = CalinoWidgetColors.ink3, fontSize = 11.widgetSp),
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
            .padding(bottom = 5.wdp),
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(CalinoWidgetColors.recordTint(row.color))
                .cornerRadius(11.wdp)
                .padding(horizontal = 9.wdp, vertical = 7.wdp)
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
                    .width(3.wdp)
                    .height((if (row.location != null) 26.wdp else 16.wdp))
                    .background(CalinoWidgetColors.record(row.color))
                    .cornerRadius(2.wdp),
                contentAlignment = Alignment.Center,
            ) {}
            Spacer(GlanceModifier.width(8.wdp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = row.title,
                    style = TextStyle(
                        color = if (row.done || row.ended) CalinoWidgetColors.ink3 else CalinoWidgetColors.ink,
                        fontSize = 12.5.widgetSp,
                        fontWeight = if (row.isNext) FontWeight.Bold else FontWeight.Medium,
                        textDecoration = if (row.done) TextDecoration.LineThrough else TextDecoration.None,
                    ),
                    maxLines = 1,
                )
                row.location?.let {
                    Text(
                        text = it,
                        style = TextStyle(color = CalinoWidgetColors.ink2, fontSize = 10.5.widgetSp),
                        maxLines = 1,
                    )
                }
            }
            Spacer(GlanceModifier.width(8.wdp))
            // A task's bare "Due" is dropped rather than set beside the
            // checkbox: the control already says the row is a task, and the
            // word adds nothing next to it. A real due *time* still shows.
            if (row.kind != WidgetRowKind.Task || row.timeLabel != null) {
                Text(
                    text = row.timeLabel ?: if (row.allDay) androidx.glance.LocalContext.current.getString(R.string.sys_all_day) else androidx.glance.LocalContext.current.getString(R.string.sys_due),
                    style = TextStyle(
                        color = if (row.isNext) CalinoWidgetColors.accent else if (row.ended) CalinoWidgetColors.ink3 else CalinoWidgetColors.ink2,
                        fontSize = 10.5.widgetSp,
                        fontWeight = if (row.isNext) FontWeight.Medium else FontWeight.Normal,
                        textAlign = TextAlign.End,
                    ),
                    maxLines = 1,
                )
            }
            if (row.kind == WidgetRowKind.Task) {
                if (row.timeLabel != null) Spacer(GlanceModifier.width(4.wdp))
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
                    .size(5.wdp)
                    .background(CalinoWidgetColors.record(row.color))
                    .cornerRadius(3.wdp),
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
            contentDescription = if (row.done) androidx.glance.LocalContext.current.getString(R.string.sys_reopen, row.title) else androidx.glance.LocalContext.current.getString(R.string.sys_complete, row.title),
            modifier = GlanceModifier.size(14.wdp),
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
private val MarkerSlot: Dp @Composable get() = 20.wdp

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
@Composable
private fun timeColumnWidth(format: CalinoTimeFormat): Dp =
    (if (format == CalinoTimeFormat.TwentyFourHour) 34.wdp else 46.wdp)

/**
 * How much the launcher will shrink this widget, undone in advance.
 *
 * Samsung's launcher hands a widget that has not opted out a larger virtual
 * canvas and then scales it down by `hsResizeRatio` (4/7 on a seven-column
 * home grid), so a widget drawn at its design size arrives at roughly 57 % of
 * it. Previews are not scaled, which is why the picker looked right and the
 * home screen did not. Every dimension and every font size goes through
 * [wdp] / [widgetSp] so the one factor, 1 / ratio, is applied uniformly and the
 * result lands at the design size. It is 1 wherever the launcher does not scale.
 */
internal val LocalWidgetScale = compositionLocalOf { 1f }

internal const val MaxWidgetScale = 2.5f

/** The compensation for a launcher's `hsResizeRatio`; 1 when absent or nonsensical. */
internal fun widgetScaleFor(hsResizeRatio: Float): Float =
    if (hsResizeRatio > 0f && hsResizeRatio < 1f) (1f / hsResizeRatio).coerceAtMost(MaxWidgetScale) else 1f

internal val Number.wdp: Dp
    @Composable get() = (toFloat() * LocalWidgetScale.current).dp

internal val Number.widgetSp: TextUnit
    @Composable get() = (toFloat() * LocalWidgetScale.current).sp
