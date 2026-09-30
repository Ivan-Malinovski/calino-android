# "Sometime this week" tasks: porting brief

This describes a feature already shipped in the Calino web app
(`Ivan-Malinovski/calino`, week view). It is written as a concept and logic
brief for reimplementing it here. UI design is deliberately left out: Android
should look and feel native, so only behaviour and the reasoning behind it are
described.

## The idea

Some to-dos have no particular day: "call the plumber", "book flights". They
belong to a week, not a date. The feature gives those a home in the week view:
a separate strip of "this week" tasks, outside the day columns, so they don't
pretend to be scheduled on a specific day.

## Core rule: no new data model

A "week task" is **not a new kind of object and nothing extra is stored**. It
is an ordinary VTODO that satisfies a rule:

> A non-recurring task whose `DTSTART` to `DUE` range covers **3 or more
> inclusive calendar days**.

- Constant: `WEEK_TASK_MIN_DAYS = 3`. Span is `(dueDay - startDay) + 1`.
- Days are compared as calendar days in the event's own timezone, after
  converting to the device frame (the web app uses `toEventInstant`). Time of
  day is ignored.
- Requires both a start and a due date, with due strictly after start.
  Undated tasks, or start on/after due, are not week tasks.
- **Recurring tasks are never week tasks** (RRULE, recurrence-id or
  override/master links all disqualify). A range on a repeating task has no
  clear meaning.
- A date-only `DUE` is the **inclusive** last day.

Why this design: other CalDAV clients (Thunderbird, Tasks.org, etc.) see a
plain task with a start and due date and behave sensibly. No custom
`X-` properties, no migration, no sync surprises. Sync and round-tripping come
for free. The threshold of 3 days keeps 1-2 day tasks in the normal day
columns, where they belong.

Related writer detail: `DTSTART` is only emitted when start differs from due
(or the task recurs), so an ordinary one-day task has no `DTSTART` and can
never accidentally qualify.

## Where week tasks show

- **Week view:** in a separate strip pinned to the bottom of the week, labelled
  "Sometime this week". Tasks overlapping the visible week that are visible
  (calendar toggled on, tasks shown in views) are listed there.
- **Not** in day columns and not repeated under their due day. A week task
  appears in exactly one place.
- Sorted by start day, then due day, then title, so the order is stable.
- Overlap test: `dueDay >= weekStart && startDay <= weekEnd`. A task that
  spans two weeks appears in both weeks.
- The strip always exists, even when empty, because it holds the add control
  and is a drop target (see below).
- Each task supports what tasks support elsewhere: tick to complete, tap to
  edit, and the same context actions (long-press on Android). Completed tasks
  stay listed, styled as done.

The week's bounds honour the user's first-day-of-week setting. Helper
`weekRangeKeys(dateKey, firstDayOfWeek)` returns the first and last day of the
week containing a date.

## Creating week tasks

Three routes, all producing the same shape of data.

1. **Quick add in the strip.** An add control reveals a one-line field. Enter
   creates the task, Esc/blur-with-empty dismisses it. The field is hidden
   until the user asks for it (the user explicitly did not want a permanent
   placeholder in the strip).
   - Created task: `start = weekStart 00:00`, `end = weekEnd 23:59:59`,
     `dueDate = weekEnd` (date only), `isAllDay = true`.
   - Calendar choice: the first writable calendar that supports VTODO,
     preferring the default calendar. If none exists, show a message and do not
     create.
   - Also add it to the local store immediately and sync to the server.
2. **"More details" from the quick-add.** Opens the full task form with the
   week range prefilled and the typed title carried over, so the user can add
   notes, priority and so on.
3. **The normal task form.** The task form gained a **start date** and a
   **"This week" preset**, so any task can become a week task, or stop being
   one.
   - "This week" sets start/due to the visible/selected date's week (first to
     last day), all-day.
   - "Add start" seeds start = due - 1 day. "Clear start" removes it.
   - Clearing the due date also clears the start (a start without a due is
     meaningless here).
   - The start is only honoured if it is strictly before the due day
     (`effectiveTaskStart`), otherwise it is ignored on save. A validation hint
     tells the user when start must be before due.
   - A hint under the dates says when the range counts as "this week" (3+ days)
     versus a short range that will still show in day columns.
   - **Recurrence is disabled while a start date is set**, with a reason
     shown ("A task with a start date can't repeat"). Conversely, start is only
     seeded when editing a non-recurring task.
   - Saving writes start as `DTSTART` (`start = ${startDay or dueDay}T${time}`)
     and the due as usual.

