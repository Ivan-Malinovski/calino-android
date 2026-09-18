# Calino Android agent guide

This repository contains the standalone native Android application formerly
developed as Calino’s native UI POC. It is a Kotlin + Jetpack Compose app. It
does not use the parent Calino repository, WebView, Capacitor, or webcal.

It **does** now speak real CalDAV and CardDAV: a connected account's events,
tasks, journal entries, and address-book contacts are fetched over HTTPS and
replace the fixture data in every relevant surface. Writes use conditional
CalDAV/CardDAV requests, recurrence-safe patching, a durable offline queue,
and incremental sync; see the CalDAV and CardDAV sections below.

Read [`HANDOFF.md`](HANDOFF.md) before making substantial changes. It contains
the current feature inventory, known gaps, architecture notes, and the review
plan for the next model.

[`TODO.md`](TODO.md) is the agreed, priority-ordered product backlog. Unless the
user asks for something specific, take the next unfinished item from the top of
that list rather than choosing your own. Work one item at a time and mark it
done only after the review protocol below has been followed.

## Scope

- The product is a UI and interaction prototype that now reads and writes real
  calendar data. With no account connected it still serves the fixture
  repository, so the sample surfaces remain reachable.
- Keep the frozen May 2026 fixture contract unless the task explicitly changes
  it. `FixtureRepository` is unchanged and is still the no-account default.
- Do not add other remote functionality (webcal, telemetry, or any other host)
  beyond the CalDAV and CardDAV read/write paths described below and the two
  sanctioned exceptions that follow.
- As of 2026-09-12 the user has explicitly approved the platform work in
  [`TODO.md`](TODO.md) items 1–3 and 10: `VALARM` round-tripping, local
  notification delivery and alarm scheduling, a home screen widget, and `.ics`
  and intent integration. (Item 9 is sync status on the calendar surfaces, a
  local concern; the `.ics` and intent work is item 10.)
- The optional AI photo import is the other exception. `data/ai/AiVision.kt`
  and `ui/surfaces/AiVisionSurfaces.kt` post a photo to an AI provider the
  person configures themselves — Anthropic, OpenAI, or a custom base URL — and
  turn the reply into a draft event. It is off until someone enters a provider
  and key, the key is stored Keystore-encrypted like DAV credentials, and no
  calendar data leaves the device unless the person imports a photo. Do not
  widen it into background or automatic model calls, and never log or commit a
  key.
- As of 2026-09-18 the user has approved Android platform backlog item 4,
  Calendar Provider and system-account integration: an account authenticator
  and a two-way sync adapter that project opted-in CalDAV calendars into
  Android's `CalendarContract` and ingest edits made elsewhere back through the
  existing write pipeline. It adds no remote host and no outbound traffic; it
  is a local projection of data Calino already holds. The product and
  architecture review the item requires is
  [`docs/calendar-provider.md`](docs/calendar-provider.md), and its decisions —
  ownership scoping, what is deliberately not projected, reminder ownership,
  and removal semantics — are binding on later changes.
- Those three are the only sanctioned exceptions; webcal, telemetry, and other
  remote hosts remain out of scope.

## CalDAV scope

Read/write, with an explicit offline and conflict policy. What exists:

- `data/caldav/` — OkHttp transport, discovery, fetching, iCalendar mapping,
  conditional writers, recurrence patching, RFC 6578 incremental sync, error
  classification, Keystore-backed credential storage, and the on-disk raw
  resource cache (`CalendarCache.kt`).
- `data/repository/CalDavRepository.kt` — a second `CalinoRepository` fed by
  those collections, selected once an account is connected. It applies
  optimistic edits, replays the durable queue, and reconciles server deltas.
- `data/repository/WriteQueue.kt` — atomic JSON persistence for CREATE, UPDATE,
  DELETE, MOVE, and source-cleanup operations, with bounded retries and
  dead-letter recovery from the Accounts surface.

Write rules that are intentionally fixed:

