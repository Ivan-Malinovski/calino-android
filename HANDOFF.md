# Calino Android — engineering handoff

This document is the working handoff for the standalone native Android app in
this repository. It is written for the next coding model or engineer who will
continue the UI work.

## Current state

This is a Kotlin + Jetpack Compose fixture-backed Android application. It is a
standalone repository and does not load the Calino web app, WebView, Capacitor,
CalDAV, CardDAV, webcal, accounts, credentials, or network services.

The current build identity is intentionally still provisional:

- Repository: `calino_android`
- Gradle root project: `calino_android`
- Package / application ID: `calino.malinov.ski.poc`
- Launcher label: `Calino POC`
- Version name: `0.1.0` from `gradle.properties`
- Minimum Android SDK: 26
- Target/compile SDK: 36
- Main validation AVD: `calino-poc-api36`

The app is a polished visual and interaction POC, not a production calendar
client. Its date/data contract is frozen around Monday, 18 May 2026 so that
visual and gesture behavior is deterministic.

The initial standalone repository snapshot is commit `32c0664`.

## NEXT TASK — continue fixture-backed calendar functionality after the interaction polish pass

The zoom performance pass, swipable week strip, swipable Settings categories,
and task detail/editor flow are complete. Preserve the frozen May 2026 fixture contract and the three
calendar rest states:

- UI 1 is `zoom == 0f`: the seven-day week endpoint and selected-day hour rail.
- UI 2 is `zoom == 1f`: the compact month grid and selected-day agenda.
- UI 3 is `zoom == 2f`: the detailed month grid.

The month Canvas owns the idle compact endpoint. The week `HorizontalPager`
remains hoisted for interaction and draws only while previewing another week,
so the selected row physically continues into the month geometry. The pager
has exclusive opaque ownership during horizontal preview; the Canvas and
pager share compact-week metrics and one selector position. Keep committed
date/route state separate from pager preview state, keep boundary previews
mounted, and keep one pointer owner per gesture.

Next scoped work should be task/calendar/settings behavior coverage: add
Compose/device tests for week paging, compact hit-target gating, task
completion/undo/detail editing, calendar rescheduling, Settings category
paging, and boundary cancellation, then continue with other fixture-backed
Calino surface gaps. Event/task
drag-and-drop remains deferred until those state and gesture contracts are
reviewed; do not add sync, persistence, or remote services.

For each meaningful UI change, use the required full check:

```bash
distrobox enter android-sdk -- bash -lc './gradlew test lintDebug assembleDebug'
```

Inspect the changed surface on `calino-poc-api36`, including slow, fast,
cancelled, boundary, and reverse gestures. Use the zoom handle semantics
`Change calendar zoom, level … of 3` for repeatable bounds. Physical-phone
validation requires an explicit request and must not be inferred from emulator
results.

### Landscape split view on the month root — 2026-09-08

- The activity is no longer pinned to portrait. `AndroidManifest.xml` drops
  `screenOrientation` and declares `configChanges` for orientation/size, so a
  rotation resizes the composition instead of recreating the activity.
- `shouldSplit(widthDp, heightDp)` in `state/PocNavigationState.kt` gates the
  layout: landscape *and* at least `SplitPaneMinWidthDp` (720dp) wide. A compact
  phone turned sideways deliberately stays on the portrait zoom surface.
- When it applies, `HomeScreen` renders `SplitHomeLayout` instead of the zoom
  continuum: the month grid pinned at its detailed endpoint on the left, a
  `DayPane` for the selected day on the right, and a 44dp rule between them
  carrying the pane's collapse control. The week strip, the day rail and the
  vertical zoom gesture are not composed in this layout, so `MonthPager` is the
  only owner of the pointer stream over the grid.
- In the split, a tap on a month cell *selects* the day into the pane; only a
  tap on the day already showing there opens the day modal.
- `DayPane` and the month agenda share `AgendaDayBlock` (`AgendaScreen.kt`), so
  the row set, sort order and "Nothing scheduled" case cannot drift.
- `dayPaneCollapsed` lives in `HomeScreen` as `rememberSaveable` and is reported
  up through `onSplitPaneChanged`, which `MainActivity` uses to slide the add
  pill over the day pane rather than leaving it centred on the rule.
- Landscape damage pass on the other roots: Agenda, Tasks, Journal and the
  sidebar were undamaged. Two fixes were needed — `SettingValue` no longer fills
  the row width (it was starving the label into one-character wrapping in the
  inline Settings row, which only landscape is wide enough to reach), and
  `BottomDetailOverlay` gives the card 96% of a short (<520dp) window instead of
  86%.
- Covered by `SplitPaneRulesTest`.

### Week-strip selector follows a boundary crossing — 2026-09-08

