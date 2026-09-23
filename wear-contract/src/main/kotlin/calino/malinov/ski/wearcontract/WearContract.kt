package calino.malinov.ski.wearcontract

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

const val WEAR_SCHEMA = 1
const val MAX_SNAPSHOT_BYTES = 96 * 1024
const val SNAPSHOT_PATH = "/calino/v1/snapshot"
const val COMMAND_PATH_PREFIX = "/calino/v1/commands/"
const val ACK_PATH_PREFIX = "/calino/v1/acks/"

enum class WearWriteState { NONE, PENDING, FAILED }
enum class WearTimeFormat { H12, H24 }
enum class WearCommandOp { SET_TASK_DONE, RESCHEDULE_TASK, UNSUPPORTED }
enum class WearAckResult { APPLIED, QUEUED, NOOP, CONFLICT, NOT_FOUND, REJECTED, EXPIRED, NO_ACCOUNT, UNSUPPORTED }

data class WearEvent(
    val occurrenceId: String, val recordId: String, val title: String, val calendar: String,
    val color: Long, val startEpochDay: Long, val endEpochDay: Long, val startMinute: Int?,
    val durationMinutes: Int?, val allDay: Boolean, val location: String?, val writeState: WearWriteState,
)

data class WearTask(
    val occurrenceId: String, val recordId: String, val title: String, val calendar: String,
    val color: Long, val dueEpochDay: Long?, val dueMinute: Int?, val category: String?,
    val done: Boolean, val progress: Int, val writeState: WearWriteState,
)

data class WearSnapshot(
    val schema: Int = WEAR_SCHEMA,
    val sourceEpoch: String,
    val sequence: Long,
    val generatedAtMillis: Long,
    val phoneZone: String,
    val stale: Boolean,
    val truncated: Boolean,
    val syncStatus: String,
    val timeFormat: WearTimeFormat,
    val events: List<WearEvent>,
    val tasks: List<WearTask>,
)

data class WearCommand(
    val uuid: String = UUID.randomUUID().toString(),
    val occurrenceId: String,
    val recordId: String,
    val op: WearCommandOp,
    val observedDone: Boolean,
    val observedDueEpochDay: Long?,
    val sourceEpoch: String,
    val sourceSequence: Long,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val targetEpochDay: Long? = null,
)

data class WearAck(
    val uuid: String,
    val result: WearAckResult,
    val message: String?,
    val atMillis: Long,
    val sourceEpoch: String,
    val sourceSequence: Long,
)

enum class TaskGroup { OVERDUE, TODAY, UPCOMING, UNDATED }

data class AgendaGroup(val epochDay: Long, val rows: List<Any>)
data class TaskSection(val group: TaskGroup, val tasks: List<WearTask>)

data class WearReducedState(
    val snapshot: WearSnapshot?,
    val pendingCommandIds: Set<String>,
    val notices: List<WearAck>,
)

object WearCodec {
    private const val MAGIC = 0x43414c57
    fun encodeSnapshot(value: WearSnapshot): ByteArray = bytes { out ->
        out.writeInt(MAGIC); out.writeInt(value.schema); out.s(value.sourceEpoch); out.writeLong(value.sequence)
        out.writeLong(value.generatedAtMillis); out.s(value.phoneZone); out.writeBoolean(value.stale)
        out.writeBoolean(value.truncated); out.s(value.syncStatus); out.writeInt(value.timeFormat.ordinal)
        out.writeInt(value.events.size); value.events.forEach { e ->
            out.s(e.occurrenceId); out.s(e.recordId); out.s(e.title); out.s(e.calendar); out.writeLong(e.color)
            out.writeLong(e.startEpochDay); out.writeLong(e.endEpochDay); out.ni(e.startMinute); out.ni(e.durationMinutes)
            out.writeBoolean(e.allDay); out.ns(e.location); out.writeInt(e.writeState.ordinal)
        }
        out.writeInt(value.tasks.size); value.tasks.forEach { t ->
            out.s(t.occurrenceId); out.s(t.recordId); out.s(t.title); out.s(t.calendar); out.writeLong(t.color)
            out.nl(t.dueEpochDay); out.ni(t.dueMinute); out.ns(t.category); out.writeBoolean(t.done)
            out.writeInt(t.progress); out.writeInt(t.writeState.ordinal)
        }
    }

    /** Stable comparison form; transport generation metadata does not create a new logical snapshot. */
    fun encodeSnapshotContent(value: WearSnapshot): ByteArray =
        encodeSnapshot(value.copy(sequence = 0, generatedAtMillis = 0))