- Existing resources are patched from the raw cache and sent with their ETag;
  a stale ETag is refreshed and the modeled local fields are rebased onto the
  current server resource while foreign properties are preserved. Do not add
  timestamp-based conflict guesses: the conflict helper compares iCalendar
  `SEQUENCE` and the documented policy is local-wins after a safe rebase.
- Queued UPDATE entries retain the original raw server representation as
  `baseData`; replay uses it for a three-way rebase after a 412. Legacy UPDATE
  entries without that base are rejected safely instead of overwriting a newer
  server resource. Queued CREATE edits coalesce into their existing FIFO slot.
- A queued CREATE that receives a 412 is verified with a GET before it becomes
  a dead letter: an exact payload match acknowledges a lost response, a missing
  resource retries the create, and a different payload is reported as a real
  collision. The same rule applies to CardDAV contacts.
- A cross-calendar move writes the destination first, then conditionally
  removes the source. A failed source cleanup remains a distinct queued
  `DELETE_HREF` item; never delete the source merely because a destination
  write failed.
- Queued moves retain the source raw payload. Source cleanup and destination
  recovery entries are inserted before dependent later writes, and cleanup
  stays conditional on the source ETag (or is left for explicit recovery when
  that proof is unavailable).
- `LocalOverlay` is optimistic process state, not a second durable cache. The
  queue is durable; an edit that cannot sync is removed if its dead letter is
  discarded.

Recurrence **is** expanded, on the client, in `ICalMapper`. The event query no
longer sends `<c:expand>` at all -- see the CalDAV section of `HANDOFF.md` for
the rules that expansion depends on.

Fetched data **is** cached to disk, read-only as cache content. `CalDavFetcher`
returns the server's raw resource text and `FileCalendarCache` stores it per
calendar, so a launch renders before any request is made and the app stays
readable offline.
What is cached is iCalendar text, never mapped occurrences -- that is what lets
a series re-expand as the window moves. `LocalOverlay` is **not** cached, on
purpose: an edit that cannot sync must not look durable. See "The read cache"
in `HANDOFF.md` before changing any of it.

Credentials: the password lives only in the sheet's draft state and in
`KeystoreCredentialStore`, encrypted under an Android Keystore key. Nothing
secret reaches the read cache -- it holds resource text only.
`CalDavAccount` has no password field, and nothing secret is written to the
account JSON. Never log a password, and never commit real credentials — the
live test reads them from the environment.
- Do not edit sibling Calino checkouts or the old `android-native-poc` copy
  while working here.
- Do not commit credentials, local environment files, keystores, generated
  build output, `.gradle/`, `.kotlin/`, or `local.properties`.

## CardDAV scope

Read/write, with the same durable queue and cache rules. What exists:

- `data/caldav/CardDavDiscovery.kt` — independent address-book-home-set
  discovery and depth-1 address-book collection listing.
- `data/caldav/CardDavFetcher.kt` and `VCardMapper.kt` — addressbook-query
  reads of raw vCards and ez-vcard mapping into the contact model.
- `CardDavWriter.kt` and `VCardWriter.kt` — conditional vCard create/update/
  delete with raw-property preservation where the model does not edit a field.
- Address-book raw-vCard cache entries under the same private `filesDir` cache
  used by calendars. Credentials remain in the Keystore; vCard cache content
  contains no password or other credential material.
- `data/repository/CalDavRepository.kt` — CardDAV sources are merged into the
  same `CalinoSnapshot`, contact writes use the server writer, and failed
  writes use the same durable queue.
- Malformed or multi-vCard resources are treated as partial/non-authoritative
  reads rather than silently replacing a valid cached address book. Cache file
  mutations are synchronized so a refresh cannot race a write or eviction.

What is not built, and must not be added without a separate review:

- No vCard import/export, duplicate merging, contact picker, or group-membership
  editing in v1.
- Contact-derived birthday/anniversary reminder events remain local app
  conveniences; they are not silently written as VEVENTs to the server.

## Project facts

- Gradle root project: `calino_android`
- Application ID: `calino.malinov.ski`
- Launcher label: `Calino`
- Version source: `gradle.properties` (`appVersionName`)
- Minimum SDK: 26
- Compile/target SDK: 36
- Main package: `calino.malinov.ski`
- Main activity and route host: `app/src/main/java/calino/malinov/ski/MainActivity.kt`

