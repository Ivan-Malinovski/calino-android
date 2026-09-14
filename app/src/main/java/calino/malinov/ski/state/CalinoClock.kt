package calino.malinov.ski.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay

/**
 * The app's idea of the present moment.
 *
 * Every "is this today", "jump to today" and current-time affordance reads this
 * rather than calling [LocalDateTime.now] at its own site. There were four
 * separate frozen copies of the date before, and the day rail's red line was a
 * hard-coded 11:20 that never moved -- so with a real account connected the app
 * opened on the real date while insisting today was in May 2026.
 */
data class CalinoNow(val today: LocalDate, val time: LocalTime) {
    val dateTime: LocalDateTime get() = today.atTime(time)

    /** How far through the day [time] is, for placing a current-time marker. */
    val hourOfDay: Float get() = time.hour + time.minute / 60f
}

/**
 * The frozen sample moment.
 *
 * With no account connected the app serves the May 2026 fixture data, and its
 * date/data contract is deliberately deterministic so that visual and gesture
 * behaviour is reproducible. The time matches where the day rail's marker has
 * always been drawn.
 */
val FixtureNow = CalinoNow(LocalDate.of(2026, 5, 18), LocalTime.of(11, 20))

val LocalCalinoNow = staticCompositionLocalOf { FixtureNow }

/**
 * Tracks the wall clock while [live], and holds [FixtureNow] otherwise.
 *
 * It re-reads on the minute rather than on a fixed interval, so the marker
 * lands on the minute boundary and the date turns over exactly at midnight
 * instead of up to an interval late.
 */
@Composable
fun rememberCalinoNow(live: Boolean): State<CalinoNow> {
    val state = remember { mutableStateOf(FixtureNow) }
    var value by state
    LaunchedEffect(live) {
        if (!live) {
            value = FixtureNow
            return@LaunchedEffect
        }
        while (true) {
            val now = LocalDateTime.now()
            value = CalinoNow(now.toLocalDate(), now.toLocalTime().truncatedTo(ChronoUnit.MINUTES))
            val nextMinute = now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
            delay(ChronoUnit.MILLIS.between(now, nextMinute).coerceAtLeast(1L))
        }
    }
    return state
}
