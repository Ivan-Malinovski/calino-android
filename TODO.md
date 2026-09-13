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

Items 1–3 and 9 add platform functionality that the current `AGENTS.md` scope
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

## 7. Fetch-window paging

`CalDavRepository.DefaultWindowMonths` is today ±6 months and does not extend.
Paging past it shows nothing, with no explanation, and search cannot find what
was never fetched.

- Extend the window as the user pages beyond it, or show an honest boundary
  marker. An indicator is the acceptable minimum; silence is not.
- Note that a cached *series* re-expands into any window, so the missing
  records are one-off events outside the fetched range.

## 8. Sync status on the calendar surfaces

`snapshot.sync` is only rendered under Calendars, so a failed refresh is
invisible from the month or agenda view and a stale cache looks identical to a
fresh one. Add a global, quiet sync/staleness indicator to the calendar
surfaces. Deferred item #4 in `HANDOFF.md`.

## 9. `.ics` and intent integration

- `text/calendar` VIEW intent filter so an `.ics` from mail or a browser opens
  in Calino.
- `SEND` with `text/plain` into Quick Add. The natural-language parser already
  exists; this is close to free.
- Share an event out as `.ics`.
- Calendar `INSERT`/`EDIT` intent handling.
- Import/export, which also gives `allowBackup="false"` an escape hatch for
  local-only state.

## 10. Search quality

`searchCalino` is substring-only over the loaded window.

- Fuzzy matching, to match the web app's Fuse.js behaviour.
- Filters by calendar, date range, and record type.
- Depends on item 7 for anything outside the fetch window.

## 11. Task priorities and percent-complete

`CalTask` models neither; `ICalWriter` writes `PERCENT-COMPLETE` as 0 or 100
only. The web app advertises due dates, priorities, and completion status.

## 12. Recurring tasks

`CalTask` has no recurrence field, so a repeating `VTODO` shows once at its due
date. The web app's wire format is standards-only (`RRULE` plus per-occurrence
completion, no vendor properties) and is already documented in the web repo at
`docs/RECURRING_TASKS.md`, including a per-client interop table. Match it —
do not invent a second format.

## 13. Timezones

`CalEvent` carries no zone; `ICalMapper` coerces everything to the device zone
on read.

- Model the event's own timezone and preserve it through a write.
- Secondary timezone display, as the web app has.

## 14. Year view

The remaining view-parity gap after item 4.

## 15. Keyword auto-categorization

Apply categories automatically from keywords in the title, as the web app does.
Categories already sync through `CATEGORIES`.

## 16. Light-mode contrast

`PaperLight.Ink3` (`#A39D93`) measures about 2.7:1 and fails WCAG AA. The web's
`built-in.css` has already corrected the same tokens to `#655F57` and
`#756D62`. Adopting them changes every light surface, so this is its own pass
with its own emulator review, and `CalinoPaletteTest` exempts light palettes
today.

---

## Lower priority — explicitly deprioritized by the user on 2026-09-12

## 17. Localization

`res/values/` contains only `colors.xml` and `styles.xml`; every user-facing
string is hardcoded in Kotlin. The web app ships `en`, `da`, and `de`.
Extracting strings is also what makes RTL and pseudolocale validation possible,
which is known gap #11 in `HANDOFF.md`.

## 18. Settings sync

Preferences are device-local `SharedPreferences`. The web app syncs them
opt-in through a dedicated hidden calendar on the user's own server; the format
is documented in the web repo at `docs/CALINOSETTINGSSYNC.md`. Match that
format rather than inventing one, and keep it opt-in and off by default.
