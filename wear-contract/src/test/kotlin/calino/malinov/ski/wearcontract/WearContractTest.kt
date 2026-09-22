package calino.malinov.ski.wearcontract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearContractTest {
    private fun snapshot() = WearSnapshot(
        sourceEpoch = "epoch",
        sequence = 7,
        generatedAtMillis = 10,
        phoneZone = "Europe/Copenhagen",
        stale = false,
        truncated = false,
        syncStatus = "READY",
        timeFormat = WearTimeFormat.H24,
        events = listOf(
            WearEvent("e@1", "e", "Meeting", "Work", 1, 1, 1, 600, 30, false, "Room", WearWriteState.NONE),
        ),
        tasks = listOf(
            WearTask("t@1", "t", "Report", "Work", 2, 1, 700, "Admin", false, 25, WearWriteState.NONE),
            WearTask("late@1", "late", "Late", "Work", 2, 0, null, null, false, 0, WearWriteState.NONE),
            WearTask("none@1", "none", "Undated", "Work", 2, null, null, null, false, 0, WearWriteState.NONE),
        ),
    )

    @Test fun snapshotRoundTripSizeAndForbiddenFields() {
        val encoded = WearCodec.encodeSnapshot(snapshot())
        assertTrue(encoded.size < MAX_SNAPSHOT_BYTES)
        assertEquals(snapshot(), WearCodec.decodeSnapshot(encoded))
        val text = encoded.toString(Charsets.ISO_8859_1)
        listOf(
            "BEGIN:VCALENDAR", "etag", "href", "attendee", "contact", "journal",
            "credential", "notes", "subtask", "raw iCalendar",
        ).forEach { assertFalse(text.contains(it, ignoreCase = true)) }
    }

    @Test fun identicalContentIgnoresSequenceAndGenerationTime() {
        val first = snapshot()
        val second = first.copy(sequence = 99, generatedAtMillis = 999)
        assertTrue(WearCodec.encodeSnapshotContent(first).contentEquals(WearCodec.encodeSnapshotContent(second)))
    }

    @Test fun commandAndAckRoundTrip() {
        val command = WearCommand(
            uuid = "u",
            occurrenceId = "task@123",
            recordId = "task",
            op = WearCommandOp.RESCHEDULE_TASK,
            observedDone = false,
            observedDueEpochDay = 1,
            sourceEpoch = "e",
            sourceSequence = 2,
            createdAtMillis = 3,
            targetEpochDay = 4,
        )
        val acknowledgement = WearAck("u", WearAckResult.APPLIED, null, 5, "e", 3)
        assertEquals(command, WearCodec.decodeCommand(WearCodec.encodeCommand(command)))
        assertEquals(acknowledgement, WearCodec.decodeAck(WearCodec.encodeAck(acknowledgement)))
    }

    @Test fun groupingOrderingAndComplicationPriority() {
        val snapshot = snapshot()
        assertTrue(WearSelection.agenda(snapshot).first() is WearTask)
        assertEquals(1, WearSelection.agendaGroups(snapshot, 1).size)
        assertEquals(
            listOf(TaskGroup.OVERDUE, TaskGroup.TODAY, TaskGroup.UNDATED),
            WearSelection.taskSections(snapshot, 1).map { it.group },
        )
        assertTrue(WearSelection.complication(snapshot, 1, 500) is WearTask)
        assertEquals(2, WearSelection.tile(snapshot, 1).size)
    }

    @Test fun reducerOptimisticallyUpdatesAndRollsBackTerminalFailure() {
        val command = WearCommand(
            uuid = "u",
            occurrenceId = "t@1",
            recordId = "t",
            op = WearCommandOp.RESCHEDULE_TASK,
            observedDone = false,
            observedDueEpochDay = 1,
            sourceEpoch = "epoch",
            sourceSequence = 7,
            createdAtMillis = 3,
            targetEpochDay = 2,
        )
        val optimistic = WearStateReducer.reduce(snapshot(), listOf(command), emptyList())
        assertEquals(2L, optimistic.snapshot!!.tasks.first { it.recordId == "t" }.dueEpochDay)
        val failed = WearAck("u", WearAckResult.CONFLICT, "changed", 4, "epoch", 8)
        val rolledBack = WearStateReducer.reduce(snapshot(), listOf(command), listOf(failed))
        assertEquals(1L, rolledBack.snapshot!!.tasks.first { it.recordId == "t" }.dueEpochDay)
        assertEquals(failed, rolledBack.notices.single())
    }

    @Test fun successfulAckKeepsOptimismUntilResultingSnapshot() {
        val command = WearCommand(
            uuid = "u",
            occurrenceId = "t@1",
            recordId = "t",
            op = WearCommandOp.SET_TASK_DONE,
            observedDone = false,
            observedDueEpochDay = 1,
            sourceEpoch = "epoch",
            sourceSequence = 7,
            createdAtMillis = 3,
        )
        val ack = WearAck("u", WearAckResult.QUEUED, null, 4, "epoch", 8)
        val awaiting = WearStateReducer.reduce(snapshot(), listOf(command), listOf(ack))
        assertTrue(awaiting.snapshot!!.tasks.first { it.recordId == "t" }.done)
        val caughtUp = WearStateReducer.reduce(snapshot().copy(sequence = 8), listOf(command), listOf(ack))
        assertFalse(caughtUp.snapshot!!.tasks.first { it.recordId == "t" }.done)
    }

    @Test fun compactFormattingUsesProjectedDateTimeAndStatus() {
        val timed = snapshot().events.single()
        assertEquals("Fri, 2 Jan", WearFormatting.eventDate(timed))
        assertEquals("10:00–10:30", WearFormatting.eventTime(timed, WearTimeFormat.H24))
        assertEquals("10:00 AM–10:30 AM", WearFormatting.eventTime(timed, WearTimeFormat.H12))
        assertEquals("10:00–10:30 · Meeting", WearFormatting.eventRow(timed, WearTimeFormat.H24))

        val multiDay = timed.copy(allDay = true, startMinute = null, startEpochDay = 1, endEpochDay = 2)
        assertEquals("Fri, 2 Jan – Sat, 3 Jan", WearFormatting.eventDate(multiDay))
        assertEquals("All day", WearFormatting.eventTime(multiDay, WearTimeFormat.H24))

        val task = snapshot().tasks.first()
        assertEquals("Due Fri, 2 Jan · 11:40", WearFormatting.taskDue(task, WearTimeFormat.H24))
        assertEquals("25% complete", WearFormatting.taskStatus(task))
        assertEquals("No due date", WearFormatting.taskDue(snapshot().tasks.last(), WearTimeFormat.H24))
    }

    @Test fun projectedPhoneZoneDeterminesTodayAndCurrentMinute() {
        val copenhagen = snapshot().copy(phoneZone = "Europe/Copenhagen")
        val instant = java.time.Instant.parse("2026-01-01T23:30:00Z").toEpochMilli()
        assertEquals(java.time.LocalDate.of(2026, 1, 2).toEpochDay(), WearFormatting.today(copenhagen, instant))
        assertEquals(30, WearFormatting.minuteNow(copenhagen, instant))
    }
}