    fun decodeSnapshot(bytes: ByteArray): WearSnapshot = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        require(input.readInt() == MAGIC); val schema = input.readInt(); require(schema == WEAR_SCHEMA)
        val epoch = input.s(); val sequence = input.readLong(); val generated = input.readLong(); val zone = input.s()
        val stale = input.readBoolean(); val truncated = input.readBoolean(); val sync = input.s()
        val time = WearTimeFormat.entries[input.readInt()]
        val events = List(input.count()) { WearEvent(input.s(), input.s(), input.s(), input.s(), input.readLong(), input.readLong(), input.readLong(), input.ni(), input.ni(), input.readBoolean(), input.ns(), WearWriteState.entries[input.readInt()]) }
        val tasks = List(input.count()) { WearTask(input.s(), input.s(), input.s(), input.s(), input.readLong(), input.nl(), input.ni(), input.ns(), input.readBoolean(), input.readInt(), WearWriteState.entries[input.readInt()]) }
        WearSnapshot(schema, epoch, sequence, generated, zone, stale, truncated, sync, time, events, tasks)
    }

    fun encodeCommand(c: WearCommand) = bytes { o -> o.s(c.uuid); o.s(c.occurrenceId); o.s(c.recordId); o.writeInt(c.op.ordinal); o.writeBoolean(c.observedDone); o.nl(c.observedDueEpochDay); o.s(c.sourceEpoch); o.writeLong(c.sourceSequence); o.writeLong(c.createdAtMillis); o.nl(c.targetEpochDay) }
    fun decodeCommand(b: ByteArray) = DataInputStream(ByteArrayInputStream(b)).use { i -> WearCommand(i.s(), i.s(), i.s(), WearCommandOp.entries[i.readInt()], i.readBoolean(), i.nl(), i.s(), i.readLong(), i.readLong(), i.nl()) }
    fun encodeAck(a: WearAck) = bytes { o ->
        o.s(a.uuid)
        o.writeInt(a.result.ordinal)
        o.ns(a.message)
        o.writeLong(a.atMillis)
        o.s(a.sourceEpoch)
        o.writeLong(a.sourceSequence)
    }
    fun decodeAck(b: ByteArray) = DataInputStream(ByteArrayInputStream(b)).use { i ->
        WearAck(i.s(), WearAckResult.entries[i.readInt()], i.ns(), i.readLong(), i.s(), i.readLong())
    }
    private fun bytes(block: (DataOutputStream) -> Unit) = ByteArrayOutputStream().also { b -> DataOutputStream(b).use(block) }.toByteArray()
    private fun DataOutputStream.s(v: String) { val b=v.toByteArray(Charsets.UTF_8); require(b.size <= 16384); writeShort(b.size); write(b) }
    private fun DataOutputStream.ns(v: String?) { writeBoolean(v != null); if (v != null) s(v) }
    private fun DataOutputStream.ni(v: Int?) { writeBoolean(v != null); if(v != null) writeInt(v) }
    private fun DataOutputStream.nl(v: Long?) { writeBoolean(v != null); if(v != null) writeLong(v) }
    private fun DataInputStream.s(): String { val n=readUnsignedShort(); require(n <= available()); return ByteArray(n).also(::readFully).toString(Charsets.UTF_8) }
    private fun DataInputStream.ns()=if(readBoolean()) s() else null
    private fun DataInputStream.ni()=if(readBoolean()) readInt() else null
    private fun DataInputStream.nl()=if(readBoolean()) readLong() else null
    private fun DataInputStream.count(): Int = readInt().also { require(it in 0..10000) }
}

private const val MINUTES_PER_DAY = 24 * 60L

object WearSelection {
    fun agenda(snapshot: WearSnapshot, today: Long? = null): List<Any> = (
        snapshot.events + snapshot.tasks.filter { task ->
            task.dueEpochDay != null && (today == null || task.dueEpochDay in today..today + 6)
        }
        )
        .sortedWith(compareBy<Any> { day(it) }.thenBy { rank(it) }.thenBy { minute(it) }.thenBy { title(it) })
    /** Open agenda rows still worth glancing at: completed tasks and events that already ended drop out. */
    fun upcoming(snapshot: WearSnapshot, today: Long, minuteNow: Int? = null): List<Any> =
        agenda(snapshot, today).filterNot { row ->
            (row is WearTask && row.done) || (minuteNow != null && ended(row, today, minuteNow))
        }
    fun tile(snapshot: WearSnapshot, today: Long, minuteNow: Int? = null, limit: Int = 5): List<Any> =
        upcoming(snapshot, today, minuteNow).take(limit)

