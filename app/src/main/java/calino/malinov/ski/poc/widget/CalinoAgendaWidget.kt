package calino.malinov.ski.poc.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import calino.malinov.ski.poc.MainActivity
import calino.malinov.ski.poc.data.CalinoContainer
import calino.malinov.ski.poc.data.repository.CalinoSnapshot
import calino.malinov.ski.poc.notify.AgendaDeepLinks
import calino.malinov.ski.poc.notify.ReminderDeepLink
import calino.malinov.ski.poc.notify.ReminderDeepLinks
import calino.malinov.ski.poc.notify.ReminderKind
import calino.malinov.ski.poc.util.CalinoTimeFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The home screen agenda: the shell both widgets share.
 *
 * [CalinoLedgerWidget] and [CalinoCardsWidget] are the two the launcher offers.
 * They differ only in [WidgetStyle] -- the row, and how many rows a size
 * affords. Everything else is deliberately one implementation: two widgets that
 * disagreed about which records are on today would be worse than one widget.
 *
 * Two things shape this file. First, it renders in whatever process the
 * launcher wakes -- frequently one with no Activity -- so it reads the disk
 * cache through [CalinoContainer.ensureCachedData] and never puts that process
 * on the network. Second, Glance composables are not reachable from the
 * plain-JUnit suite, so every decision about *what* to show was already made in
 * [WidgetAgendaBuilder]; what is left here is layout.
 */
