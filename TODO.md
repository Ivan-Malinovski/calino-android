# Calino Android — product backlog

This is the agreed, priority-ordered work list. Agents should read it together
with [`AGENTS.md`](AGENTS.md) and [`HANDOFF.md`](HANDOFF.md) before starting
work, and should take the next unfinished item unless the user asks for
something specific.

Rules for using this file:

- Work top to bottom. The order is deliberate and was set by the user on
  2026-09-12; do not reorder or skip ahead on your own judgement.
- Do not batch several items into one change. Each item is its own review,
  its own checks, and its own emulator pass.
- Mark an item `[x]` only when it is implemented, tested, and validated on the
  API 36 emulator per the review protocol in `AGENTS.md`.
- Update `HANDOFF.md` when an item lands, and note anything it changed about
  the known-gaps list there.

### Scope note

Items 1–3 and 10 add platform functionality that the current `AGENTS.md` scope
section does not cover (it restricts remote/native work to CalDAV and CardDAV).
The user has explicitly asked for them, so they are in scope as of 2026-09-12.
They remain the only sanctioned exceptions: webcal, telemetry, and other remote
hosts are still out of scope.

---

## 1. Make reminders actually work (VALARM round-trip)

The editor collects `reminders`, Settings has a default-reminder preference, and
none of it reaches the server. `grep -r reminders app/src/main/.../data/caldav/`
returns nothing, and `ICalWriter.kt:51` lists `VALARM` among the properties
Calino "does not model" and only preserves on patch.

- Model alarms properly on `CalEvent` (and decide the same question for
  `CalTask`, which currently has a single `reminder` field).
- Read `VALARM` in `ICalMapper` — at minimum `TRIGGER` with a relative
  duration before start; decide explicitly what to do with absolute triggers,
  `REPEAT`/`DURATION`, and `ACTION` values Calino cannot present.
- Write `VALARM` in `ICalWriter`/`ICalPatcher`. Preserve foreign alarm
  properties on patch the way other unmodelled properties are preserved; do
  not drop an alarm Calino did not author.
- Round-trip must survive an edit made in another client (Thunderbird is the
  reference, as it is for recurring tasks in the web app).
- Unit tests for mapping and patching, including an event whose alarm Calino
  did not create.

Done when a reminder set in Calino is visible in another CalDAV client and
survives a round trip in both directions. Local delivery is item 2.

**Status 2026-09-12 — [x] done.** Mapping, writing, patching and the rebase are
in (`data/caldav/ICalAlarms.kt` plus the three iCal files), with unit coverage
for the ownership predicate, the round trip, foreign-alarm preservation and the
stale-ETag rebase.

Validated live against Radicale by `CalDavAlarmLiveTest`, which plants a
resource shaped the way another client emits one -- a `DISPLAY` alarm Calino can
own beside an `EMAIL`/`RELATED=END`/`REPEAT` alarm it cannot -- and drives real
edits through the server. Both directions hold: the foreign alarm, `ORGANIZER`,
`X-` properties and the origin `PRODID` all survive, our own alarm is not
rebuilt when its lead time is unchanged, and clearing removes only ours. Note
this is a synthetic foreign resource, not Thunderbird itself; the interop
property is what is proven, not that specific client.

Local delivery is item 2: a reminder now syncs and still notifies nobody.

## 2. Local notification delivery

Nothing in the app can notify. No `POST_NOTIFICATIONS`, no channel, no
`AlarmManager`, no receiver. The existing Notifications screen is a preview
surface and is not wired to Android.

- `POST_NOTIFICATIONS` permission with a runtime request at a sensible moment,
  not at first launch.
- Notification channels for event and task reminders, matching the channel rows
  the preview surface already shows.
- Exact-alarm scheduling for upcoming reminders, with reschedule on boot
  (`BOOT_COMPLETED`), on timezone change, and after a sync that changes a
  scheduled record. Decide and document whether the app requests
  `SCHEDULE_EXACT_ALARM` or accepts inexact delivery.
