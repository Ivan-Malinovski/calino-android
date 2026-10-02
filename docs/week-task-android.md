# Sometime this week on Android

Android implementation of the behavior in `SOMETIME_THIS_WEEK.md`.

## Storage and visibility

A week task is an ordinary non-recurring VTODO with both DTSTART and DUE,
covering at least three inclusive calendar days. `WeekTaskRules` uses the
mapper's display-frame dates; date-only values retain their calendar dates.
RRULE and either recurrence identity disqualify a task. No vendor property,
new store, or migration is introduced.

Range presents week tasks in every day-count mode (1, 3 and 7) through a corner
badge + popover, or (landscape/wide) a chip strip; see `HANDOFF.md` and
`weekShelfLayoutFor`. The list shows visible calendars' week tasks overlapping
the displayed dates, ordered by start, due, title and ID, including completed
tasks. Those tasks are removed from the page's day lanes in every mode. Agenda
and Tasks retain their ordinary due-date projection. A shifted seven-day window
uses its actual first and last dates, rather than snapping the shelf to a
different calendar week. In one- and three-day modes, adding a week task, the
bottom "release to plan for this week" drop and "Move to this week" all use the
calendar week (per the week-start preference) that contains the first displayed
day.

Quick-add creates date-only DTSTART and inclusive DUE, chooses the first
writable VTODO calendar (the same calendar ordering as the normal editor), and
keeps the entered title after a rejected write. More details seeds the existing
editor with the title and range. The shared date controls support start, clear
start and a This week preset; the preset uses the selected planning date and
configured week start. It is unavailable for recurring tasks, whose legitimate
DTSTART remains editable and preserved. Existing start-only tasks remain
supported; explicitly clearing due also clears start.

## Scheduling and pointer ownership

Range's host owns hold-to-lift task and event streams through the same generic
observer. Completion has its own 44dp lane in the shelf; a stationary hold opens
the shared context menu, and movement after the hold lifts the task. A second
pointer cancels the lift and leaves pinch ownership to the page. Pager movement
and date-bar stepping are disabled during a task drag; vertical edge scrolling
uses the current pointer and current scroll position.

The shelf and sticky headers take priority over underlying hour cells. A header
drop sets a date-only due; a grid drop clears start and sets the due time,
floored to a quarter hour. Timed tasks render as deadline markers, without an
invented duration. The preview's line and label use the same destination
calculation as release, which recomputes from the final pointer instead of
capturing a previous composition's destination. Dropping a non-recurring task
on the shelf sets the visible range and clears times. Read-only and recurring
tasks cannot be lifted. Explicit Schedule and Move to this week actions are
available through the shared menu and accessibility actions.

A consumed synthetic up from Android ACTION_CANCEL calls cancellation, never
release. The observer restarts after that synthetic event so the next stream
is not swallowed by waiting on the previous pressed event. The hold boundary
is checked after input delivery as well as by its timer: a delayed timer must
not lose a first move that follows a completed hold.

All writes go through the existing repository entry points, container-owned
queue and conditional DAV machinery. No cache, reminder, projection or Wear
ownership changes. The fixture repository now retains input start date/time
on create and update, allowing the same flow without an account.

## Samples and verification

The frozen May 18, 2026 fixture includes Call the plumber (May 18–24), Book
summer flights (May 21–27, overlapping both weeks), and Choose a birthday gift
(completed, May 18–24). Existing fixture events and task anchors remain intact.

`WeekTaskRulesTest` covers qualification, recurrence identity, overlap/order,
week starts, field preservation, pointer flooring, timezone conversion and
writer/mapper round trips. `WeekTaskInteractionTest` covers sample uniqueness,
completion, quick-add and full-editor saving, paging, drag in both directions,
header priority, cancellation and the following fast drag. Dark-theme tests
capture shelf and enter/exit frames; save-failure tests check retained date
drafts and selected-week presets. Device runs are pinned to emulator-5554.

The final focused API 36 run passed all 24 tests across the week-task, dark
preview, save-failure, Range interaction and existing task interaction suites.
Rendered light/dark surfaces, quick-add enter/exit frames and drag previews
were inspected at the emulator's native phone size. This is emulator UI
validation; connected DAV and physical-phone interactions were not tested.

Final `./gradlew test lintDebug assembleDebug` passed on 2026-09-30.
