# Calino Android — engineering handoff

This document is the working handoff for the standalone native Android app in
this repository. It is written for the next coding model or engineer who will
continue the UI work.

## Current state

This is a Kotlin + Jetpack Compose Android application. It is a standalone
repository and does not load the Calino web app, WebView, Capacitor, CardDAV, or
webcal.

It now carries a **real, read-only CalDAV integration**: an OkHttp transport,
well-known/principal/calendar-home discovery, `calendar-query` REPORTs,
iCalendar mapping via biweekly, and Keystore-encrypted credential storage. The
app declares `INTERNET`. With no account connected it still serves the frozen
May 2026 fixture data, so the sample surfaces stay reachable.

Recurrence is expanded **on the client**, so a repeating event lands on every
occurrence in the fetch window regardless of what the server will do.

One limit worth knowing before touching it: **nothing is written back to the
server.** Local edits apply to an in-memory overlay that a refetch discards.

See "CalDAV (real, read-only)" and "CalDAV — what still needs doing".

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
client. Its **fixture** date/data contract is frozen around Monday, 18 May 2026
so that visual and gesture behavior is deterministic. That anchor applies only
when no account is connected; a connected account opens the calendar on today.

The initial standalone repository snapshot is commit `32c0664`.

## NEXT TASK — see "CalDAV — what still needs doing"

Read-only CalDAV landed after the section below was written, and client-side
recurrence expansion after that. The remaining work is in **"CalDAV — what
still needs doing"** near the end of this file. The fixture-backed
test-coverage work described immediately below remains valid and unfinished.

## Earlier task list — fixture-backed calendar functionality after the interaction polish pass

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
reviewed. Do not add server writes or sync; read-only CalDAV plus the account
list and its encrypted credentials is the agreed extent.

For each meaningful UI change, use the required full check:

```bash
distrobox enter android-sdk -- bash -lc './gradlew test lintDebug assembleDebug'
```

Inspect the changed surface on `calino-poc-api36`, including slow, fast,
cancelled, boundary, and reverse gestures. Use the zoom handle semantics
`Change calendar zoom, level … of 3` for repeatable bounds. Physical-phone
validation requires an explicit request and must not be inferred from emulator
results.

### Offline read cache — 2026-09-09

A connected calendar now renders from disk on launch and stays readable with no
network. Nothing calendar-related survived a restart before: only the account
list and the encrypted password reached disk, so every cold start showed an
empty calendar until discovery plus three REPORTs per collection came back, and
offline it never filled in at all.

Fetching and mapping were split to make it possible. `CalDavFetcher` now
returns the server's raw `CalendarResource(href, etag, ics)` and does no
mapping; `ICalMapper.mapAll` maps a collection for a given window; the
repository owns both. `FileCalendarCache` stores the resource text per calendar
as gzipped JSON, and the repository loads and maps it before making any
request, then refetches behind it. Caching text rather than mapped occurrences
is the point: a series re-expands into whatever window is current, so the cache
does not go stale as the calendar date moves.

`SyncState.Loading` gained `cachedAt`, so a refresh over a full calendar says
what is on screen rather than implying it might be empty.
`CalDavConnectionManager.restore` now seeds sources from the persisted calendar
list before discovery, so the cache is not gated behind a network round trip.
`LocalOverlay` deliberately still does not persist.

Verified: 175 unit tests; the live read-only test against the maintainer's
Baikal server round-trips all 350 events through the cache byte-identically; on
the emulator a cold start with WiFi and mobile data off renders the full
calendar in about two seconds, the Calendars screen says the refresh failed and
it is showing the last data read, and removing the account leaves
`files/caldav-cache/` empty. 24 KB on disk for three collections.

### Client-side recurrence and a real clock — 2026-09-09

Two user-reported defects, both fixed and both verified on the emulator against
the maintainer's live Baikal server.

**Recurring events showed only on their first date.** `<c:expand>` is gone from
the event query; `ICalMapper` expands instead. The whole story, including the
rules that expansion depends on, is under "Recurrence" in the CalDAV section —
read that before touching `ICalMapper.expandSeries` or `applyExceptions`.

**"Today" was a constant, in four different places.** The day rail's red
current-time marker was drawn at a literal `11.33f * 62` dp — an unmoving
11:20, painted on *every* day page rather than on today's. Separately,
`FixtureDate`/`May18` was copied into `HomeScreen`, `MainActivity`,
`FixtureRepository` and `SecondarySurfaces`, and `isToday` highlights, the
Today button, the Today/Tomorrow/Next week reschedule presets and the overdue
tint all compared against it. With an account connected the app therefore
opened on the real date while insisting today was in May 2026.