- Tapping a notification deep-links to the event or task.
- Wire the existing Notifications preview surface to real state.
- Document the vendor battery-killer caveat the way the web README already does
  (dontkillmyapp.com), somewhere the user will see it.

**Status 2026-09-12 — [x] done.** Everything above is in, under `notify/`,
plus the shade actions (Snooze 5 min, Mark done, Tomorrow) the user asked for.
`SCHEDULE_EXACT_ALARM` is declared rather than `USE_EXACT_ALARM`, with an honest
inexact fallback surfaced in the UI; the reasoning is in `HANDOFF.md`. The data
layer became process-scoped (`data/CalinoContainer.kt`) because a shade action
writes from a receiver with no Activity -- that is the part of this change to
review hardest.

Validated live on the API 36 emulator against a local Radicale
(`scripts/live-caldav/`), driving a real account rather than the fixture:

- A `VALARM` planted on the server synced, planned, and wrote a durable
  schedule; the alarm armed as `RTC_WAKEUP` with `exactAllowReason=permission`.
- The reminder fired at 21:10:48.001Z against an armed time of 21:10:48 -- one
  millisecond late -- on `calino.reminders.events`, importance 4, one action.
- **Reboot re-arm**: after `adb reboot`, with no Activity running, the alarm was
  re-armed from the stored schedule alone, still exact.
- A `VTODO` reminder posted on `calino.reminders.tasks` with all three actions.
  **Mark done** from the shade, in a process with no Activity, wrote through to
  the server: `STATUS:COMPLETED`, `PERCENT-COMPLETE:100`. **Tomorrow** moved
  `DUE` from 20260912 to 20260913, and the new date came back through sync into
  the next plan. **Snooze** cancelled the notification, wrote a snooze five
  minutes out, and re-armed for exactly that instant.
