package calino.malinov.ski.widget

import calino.malinov.ski.R
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
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
import calino.malinov.ski.MainActivity
import calino.malinov.ski.data.CalinoContainer
import calino.malinov.ski.data.repository.CalinoSnapshot
import calino.malinov.ski.notify.AgendaDeepLinks
import calino.malinov.ski.notify.ReminderDeepLink
import calino.malinov.ski.notify.ReminderDeepLinks
import calino.malinov.ski.notify.ReminderKind
import calino.malinov.ski.state.CalinoDeviceDefaults
import calino.malinov.ski.util.CalinoTimeFormat
import calino.malinov.ski.util.WidgetLookAhead
import java.time.LocalDate
import java.time.LocalTime

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
    private val content: WidgetContent = WidgetContent.Agenda,
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
        val deviceDefaults = CalinoDeviceDefaults.from(context)
        val options = WidgetAgendaOptions(
            hideCompletedTasks = preferences.loadHideCompletedTasks(),
            showLocations = preferences.loadShowLocations(),
            timeFormat = preferences.loadTimeFormat().resolved(deviceDefaults.timeFormat),
            // Read, and gating nothing today: the widget shows events and
            // tasks only. See HANDOFF.md before deciding this is dead weight.
            journalEnabled = preferences.loadJournalEnabled(),
            contactsEnabled = preferences.loadContactsEnabled(),
            content = content,
            locale = context.resources.configuration.locales[0],
            untitledLabel = context.getString(R.string.sys_no_title),
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
        val context = androidx.glance.LocalContext.current
        val lookAhead = WidgetSettings.lookAhead
        val shaped = options.copy(
            locale = context.resources.configuration.locales[0],
            untitledLabel = context.getString(R.string.sys_no_title),
            // The window is the person's choice, not the widget's size: the
            // rows go into a scrolling list, so a small widget shows the same
            // agenda through a smaller window instead of a shorter agenda.
            dayCount = lookAhead.days,
            maxRows = style.rowCeiling,
            earlierToday = WidgetSettings.earlierToday,
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

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(CalinoWidgetColors.canvas)
                .cornerRadius(20.dp)
                .clickable(openAgenda(today)),
        ) {
            when {
                agenda == null -> Notice(context.getString(R.string.sys_widget_connect), context.getString(R.string.sys_widget_setup))
                agenda.empty -> Notice(emptyText, null)
                else -> Agenda(agenda, today, size, shaped.timeFormat)
            }
            // Over the list, not above it: the rows scroll under the pills, so
            // the widget's whole height is agenda and the chrome costs a
            // corner of it rather than two bands.
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
                HeaderPills(today, lookAhead)
            }
            if (agenda != null && !agenda.empty) {
                Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
                    FooterPill(agenda, today, lookAhead)
                }
            }
        }
    }

    @Composable
    private fun Pill(modifier: GlanceModifier = GlanceModifier, content: @Composable () -> Unit) {
        Row(
            modifier = modifier
                .background(CalinoWidgetColors.pill)
                .cornerRadius(PillHeight / 2)
                .padding(horizontal = 11.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }

    @Composable
    private fun HeaderPills(today: LocalDate, lookAhead: WidgetLookAhead) {
        val context = androidx.glance.LocalContext.current
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = PillInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Pill {
                Text(
                    text = if (content == WidgetContent.Tasks) context.getString(R.string.sys_tasks) else context.getString(R.string.sys_today),
                    style = TextStyle(color = CalinoWidgetColors.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                )
                Spacer(GlanceModifier.width(7.dp))
                Text(
                    text = today.format(calino.malinov.ski.util.localizedDisplayFormatter("EEE, MMM d", context.resources.configuration.locales[0])),
                    style = TextStyle(color = CalinoWidgetColors.ink3, fontSize = 12.sp),
                )
            }
            Spacer(GlanceModifier.defaultWeight())
            if (lookAhead != WidgetLookAhead.Today) {
                Pill {
                    Text(
                        text = context.getString(R.string.sys_widget_next_days, lookAhead.days),
                        style = TextStyle(color = CalinoWidgetColors.ink2, fontSize = 11.sp),
                        maxLines = 1,
                    )
                }
            }
        }
    }

    @Composable
    private fun FooterPill(agenda: WidgetAgenda, today: LocalDate, lookAhead: WidgetLookAhead) {
        val context = androidx.glance.LocalContext.current
        val last = today.plusDays((lookAhead.days - 1).toLong())
        val text = when {
            agenda.moreRows > 0 -> context.getString(R.string.sys_widget_more, agenda.moreRows)
            lookAhead == WidgetLookAhead.Today -> context.getString(R.string.sys_widget_open_agenda)
            else -> context.getString(
                R.string.sys_widget_to_date,
                last.format(calino.malinov.ski.util.localizedDisplayFormatter("EEE, MMM d", context.resources.configuration.locales[0])),
            )
        }
        Row(modifier = GlanceModifier.padding(end = 10.dp, bottom = PillInset)) {
            Pill(GlanceModifier.clickable(openAgenda(if (agenda.moreRows > 0) today else last))) {
                Text(
                    text = text,
                    style = TextStyle(color = CalinoWidgetColors.ink2, fontSize = 11.sp),
                    maxLines = 1,
                )
            }
        }
    }

    @Composable
    private fun Notice(title: String, subtitle: String?) {
        Box(
            modifier = GlanceModifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = ListGap),
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
    private fun Agenda(
        agenda: WidgetAgenda,
        today: LocalDate,
        size: DpSize,
        timeFormat: CalinoTimeFormat,
    ) {
        // Flattened before the list so a day heading and its rows scroll as one
        // stream; LazyColumn has no section concept to lean on.
        val entries = buildList {
            add(WidgetEntry.Gap)
            if (agenda.overdue.isNotEmpty()) {
                add(WidgetEntry.Overdue)
                agenda.overdue.forEach { add(WidgetEntry.RowItem(it)) }
            }
            agenda.days.forEach { day ->
                // A future day with nothing on it is simply not shown: a
                // heading with no rows under it reads as a loading failure, and
                // at the smallest size it is the only thing that fits.
                if (day.rows.isEmpty()) {
                    if (day.date == today) add(WidgetEntry.Empty)
                    return@forEach
                }
                when {
                    day.date != today -> add(WidgetEntry.Heading(day.date))
                    // Today normally needs no heading -- the header above
                    // already says so. It does once something sits above it:
                    // unlabelled rows under an "Overdue" block read as more
                    // of the same, which is the opposite of the truth.
                    agenda.overdue.isNotEmpty() -> add(WidgetEntry.Today)
                }
                day.rows.forEach { add(WidgetEntry.RowItem(it)) }
            }
            add(WidgetEntry.Gap)
        }

        LazyColumn(modifier = GlanceModifier.fillMaxSize().padding(horizontal = style.canvasPadding)) {
            items(entries.size) { position ->
                when (val entry = entries[position]) {
                    // Room for a pill to sit over: the first and last rows have to
                    // be able to scroll clear of the chrome.
                    is WidgetEntry.Gap -> Spacer(GlanceModifier.fillMaxWidth().height(ListGap))
                    is WidgetEntry.Heading -> DayHeading(entry.date)
                    is WidgetEntry.Overdue -> SectionHeading(androidx.glance.LocalContext.current.getString(R.string.sys_overdue))
                    is WidgetEntry.Today -> SectionHeading(androidx.glance.LocalContext.current.getString(R.string.sys_today))
                    is WidgetEntry.Empty -> Text(
                        text = emptyText,
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
                            timeFormat = timeFormat,
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

    /**
     * A heading for a block that is not a day. Same weight as [DayHeading] and
     * deliberately not louder: "Overdue" is a label, not an alarm, and a task
     * list whose first row shouts sets the wrong tone every morning.
     */
    @Composable
    private fun SectionHeading(label: String) {
        Text(
            text = label,
            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 2.dp),
            style = TextStyle(
                color = CalinoWidgetColors.ink3,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }

    @Composable
    private fun DayHeading(date: LocalDate) {
        Text(
            text = date.format(calino.malinov.ski.util.localizedDisplayFormatter("EEE, MMM d", androidx.glance.LocalContext.current.resources.configuration.locales[0])),
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
        data object Gap : WidgetEntry
        data class Heading(val date: LocalDate) : WidgetEntry
        data object Overdue : WidgetEntry
        data object Today : WidgetEntry
        data class RowItem(val row: WidgetAgendaRow) : WidgetEntry
        data object Empty : WidgetEntry
    }

    private val emptyText: String
        @Composable get() = androidx.glance.LocalContext.current.getString(
            if (content == WidgetContent.Tasks) R.string.sys_nothing_due else R.string.sys_nothing_scheduled,
        )

    companion object {
        // Roughly 4x2, 4x3 and 5x4 launcher cells. Responsive picks the largest
        // that fits, so these only have to be ordered and distinct.
        private val SmallSize = DpSize(180.dp, 110.dp)
        private val MediumSize = DpSize(240.dp, 180.dp)
        private val LargeSize = DpSize(300.dp, 280.dp)

        private val PillHeight = 28.dp
        private val PillInset = 8.dp

        /** Clears a pill and its inset, with a little air beneath. */
        private val ListGap = PillHeight + PillInset + 2.dp

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

/**
 * The task list: the ledger's rows, tasks only, with whatever is already late
 * gathered above today.
 *
 * Ledger rather than cards on purpose. A task rarely carries a location, so the
 * second line a card buys would mostly be empty, and a list of deadlines wants
 * the density and the aligned dates that the ruled layout gives it.
 */
class CalinoTasksWidget : CalinoAgendaWidget(WidgetStyle.Ledger, WidgetContent.Tasks)