- `state/CalinoClock.kt` is the single source: `CalinoNow(today, time)`,
  `LocalCalinoNow`, and `rememberCalinoNow(live)`. It re-reads on the *minute
  boundary* rather than on an interval, so the marker lands on the minute and
  the date turns over exactly at midnight.
- `live` is `pocViewModel.hasAccounts`. With no account it holds `FixtureNow`
  (18 May 2026, 11:20), which keeps the fixture contract deterministic and
  keeps the marker where it has always been drawn.
- `CalinoApp` split into a thin shell plus `CalinoAppContent`, so the provider
  can wrap the content without re-indenting the host.
- The marker now draws only when `day == now.today` and carries a
  `contentDescription`.
- **`PagerEpoch` in `HomeScreen.kt` is deliberately still a constant.** It is
  the origin for day/week/month page-index arithmetic, not "today". If it ever
  starts advancing, every mounted pager silently renumbers its pages at
  midnight. It was renamed away from `FixtureDate` to make the two roles
  impossible to confuse.
- `May18` survives in `SecondarySurfaces.kt` for sample records and preview
  defaults only, and is now derived from `FixtureNow.today`.

Covered by `CalinoClockTest`. Emulator (`calino-poc-api36`, clock at
2026-09-09 08:30 CEST): the marker sits between 08:00 and 09:00 on today's rail
and is absent on 4 Sep; `💰 Work` renders on 9 Sep and on Mon–Thu of that week
but not Sat/Sun; 4 Sep is empty of it, honouring `EXDATE:20260904T060000Z`; the
Today button appears and targets the real date. The full
`test lintDebug assembleDebug` check passed. No physical-phone validation.

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
- Settings are real. `CalinoPreferences` now carries twelve values, all
  persisted and all defaulting to what the app did before the setting existed:
  theme, time format, pull bar, first day of week, event density, week numbers,
  default view, default event duration, default reminder, hide completed tasks,
  show end times, show locations. Rows with nothing behind them yet --
  accent, font size, timezone, date format, language, notifications, sync
  frequency, categories CRUD, import/export -- are tagged PLANNED, dimmed, and
  genuinely inert rather than moving without meaning anything.
- Dark mode is real, and the palette is a theme registry rather than a
  boolean. See "Theming" below before touching any color.
- Adding a preference is still the six-step pattern in `CalinoPreferences.kt`.
  The trap is `SettingSegmented`, which keeps the selection in its own
  `rememberSaveable`: wired segmented rows use `SettingChoiceRow`, which binds
  `CompactSegmentedControl` straight to the preference.
- First day of week is real, and Monday is no longer baked into the grid.
  `util/CalinoWeek.kt` owns the vocabulary -- `startOfWeek`, `weekdayColumn`,
  `dayOfWeekForColumn`, `weekdayLetters`, `weekendColumns`, `gridStart`. Three
  things to know before touching it:
  - `LocalDate.with(DayOfWeek.SUNDAY)` is **not** "previous or same". It moves
    within the Monday..Sunday ISO week, so for a Monday it jumps six days
    forward. `startOfWeek` uses `previousOrSame`; `WeekStartTest` fails loudly
    if anyone simplifies it back.
  - `monthGridRows` returns 5 or 6 for the *same* month depending on the week
    start, so every cache sized `rows * 7` moves with it. They key on
    `monthGridGeometry(month, weekStart)` rather than the month; treat a
    surviving `remember(month, ...)` in a renderer as a bug.
  - The week pager's page index for a date is not stable across the change --
    for a Sunday it shifts by one. The three pager states are wrapped in
    `key(weekStart)` and seeded from the live `selectedEpoch`. Without that a
    restored pager reads back a date a week off and the settled-page collector
    commits it, which is a wrong date rather than a cosmetic glitch.
- The weekend wash is computed, not hardcoded. Under a Sunday start the weekend
  is columns 0 and 6 -- two bands, not one -- so `monthWashPlan` returns regions
  in column space with per-corner flags and the draw code just renders them.
  Regions abut rather than overlap: two translucent washes on one cell compound
  into a patch far darker than either. `MonthWashPlanTest` sweeps both settings
  across every month of 2026 asserting no cell is washed twice.