- Each action replaced the reminder in place with what happened ("Moved to
  tomorrow"), rather than vanishing.
- Deep link resolves an event and opens its detail; both channels are created at
  launch; granting exact alarms flips the "Exact timing is off" notice on the
  next resume.

Known limitations, deliberate and recorded in `HANDOFF.md`: a timezone change
re-arms stored instants only and the anchors are corrected on the next
foreground; the planner uses `CalEvent.occursOn` for unexpanded recurrence
masters and so inherits item 5's missing `INTERVAL`; and the "Daily brief" the
old mock advertised was removed rather than built.

## 3. Home screen widget

- A Glance widget showing the agenda for today and the near future.
- Tap targets open the app on the relevant date/record.
- Respect calendar visibility and the Journal/Contacts availability flags.
- Update on sync and on date change without a background poll loop.

**Status 2026-09-12 — [x] done.** A resizable Glance agenda under `widget/`:
today plus the next days at larger sizes, events and tasks, empty days skipped.
`WidgetAgenda.kt` is the Android-free core (the `ReminderPlan.kt` precedent) and
borrows `EventDateIndex`, `tasksDueOn` and the agenda's ordering rather than
restating them, so the widget cannot drift from the grid behind it.

It never touches the network: `CalDavConnectionManager.restore()` was split so
`CalinoContainer.ensureCachedData()` publishes the disk cache alone. Updates
come from a `CalinoWidgetBridge` on the repository (the `ReminderSchedulerBridge`
shape, conflated the same way), plus `DATE_CHANGED`/`TIME_SET`/`TIMEZONE_CHANGED`
on the receiver; `updatePeriodMillis` is 0, so there is no poll loop. Record
rows reuse the reminder deep link unchanged; day headers use a new
`AgendaDeepLinks` beside it.

Validated on the API 36 emulator against a local Radicale with a real account:
reboot re-render with no Activity in the process; an identical render with the
server **stopped**, which is what proves the cache path; cold taps into an
event, a task and a day header; a server delete and an in-app completion both
arriving through the bridge; calendar visibility off and back on; a framework
time-set moving "today"; the no-account prompt; light and dark. 521 unit tests,
none failing.

Two Glance traps are written up in `HANDOFF.md` — both make the widget render
once and then silently never change. Read that section before editing it.

Calendar visibility and `showTasksInViews` are honoured. The Journal/Contacts
flags are read into `WidgetAgendaOptions` but gate nothing, because the widget
shows neither; that is recorded rather than claimed as met.

## 4. 3-day and 7-day range views

`CalinoDefaultView` currently has Month, Week, and Day only.

- Add 3-day and 7-day range views, matching the web app's 3-day view.
- They must participate in the existing zoom lane and horizontal paging rather
  than becoming a separate screen. Read the gesture-ownership section of
  `AGENTS.md` first: this touches the pager/zoom arbitration directly.
- Extend `CalinoDefaultView` so a range view can be the default view, and make
  sure the persisted preference migrates cleanly.

**Status 2026-09-13 — [x] done, with an approved design change.** The user
chose a dedicated Range root page rather than adding two more stops to the
month/day zoom lane. It sits between Month and Agenda in the sidebar and add
pill swipe order, uses one persisted 3/7-day segmented toggle, and is available
as the single `Range` default view. Three-day windows roll from the selected
date; seven-day windows align to the configured week start.

The range grid reuses the shared calendar heading, segmented control,
`EventDateIndex`, hour rail/grid, event cards, menus, task rules, time format,
motion, and root navigation. All columns remain on screen on narrow phones;
secondary card metadata collapses before geometry does. Paging, event opening
and menus, held event moves across time/day, empty-slot creation, task actions,
and timeline pinch scaling are wired through the same repository callbacks as
the existing calendar.

## 5. Fix the recurrence engines

There are two, and they disagree.

- `CalEvent.occursOn` (`CalinoModels.kt:66`) parses only `FREQ`, `BYDAY` and
  `UNTIL`. It has no `INTERVAL`, no `COUNT`, no `BYMONTHDAY`, no `BYSETPOS`,
  and no `EXDATE`.
- Per `HANDOFF.md`, unbounded recurrence masters fall through to `occursOn` for
  final verification, and it is the path used at `HomeScreen.kt:4820`,
  `EventDateIndex.kt:33`, `SecondarySurfaces.kt:359` and
  `MainActivity.kt:1341`.
- Expected consequence, to be confirmed with a test first: an unbounded
  `FREQ=WEEKLY;INTERVAL=2` event renders every week.

Start with a failing test that pins the current wrong behaviour, then decide
whether `occursOn` grows to match `ICalMapper` or whether the UI paths stop
using a second engine at all. One engine is the preferred end state.

**Status 2026-09-13 — [x] done.** There is now one engine.
`data/model/RecurrenceRules.kt` evaluates a rule by handing biweekly the same
RFC 5545 iterator `ICalMapper` expands with, and `CalEvent.occursOn` delegates
to it, so the grid, the reminder planner and the widget cannot disagree with
the CalDAV expander. `INTERVAL`, `COUNT`, `BYMONTHDAY`, `BYSETPOS`, `BYMONTH`,
ordinal `BYDAY` and `EXDATE` are all honoured; the confirmed bug
(`FREQ=WEEKLY;INTERVAL=2` rendering every week) is pinned by
`RecurrenceRuleTest`, which fails against the old hand-rolled parser.

Two deliberate behaviour changes, both toward the standard:

- A monthly anchor past the length of a shorter month now **skips** that month
  rather than clamping to its last day (RFC 5545 3.3.10). The old clamp was
  the hand-rolled engine's invention and the expander never agreed with it.
- `EventDateIndex` only buckets a rule whose placement follows from its `FREQ`
  alone. Anything with a `BY*` part other than a weekly `BYDAY` is now a
  candidate on every date, because such a rule can land where the anchor does
  not predict; `RecurrenceRules` makes the decision.

Results are memoised per rule, anchor and year, since `occursOn` is called once
per visible event per visible day.

Validated on the API 36 emulator: a weekly series created in the editor renders
on its weekday only, across the month grid, and continues correctly across the
month and year boundary into January 2027; deleting the series clears every
occurrence. 535 unit tests, none failing, plus `lintDebug` and `assembleDebug`.

## 6. Instrumented tests

There is no `androidTest` source set, for an app whose core value is gesture and
animation behaviour.

- Stand up the `androidTest` source set and a Compose test harness.
- Cover the high-risk interactions already listed under "Testing expectations"
  in `AGENTS.md`: date selection, paging and cancellation, month-to-week morph
  targets, modal dismissal and spring-back, Quick Add return targets, task
  completion/reschedule/undo, journal flows, settings retention, and dock
  indicator bounds.
- Prefer assertions about committed state, visible content, and semantics over
  pixel coordinates.

**Status 2026-09-13 — [x] done.** `app/src/androidTest/` exists with a Compose
harness (`CalinoUiTest` + `CalinoTestActions`) and 54 tests across nine classes,
one per listed interaction. They run on the fixture repository with no account,
which freezes the clock at 2026-05-18 and pins every pager to its centre page;
`CalinoResetRule` returns preferences, accounts and fixture data to a
first-launch state before each test, ordered outside the Compose rule because
the Activity reads preferences during its first composition.

Validated on the API 36 emulator: 54 passing, then **54 passing again on an
immediate second run** and on per-class runs in a different order, which is what
actually exercises the reset rule. Plus `test lintDebug assembleDebug`.

Writing them found four real accessibility defects, fixed in main source rather
than worked around: `MenuButton` was a 40dp touch target against the documented
44dp lane; a settings `Switch` split its label and its state across two nodes;
`CompactMonthRow` dropped `", selected"` while still clickable; and the global
undo banner had an unlabelled action and no live region. Three test tags were
added where no user-facing label belongs (the two dismiss gesture containers,
the undo banner, the range pager).

Note `AGENTS.md` asked for "dock indicator destinations"; there is no dock in
this app, so that bullet is covered as sidebar destinations and the stale
wording has been corrected there. The five traps that make these tests
non-obvious -- chiefly that a pager commits a date only after a *real* drag, and
that two day cells legitimately report themselves selected while paging -- are
written up under "Device tests" in `HANDOFF.md`. Read that before adding tests.

## 7. Polish the week-strip/month transition

The hinged week-strip → split-month unfold is visually more layered, but the
first initialization can still be janky and the month morph no longer stays
fully coupled to the day-surface transition. The next pass should make the
first-run frame deterministic and restore one shared transition contract.

- Remove first-frame jumps, blank/duplicate content, and late initialization
  when the calendar first enters or settles on the month level.
- Reconnect the week/month unfold progress, selected-date anchor, and day
  surface blend so the calendar and agenda move as one composition in both
  directions.
- Preserve continuous finger-follow behavior, pager/event/pinch ownership,
  and the fixed-size rendering budget; do not paper over the issue with a
  second animation state machine.
- Add regression coverage for cold entry, reverse/cancelled settles, and the
  coupled day-surface transition, then validate slow/fast gestures on the API
  36 emulator and the approved physical phone.

**Status 2026-09-14 — [x] done.** The
week/month unfold and the day rail/agenda reveal now consume one pure transition
frame derived from the live zoom value. Cold entries seed input ownership from
the stored endpoint instead of publishing the wrong owner until a launched
effect catches up. The accordion rows and selected-date morph remain draw-time,
finger-driven work; no second animation clock was added.

Pure tests cover cold endpoints, reversible shared progress and cancellation
hysteresis. Dedicated device tests launch directly into the split and detailed
month defaults. `test lintDebug assembleDebug` and all 67 API 36 emulator tests
pass; slow and fast expand/reverse gestures plus a short settle-back were
recorded without blank, duplicate or superimposed calendar/day content. The
verified APK was deployed to the approved phone for animation-feel review.

The first phone review rejected that build: the level 2 → 1 path could still
blank the full month, and a week swipe still moved ahead of the title and
timeline. The follow-up fixes the actual owners: a background week preview can
hide the month canvas only inside the compact endpoint where the week strip is
mounted, while the month title and outgoing/incoming day rails now use the live
week-pager offset. The blanking rule has direct unit coverage; emulator frame
captures cover level 2 → 1 and a cross-month week swipe. This remains awaiting
the second phone review.

The next phone review found one final settle race: removing the translated week
preview briefly exposed the previous day before the day pager synchronized.
The settle now requests the destination day page before committing the selected
date and releasing week ownership, using the same atomic handoff as month
paging. Emulator frame review and the focused paging device suite pass.

A subsequent phone review caught a one-frame layer-swap blink after the date
was already correct. The settled preview is now retained until the real day
pager has reached the destination and rendered two frames underneath it, then
removed without animation. A 15-fps settle review, focused paging tests and lint
pass. Ivan accepted the final behavior on the physical phone.

## 8. Fetch-window paging

`CalDavRepository.DefaultWindowMonths` is today ±6 months and does not extend.
Paging past it shows nothing, with no explanation, and search cannot find what
was never fetched.

- Extend the window as the user pages beyond it, or show an honest boundary
  marker. An indicator is the acceptable minimum; silence is not.
- Note that a cached *series* re-expands into any window, so the missing
  records are one-off events outside the fetched range.

## 9. Sync status on the calendar surfaces

`snapshot.sync` is only rendered under Calendars, so a failed refresh is
invisible from the month or agenda view and a stale cache looks identical to a
fresh one. Add a global, quiet sync/staleness indicator to the calendar
surfaces. Deferred item #4 in `HANDOFF.md`.

**Status 2026-09-13 — [x] done.** One marker, in the shared
`CalinoMonthHeading`, so Month/Day, Range and Agenda cannot disagree: a single
dot beside the month/year lockup, rose when the last read failed or came back
partial, neutral when a clean read has aged past `SyncStaleAfter`, a small
spinner while a read is in flight, and **nothing at all** otherwise. Tapping it
opens Calendars — which keeps the timestamp, the warnings and Refresh — and
back returns to the calendar the marker was tapped from rather than always to
Day.

`state/SyncIndicatorRules.kt` holds the whole decision as a pure function
(`syncBadgeFor`) plus the `LocalCalinoSync` composition local that `MainActivity`
provides from `snapshot.sync`; the local exists because every calendar heading
needs the value and no layer between the root and the heading has any business
carrying it. `SyncIndicatorRulesTest` pins the rules, including that a warning
outranks age and that a `fetchedAt` in the future counts as fresh rather than
stale.

Two decisions worth knowing:

- **Staleness is 30 minutes**, because nothing refreshes on a timer — a read
  happens at launch, on connect, and when a person asks. An app left open
  overnight was showing yesterday's calendar with no outward difference from a
  fresh one, which is the failure this exists for. The marker appears on its
  own as `LocalCalinoNow` ticks; it does not wait for an interaction.
- **The marker costs the heading 26dp, not 44dp.** A fourth full-width control
  wrapped "2026" onto its own line on a narrow phone. It keeps a 44dp touch
  lane by overflowing its slot — `Row` does not clip and hit testing uses the
  node's own bounds — and the overflow leans into the title, which is not
  clickable, rather than into the next chevron.

Validated on the API 36 emulator against the local Radicale, in all four
states: absent with no account and after a clean read, rose when the server was
stopped and Refresh failed (with the cached event still on screen), neutral
after the device clock crossed the 30-minute window on its own, and the tap
landing on Calendars and returning to the right root from both Day and Agenda.
Range and Agenda were checked as well as the month/day heading, and the
month/year lockup stays on one line in the tightest case — marker, Today button
and both chevrons at once. 544 unit tests, 54 device tests, plus `lintDebug`
and `assembleDebug`.

## 10. `.ics` and intent integration

- `text/calendar` VIEW intent filter so an `.ics` from mail or a browser opens
  in Calino.
- `SEND` with `text/plain` into Quick Add. The natural-language parser already
  exists; this is close to free.
- Share an event out as `.ics`.
- Calendar `INSERT`/`EDIT` intent handling.
- Import/export, which also gives `allowBackup="false"` an escape hatch for
  local-only state.

**Status 2026-09-13 — [x] done.** Calendar VIEW, shared plain text,
Calendar INSERT/EDIT prefill, event sharing, and reviewed event-only
import/export are implemented. Connected export performs an unbounded VEVENT
query instead of exporting only the normal sync window; import chooses one
writable destination, skips matching UIDs, and uses the durable write queue.
`IcsInteropTest`, `test lintDebug assembleDebug`, and all 56 API 36 device tests
pass; direct SEND and INSERT intents were also inspected on the emulator.

## 11. Search quality

`searchCalino` is substring-only over the loaded window.

- Fuzzy matching, to match the web app's Fuse.js behaviour.
- Filters by calendar, date range, and record type.
- Depends on item 8 for anything outside the fetch window.

**Status 2026-09-13 — [x] done.** Search now uses deterministic, balanced fuzzy
ranking across events, tasks, journals, and contacts: exact/title-prefix/word
matches lead, modest typos and compact ordered subsequences follow, and title
matches outrank metadata. Case, punctuation, whitespace, and diacritics are
normalized without adding a dependency.

The search capsule has an animated filter panel for record type, calendar, and
date. Date choices are Any time, Past, Upcoming, and an inclusive custom range;
active date filters omit undated tasks and contacts. Calendar selection applies
to events and tasks because `JournalEntry` carries no calendar identity. Filters
are intentionally session-local and reset when search closes; date navigation
and Quick Add remain available through every filter combination.

Item 7 remains a real dependency rather than being folded into this change.
Connected-account search says that it covers downloaded calendar data and does
not imply server-wide results. Validated with the search unit tests and on the
API 36 emulator: 56 device tests passing, plus `test lintDebug assembleDebug`.

## 12. Task priorities and percent-complete

`CalTask` models neither; `ICalWriter` writes `PERCENT-COMPLETE` as 0 or 100
only. The web app advertises due dates, priorities, and completion status.

## 13. Recurring tasks

`CalTask` has no recurrence field, so a repeating `VTODO` shows once at its due
date. The web app's wire format is standards-only (`RRULE` plus per-occurrence
completion, no vendor properties) and is already documented in the web repo at
`docs/RECURRING_TASKS.md`, including a per-client interop table. Match it —
do not invent a second format.