    /** True for an event whose end is at or before the given moment; tasks never end. */
    fun ended(row: Any, today: Long, minuteNow: Int): Boolean {
        if (row !is WearEvent) return false
        if (row.allDay || row.startMinute == null) return row.endEpochDay < today
        val end = row.startEpochDay * MINUTES_PER_DAY + row.startMinute + (row.durationMinutes ?: 0)
        return end <= today * MINUTES_PER_DAY + minuteNow
    }

    /** Instants after [fromMillis] where current/next selection can change: event starts, ends and midnights. */
    fun boundaries(snapshot: WearSnapshot, fromMillis: Long, horizonMillis: Long = 24 * 60 * 60 * 1000L): List<Long> {
        val until = fromMillis + horizonMillis
        val today = WearFormatting.today(snapshot, fromMillis)
        val midnights = (1..2L).map { WearFormatting.instantMillis(snapshot, today + it, 0) }
        val edges = snapshot.events.filterNot { it.allDay || it.startMinute == null }.flatMap { event ->
            val start = WearFormatting.instantMillis(snapshot, event.startEpochDay, event.startMinute!!)
            listOf(start, start + (event.durationMinutes ?: 0) * 60_000L)
        }
        return (midnights + edges).filter { it in (fromMillis + 1)..until }.distinct().sorted()
    }
    fun agendaGroups(snapshot: WearSnapshot, today: Long): List<AgendaGroup> =
        agenda(snapshot, today).groupBy(::day).toSortedMap().map { AgendaGroup(it.key, it.value) }

    fun taskSections(snapshot: WearSnapshot, today: Long): List<TaskSection> {
        val open = snapshot.tasks.filterNot { it.done }
        fun tasks(group: TaskGroup) = open.filter {
            when (group) {
                TaskGroup.OVERDUE -> it.dueEpochDay != null && it.dueEpochDay < today
                TaskGroup.TODAY -> it.dueEpochDay == today
                TaskGroup.UPCOMING -> it.dueEpochDay != null && it.dueEpochDay > today
                TaskGroup.UNDATED -> it.dueEpochDay == null
            }
        }.sortedWith(compareBy<WearTask> { it.dueEpochDay ?: Long.MAX_VALUE }.thenBy { it.dueMinute ?: Int.MAX_VALUE }.thenBy { it.title })
        return TaskGroup.entries.mapNotNull { group -> tasks(group).takeIf { it.isNotEmpty() }?.let { TaskSection(group, it) } }
    }
    fun complication(snapshot: WearSnapshot, today: Long, minuteNow: Int): Any? =
        snapshot.tasks.filter { !it.done && it.dueEpochDay != null && it.dueEpochDay < today }.minByOrNull { it.dueEpochDay!! }
            ?: snapshot.events.filter {
                it.startEpochDay == today && (
                    it.allDay || ((it.startMinute ?: 0) <= minuteNow &&
                        (it.startMinute ?: 0) + (it.durationMinutes ?: 0) > minuteNow)
                    )
            }.maxByOrNull { it.startMinute ?: -1 }
            ?: snapshot.events.filter {
                it.startEpochDay > today || (it.startEpochDay == today && (it.allDay || (it.startMinute ?: 0) > minuteNow))
            }.minWithOrNull(compareBy<WearEvent> { it.startEpochDay }.thenBy { if (it.allDay) -1 else it.startMinute ?: 0 })
            ?: snapshot.tasks.filter { !it.done && it.dueEpochDay != null }.minByOrNull { it.dueEpochDay!! }
    private fun day(v: Any)=when(v){is WearEvent->v.startEpochDay; is WearTask->v.dueEpochDay ?: Long.MAX_VALUE; else->Long.MAX_VALUE}
    private fun rank(v: Any)=when(v){is WearEvent->if(v.allDay)0 else 1; is WearTask->2; else->3}
    private fun minute(v: Any)=when(v){is WearEvent->v.startMinute ?: -1; is WearTask->v.dueMinute ?: Int.MAX_VALUE; else->0}
    private fun title(v: Any)=when(v){is WearEvent->v.title; is WearTask->v.title; else->""}
}

object WearStateReducer {
    private val success = setOf(WearAckResult.APPLIED, WearAckResult.QUEUED, WearAckResult.NOOP)