- Event density caps what a month cell shows, layered over the measured
  geometry and never replacing it, so Dense means "everything that fits". The
  morph paths keep their two-chip ceiling on purpose -- it is a performance
  limit on the hot zoom path -- so they use `min(2, density.maxItems)`. Every
  "+n" is `eventCount - monthCellShownCount(...)` against that renderer's own
  cap; no renderer subtracts a literal.
- Settings has a landscape layout: above `shouldSplit` the section chips become
  a side rail with the content pane beside them. Same `sectionName` and pager
  state as portrait, so chip taps and swipes cannot disagree between
  orientations.
- At the compact endpoint the day rail reaches up behind the zoom handle and
  the week strip (`CompactLaneOverlap` in `HomeScreen.kt`) instead of starting
  below them, so hours slide under a frosted lane rather than stopping at a
  hard edge. `CompactLaneScrim` paints that lane: opaque canvas, a subtle
  blurred copy of the rail over it (`GraphicsLayer` + `BlurEffect`, Android 12+
  only; below that the lane is simply opaque), and a short gradient at the
  lower edge so content emerges instead of clipping on a line. The rail erases
  itself inside the lane band: drawn twice, once crisp and once blurred, it
  read as smudged text rather than as something underneath. The scrim tint
  turns fully opaque as the month grid takes the lane, which is what keeps the
  rail from bleeding through the grid.
- The `AddPill` uses the same glass. `MainActivity` records the root
  `AnimatedContent` into a `GraphicsLayer` and hands it to the pill, which
  blurs its own patch of it and lays the ink over at 93%. The pill is a sibling
  of that stack, never a child, so the recording cannot recurse. Its position
  comes from `positionInRoot` on both nodes.
- The zoom handle occupies a half-height band (`ZoomHandleHeight`, 22dp) but
  keeps a 44dp touch lane (`ZoomHandleTouchHeight`) that overflows that band
  evenly, so compacting the bar did not shrink what a finger has to hit.
- The zoom handle occupies a half-height band (`ZoomHandleHeight`, 22dp) but
  keeps a 44dp touch lane (`ZoomHandleTouchHeight`) that overflows that band
  evenly, so compacting the bar did not shrink what a finger has to hit. It can
  be turned off entirely -- Settings > Calendar > "Show pull bar", on by
  default, persisted through `CalinoPreferences.showZoomHandle`. Hidden, it
  gives its band back to the day surface.
- The zoom drag is hosted on the calendar container (`calendarZoomGesture`),
  not on the layers it moves. Two bugs lived in the old arrangement, and both
  made a continuous morph behave like a switch:
  - The week strip leaves the composition at `MonthEndpointBlendEnd`, so a drag
    that started on it died a fifth of the way through. The container outlives
    every layer.
  - `positionChange()` returns zero once the gesture has consumed a change, so
    after the first frame the drag contributed nothing and only the settling
    fling changed a level. It reads `positionChangeIgnoreConsumed()`.
  It watches the initial pointer pass and claims only once the drag is
  decisively vertical, leaving taps and the pagers' horizontal swipes to the
  children; a drag starting below the calendar band belongs to the day rail and
  is never claimed. The strip, the grid and the handle no longer carry gestures
  of their own. Turning the pull bar off depends on this.
- The all-day / due-task strip is now an overlay on that lane rather than a row
  above the rail. It still never scrolls. With neither tasks nor all-day events
  it renders nothing at all -- the old "NO ALL-DAY EVENTS" placeholder is gone,
  since an absent row already says it.
- Dragging the `AddPill` reveals the destination view's name on the edge the
  pill vacates; the chip fills in at the commit threshold, so the swipe is no
  longer a blind commit. Route names come from `pockRouteLabel` in
  `NavSidebar.kt`, shared with the sidebar.
- The month heading's subtitle is the week number only; the selected date it
  used to repeat is already shown by the grid's selection pill.
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

The page includes representative controls for regional defaults, accent,
font size, calendar display, event defaults, reminders, categories,
import/export placeholders, and danger-zone placeholders. Controls change
local Compose preview state only, with one exception: the Sync section's
connected-accounts group reads live `CalDavAccountStore` state, and both its
per-account `Manage` row and its `+ Add calendar account` button navigate to
`PockRoute.Accounts`.

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

