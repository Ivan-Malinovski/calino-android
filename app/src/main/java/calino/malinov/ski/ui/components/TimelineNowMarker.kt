package calino.malinov.ski.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import calino.malinov.ski.R
import calino.malinov.ski.design.CalinoColors
import calino.malinov.ski.state.LocalCalinoNow
import calino.malinov.ski.util.LocalCalinoLocale
import calino.malinov.ski.state.LocalTimeFormat
import java.time.LocalDate

/**
 * The current-time marker: a rose line with a dot at the start of the rail,
 * drawn only on today's column.
 *
 * It is one composable because the day timeline and Range both draw the same
 * hour rail, and the web shows this line in both of its time views. The
 * position includes the minutes, not just the hour: a line that only moves once
 * an hour reads as broken to anyone watching it.
 */
@Composable
fun TimelineNowMarker(
    day: LocalDate,
    hourHeight: Dp,
    railStart: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val now = LocalCalinoNow.current
    if (day != now.today) return
    val timeFormat = LocalTimeFormat
    val description = stringResource(
        R.string.cal_current_time,
        timeFormat.format(now.time, LocalCalinoLocale),
    )
    // Read outside the draw lambda: the palette accessor is composable.
    val rose = CalinoColors.Rose
    Canvas(
        modifier
            .fillMaxWidth()
            .offset(y = hourHeight * now.hourOfDay)
            .height(8.dp)
            .semantics { contentDescription = description },
    ) {
        val start = railStart.toPx()
        val centre = Offset(start + 4.dp.toPx(), 4.dp.toPx())
        drawLine(
            rose,
            Offset(start, 4.dp.toPx()),
            Offset(size.width, 4.dp.toPx()),
            strokeWidth = 1.5f,
        )
        drawCircle(rose, 4.dp.toPx(), centre)
    }
}