- A day swipe that crosses a week boundary no longer parks the pill on the
  incoming week's Monday and then snaps to the real day. `WeekStrip` used to
  draw any non-committed page's indicator at the committed weekday, so the
  previewed week rendered the wrong column until the date committed and the
  column changed in one frame.
- The displayed week (`compactBoundaryDay ?: selected`) now owns the moving
  indicator, and the selector spring aims at the previewed boundary day from
  the moment the preview starts, so the pill travels with the incoming week.
  Weeks that are only sliding past keep the committed weekday.
- Frame-tracked pill x-centre, Mon 18 May to Sun 17 May: previously
  109 → (page) → 109 held for ~200ms → 971 in one frame; now
  109 → 238 → 485 → 651 → 849 → 967 → 971. The forward crossing and a plain
  week swipe were checked for regressions and stay continuous.

### Week-strip selector handoff fixed — 2026-09-08

- Releasing a day swipe no longer snaps the compact week pill back to the
  previous weekday before animating to the committed one. The live preview
  now seeds the selector spring while the drag is in flight, so when the
  settle consumes the gesture the spring's resting value already matches the
  previewed position and the pill simply continues into its settle.
- Verified by frame-tracking a screen recording of the pill's x-centre: the
  previous build ran 107 → 251, jumped back to 108, then re-animated to 251;
  the current build is monotonic with no reversal.

### Agenda root view added — 2026-09-08

- Agenda is its own root destination (`PockRoute.Agenda`), separate from the
  zooming month surface. `ui/surfaces/AgendaScreen.kt` renders one month per
  `HorizontalPager` page, listing every day of that month with its events and
  due tasks, a per-day `+ Add`, and a "Nothing scheduled" line on empty days.
- Order is `Month, Agenda | Tasks, Journal, Settings` in the sidebar (with a
  rule between the two calendar views and the rest) and
  `Month → Agenda → Tasks → Journal` in the add-pill swipe order. The former
  "Calendar" sidebar label is now "Month".
- The agenda shares the calendar's month-page arithmetic (`monthPageFor` /
  `monthForPage`), its per-day event index (`monthEventIndex`) and its header,
  which moved to `ui/components/CalinoMonthHeading` — `HomeScreen.MonthHeading`
  now delegates to it and supplies the `Week N · …` subtitle. Those helpers and
  `FixtureDate` became `internal` for this.
- `AgendaTaskRow` in `ui/components/CalinoComponents.kt` is the agenda shape of
  a due task: the event row's rail/time/card geometry with the completion circle
  as a trailing control. The checkbox-first `AgendaRow(task, …)` remains the
  Tasks-surface shape.
- Agenda times are 24h so they fit the row's mono time column, matching the
  day rail. Event rows pass their own day to the detail route, since an agenda
  row is not necessarily the selected date.
- `PocReturnTarget.Agenda` carries the origin so detail, task detail, and Quick
  Add all return to the agenda.

### Zoom, week-navigation, and calendar-task pass completed — 2026-09-08

- `HomeScreen.kt` keeps the month/day surfaces at stable measured sizes during
  zoom. The compact-to-detailed month path now uses one Canvas: the selected
  week collapses/expands into the compact grid, then rows and event markers
  interpolate into detailed rows and event chips. This removes the old
  duplicate-grid crossfade and its ghosted intermediate frames.
- The day rail→agenda transition uses an opaque clipped agenda reveal instead
  of compositing two full-screen alpha layers. This keeps event cards from
  showing through one another while the surface grows.
- Month date hit targets use the same interpolated row geometry as the painted
  grid and retain date/event/journal semantics. The current emulator check
  tapped May 25 and committed May 25 correctly.
- The week strip is an independent seven-day `HorizontalPager`. It keeps the
  selected weekday while swiping a whole week, previews neighboring weeks,
  and commits the selected date only when the week pager settles. The current
  emulator check committed Week 21 → Week 22 and preserved Monday selection.
- The idle compact endpoint is painted by the same month Canvas as the
  month-to-week morph. The week pager remains hoisted for taps and horizontal
  preview, but its separate renderer is transparent at rest; this removes the
  reported month-fades/week-appears regression.
- Compact month hit targets follow the painted row geometry and remain
  interactive while a row is visibly present; collapsed rows are removed from
  both taps and accessibility only after their lane falls below 20dp. The
  month pager also relinquishes horizontal ownership at the week handoff,
  including during reverse zoom settling.
- Boundary week preview synchronization now returns a canceled visual preview
  to the committed week and keeps one-shot suppression scoped to the settle
  lifecycle. Day rail/agenda ownership follows the actually visible clipped
  surfaces and disables both owners once the day surface is offscreen.