## 14. Timezones

`CalEvent` carries no zone; `ICalMapper` coerces everything to the device zone
on read.

- Model the event's own timezone and preserve it through a write.
- Secondary timezone display, as the web app has.

## 15. Year view

The remaining view-parity gap after item 4.

## 16. Keyword auto-categorization

Apply categories automatically from keywords in the title, as the web app does.
Categories already sync through `CATEGORIES`.

## 17. Light-mode contrast

`PaperLight.Ink3` (`#A39D93`) measures about 2.7:1 and fails WCAG AA. The web's
`built-in.css` has already corrected the same tokens to `#655F57` and
`#756D62`. Adopting them changes every light surface, so this is its own pass
with its own emulator review, and `CalinoPaletteTest` exempts light palettes
today.

---

## Lower priority — explicitly deprioritized by the user on 2026-09-12

## 18. Localization

`res/values/` contains only `colors.xml` and `styles.xml`; every user-facing
string is hardcoded in Kotlin. The web app ships `en`, `da`, and `de`.
Extracting strings is also what makes RTL and pseudolocale validation possible,
which is known gap #11 in `HANDOFF.md`.

## 19. Settings sync

Preferences are device-local `SharedPreferences`. The web app syncs them
opt-in through a dedicated hidden calendar on the user's own server; the format
is documented in the web repo at `docs/CALINOSETTINGSSYNC.md`. Match that
format rather than inventing one, and keep it opt-in and off by default.