    fun reduce(snapshot: WearSnapshot?, commands: List<WearCommand>, acknowledgements: List<WearAck>): WearReducedState {
        val acknowledged = acknowledgements.associateBy { it.uuid }
        val pending = commands.filter { command ->
            val acknowledgement = acknowledged[command.uuid]
            acknowledgement == null || (
                acknowledgement.result in success &&
                    (snapshot?.sourceEpoch != acknowledgement.sourceEpoch ||
                        snapshot.sequence < acknowledgement.sourceSequence)
                )
        }
        var reduced = snapshot
        pending.forEach { command ->
            reduced = reduced?.copy(tasks = reduced!!.tasks.map { task ->
                if (task.occurrenceId != command.occurrenceId) task
                else when (command.op) {
                    WearCommandOp.SET_TASK_DONE -> task.copy(done = true, progress = 100, writeState = WearWriteState.PENDING)
                    WearCommandOp.RESCHEDULE_TASK -> task.copy(dueEpochDay = command.targetEpochDay, writeState = WearWriteState.PENDING)
                    WearCommandOp.UNSUPPORTED -> task
                }
            })
        }
        val notices = acknowledgements.sortedByDescending { it.atMillis }
        return WearReducedState(reduced, pending.mapTo(linkedSetOf()) { it.uuid }, notices)
    }
}

object WearFormatting {
    private val dateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
    private val time24Formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val time12Formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val time12CompactFormatter = DateTimeFormatter.ofPattern("h:mma", Locale.ENGLISH)

    fun today(snapshot: WearSnapshot, nowMillis: Long = System.currentTimeMillis()): Long =
        Instant.ofEpochMilli(nowMillis).atZone(zone(snapshot.phoneZone)).toLocalDate().toEpochDay()

    fun minuteNow(snapshot: WearSnapshot, nowMillis: Long = System.currentTimeMillis()): Int {
        val time = Instant.ofEpochMilli(nowMillis).atZone(zone(snapshot.phoneZone)).toLocalTime()
        return time.hour * 60 + time.minute
    }

    fun instantMillis(snapshot: WearSnapshot, epochDay: Long, minuteOfDay: Int): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay(zone(snapshot.phoneZone)).plusMinutes(minuteOfDay.toLong())
            .toInstant().toEpochMilli()

    fun time(minuteOfDay: Int, format: WearTimeFormat, compact: Boolean = false): String {
        val normalized = Math.floorMod(minuteOfDay, 24 * 60)
        val value = LocalTime.of(normalized / 60, normalized % 60)
        return when {
            format == WearTimeFormat.H24 -> value.format(time24Formatter)
            // "10am", not "10:00am": compact slots (tile rows, complications) are a few glyphs wide.
            compact -> value.format(time12CompactFormatter).lowercase(Locale.ENGLISH).replace(":00", "")
            else -> value.format(time12Formatter)
        }
    }

    fun date(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(dateFormatter)

    /**
     * Title for a few-character complication slot. A leading emoji or symbol eats the whole slot
     * ("💰 Work" rendered as "💰"), so it is dropped when words follow; symbol-only titles stay.
     */
    fun glanceTitle(title: String): String {
        val trimmed = title.trim()
        val firstWord = trimmed.indexOfFirst { Character.isLetterOrDigit(it.code) }
        return if (firstWord > 0) trimmed.substring(firstWord) else trimmed
    }

    fun eventDate(event: WearEvent): String = if (event.endEpochDay > event.startEpochDay) {
        "${date(event.startEpochDay)} – ${date(event.endEpochDay)}"
    } else {
        date(event.startEpochDay)
    }

    fun eventTime(event: WearEvent, format: WearTimeFormat): String {
        if (event.allDay) return "All day"
        val start = event.startMinute ?: return "Time unavailable"
        val end = event.durationMinutes?.let { start + it }
        return if (end != null) {
            "${time(start, format)}–${time(end, format)}"
        } else {
            time(start, format)
        }
    }

    fun taskDue(task: WearTask, format: WearTimeFormat): String {
        val day = task.dueEpochDay ?: return "No due date"
        val time = task.dueMinute?.let { " · ${time(it, format)}" }.orEmpty()
        return "Due ${date(day)}$time"
    }

    fun taskStatus(task: WearTask): String = when {
        task.done -> "Completed"
        task.progress > 0 -> "${task.progress}% complete"
        else -> "Open"
    }

    fun eventRow(event: WearEvent, format: WearTimeFormat): String =
        "${eventTime(event, format)} · ${event.title}"

    fun taskRow(task: WearTask, format: WearTimeFormat): String =
        "${taskDue(task, format)} · ${task.title}"

    private fun zone(id: String): ZoneId = runCatching { ZoneId.of(id) }.getOrDefault(ZoneId.of("UTC"))
}