- Calendar tasks are indexed by date. Open-task counts appear in week cells,
  task markers/counts persist in the month Canvas, and the week/day surfaces
  show labelled due tasks with completion controls. Completion uses the local
  repository and the existing undo banner; undated tasks remain in Tasks.
- Calendar due-task rows now expose an animated reschedule control with
  Tomorrow, Next week, and No date choices. Each choice goes through the same
  local repository mutation and undo banner as the Tasks surface.
- The selected-week pill is driven by one hoisted spring position shared by
  the compact Canvas and pager. Week-strip preview uses an opaque backing lane
  so the moving pager cannot reveal a stuck Canvas row underneath.
- Compact-week geometry uses shared height, inset, pill, and center metrics in
  the Canvas, pager, and hit-target layout. Boundary preview suppression has a
  generation/tombstone state and invalidates on a real horizontal week gesture.
- Settings categories are hosted by a dedicated `HorizontalPager`; the chip
  rail remains an accessible shortcut and a chip destination replaces an
  in-progress pager settle rather than being overwritten by it.
- Task rows in both the calendar and Tasks surface open a fixture-backed Task
  Details editor. Title, category, due preset, and completion state save via
  `FixtureRepository.updateTask`, preserving the task ID and origin route.
- Commit `199a5ac` is the verified milestone. Its APK is installed and
  running on the API 36 emulator. The requested physical-phone push was
  attempted without stopping the phone app, but wireless ADB was unavailable
  (`physical-device:5555` and the documented `:45095` endpoint both returned
  no route); do not infer physical-phone validation from this pass.
- On the warmed API 36 `calino-poc-api36` emulator, the latest single-renderer
  slow-drag reports (0→1, 1→2, 2→1) were respectively: 76/16/16/2,
  75/12/16/2, and 76/16/16/2 for total frames / 50th percentile / 95th
  percentile / janky frames. These are emulator results, not physical-phone
  validation. The latest captures show one compact endpoint and no duplicate
  month/week header or grid ghosting.
- The final debug APK was installed on both `emulator-5554` and the explicitly
  requested phone at `physical-device:5555` without issuing a force-stop to the
  phone. No physical-device smoothness claim is made.
- The full `test lintDebug assembleDebug` handoff check passed after the final
  endpoint, ownership, and calendar-task reschedule changes. Drag-and-drop
  remains deferred.

### Month-to-week continuity correction — 2026-09-08

- Selected-row date/event positions interpolate from their month positions
  into the centered week content, removing the layout switch at drag onset.
- One weekday heading set travels from the month header to the week strip;
  it is drawn above the selection pill without a fade-out/fade-in handoff.
- The month Canvas now paints events only: task counts/dots and journal dots
  are removed. Due-task rows and week-pager task counts remain available.
- When the active week is the first row, its center and date positions move
  directly between endpoint layouts instead of following the expanding row
  center, removing the down/up bounce.
- The full `test lintDebug assembleDebug` check passed and the APK was
  installed on the API 36 emulator. Ivan confirmed both transition corrections
  work. Comprehensive emulator gesture/frame review was interrupted; no
  physical-phone validation was performed in this pass.

### Bottom detail cards and event paging — 2026-09-08

- Event details, task details, and journal entries now open as rounded cards
  sliding up over their mounted originating page and a dimmed backdrop.
- `ui/components/BottomDetailCard.kt` shares the backdrop, entry/exit motion,
  card shape, and grab handle. Android Back and backdrop taps use the same
  animated dismissal as the card controls. Journal unsaved-edit confirmation
  remains in place; save/delete/discard also animate before removing the card.
- Events page horizontally through the selected day's occurrences, all-day
  first and then by start time. Each pager page owns a complete rounded card,
  including the event-colored handle area. There are no Previous/Next buttons
  or page-counter row. Neighboring cards remain mounted before a swipe; the
  selected event ID commits on pager settlement and Edit targets that event.
- `SwipeDownDismiss` now reads the latest dismissal callback, preventing a
  long-lived pointer handler from bypassing newly dirty journal state.
- Ivan confirmed whole-card swiping and Android Back work. API 36 emulator
  spot checks covered event/task/journal card rendering, horizontal swipes,
  short downward spring-back, slow/fast dismissal, and event Android Back via
  key and edge swipe. Comprehensive gesture automation, keyboard layouts,
  and physical-phone validation remain outstanding. No phone deployment was
  performed for this change.
- The full `test lintDebug assembleDebug` check passed for the card/pager work.

### Calendar selection feedback correction — 2026-09-08

- Pager settlement now commits a date only for an explicit navigation action:
  a drag registered by that pager's interaction source, or a month arrow.
  The action remembers its originating selection, so a later date click also
  invalidates an older settling gesture. Programmatic day/week/month alignment
  cannot feed intermediate dates back into the selected date.