The former `calino.malinov.ski.poc` identity was retired before distribution.
Changing `calino.malinov.ski` again is an explicit release/migration task
because it affects installed-app upgrades.

## Architecture

```text
app/src/main/java/calino/malinov/ski/
  MainActivity.kt                  route host and top-level UI state
  data/model/                      event, task, and journal models
  data/parser/                     local Quick Add parser
  data/repository/                 fixture repository and snapshots
  data/caldav/                     DAV transport, mapping, cache, and writers
  data/ical/                       `.ics` import and export interop
  data/search/                     the deterministic fuzzy search index
  data/ai/                         the optional AI photo-import client
  design/                          colors, typography, motion, shapes
  state/                           navigation-state helpers
  qa/                              state rules asserted by tests
  ui/components/                   shared Compose components and gestures
  ui/home/HomeScreen.kt            calendar, pagers, zoom, and agenda
  ui/range/                        the 1-day, 3-day, and 7-day range views
  ui/surfaces/                     Tasks, Journal, Settings, calendar accounts, and modals
  notify/                          reminder planning, scheduling, and delivery
  widget/                          the Glance home screen agenda widget
  data/CalinoContainer.kt          the process-wide data layer
  util/                            formatting and recurrence helpers
```

`CalinoContainer` owns the repositories, caches, stores and queues.
`PocRepositoryViewModel` is a Compose-state facade over it and constructs
nothing. That split exists because a notification action runs in a
`BroadcastReceiver` with no Activity and must write through the same durable
queue; do not reintroduce a second instance of any store.

`FixtureRepository` is intentionally process-local. It implements the small
`CalinoRepository` interface and supports the local event, task, and journal
mutations needed by the prototype.

`ui/home/HomeScreen.kt` is the only calendar implementation. The deprecated
`home/CalendarHome.kt` wrapper was deleted; do not reintroduce it or any second
calendar surface.

## UI requirements

Animation is a product requirement, not decoration. Every visible state change
should have an intentional transition:

- Animate enter and exit states; do not pop surfaces in or out.
- Animate selected indicators physically between destinations.
- Keep pager preview content mounted before a swipe animation begins.
- Let drag surfaces follow the finger and spring back on cancellation.
- Do not let a settle animation and a finger-follow animation write the same
  offset at the same time.
- Keep committed date/route state separate from in-progress preview state.
- Preserve smoothness during calendar zoom and boundary transitions.

Use the existing motion tokens in `design/CalinoTheme.kt` / `CalinoMotion`.
Reuse shared components in `ui/components/` instead of creating one-off
controls with subtly different geometry.

Interactive controls should have at least a 44dp touch lane even when their
painted visual is compact. Every selectable/clickable surface needs useful
semantics and a meaningful content description.

## Gesture ownership

Before changing a gesture, identify which component owns the pointer stream.
The major shared gesture primitive is `SwipeDownDismiss` in
`ui/components/CalinoComponents.kt`.

Pay special attention to conflicts between:

- HorizontalPager and vertical zoom gestures.
- LazyColumn scrolling and full-surface downward dismissal.
- Pager preview position and committed selected-date state.
- Child modal drag offsets and host exit animations.

Test both slow drags and fast flings, plus cancelled drags. A gesture that only
works at one velocity or starting position is not finished.

## Required checks

Use the Android SDK inside the local `android-sdk` Distrobox when available.
From the repository root, the normal focused/full check is:

```bash
distrobox enter android-sdk -- bash -lc './gradlew test lintDebug assembleDebug'
```

The instrumented suite is a **separate** check, because it needs a booted
emulator. Run it before a handoff that touches UI:

```bash
distrobox enter android-sdk -- bash -lc './gradlew :app:connectedDebugAndroidTest'
```

The debug APK is:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The documented emulator is:

```bash
emulator -avd calino-poc-api36
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am force-stop calino.malinov.ski
adb -s emulator-5554 shell am start -W -n calino.malinov.ski/.MainActivity
```