1. CalDAV is **read-only**. See "CalDAV — what still needs doing" for the
   full list; there is no write path, no incremental sync, no ETag conflict
   handling, and no offline queue. Local edits go to `LocalOverlay` and are
   discarded on refetch; the accounts surface states this on screen. There is
   still no CardDAV or webcal.
2. Persistence covers CalDAV accounts, their credentials, and a read cache of
   fetched calendar data. Settings, event changes, task changes, and journal
   changes are still not durable, and the fixture repository remains
   process-local. `LocalOverlay` deliberately does not persist: an edit that
   cannot sync must not look durable.
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
12. A recurring VTODO still shows once at its due date; `CalTask` has no
    recurrence field. Events are expanded, tasks are not.
13. Loading, error, and partial-read states exist for CalDAV
    (`CalinoSnapshot.sync`, shown by the accounts surface's status card), but
    conflict and offline-queue states do not, because there is no write path.
    The calendar surfaces themselves show no sync banner yet -- a failed
    refresh keeps the last data on screen and is only reported under Calendars.

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

## CalDAV (real, read-only)

The app reads a connected account's events, tasks, and journal entries over
HTTPS. With no account connected `FixtureRepository` still serves the frozen
May 2026 sample data, so the sample surfaces stay reachable. **Nothing is
written back to the server.**

### Layers

`data/caldav/`

| File | Role |
|---|---|
| `DavHttp.kt` | OkHttp transport. Not `HttpURLConnection`: it raises `ProtocolException` on `PROPFIND` and `REPORT`. |
| `DavCredentials.kt` | Basic auth, UTF-8 per RFC 7617. `toString()` masks the password. |
| `DavXml.kt` | DOM parsing for multistatus, namespace-aware **with a local-name fallback**. |
| `CalDavDiscovery.kt` | Well-known probe, principal, calendar-home, collection listing. Implements the existing `CalDavClient` seam. |
| `CalDavFetcher.kt` | The three component queries and partial-result handling. Returns the server's raw `CalendarResource`s; it does not map. |
| `ICalMapper.kt` | biweekly → `CalEvent` / `CalTask` / `JournalEntry`, plus recurrence expansion. `mapAll` maps a whole collection for a given window. |
| `CalendarCache.kt` | The on-disk read cache: `FileCalendarCache` (one gzipped JSON file per calendar) and `CalendarCacheJson`. |
| `CalDavErrors.kt` | `CalDavErrorCode` and the status/throwable classifiers. |
| `CredentialStore.kt` | `KeystoreCredentialStore` (AES-GCM under an Android Keystore key) and an in-memory one for tests. |
| `CalDavAccountJson.kt` | Account-list persistence in private `SharedPreferences`. |
| `CalDavConnectionManager.kt` | Turns the account list into the repository's sources. |

`data/repository/CalDavRepository.kt` implements `CalinoRepository`, so
`rememberRepositorySnapshot` consumes it unchanged. `PocRepositoryViewModel`
holds both repositories and swaps `activeRepository` when an account connects.

### Behaviour that is load-bearing

Each of these was paid for once; do not undo them casually.

- **Well-known probing is judged by status (207 or 401), never by the URL it
  lands on.** Radicale redirects `/.well-known/caldav` to `/` and on to its web
  UI; a path check accepts that HTML page as the DAV root. A 401 counts because
  an auth challenge proves the endpoint understood `PROPFIND`.
- **XML lookups fall back to local-name matching.** Strict namespace matching
  alone breaks against real servers.
- **Address books and scheduling collections are filtered out** of the calendar
  home by `resourcetype`. Both test servers list them.
- **A collection reporting no privilege information is writable**, not
  read-only. Absent metadata is not a denial.
- **`supported-calendar-component-set` is a hint, not a gate.** All three
  components are always requested. A Baikal calendar advertising `VTODO` only
  still returned 81 events to a plain query; gating on that property hid the
  whole calendar and, since nothing had *failed*, reported the result as
  complete.
- **Component queries are independent.** One failing must not lose the others,
  and a partial result is never authoritative about what the server no longer
  holds.
- **Events are time-ranged; tasks and journals are not.** A `VTODO` may carry
  no `DTSTART` or `DUE` at all, and a time-range filter drops exactly those.
- **`<c:expand>` is never requested.** See "Recurrence" below.
- **`DTEND` is exclusive.** All-day spans carry an inclusive `CalEvent.endDate`
  instead, converted once in the parser.
- **`ICalMapper.toLocalDateTime` is separate and directly tested.** Expanded
  instances arrive in UTC; a 23:00 Europe/Copenhagen event arrives as `21:00Z`
  and must not slide onto the next day.
- **VTODO takes its value type from `DTSTART` over `DUE`.** Trusting `DUE`
  flips a timed task to all-day.
- **A leading BOM is stripped before parsing**, or the parse silently yields
  zero components.

### The read cache

A connected calendar renders from disk on launch and stays readable with no
network. Before this, `CalDavRepository.fetched` was a plain in-memory field:
every cold start showed an empty calendar for two network round trips
(discovery, then three REPORTs per collection), and offline it never filled in
at all.

`FileCalendarCache` keeps one gzipped JSON file per calendar under
`filesDir/caldav-cache/`, named by the SHA-256 of the calendar URL. The
maintainer's three collections -- 350 events, 38 tasks, 3 journals -- come to
24 KB on disk.

Load-bearing rules:

- **What is cached is the server's own iCalendar text, not mapped
  occurrences.** Expansion is a function of the fetch window, so cached
  occurrences would be frozen to the window they were fetched under. Caching
  the resources and re-running `ICalMapper.mapAll` against a window computed
  from *today* means a series re-expands into the current window on its own.
  It is also far smaller: one master rather than 198 occurrences.
- **The event query is time-ranged, so cached coverage is not unbounded.** A
  recurring series re-expands anywhere, but a one-off event outside the cached
  window was never fetched and cannot appear until a refresh succeeds.
- **Fetching and mapping are separate.** `CalDavFetcher` returns
  `CalendarResource(href, etag, ics)` and does no mapping; the repository maps.
  That split is what makes the cache possible at all.
- **A generation counter orders the cache load against the fetch.** They race
  by construction and the cache is the older answer; the counter is what stops
  a slow disk read from overwriting a fetch that already landed.
- **`setSources` is a no-op for an unchanged source set.** A cold start calls
  it twice -- once from the persisted account list, once when rediscovery
  confirms it -- and the second call must not discard the cache or refetch.
- **`CalDavConnectionManager.restore` seeds sources from the persisted calendar
  list before discovery runs**, so the cache is not gated behind a network
  round trip. The provisional `DiscoveredCalendar` carries `components =
  emptySet()`, which is safe only because the fetcher queries all three
  components regardless.
- **An unreadable cache yields nothing and the calendar refetches.** Corrupt,
  truncated, and wrong-version files all decode to null. This runs on the
  launch path; throwing here would be a crash on startup.
- **Writes go to a `.tmp` and are renamed**, so a kill mid-write leaves the
  previous copy rather than a truncated one.
- **Only the read side is cached.** `LocalOverlay` still lives and dies with
  the process. Persisting unsyncable edits would be a half-built offline queue.
- **Removing an account, or disabling a calendar, evicts its cached content.**
  Verified on the emulator, not only in a unit test.

Nothing secret is cached: resource text only. The password stays in
`KeystoreCredentialStore`, the account list in `CalDavAccountJson`.

### Recurrence

Expanded by `ICalMapper`, not by the server. The event query asks for plain
`calendar-data`; a `CalDavFetcherTest` case asserts that no request contains
the string `expand` at all.

**Why the server is not trusted with it.** sabre parses a collection's
`calendar-timezone` property while expanding. A calendar storing a bare
`Europe/Copenhagen` there, rather than a whole `VCALENDAR` wrapping a
`VTIMEZONE` as RFC 4791 requires, makes the expanded query fail:

```
HTTP 500  Sabre\VObject\ParseException: This parser only supports VCARD and VCALENDAR files
```

Two of three calendars on the maintainer's server are in that state. Other
servers ignore `<c:expand>` silently instead, which is worse: the master comes
back with its RRULE intact and a weekday series renders as one event. Both
failures are gone now; expansion costs the same on every server.

The engine is `Google2445Utils.getDateIterator`, Google's rfc2445 code shaded
into biweekly 0.6.8. **No new dependency was added.** It unions RRULE + RDATE
and subtracts EXRULE, so INTERVAL, COUNT, UNTIL, BYDAY, BYMONTH and BYSETPOS
all come for free, and `advanceTo` skips an old series forward to the window
without materialising the years between.

Load-bearing rules, each paid for once:

- **The window bounds expansion, and it is not optional.** The maintainer's own
  `FREQ=DAILY;BYDAY=MO,TU,WE,TH,FR` has no UNTIL and no COUNT. It is infinite.
  `MaxOccurrencesPerSeries` (2000) is a second stop for a pathological rule.
- **VEVENTs are grouped by UID before anything else.** One `.ics` resource
  carries the master and its `RECURRENCE-ID` detached instances together, and
  neither can be read without the other. Ungrouped, a master and its override
  both render and the day shows the occurrence twice.
- **An override beats an EXDATE naming the same instant** (RFC 5545 §3.8.5.1).
  Moving an occurrence and cancelling it are different acts.
- **EXDATE matching is two-tier: exact instant first, then whole day.** EXDATEs
  are lifted off the component and applied by `applyExceptions` rather than by
  the iterator, precisely so the second tier is possible. Real files carry
  EXDATEs written at the wrong time of day -- the maintainer's weekday series
  is stored at `06:00Z` with eight of its fourteen EXDATEs stamped `T000000Z`
  -- and under a strict reading those cancelled days reappear as work days. A
  day-level fallback applies only to an EXDATE inside the window that cancelled
  nothing exactly.
- **`CalEvent.recurrence` stays null on expanded instances.** `occursOn()` in
  `data/model/CalinoModels.kt` is a second, hand-rolled expander that fires
  whenever `recurrence` is set. Populating it would place every occurrence
  twice. If a series summary is ever wanted on the detail card, add a
  display-only field that `occursOn` does not consult.
- **Occurrence id is `"$uid@$instant"`**, so occurrences are individually
  addressable while `CalEvent.uid` still names the series for editing. A
  detached instance is keyed on its `RECURRENCE-ID`, not its own moved start,
  so its id survives a reschedule. A non-recurring event keeps `uid` as its id.

Verified against the live server: the `💰 Work` series now expands to 198
occurrences over the ±6-month window, where it previously produced one.

### Write posture

`CalinoRepository` carries the app's write methods and live UI paths call them,
so they apply to `data/repository/LocalOverlay.kt`, an in-memory layer over the
fetched data that a refetch discards. Making them throw was rejected: the
editor and task list would crash rather than degrade. The accounts surface
states the limitation on screen.

### Sync state

`CalinoSnapshot.sync` is `Idle | Loading | Ready(fetchedAt, warnings) | Failed`.
`Ready.warnings` names each affected calendar and what is missing; the status
card in `CalendarAccountsSurface` renders them in red. A read that is short of
the whole calendar must say **which part** is missing — reporting it as
complete is the worse failure, and was the reason the Baikal bug took a
reproduction to find rather than a glance.

A failed refresh keeps the previously fetched data on screen. Blanking the
calendar because a refresh failed is worse than stale data beside a clear error.

### Credentials

The password exists in the sheet's draft state (plain `remember`, never
`rememberSaveable`) and in `KeystoreCredentialStore`, encrypted under a
Keystore key that never leaves the Keystore. `CalDavAccount` has no password
field by construction, and the persisted account JSON carries no secret. Never
log a password; `DavCredentials.toString()` masks it.