- Day-driven selector travel and boundary previews only follow a user-owned
  day gesture. The agenda catching up after a week swipe no longer sends the
  selector to another weekday and back.
- Moving week cells now use the resting Canvas's event-only marker count,
  all-day bars, date typography, spacing, and selection-pill width. Task rows
  and task accessibility information remain available.
- Full `test lintDebug assembleDebug` passed. API 36 emulator checks covered
  slow forward and fast reverse week swipes, short canceled swipes, and seven
  month date selections across different rows/weekday columns. Recorded week
  transition frames were inspected. No phone deployment or validation.
- Repeat the selection regression with
  `python3 scripts/check_calendar_selection.py` after installing the debug APK;
  it uses the SDK Distrobox and explicitly targets `emulator-5554`.
- Automated Compose interaction coverage and exhaustive interrupted/boundary
  gesture coverage remain outstanding.

### Compact calendar task rows — 2026-09-08

- Calendar-context task rows use a compact single-line presentation with the
  category inline and no repeated due date; the full Tasks surface keeps its
  existing date metadata.
- The selected-day agenda now puts the date and `OPEN DAY` action on one
  compact line, with a tappable `TASKS DUE · … OPEN` header that animates the
  task rows open and closed.
- Agenda task labels keep their full touch lanes while using a tighter visual
  offset, and event cards retain a small separation instead of touching.
- The compact rows retain the 44dp interaction lane and completion,
  reschedule, detail, and accessibility behavior.
- Full `test lintDebug assembleDebug` passed. The API 36 emulator was
  inspected at the compact month/selected-day state; no physical-phone
  validation was performed.

### Future only — concrete drag-and-drop implementation checklist

Do not start this checklist until the zoom measurements and Ivan’s visual
approval are complete. It is a staged fixture-only plan, not permission to add
remote sync, persistence, a broad refactor, or a new dependency.

1. Add a small drag state/helper with stable event/task IDs and explicit
   `idle`, `armed`, `dragging`, `settling`, and `cancelled` phases. Put host
   callbacks in `MainActivity.kt` beside the existing `HomeScreen` and `Tasks`
   callbacks. Put the shared long-press recognizer/overlay primitives in
   `ui/components/CalinoComponents.kt` (or a narrowly scoped adjacent helper).
   Use measured root bounds, viewport and scroll offsets, and the pointer grab
   offset. One component owns the pointer stream; disable pager/zoom only after
   activation, then restore it on settle/cancel. Reset to `idle` on route
   disposal and every pointer cancellation.

2. First milestone: move one nonrecurring, timed event within the visible day
   rail. Wire `HomeScreen.kt`’s `HourRailContent`/private `EventChip` and
   `DayPagerSurface`; the rail currently uses 62dp per hour. Convert with the
   current density (`pxPerMinute = density * 62f / 60f`) and calculate:

   `startMinute = round(((pointerRootY - railViewportTop + scrollPx - grabOffsetPx) / pxPerMinute) / 15) * 15`

   Clamp the result to `0..1439`. Preserve the event duration, allow a move to
   cross midnight without shortening it, and reject an invalid drop or cancel
   without writing anything. Commit one accepted drop through the host callback
   to repository `updateEvent`.

3. Build the event `NewEvent` explicitly from the original `CalEvent` before
   calling `updateEvent`: preserve title, color, duration, location, notes,
   attendees, calendar ID, and recurrence fields. `FixtureRepository` currently
   rebuilds through `eventFromInput`, and `UndoableChange`/`ChangeKind` support
   only tasks, so event undo is unavailable until a deliberate extension or a
   dedicated reversible move is implemented. Never silently lose metadata.

4. Second event milestone: drop onto a visible month day. Use `MonthGrid` in
   `HomeScreen.kt` to expose measured date bounds, and target only a rendered
   day. Preserve the event’s local start time and duration; an all-day event
   remains all-day and moves by date only. Recurring events are unsupported in
   this stage: reject them with an explanation and direct the user to the
   existing edit flow until occurrence-versus-series behavior is designed.

5. Task milestone: add a long-press drag in `TasksSurface` in
   `ui/surfaces/SecondarySurfaces.kt` and its private active `TaskRow` at
   `SecondarySurfaces.kt:939`. The same-named `TaskRow` in
   `ui/components/CalinoComponents.kt` is an unrelated shared wrapper; do not
   edit it assuming it is the Tasks screen row. Accept only explicit visible
   Today, Tomorrow, Next week, and No date trays. Call `rescheduleTask(id, due)`
   or `rescheduleTask(id, null)` through the host and retain its existing undo
   banner. Moving must never complete a task. Do not fake ordering until the
   model/repository has a rank field.

