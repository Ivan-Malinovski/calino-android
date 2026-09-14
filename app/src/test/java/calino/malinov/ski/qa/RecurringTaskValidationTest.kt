package calino.malinov.ski.qa

import calino.malinov.ski.data.model.CalTask
import calino.malinov.ski.data.model.NewTask
import calino.malinov.ski.data.model.RecurrenceEditScope
import calino.malinov.ski.data.repository.recurringTaskValidation
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecurringTaskValidationTest {
    private val day = LocalDate.of(2026, 3, 3)

    @Test fun `recurring task requires a due date`() {
        assertEquals(
            "A repeating task needs a due date.",
            recurringTaskValidation(NewTask("Repeat", recurrence = "FREQ=DAILY"), emptyList()),
        )
    }

    @Test fun `subtask cannot repeat`() {
        assertEquals(
            "A subtask cannot repeat.",
            recurringTaskValidation(NewTask("Repeat", due = day, recurrence = "FREQ=DAILY", parentTaskId = "parent"), emptyList()),
        )
    }

    @Test fun `task with subtasks cannot become recurring`() {
        val child = CalTask("child", "Child", 1L, day, parentTaskId = "parent")
        assertEquals(
            "A task with subtasks cannot repeat.",
            recurringTaskValidation(NewTask("Parent", due = day, recurrence = "FREQ=DAILY"), listOf(child), "parent"),
        )
    }

    @Test fun `future scope is rejected rather than treated as this occurrence`() {
        assertEquals(
            "Recurring tasks support this occurrence or the entire series, not this-and-future edits.",
            recurringTaskValidation(
                NewTask("Repeat", due = day, recurrence = "FREQ=DAILY", recurrenceScope = RecurrenceEditScope.Future),
                emptyList(),
            ),
        )
    }

    @Test fun `dated top-level recurring task is accepted`() {
        assertNull(recurringTaskValidation(NewTask("Repeat", due = day, recurrence = "FREQ=DAILY"), emptyList()))
    }
}