### Account surface and navigation

`PockRoute.Accounts` renders `CalendarAccountsSurface`
(`ui/surfaces/CalDavScreen.kt`), reachable from the sidebar's calendar group and
from Settings → Sync. `AddCalDavAccountSheet` is the three-step add flow —
credentials, connecting, choose calendars — hosted in `BottomDetailCard` and
stepped with `AnimatedContent`.

`state/CalDavRules.kt` (URL normalization, per-field validation, default display
name, stable account id) is pure Kotlin and transferred to the real client
unchanged. `FixtureCalDavClient` remains for tests and for exercising the
sheet's states without a server.

Origin handling mirrors `notificationOrigin`: `accountsOrigin` sends back to
Settings or the calendar, `accountsAutoAdd` opens the sheet on arrival from the
Settings add button, and `accountsFocusId` scrolls a Settings `Manage` row's
account into view.

### The calendar's anchor date

`selectedDate` starts at `FixtureNow.today` (May 2026) only when no account is
connected; with one it starts at today, and connecting the first account
mid-session moves it. Landing a connected account on the fixture month shows an
empty calendar and reads as a broken integration.

### Tests

`app/src/test/resources/caldav/` holds verbatim responses captured from a live
Radicale server, so parsing tests run against what a server actually sends.

| Test | Covers |
|---|---|
| `CalDavDiscoveryTest` | Collection filtering, colours, privileges, prefix variation, error mapping |
| `ICalMapperTest` | Day bucketing, exclusive `DTEND`, VTODO value type, BOM, expand verification |
| `CalDavFetcherTest` | Query shapes, partial failures, the VTODO-only regression, the sabre-500 fallback |
| `CalDavRepositoryTest` | `observe` contract, sync transitions, source filtering, overlay posture |
| `CalDavViewingTest` | Month-grid placement, ordering, account JSON round trip, credential hygiene |
| `CalDavLiveTest` | Opt-in, against a real server |

