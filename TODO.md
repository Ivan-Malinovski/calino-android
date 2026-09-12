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

## 3. Home screen widget

- A Glance widget showing the agenda for today and the near future.
- Tap targets open the app on the relevant date/record.
- Respect calendar visibility and the Journal/Contacts availability flags.
- Update on sync and on date change without a background poll loop.

## 4. 3-day and 7-day range views

`CalinoDefaultView` currently has Month, Week, and Day only.

- Add 3-day and 7-day range views, matching the web app's 3-day view.
- They must participate in the existing zoom lane and horizontal paging rather
  than becoming a separate screen. Read the gesture-ownership section of
  `AGENTS.md` first: this touches the pager/zoom arbitration directly.
- Extend `CalinoDefaultView` so a range view can be the default view, and make
  sure the persisted preference migrates cleanly.

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