---

## Android platform integration backlog

This is a separate, priority-ordered list of Android-specific work. It does not
renumber or silently reorder the product backlog above. Work these items one at
a time, with the same review and emulator protocol as the main list. Calendar
Provider integration is a deliberate exception only after its product and data
ownership implications have been reviewed; it must not become an accidental
second synchronization path.

### Android 1. Predictive-back transitions

The manifest opts into predictive back, but Calino's custom navigation and
transient surfaces currently use ordinary Compose `BackHandler` callbacks. A
system back gesture therefore commits the navigation without letting Calino's
own motion follow the finger.

- Use `PredictiveBackHandler` where Calino owns back: the sidebar, search,
  detail/editor surfaces, journal and contact modals, calendar zoom levels, and
  root route changes.
- Drive the existing surface offset, scrim, scale, and reveal state from the
  platform gesture's progress. A cancelled gesture must return through the
  existing gesture-return motion; a completed gesture must hand off cleanly to
  the existing exit transition without restarting or jumping.
- Preserve pointer ownership. System edge-back must not fight horizontal
  calendar paging, vertical zoom, or a modal's own downward-dismiss gesture.
- Keep plain `BackHandler` behavior as the compatibility path where predictive
  progress is unavailable.
- Add device coverage for completion and cancellation, and inspect slow edge
  drags on the API 36 emulator for blank frames, double animation, clipping,
  and route changes that commit before the visual transition.