MockWebServer routes by **query body, not queue order** — the three component
queries run concurrently, so an enqueued queue matches responses to the wrong
component from run to run.

The live test is gated on environment variables and skips without them:

```bash
CALINO_CALDAV_URL=https://example.com/dav.php \
CALINO_CALDAV_USER=you CALINO_CALDAV_PASS=... \
  distrobox enter android-sdk -- bash -lc './gradlew test --tests "*CalDavLiveTest*"'
```

## CalDAV — what still needs doing

Roughly in the order that would deliver the most.

1. **Recurring tasks.** `CalTask` has no recurrence field, so a repeating
   VTODO still shows once at its due date. Events are handled; tasks are not.
2. **The write path.** `LocalOverlay` edits never reach the server. Needs
   `PUT`/`DELETE` with `If-Match`, and `CalEvent.etag`/`href` already exist to
   carry it. Gated behind its own review.
3. **Incremental sync.** Every refresh refetches the whole window.
   `sync-collection` (RFC 6578) and the stored `ctag` would fix that. The cache
   is the substrate for it: it already stores each resource's `href` and
   `etag`, which is what a differential update needs. Port the
   web app's rules: any non-2xx invalidates the token, and a tombstone is a
   `<status>` that is a **direct child** of `<response>`.
4. **A sync indicator on the calendar surfaces.** `snapshot.sync` is only
   rendered under Calendars, so a failed refresh is invisible from the month or
   agenda view. Less acute since the cache landed -- a cold start is no longer
   a blank calendar -- but a stale copy still looks identical to a fresh one
   outside the Calendars screen.