6. Keep cross-route drops for a later stage. If they are added, the host owns
   the drag state and every target is visible and explicit; there are no
   invisible drop zones. Add auto-scroll and dwell-based pager changes only
   after stationary drops are reliable. Every cancel has no mutation and every
   accepted drop has one atomic mutation. Provide keyboard and TalkBack
   alternatives for the same move/reschedule action.

7. Add pure mapping/state tests beside `HomeGestureRulesTest.kt` and
   `PocStateRulesTest.kt`, then device/Compose gesture tests for long-press,
   slow, fast, cancelled, invalid, boundary, all-day, recurring, and undo
   cases. Assert IDs, committed dates/times, preserved metadata, no write on
   cancel, and route restoration; do not assert private pixel coordinates.

## What currently works

### Calendar

`ui/home/HomeScreen.kt` is the primary calendar surface. It currently includes:

- A calendar header with month navigation, current month/year context, week
  context, and a Today action.
- A detailed month grid with weekday headings, out-of-month days, event marks,
  all-day indicators, journal indicators, and event density handling.
- A compact month/day-agenda state in which the selected week is reused from the
  month surface while the other weeks collapse away through one Canvas morph.
- A week-strip state with seven day cells, event dots/marks, a moving selected
  day indicator, and horizontal paging.
- A selected-day agenda/timeline with hour rails, event cards, all-day area,
  current-time line, and event click targets.
- Three zoom levels driven by vertical drag, with spring settling and a tap
  handle. The month-to-week transition morphs the existing month surface into
  its selected week instead of replacing it with a separate bar.
- Horizontal date/week/month paging with committed selection state separated
  from in-progress pager preview state.
- Day selection from month cells, week cells, and agenda navigation.
- Due task markers/counts in calendar cells and due-task rows in the week/day
  surfaces, with completion/reopen/reschedule controls and the existing undo
  banner.
- Animated month/week/day transitions, selection indicator movement, event
  appearance changes, and zoom transitions.
- A bottom Add action that opens Quick Add for the selected date.

Important calendar implementation details:

- `HomeScreen` owns the visual pager state and keeps the selected date stable
  until the relevant pager settles.
- Boundary-week previews are deliberately kept mounted through the last
  settling frame to avoid a blank/old-month flash.
- The month grid indexes events and journal dates per month; do not reintroduce
  per-cell full-list scans inside the zoom animation.
- `DayPagerSurface` and `MonthPager` have intentionally small pager viewport
  budgets because composing several full agenda trees made zoom visibly janky.
- The compact week strip and month grid must continue to represent the same
  targeted week during the morph.

### Day modal and event detail

`ui/surfaces/SecondarySurfaces.kt` contains the modal/detail surfaces:

- A day sheet showing the selected date’s events and journal entries.
- Event detail with title, time/duration, location, recurrence, notes, calendar,
  and attendee information when present.
- Event editing through the shared full editor (`ui/surfaces/EditorSurface.kt`),
  opened as the Quick Add route seeded from the saved record.
- Event create/update mutations against the local fixture repository.
- Downward swipe dismissal from the full surface, not only from a grab handle.
- Spring-back behavior for failed/short drags and animated host removal after a
  successful drag.
- Back navigation and origin restoration for calendar, day sheet, task, journal,
  and settings entry points.

`SwipeDownDismiss` in `ui/components/CalinoComponents.kt` is the shared gesture
primitive. It owns drag offset, axis arbitration, thresholding, spring-back,
and the handoff to the host exit animation. Do not add a second competing
pointer recognizer to an already dismissible surface without checking gesture
ownership first.

### Quick Add

Quick Add is a fixture-only creation flow with three modes:

- Event
- Task
- Journal

The parser accepts natural-language-looking input and exposes parsed chips for
date, time, duration, and location. The parser is deliberately local and
simplified; it is not a CalDAV or natural-language production implementation.

Below the typed line the same sheet is a full editor (`EditorSurface`), so the
natural-language field pre-fills a form rather than replacing it:

- Events: start/end date and time, all-day, availability, recurrence (daily,
  weekly with BYDAY, monthly, yearly, each with an optional UNTIL), location,
  calendar, categories, description, and a collapsed More section with travel
  time, reminders, related tasks, and attendees.
- Tasks: due date and time, category, description, one reminder, and colour.
- Journal: title and note, unchanged.

The draft lives in `data/model/EditorDraft.kt`, outside Compose, so the merge
and mapping rules are unit-tested (`EditorDraftTest`). A field the person edits
by hand is marked touched and the parser stops writing to it, so later typing
cannot undo a deliberate edit. Opening the editor on a saved record seeds the
draft with every touched field and the host calls the matching update instead of
an add.