In Calino, this lets a person peek back from an event editor to its detail,
from detail to the originating day or range, close the sidebar or search, and
collapse calendar zoom levels with the surface visibly tracking their Android
back gesture.

**Status 2026-09-14 — [x] done.** Predictive progress now belongs to the same
owners as each existing interaction: the calendar writes the live back fraction
straight into its zoom continuum; `AdaptiveSurfaceHost` drives modal travel,
scale and scrim; and the sidebar, search capsule and root route host expose
their existing geometry to `PredictiveBackHandler`. Cancellation returns with
`CalinoMotion.gestureReturn()`. Completion retains the final gesture frame and
commits without replaying the ordinary exit transition from rest. The Activity
1.11 handler supplies the ordinary callback path on Android versions where
predictive progress is unavailable.

Root transitions keep their real return destination composed beneath the
outgoing surface and use the Android design guide's decelerated 100-to-90% /
110-to-100% scale plus 35% fade-through. They therefore preview Calendar (or
the actual Settings/Accounts origin) instead of revealing the bare Canvas.

Redundant editor/detail handlers were removed so they cannot steal ownership
from their shared adaptive host. Validated with `test lintDebug assembleDebug`,
24 focused calendar/modal/navigation device tests, and API 36 emulator frame
inspection of a 2.5-second sidebar edge drag plus a cancelled edge drag. The
same debug APK was installed on the approved physical phone for hands-on motion
review.