5. **Fetch-window paging.** The window is today ±6 months
   (`CalDavRepository.DefaultWindowMonths`) and does not extend when the user
   pages beyond it — events simply stop. Note the cache changes the shape of
   this: a cached *series* re-expands into whatever window is asked for, so
   only one-off events outside the fetched range are missing.
6. **`monthEventIndex` cost.** It is O(events × 42) per month page with three
   months composed at once, and a real expanded calendar is far larger than the
   38 fixtures it was written against. Not yet observed to be slow; measure
   before rewriting.
7. **Multiple accounts** are modelled but only lightly exercised; only one has
   been used at a time.
8. **A task with a midnight `DUE` renders as `00:00`** in the agenda rather
   than as an all-day task. Cosmetic, seen on real data.
9. **No instrumented tests.** There is still no `androidTest` source set, so
   none of this is covered at the Compose layer.

## Safe continuation rules

- CalDAV is read-only and must stay that way until the write path is reviewed
  separately. Do not turn `LocalOverlay` edits into server writes, and do not
  add sync tokens, conflict resolution, or an offline queue as a side effect of
  other work.
- Never commit credentials. `CalDavLiveTest` reads them from
  `CALINO_CALDAV_URL` / `_USER` / `_PASS` and skips when they are unset.
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

# Connected physical device, only when explicitly requested.
# The wireless serial changes; take it from `adb devices`. The Galaxy Z Fold
# has multiple displays, so screencap needs an explicit display id:
#   adb -s <serial> exec-out screencap -p -d <id> > shot.png
# with the id from `adb -s <serial> shell dumpsys SurfaceFlinger --display-id`.
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug APK is:

`app/build/outputs/apk/debug/app-debug.apk`


## Theming

`design/CalinoTheme.kt` owns the whole design system. What used to be
`object CalinoColors`, a set of static literals every surface read directly, is
now three things:

- `CalinoPalette` -- an `@Immutable data class` holding one theme's colors.
- `CalinoThemes` -- the registry. `PaperLight` and `PaperDark` ship; `all` and
  `byId` are what a settings picker and a stored preference talk to.