For user-visible changes, build checks are not enough. Use the emulator to
inspect the actual rendered surface, especially after layout or gesture work.
Capture screenshots at rest and during transitions when visual geometry is the
subject of the change. Do not claim physical-phone validation from emulator
results.

### A server to test against

`scripts/live-caldav/radicale.sh start` runs a throwaway Radicale on this
machine, in its own virtualenv, with its own gitignored storage. Use it for the
live tests and for any emulator check that needs a real account; never point a
live test at a real calendar. `scripts/live-caldav/README.md` has the addresses,
the credentials, and how to plant a fixture resource by hand.

The emulator reaches it at `http://10.0.2.2:5232/`. Plain HTTP works only
because the **debug** source set permits cleartext to three loopback addresses
(`app/src/debug/res/xml/network_security_config.xml`); release builds are
unchanged and still refuse it everywhere.

The physical phone may be used only when the user explicitly requests a
deployment or phone check. Obtain its current wireless serial from
`adb devices`; addresses can change.

## Testing expectations

Existing unit tests live under `app/src/test/` and cover fixture contracts,
formatting, recurrence, navigation rules, Quick Add parsing, journal drafts,
and task rules.

Compose device tests live under `app/src/androidTest/` and cover the high-risk
interactions. They run on the fixture repository with no account connected,
which is what makes them deterministic: the clock is frozen at 2026-05-18 and
`PagerEpoch` is the same date. `CalinoUiTest` is the base class; read its
doc comment and the "Device tests" section of `HANDOFF.md` before adding to it.

- `CalendarDateSelectionTest` — month cell and week-day date selection.
- `CalendarPagingTest` — day/week/month paging and cancellation.
- `CalendarZoomMorphTest` — month-to-week morph target selection.
- `ModalDismissalTest` — modal/editor downward dismissal and spring-back.
- `QuickAddReturnTargetTest` — Quick Add return-target restoration.
- `TaskInteractionTest` — task completion, reschedule, and undo.
- `JournalFlowTest` — journal create/edit/delete and mode changes.
- `SettingsRetentionTest` — settings state retention.
- `NavigationDestinationsTest` — navigation destinations and accessibility
  bounds. (This bullet used to read "dock indicator destinations"; there is no
  dock. Navigation is the sidebar.)
- `AgendaFocusDateTest` — which date the agenda keeps focus on.
- `CalendarColdEntryTest` — first-frame calendar state on a cold start.
- `RangeInteractionTest` — the 1-day, 3-day, and 7-day range views.
- `SearchQualityTest` — search ranking and result content.
- `SplitDayPanePagingTest` — paging inside the split day pane.

`CalinoTestActions.kt` holds the shared gesture and assertion helpers; prefer
extending it over re-deriving a drag in a new test.

Add or update tests for user-visible behavior where a behavior-level test is
practical. Prefer assertions about committed dates, route state, visible
content, and semantics over private pixel coordinates.

## Review protocol

After implementing a meaningful UI change:

1. Review the diff for state ownership and gesture conflicts.
2. Run the smallest relevant unit/compile/lint check.
3. Run the full `test lintDebug assembleDebug` check for a handoff build.
4. Inspect the changed surface on the API 36 emulator.
5. Review animation frames for blank pages, jumps, snap-back, clipping, and
   jank.
6. Re-read `HANDOFF.md` and update it when feature state or known gaps change.
7. Report changed files, checks, emulator/phone validation, and any remaining
   uncertainty.

Do not silently expand scope into webcal, telemetry, other hosts, or production
release work. CalDAV/CardDAV writes, queueing, incremental sync, and the account
list with encrypted credentials are the agreed extent.

## Git and handoff rules

- Keep commits focused and descriptive.
- Do not rewrite or reset user work destructively.
- Keep generated files ignored.
- Leave the working tree clean when handing off, unless clearly reporting
  intentional uncommitted work.
- Update `HANDOFF.md` for architectural changes, newly completed features, or
  newly discovered risks.