## Drag and drop (the newest part)

Both directions, plus finer time placement.

### Strip to grid

Dropping a week task onto the week grid **replaces its range** with the drop
location. It stops being a week task because its range collapses to one day.

- **Onto a day header (the all-day area):** all-day task on that day.
  `start = day 00:00`, `end = day 23:59:59`, `dueDate = day` (date only),
  `isAllDay = true`.
- **Onto a time slot:** a timed task on that day at that time.
  `start = end = dueDate = day + time`, `isAllDay = false`. This is a
  point-in-time due, not a block. Decision: a to-do has a due time, not a
  duration, so no invented one-hour block.
- **Quarter-hour precision.** The time is the slot under the pointer, snapped
  **down** to 15 minutes (`MINUTE_SNAP_INTERVAL = 15`). Each hour cell is split
  into four zones by the pointer's vertical position inside the cell. The
  first version only had hour granularity and this was the user's follow-up
  request. Timed events dragged within the grid use an existing relative snap
  from their own start; this pointer-based snap exists because a strip pill
  has no start time on the grid to offset from.
- A live drop preview shows the snapped slot while dragging.

### Grid to strip

Dropping a **non-recurring task** onto the strip makes it a week task for the
**visible week**: `start = weekStart 00:00`, `end = weekEnd 23:59:59`,
`dueDate = weekEnd` (date only), all-day.

- Only tasks. Events are ignored when dropped on the strip.
- Recurring tasks are refused (same reason as the rule above).
- The strip highlights while a valid drop is hovering.

### Drag decisions worth keeping

- **Overlapping drop targets.** The strip and the day headers are sticky, so
  they sit on top of grid cells that have scrolled underneath them. When the
  pointer is over the strip or a day header, that target wins over the cell
  beneath. Without this, dropping on the header landed on a hidden 5am cell.
- **Track the real pointer for the quarter-hour snap.** The drag library's
  delta drifts when the grid autoscrolls mid-drag, so the last observed pointer
  position is used instead of `start + delta`.
- **Distinguish strip pills from cards.** They have their own draggable id
  namespace so a task's strip pill can't collide with the same task's card.
- **Keep tap targets working.** Completing and opening a pill must still work,
  so dragging needs an activation threshold (distance for mouse, hold-delay
  for touch, as the grid already does; a plain distance threshold loses to the
  long-press context menu on touch).
- Drops persist through the normal update path: update the local store, then
  sync to CalDAV, with the usual failure handling. Same as any other drag.

## Edge cases and gotchas

- All-day storage varies by source (`...T00:00:00`, or date-only). Always
  compare day keys, never raw strings.
- The `DUE` day is inclusive, so "Mon to Sun" has a due of Sunday, giving a
  7-day span.
- A task dragged into the strip retains its time-of-day only insofar as the
  all-day range replaces it. Timed information is dropped.
- Tasks where the start is at or after the due day are not week tasks and
  render as ordinary tasks.
- Recurrence and week tasks are mutually exclusive in the editor, by design.

## Suggested tests

Pure logic that ports easily as unit tests:

- Qualification: 2-day vs 3-day, recurring excluded, missing start/due,
  start after due, date-only due, timezone-shifted days.
- Week range: honours first-day-of-week; Sunday vs Monday start.
- Overlap and ordering across week boundaries.
- Quick-add produces the exact stored shape above; picks the right calendar and
  reports when none is writable.
- Drop math: header vs slot, quarter-hour flooring (e.g. 60% down a 10:00
  cell gives 10:30), end-of-hour cases (never reaches the next hour).
- Strip drop: recurring task rejected, non-task ignored.

## Reference (web app, for reading only)

- `src/lib/weekTasks.ts`: `isWeekScopedTask`, `taskDayRange`,
  `weekScopedTasksInRange`, `weekRangeKeys`, `taskSpanDays`.
- `src/features/calendar/components/WeekTasksBar.tsx`: strip, quick add,
  draggable pills, drop target.
- `src/features/calendar/components/WeekView.tsx`: drag handlers and collision
  detection.
- `src/features/calendar/lib/dragSnap.ts`: `pointerMinuteOfDay`.
- `src/features/calendar/components/TaskFormFields.tsx` and `EventModal.tsx`:
  start date, "This week" preset, recurrence lock.
- `e2e/week-tasks.spec.ts`: behaviour spec for all of the above.
