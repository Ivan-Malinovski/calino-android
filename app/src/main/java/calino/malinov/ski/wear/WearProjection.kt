package calino.malinov.ski.wear

import calino.malinov.ski.data.model.occursOn
import calino.malinov.ski.data.model.spanLengthDays
import calino.malinov.ski.data.repository.CalinoSnapshot
import calino.malinov.ski.data.repository.RecordWriteState
import calino.malinov.ski.data.repository.RecordWriteStatus
import calino.malinov.ski.data.repository.SyncState
import calino.malinov.ski.data.repository.taskCalendarIds
import calino.malinov.ski.data.repository.visibleCalendarIds
import calino.malinov.ski.wearcontract.MAX_SNAPSHOT_BYTES
import calino.malinov.ski.wearcontract.WearCodec
import calino.malinov.ski.wearcontract.WearEvent
import calino.malinov.ski.wearcontract.WearSnapshot
import calino.malinov.ski.wearcontract.WearTask
import calino.malinov.ski.wearcontract.WearTimeFormat
import calino.malinov.ski.wearcontract.WearWriteState
import java.time.LocalDate
import java.time.ZoneId

object WearProjection {
    fun build(
        snapshot: CalinoSnapshot,
        today: LocalDate,
        zone: ZoneId,
        epoch: String,
        sequence: Long,
        h24: Boolean,
        generatedAtMillis: Long = System.currentTimeMillis(),
    ): WearSnapshot {
        val last = today.plusDays(6)
        val calendars = snapshot.calendars.associateBy { it.id }
        val visible = visibleCalendarIds(snapshot.calendars)
        val taskCalendars = taskCalendarIds(snapshot.calendars)
        val events = snapshot.events.asSequence()
            .filter { it.calendarId in visible }
            .flatMap { event ->
                generateSequence(today) { if (it < last) it.plusDays(1) else null }
                    .filter(event::occursOn)
                    .map { day ->
                    WearEvent(
                        occurrenceId = occurrenceIdentity(
                            event.id,
                            event.recurrenceId?.toEpochMilli(),
                            event.recurrenceDate ?: day,
                        ),
                        recordId = event.id,
                        title = event.title.take(160),
                        calendar = calendars[event.calendarId]?.name.orEmpty().take(80),
                        color = event.color,
                        startEpochDay = day.toEpochDay(),
                        endEpochDay = day.plusDays(event.spanLengthDays()).toEpochDay(),
                        startMinute = if (event.allDay) null else event.start?.toLocalTime()?.toSecondOfDay()?.div(60),
                        durationMinutes = event.durationMinutes,
                        allDay = event.allDay,
                        location = event.location?.take(160),
                        writeState = snapshot.writeStatus[event.id].toWear(),
                    )
                }
            }
            .distinctBy { it.occurrenceId }
            .sortedWith(
                compareBy<WearEvent> { it.startEpochDay }
                    .thenBy { if (it.allDay) -1 else it.startMinute ?: 0 }
                    .thenBy { it.occurrenceId },
            ).toList()
        val tasks = snapshot.tasks.asSequence()
            .filter { !it.done && it.calendarId in taskCalendars }
            .filter { it.due == null || !it.due.isBefore(today.minusDays(30)) }
            .map { task ->
                WearTask(
                    occurrenceId = occurrenceIdentity(
                        task.id,
                        task.recurrenceId?.toEpochMilli(),
                        task.recurrenceDate ?: task.due,
                    ),
                    recordId = task.id,
                    title = task.title.take(160),
                    calendar = calendars[task.calendarId]?.name.orEmpty().take(80),
                    color = task.color,
                    dueEpochDay = task.due?.toEpochDay(),
                    dueMinute = task.dueTime?.toSecondOfDay()?.div(60),
                    category = task.category?.take(80),
                    done = task.done,
                    progress = task.percentComplete,
                    writeState = snapshot.writeStatus[task.id].toWear(),
                )
            }.sortedWith(
                compareBy<WearTask> { it.dueEpochDay ?: Long.MAX_VALUE }
                    .thenBy { it.dueMinute ?: Int.MAX_VALUE }
                    .thenBy { it.occurrenceId },
            ).toList()
        val status = when (val sync = snapshot.sync) {
            is SyncState.Ready -> if (sync.partial) "PARTIAL" else "READY"
            is SyncState.Loading -> "LOADING"
            is SyncState.Failed -> "FAILED"
            SyncState.Idle -> "IDLE"
        }
        val base = WearSnapshot(
            sourceEpoch = epoch,
            sequence = sequence,
            generatedAtMillis = generatedAtMillis,
            phoneZone = zone.id,
            stale = snapshot.sync is SyncState.Failed,
            truncated = false,
            syncStatus = status,
            timeFormat = if (h24) WearTimeFormat.H24 else WearTimeFormat.H12,
            events = events,
            tasks = tasks,
        )
        return truncate(base)
    }

    private fun truncate(source: WearSnapshot): WearSnapshot {
        if (WearCodec.encodeSnapshot(source).size <= MAX_SNAPSHOT_BYTES) return source
        var events = source.events
        var tasks = source.tasks
        while (events.isNotEmpty() || tasks.isNotEmpty()) {
            if (tasks.size > events.size) tasks = tasks.dropLast(1) else events = events.dropLast(1)
            val value = source.copy(events = events, tasks = tasks, truncated = true)
            if (WearCodec.encodeSnapshot(value).size <= MAX_SNAPSHOT_BYTES) return value
        }
        return source.copy(events = emptyList(), tasks = emptyList(), truncated = true)
    }

    fun occurrenceIdentity(id: String, instant: Long?, date: LocalDate?) = "$id@${instant ?: date?.toEpochDay() ?: "base"}"

    private fun RecordWriteStatus?.toWear() = when (this?.state) {
        RecordWriteState.Pending -> WearWriteState.PENDING
        RecordWriteState.Failed -> WearWriteState.FAILED
        null -> WearWriteState.NONE
    }
}

fun isAuthoritativeWearSource(hasAccounts: Boolean, isFixtureRepository: Boolean, sync: SyncState): Boolean =
    hasAccounts && !isFixtureRepository && sync !is SyncState.Idle