Fixture calendars and the shared category list live in `FixtureRepository` and
reach the editor through `CalinoSnapshot`; Settings reads the same list.

Quick Add can originate from the calendar, day sheet, Tasks, Journal, Settings,
or event detail. The host keeps an explicit return target so dismissing it
returns to the correct surface. Its sheet has a full-surface downward gesture
and animated enter/exit behavior.

### Tasks

`TasksSurface` in `ui/surfaces/SecondarySurfaces.kt` currently includes:

- All, Active, and Completed filters.
- Animated filter transitions.
- Buckets for Overdue, Today, This week, Later, No date, and Completed.
- Animated completion presentation.
- Local task completion and reopen behavior.
- A completion undo banner with a five-second-style local window.
- Local rescheduling flow and date selection.
- Task cards with category/color presentation.
- Tappable task bodies with a bottom-card detail/editor route.
- Local task title/category/due/completion edits that preserve task identity.
- New task action through Quick Add.
- Bottom viewport padding so the last item does not sit under the fixed action.
- A shared compact segmented control for the filter.

The task repository mutations are local and synchronous. There is no task sync,
recurrence engine, or durable task database.

### Journal

`ui/surfaces/JournalScreen.kt` currently includes:

- All entries and By month modes using the shared compact segmented control.
- Animated mode transitions.
- Recent-entry list and month-grouped list.
- Empty states.
- Journal cards with dates, titles, and body previews.
- Create, edit, read, and delete flows.
- A writing surface and a small markdown-style preview renderer.
- Full-surface downward swipe dismissal for the editor/read surface.
- New journal entry creation through Quick Add.
- Local repository mutations only.

### Settings

`ui/surfaces/SettingsScreen.kt` is a UI-only settings preview with an animated
horizontal section rail and these sections:

- General
- Appearance
- Calendar
- Events
- Categories
- Notifications
- Sync
- Data

The page includes representative controls for regional defaults, theme,
accent, font size, calendar display, event defaults, reminders, categories,
connected-account preview, import/export placeholders, and danger-zone
placeholders. Controls change local Compose preview state only.

The Settings layout has an important defensive rule: every setting row gets a
full-width label measurement on phone-sized layouts; controls are placed below
the label when needed. This prevents the former `Timezone` failure where the
label was measured at intrinsic width and rendered one character per line.
Category bodies are swipable through the dedicated pager; the horizontal chip
rail remains available as a direct-access and accessibility shortcut.

### Bottom dock

`ui/components/BottomDock.kt` provides the root navigation dock for Calendar,
Tasks, Journal, and Settings.

- Each item has a weighted, equal-width touch slot.
- The selected pill animates horizontally between slots.
- The selected pill is vertically aligned to the icon container and covers the
  full icon slot, not the text label.
- Selection tint and icon scale animate.
- Dock visibility is animated when sheets, dialogs, and full-screen surfaces
  take over.

The dock indicator previously omitted the 6dp gaps between weighted items,
which shifted later selections left. It also started at the dock content’s top
edge instead of the icon slot. Both geometry issues are fixed in the current
`BottomDock.kt`.

### Notifications preview

There is a local notification preview surface reachable from Settings. It shows
representative event/task notification cards and channel rows. It is not wired
to Android notifications.

### Recent UI polish

The current app includes a scoped visual polish pass committed as `a5b3a41` in
`HomeScreen.kt` and `SecondarySurfaces.kt`:

- Calendar month and week layouts now center date labels and align selected/today
  states through the month-to-week morph. Compact event markers keep all-day
  bars, timed markers, and journal dots visually separate and scale as a group.
- Agenda and event cards now have clearer title/metadata hierarchy, color rails,
  stronger touch sizing, and restrained borders/backgrounds. The hour-rail
  cards use the same hierarchy.
- Tasks use tighter progress spacing, rounded rows, explicit completion and
  reschedule icons, progressive swipe action feedback, readable category chips,
  and wrapped reschedule actions with 44dp touch lanes. Task row and bucket
  animation keys are bucket-qualified, so rows fade across bucket changes while
  bucket headings animate their placement instead of crossing unrelated rows.

Validation for this pass: `distrobox enter android-sdk -- bash -lc './gradlew test lintDebug assembleDebug'` passed, and the resulting APK was installed on
the API 36 emulator `emulator-5554`. Rendered screenshots were inspected for
the week/agenda, month, and detailed month states; dates align, calendar
markers remain distinct, journal dots are visible, and agenda color rails and
title/time hierarchy fit the surface. This records emulator spot checks only;
it does not establish comprehensive gesture/animation coverage or physical
phone validation.