### Android 2. Background CalDAV/CardDAV sync

Connected accounts currently refresh on app-driven paths rather than through a
persistent Android background schedule. Reminders and widgets can consequently
remain based on old cached data until Calino runs again, and offline writes can
wait unnecessarily for a foreground refresh.

- Use unique, network-constrained WorkManager work for periodic incremental
  CalDAV and CardDAV sync. Keep the cadence honest and configurable rather than
  presenting it as exact; Android decides when periodic work actually runs.
- Enqueue an immediate constrained retry after an offline write and when other
  in-scope triggers establish that cached data needs reconciliation. Coalesce
  work so opening Calino, a manual refresh, and a worker cannot run competing
  refreshes.
- Restore the account and encrypted credential through `CalinoContainer`; do
  not construct parallel stores, queues, caches, or repositories in a worker.
- Replay the durable queue using all existing conditional-write, rebase,
  dead-letter, and move-cleanup rules, then perform incremental reads.
- After a changed snapshot, re-plan reminder alarms and update every widget.
  Surface the last background attempt and Android restrictions without turning
  expected WorkManager delay into an error.
- Resolve main backlog item 8's fetch-window policy as part of the design: a
  background refresh of the same fixed window must not imply that older search
  results or distant widget records are complete.

In Calino, a server-side meeting change can update the agenda widget and its
local alarm before the app is opened, while a task completed offline can leave
the durable queue as soon as network access returns.

