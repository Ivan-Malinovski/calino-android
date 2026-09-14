package calino.malinov.ski.poc.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
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
     * [Cards] takes less: the chip carries 9dp of its own, and stacking both
     * would leave the fills floating in a margin wide enough to read as a
     * mistake. The ledger has no fill, so its text needs the full inset.
     */
    val canvasPadding: Dp get() = if (this == Cards) 10.dp else 14.dp

    private companion object {
        val MediumHeight = 180.dp
        val LargeHeight = 280.dp
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
                modifier = GlanceModifier.width(TimeColumnWidth),
                style = TextStyle(
                    color = if (row.isNext) CalinoWidgetColors.accent else CalinoWidgetColors.ink2,
                    fontSize = 10.5.sp,
                    fontWeight = if (row.isNext) FontWeight.Medium else FontWeight.Normal,
                    textAlign = TextAlign.End,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.width(8.dp))
            Box(
                modifier = GlanceModifier
                    .size(5.dp)
                    .background(CalinoWidgetColors.record(row.color))
                    .cornerRadius(3.dp),
                contentAlignment = Alignment.Center,
            ) {}
            Spacer(GlanceModifier.width(8.dp))
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
            // in neighbouring hues sit next to each other.
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
    }
}

/** The ledger's time column. Sized for "12:30 AM", the longest label it takes. */
private val TimeColumnWidth = 52.dp