Representative interaction checks on the emulator also covered a slow vertical
drag and a short drag settling back to selected Monday, 18 May 2026, plus a
fast horizontal fling selecting Tuesday, 19 May with the pill and heading in
agreement. Completion/undo was inspected as the task count changed from four
to three and the original row was restored. Resting states were clean, but a
partial zoom capture showed agenda/list text briefly superimposed during the
crossfade; Ivan should review that intermediate frame visually. These checks
remain representative spot checks rather than comprehensive gesture acceptance.

## Data and architecture

The app host is `MainActivity.kt` / `CalinoApp()`.

The host currently owns:

- Root route
- Selected date
- Day sheet visibility
- Selected event and occurrence date
- Selected task and task-detail origin
- Editor draft seed and edit target
- Quick Add origin/type
- Journal editor/review state
- Notification origin
- Undo banner state

`FixtureRepository` implements the small `CalinoRepository` interface and
stores a `CalinoSnapshot` in Compose state. It publishes local snapshots to
listeners and supports add/update/delete for the POC’s event, task, and journal
flows. It has no disk persistence and no remote layer.

The main package layout is:

```text
app/src/main/java/calino/malinov/ski/poc/
  MainActivity.kt                  route host and top-level state
  data/model/                      event, task, journal models
  data/parser/                     local Quick Add parser
  data/repository/                 fixture repository and snapshot
  design/                          colors, typography, motion, shapes
  state/                           small navigation-state helpers
  ui/components/                   shared Compose components/gestures/icons
  ui/home/HomeScreen.kt            calendar and pager/zoom implementation
  ui/surfaces/                     Journal, Tasks, Settings, editor, modal surfaces
  util/                            date/time/recurrence formatting helpers
```

There is an older `home/CalendarHome.kt` compatibility wrapper. It is
deprecated and should not become a second calendar implementation.

## Animation and interaction contract

Animation is a first-class product requirement. A new visible state should not
simply pop in or disappear. Review every UI change for:

- Enter and exit motion.
- Selection indicator movement rather than independent background swaps.
- Correct preview mounting during pager transitions.
- Spring-back on cancelled drags.
- No one-frame flash of the old target at gesture release.
- No competing pointer handlers fighting over the same gesture.
- Stable committed state while a pager or drag is still in progress.
- Reasonable frame cost during zoom, page changes, and modal movement.

Important validation boundary: code review, tests, and emulator inspection can
check implementation details and basic behavior, but the product judgment for
animations, gesture feel, timing, visual polish, and similar “how it actually
looks and feels” questions belongs to Ivan. When a review concerns the
rendered experience rather than code correctness, explicitly flag it for Ivan
to inspect and validate instead of treating source inspection as sufficient.

Use the existing motion tokens in `design/CalinoTheme.kt` / `CalinoMotion`
where possible. Keep animation ownership explicit: the child should follow the
finger, and the host should own the final dismissal/removal transition.

## Known gaps and technical debt

These are known and should be treated as review targets, not silently assumed
to be complete:

1. There is no CalDAV, CardDAV, webcal, account, auth, sync, or network layer.
2. The fixture repository is process-local; settings, event changes, task
   changes, and journal changes are not durable.
3. Many settings use local state inside section composables. Switching away and
   back can restore the hard-coded preview default. Decide whether the eventual
   state belongs in a settings state holder or repository before wiring real
   persistence.
4. Task filter state is local to `TasksSurface`; route recreation can reset it.
5. The host and surface files are large (`MainActivity.kt`, `HomeScreen.kt`, and
   `SecondarySurfaces.kt`). Refactor only after preserving gesture ownership and
   animation timing with tests.
6. The package and launcher label still contain `poc`. Renaming them later will
   affect installed-app upgrades and must be planned rather than done casually.
7. The old `CalendarHome` wrapper is deprecated but remains in source for
   compatibility.
8. Gradle reports pre-existing warnings about the non-public
   `FixtureRepository` constructor exposed through `copy()` and the deprecated
   `CalendarHome` wrapper.
9. The repository has unit tests, but it does not yet have a comprehensive
   Compose UI test suite for every gesture and animation.
10. The README still calls parts of the project a POC; update public naming as
    the product identity settles.
11. Text scaling, split-screen/freeform windows, RTL, localization, and very
    narrow widths have not been comprehensively validated.
12. The UI is fixture-backed and intentionally optimistic: error, loading,
    conflict, offline, and partial-sync states do not exist yet.

## What the next model should review first

The next model should review in this order:

### P0 — establish the real baseline

- Read this file and `README.md`.
- Confirm the working directory is `<repo-root>`, not the
  original Calino checkout.
- Run `git status` and confirm generated `.gradle/` and `app/build/` files are
  ignored.