abstract class CalinoAgendaWidget internal constructor(
    private val style: WidgetStyle,
) : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(SmallSize, MediumSize, LargeSize),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = CalinoContainer.get(context)
        val connected = container.hasAccounts

        // Fixture mode renders the prompt rather than the sample agenda: the
        // fixtures live around May 2026, so a real "today" against them is
        // permanently empty and reads as a broken widget. Reminders decline to
        // schedule in fixture mode for the same reason.
        if (connected) {
            container.ensureCachedData()
            // Only to make the *first* frame correct; from then on the
            // composition below reads the repository itself.
            awaitCachedSnapshot(container)
        }

        val preferences = container.preferenceStore
        val options = WidgetAgendaOptions(
            hideCompletedTasks = preferences.loadHideCompletedTasks(),
            showLocations = preferences.loadShowLocations(),
            timeFormat = preferences.loadTimeFormat(),
            // Read, and gating nothing today: the widget shows events and
            // tasks only. See HANDOFF.md before deciding this is dead weight.
            journalEnabled = preferences.loadJournalEnabled(),
            contactsEnabled = preferences.loadContactsEnabled(),
        )

        provideContent {
            Body(if (connected) currentSnapshot(container) else null, options)
        }
    }

    /**
     * The repository's snapshot, read *inside* the composition.
     *
     * This is the one thing about Glance that is easy to get wrong here.
     * `provideGlance` runs once per **session**, not once per update: an
     * `updateAll` recomposes the content lambda that was already produced. A
     * snapshot captured before `provideContent` is therefore frozen for the
     * life of that session, and the widget silently keeps showing the agenda it
     * was born with -- it renders, it just never changes, which looks like a
     * broken bridge rather than a stale capture. Observing from the composition
     * fixes both halves: a recomposition re-reads, and while the session is
     * alive the widget follows the repository without waiting to be told.
     */
    @Composable
    private fun currentSnapshot(container: CalinoContainer): CalinoSnapshot {
        val repository = container.activeRepository
        var snapshot by remember(repository) { mutableStateOf(repository.snapshot()) }
        DisposableEffect(repository) {
            val handle = repository.observe { snapshot = it }
            onDispose { handle.close() }
        }
        return snapshot
    }

    @Composable
    private fun Body(snapshot: CalinoSnapshot?, options: WidgetAgendaOptions) {
        val size = LocalSize.current
        val today = WidgetClock.today
        val shaped = options.copy(
            dayCount = style.dayCountFor(size),
            maxRows = style.maxRowsFor(size),
        )
        // Read straight from the clock, not from observable state. The mark is
        // worth no more than the render it was drawn in: the widget redraws on
        // a sync, an edit and the date roll, and there is no broadcast for "a
        // minute passed" that a manifest receiver may declare. So the bold row
        // is correct when something happened and drifts quietly when nothing
        // did, which is the honest trade -- the alternative is a periodic
        // wake-up that the backlog rules out for exactly this reason.
        val agenda = snapshot?.let {
            WidgetAgendaBuilder.build(it, today, shaped, now = LocalTime.now())
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(CalinoWidgetColors.canvas)
                .cornerRadius(20.dp)
                .padding(horizontal = style.canvasPadding, vertical = 12.dp)
                .clickable(openAgenda(today)),
        ) {
            Header(today)
            Spacer(GlanceModifier.height(8.dp))
            when {
                agenda == null -> Notice("No calendar connected", "Tap to set one up")
                agenda.empty -> Notice(WidgetNothingScheduled, null)
                else -> Agenda(agenda, today, size)
            }
        }
    }

    @Composable
    private fun Header(today: LocalDate) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Today",
                style = TextStyle(
                    color = CalinoWidgetColors.ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = today.format(HeaderFormat),
                style = TextStyle(color = CalinoWidgetColors.ink3, fontSize = 12.sp),
            )
        }
    }

    @Composable
    private fun Notice(title: String, subtitle: String?) {
        Box(
            modifier = GlanceModifier.fillMaxSize(),
            contentAlignment = Alignment.TopStart,
        ) {
            Column {
                Text(
                    text = title,
                    style = TextStyle(color = CalinoWidgetColors.ink2, fontSize = 13.sp),
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = TextStyle(color = CalinoWidgetColors.ink3, fontSize = 12.sp),
                    )
                }
            }
        }
    }

    @Composable
    private fun Agenda(agenda: WidgetAgenda, today: LocalDate, size: DpSize) {
        // Flattened before the list so a day heading and its rows scroll as one
        // stream; LazyColumn has no section concept to lean on.
        val entries = buildList {
            agenda.days.forEach { day ->
                // A future day with nothing on it is simply not shown: a
                // heading with no rows under it reads as a loading failure, and
                // at the smallest size it is the only thing that fits.
                if (day.rows.isEmpty()) {
                    if (day.date == today) add(WidgetEntry.Empty)
                    return@forEach
                }
                if (day.date != today) add(WidgetEntry.Heading(day.date))
                day.rows.forEach { add(WidgetEntry.RowItem(it)) }
            }
        }

        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            items(entries.size) { position ->
                when (val entry = entries[position]) {
                    is WidgetEntry.Heading -> DayHeading(entry.date)
                    is WidgetEntry.Empty -> Text(
                        text = WidgetNothingScheduled,
                        modifier = GlanceModifier.padding(vertical = 4.dp),
                        style = TextStyle(color = CalinoWidgetColors.ink3, fontSize = 12.sp),
                    )
                    is WidgetEntry.RowItem -> when (style) {
                        WidgetStyle.Ledger -> LedgerRow(
                            row = entry.row,
                            // Only between rows. A rule under a day heading, or
                            // at the very top of the list, reads as an underline.
                            ruled = entries.getOrNull(position - 1) is WidgetEntry.RowItem,
                            wide = size.width >= LargeSize.width,
                            onClick = openRecord(entry.row),
                        )
                        WidgetStyle.Cards -> CardRow(
                            row = entry.row,
                            onClick = openRecord(entry.row),
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun DayHeading(date: LocalDate) {
        Text(
            text = date.format(HeaderFormat),
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 2.dp)
                .clickable(openAgenda(date)),
            style = TextStyle(
                color = CalinoWidgetColors.ink3,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }

    private fun openAgenda(date: LocalDate) =
        actionStartActivity(viewIntent(AgendaDeepLinks.uri(date)))

    private fun openRecord(row: WidgetAgendaRow) = actionStartActivity(
        viewIntent(
            ReminderDeepLinks.uri(
                ReminderDeepLink(
                    kind = if (row.kind == WidgetRowKind.Event) ReminderKind.Event else ReminderKind.Task,
                    recordId = row.recordId,
                    uid = row.uid,
                    occurrenceDay = row.day.toEpochDay(),
                ),
            ),
        ),
    )

    /**
     * The same intent shape a notification tap produces, on purpose:
     * `MainActivity` already parses and resolves these links, including the
     * id -> uid + day -> next-occurrence degradation that matters when a
     * series re-expands between the render and the tap.
     */
    private fun viewIntent(uri: String) = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    private sealed interface WidgetEntry {
        data class Heading(val date: LocalDate) : WidgetEntry
        data class RowItem(val row: WidgetAgendaRow) : WidgetEntry
        data object Empty : WidgetEntry
    }

    companion object {
        // Roughly 4x2, 4x3 and 5x4 launcher cells. Responsive picks the largest
        // that fits, so these only have to be ordered and distinct.
        private val SmallSize = DpSize(180.dp, 110.dp)
        private val MediumSize = DpSize(240.dp, 180.dp)
        private val LargeSize = DpSize(300.dp, 280.dp)

        private val HeaderFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
    }
}

/**
 * The ledger widget: one line per record, ruled, with an aligned time column.
 *
 * Kept as the class [CalinoWidgetReceiver] provides so that widgets already on
 * a home screen stay bound to their provider -- the launcher tracks the
 * receiver, and a rename would strand every existing placement.
 */
class CalinoLedgerWidget : CalinoAgendaWidget(WidgetStyle.Ledger)

/** The cards widget: one tinted chip per record, with room for a location. */
class CalinoCardsWidget : CalinoAgendaWidget(WidgetStyle.Cards)