- `CalinoColors` -- a `@Composable @ReadOnlyComposable` property returning
  `LocalCalinoPalette.current`.

The property kept the old name **and** the old capitalised field names on
purpose. That is what let ~483 existing call sites keep compiling untouched
while the palette became a provided value; the ~50 that could not -- draw
scopes, non-composable helpers, default arguments -- were enumerated by the
compiler rather than found by grep. Do not "fix" the capitalisation.

Values track `src/themes/built-in.css` in the Calino **web** repository, which
is the same design system. That repo is reference only; never edit it from
here. Three of its rules are implemented here:

- **The warm neutral rule.** No flat grey, no pure black, no pure white.
- **The flat-dark rule.** Shadows are a light-mode device. `elevationAlpha` is
  `0f` in dark, and elevation is expressed by the `Canvas`/`Panel`/`Side` steps
  and the hairlines instead.
- **Event tint.** A mix that reads as a whisper over paper is invisible over
  ink, so `eventTintScale` pushes it to 1.6x in dark.

### Things that bit, and will bite again

- **`Text` with no explicit color.** Material defaults `LocalContentColor` to
  black outside a `Surface`, which was right on paper and invisible on ink.
  `CalinoTheme` now provides `LocalContentColor` from the palette, which is
  what carries every implicit `Text` into a dark theme. The month heading was
  the one that showed it.
- **Scrims built out of `Ink`.** At night `Ink` is nearly white, so a scrim
  made of it *lightens* what it is meant to push back. Use
  `CalinoColors.scrim(alpha)`; each surface keeps its own weight and the
  palette's `scrimBoost` makes dark heavier.
- **`Color.White` on a selected surface.** Three kinds live in the tree and
  only one is a bug: alpha masks inside `BlendMode.DstIn` gradients are
  theme-independent and must stay; real paint on an accent or ink fill must be
  `OnAccent`/`OnInk`. Watch `WeekDay`, where the pill is `Ink` and its date is
  `OnInk` -- move one without the other and the date disappears.
- **Inverting a solid fill literally.** The add pill is [Ink] on paper, which
  defines itself with no edge. Flipping that to cream at night made it the
  loudest thing on screen. `FloatFill`/`OnFloat`/`FloatBorder` let dark keep the
  pill dark and give it a brighter hairline instead; light's border is
  `Transparent`, exactly as it always drew. The same question is still open for
  the selected-day chip in the week strip, which is still a solid `Ink` block.
- **Event colors are data, not tokens.** `Event.color` is a raw ARGB `Long`
  from the fixtures and from whatever a CalDAV server chose. `eventColor(Long)`
  is the single seam: it runs `CalinoPalette.forEvent`, which maps the six
  hues the app ships to designed dark counterparts and lifts anything else off
  the canvas. The editor's swatch row deliberately *stores* the light hues, so
  picking Rose at night does not persist a value that reads washed out by day.

### Adding a theme

Add a `CalinoPalette` to `CalinoThemes` and list it in `all`. Nothing else --
the settings cards paint their own previews from palette values. `CalinoPaletteTest`
asserts every **dark** palette clears WCAG AA 4.5:1 for `Ink`/`Ink2`/`Ink3` and
the two on-colors, which is what stops an unreadable port from shipping.

`CalinoThemeChoice` names a *mode* (System/Light/Dark), not a theme. Once the
registry holds more than the two built-ins, follow the web's shape and add
`lightThemeId`/`darkThemeId` preferences beside it rather than adding enum
entries.

### Known gaps

- **Light mode's contrast was left alone, deliberately.** `PaperLight.Ink3`
  (`#A39D93`) measures about 2.7:1 and fails WCAG AA; the web's `built-in.css`
  has since corrected the same tokens to `#655F57` and `#756D62`. Adopting them
  changes how light mode looks on every surface, so it is its own pass with its
  own emulator review. `CalinoPaletteTest` exempts light palettes and says why.
- **Cold-start flash.** `values-night/colors.xml` gives the launch window a dark
  background, so a dark *system* launches dark. Someone who forces Dark while
  the system is Light still gets one light frame: the launch theme is resolved
  from resource qualifiers before Compose runs and cannot see an in-app
  preference. Fixing it means persisting the choice somewhere the launch theme
  can read, which was out of scope here.
- The accent-color picker is still PLANNED. `Accent` is a palette field, so
  wiring it is small, but each of the five accents needs a soft/contrast pair
  per mode.