- Run `distrobox enter android-sdk -- bash -lc './gradlew test lintDebug assembleDebug'`.
- Install the APK on `calino-poc-api36` and capture the launch screen.
- Verify that the package being tested is `calino.malinov.ski.poc`, not the
  separate Capacitor app `calino.malinov.ski` or `.debug`.

### P0 — visual/interaction audit

Walk every root destination and capture screenshots at rest and during motion.
Treat the captures and motion review as material for Ivan’s visual validation;
do not mark animation or interaction polish complete solely because the code
looks correct or automated checks pass:

- Calendar at all three zoom levels.
- Month cell click, week-day click, and day agenda navigation.
- Month/week boundary swipe, especially Sunday↔Monday and month boundaries.
- Event detail, event edit, day sheet, Quick Add, and Journal editor dismissal.
- Tasks filter, completion/undo, reschedule, and New task.
- Journal mode switch, entry read/edit, and New journal.
- Every Settings section, especially General, Appearance, and Categories.
- Dock selection from each root destination.

Look for blank preview pages, indicator jumps, touch-target overlap, clipped
text, stale state after returning to a route, and any visible pop-in. Check both
slow drags and fast flings; a gesture that only works at one velocity is not
finished.

### P1 — Compose and gesture review

- Audit all `pointerInput`, `detect*DragGestures`, pager, and sheet handlers for
  competing ownership.
- Confirm drag offsets are not written by both a finger-follow path and a
  settle animation at the same time.
- Check that target pages remain composed before the transition starts.
- Check all `AnimatedContent` keys and state holders for unwanted resets.
- Check that every clickable/ selectable element has semantics and a useful
  content description.
- Check interactive bounds against at least 44dp; keep painted controls
  compact without shrinking their touch lane.
- Use the emulator’s layout/accessibility inspection for actual bounds, not
  only source-level assumptions.

### P1 — state model review

Before integrating remote data, decide and document ownership for:

- Selected date and visible calendar page.
- Settings values.
- Task filter and undo history.
- Journal draft lifecycle.
- Event occurrence versus recurring-series identity.
- Route restoration after modal dismissal.

Prefer a small explicit UI state model over adding more independent
`remember` values to the already large host.

### P1 — performance review

Profile or inspect recomposition during:

- Vertical calendar zoom.
- Horizontal day/month paging.
- Boundary morphs.
- Task completion animation.
- Full-screen swipe dismissal.

Do not restore broad `beyondViewportPageCount` values or per-cell event scans
without measuring. The current code has deliberate caching and viewport
limits because prior versions became visibly janky.

### P2 — product/design review

Resolve these decisions before real integrations:

- Final app name, launcher label, package ID, and icon.
- Whether the dock is the permanent root navigation model.
- Exact calendar zoom vocabulary and affordance discoverability.
- Settings information architecture and which settings are real versus preview.
- Whether journal/tasks belong in the same repository/data model as calendar.
- Offline-first behavior and conflict model.
- Accessibility and localization targets.

### P2 — testing plan

Add Compose UI tests or a suitable device test harness for:

- Dock selection and indicator destination.
- Month cell date selection.
- Week/day swipe commit and cancellation.
- Month-to-week morph target week.
- Full-surface downward dismissal and spring-back.
- Quick Add return-target restoration.
- Task completion/undo.
- Journal create/edit/delete.
- Settings controls and state retention.
- Accessibility labels and minimum bounds.

Tests should assert user-visible state and committed dates, not private pixel
coordinates or animation implementation details. Keep a small set of emulator
screenshot checkpoints for visual regressions.

## Safe continuation rules

- Do not add real CalDAV functionality until the UI state/data boundaries have
  been reviewed and the fixture flows have reliable tests.
- Do not edit the original `<sibling-native-poc>` copy when
  working on this repository. The standalone repo is the source of truth from
  this handoff onward.
- Preserve the May 2026 fixture contract unless a task explicitly changes it.
- Keep the app ID stable while testing installed upgrades.
- Use `apply_patch` for source edits and keep generated build output ignored.
- Do not claim phone validation from a build-only check. Emulator screenshots
  validate the rendered POC; physical-phone deployment is a separate step.

## Useful commands

```bash
# From <repo-root>
distrobox enter android-sdk -- bash -lc './gradlew test lintDebug assembleDebug'

# Emulator
emulator -avd calino-poc-api36
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am force-stop calino.malinov.ski.poc
adb -s emulator-5554 shell am start -W -n calino.malinov.ski.poc/.MainActivity

# Connected physical device, only when explicitly requested
adb -s physical-device:45095 install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug APK is:

`app/build/outputs/apk/debug/app-debug.apk`