### Android 3. Launcher shortcuts

Calino only publishes the optional AI photo-import dynamic shortcut. Add
stable, non-sensitive entry points for the actions people repeat most often.

- Provide launcher shortcuts for Quick Add, Today, New event, New task, and
  Search, respecting the launcher's supported shortcut count and ranking the
  most useful actions first.
- Route every shortcut through the existing single-activity intent handling and
  return-target model. Reusing an existing Activity must not discard an open
  draft or create a duplicate navigation stack.
- Let a person explicitly pin a chosen view or calendar only if its identifier
  remains stable. Disable or update pinned shortcuts when their destination is
  removed, renamed, hidden, or loses access.
- Keep titles and icons free of event, task, contact, or account details because
  shortcut metadata is visible to the launcher.
- Republish dynamic shortcuts after restore or upgrade and report usage so the
  launcher can rank relevant actions.

In Calino, long-pressing the launcher icon can jump straight into today's
agenda, a blank event or task editor, Quick Add, or search instead of opening
the last-used surface and navigating from there.

### Android 4. Calendar Provider and system-account integration

Calino's CalDAV calendars are private to Calino. They do not appear through
Android's `CalendarContract`, so other calendar clients, system surfaces, and
apps that read the device calendar cannot use them. Full integration would make
Calino an Android calendar-account provider, not merely add another intent.

- Begin with a product and architecture review covering account ownership,
  permission disclosure, expected interoperability, and whether Calino or the
  Android provider is authoritative for each local row.
- If approved, add an account authenticator and sync-adapter projection for
  Calino-owned accounts and calendars. Use stable mappings among Android row
  IDs, CalDAV hrefs, UIDs, recurrence identities, and ETags.
- Preserve Calino's existing conditional writes, three-way rebase, recurrence
  patching, durable offline queue, move ordering, and foreign-property rules.
  Provider edits must enter that same pipeline rather than bypass it.
- Prevent feedback loops, duplicate calendars, duplicate alarms, and duplicate
  writes when both Calino and another Android calendar client touch a record.
- Define how tasks, journals, contacts, unsupported iCalendar properties, and
  local fixture data behave; do not silently flatten or discard data the
  Android provider cannot represent.
- Make the feature explicit and reversible. Removing the Android account must
  have a documented effect on Calino credentials, cached resources, queued
  writes, and server data before implementation begins.

In Calino, an opted-in CalDAV calendar could appear in Android's system calendar
store, allowing another calendar app or an Android calendar picker to display
and edit it while Calino remains responsible for safe synchronization with the
CalDAV server.

### Android 5. AppSearch and AppFunctions

Calino has good in-app search, but downloaded records and safe actions are not
available to Android's system search or approved agentic surfaces.

- First index the locally available event, task, journal, and contact models in
  AppSearch. Update or remove documents transactionally when a sync, local
  mutation, calendar-visibility change, or account removal changes the
  snapshot.
- Default to app-private search. Any platform-visible schema or result must be
  separately reviewed for lock-screen exposure, work-profile boundaries,
  contact privacy, hidden calendars, and journal sensitivity.
- Deep-link results back to the exact record and originating date using the
  existing reminder/widget link resolution rather than creating another
  identity scheme.
- After the index and privacy model are proven, evaluate AppFunctions for
  reviewed actions such as creating an event or task, opening today's agenda,
  and searching downloaded records.
- Agent-created changes must show a confirmation or review surface when input
  is ambiguous and must write through `CalinoRepository` and its durable queue.
  Never expose credentials, raw cached calendar/vCard text, or unbounded remote
  search through an app function.
- State result completeness honestly: AppSearch covers downloaded data and
  remains limited by main backlog item 8 until fetch-window paging is solved.

In Calino, Android search could take a person directly to a downloaded event or
task, while an approved system agent could prepare “Dentist next Tuesday at
10” in Calino's existing editor for confirmation and safe queued creation.
