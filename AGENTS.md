# Calino Android agent guide

This repository contains the standalone native Android application formerly
developed as Calino’s native UI POC. It is a Kotlin + Jetpack Compose app. It
does not use the parent Calino repository, WebView, Capacitor, CardDAV, or
webcal.

It **does** now speak real CalDAV, read-only: a connected account's events,
tasks, and journal entries are fetched over HTTPS and replace the fixture data
in every calendar surface. That was previously prohibited here and was opened by
explicit user request. Nothing is written back to the server; see “CalDAV
scope” below.

Read [`HANDOFF.md`](HANDOFF.md) before making substantial changes. It contains
the current feature inventory, known gaps, architecture notes, and the review
plan for the next model.

## Scope

- The product is a UI and interaction prototype that now reads real calendar
  data. With no account connected it still serves the fixture repository, so the
  sample surfaces remain reachable.
- Keep the frozen May 2026 fixture contract unless the task explicitly changes
  it. `FixtureRepository` is unchanged and is still the no-account default.
- Do not add other remote functionality (CardDAV, webcal, telemetry, or any
  other host) beyond the CalDAV read path described below.

## CalDAV scope

Read-only, and deliberately so. What exists:

- `data/caldav/` — OkHttp transport, discovery, fetching, iCalendar mapping,
  error classification, Keystore-backed credential storage, and the on-disk
  read cache (`CalendarCache.kt`).
- `data/repository/CalDavRepository.kt` — a second `CalinoRepository` fed by
  those collections, selected once an account is connected.

What is **not** built, and must not be added without a separate review:

- Writing to the server. The app's write methods apply to an in-memory overlay
  (`LocalOverlay`) that a refetch discards, and the accounts surface says so on
  screen. Do not quietly turn those into `PUT`/`DELETE`.
- `sync-collection` / sync-token incremental sync, the offline change queue,
  ETag `If-Match` conflict handling.

Recurrence **is** expanded, on the client, in `ICalMapper`. The event query no
longer sends `<c:expand>` at all -- see the CalDAV section of `HANDOFF.md` for
the rules that expansion depends on.

Fetched data **is** cached to disk, read-only. `CalDavFetcher` returns the
server's raw resource text and `FileCalendarCache` stores it per calendar, so a
launch renders before any request is made and the app stays readable offline.
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
- Do not edit `<sibling-calino>` or the old
  `<sibling-native-poc>` copy while working here.
- Do not commit credentials, local environment files, keystores, generated
  build output, `.gradle/`, `.kotlin/`, or `local.properties`.

## Project facts

- Gradle root project: `calino_android`
- Application ID: `calino.malinov.ski.poc`
- Launcher label: `Calino POC`
- Version source: `gradle.properties` (`appVersionName`)
- Minimum SDK: 26
- Compile/target SDK: 36
- Main package: `calino.malinov.ski.poc`
- Main activity and route host: `app/src/main/java/calino/malinov/ski/poc/MainActivity.kt`

The package and label still contain `poc`. Treat renaming them as an explicit
release/migration task because it affects installed-app upgrades.

## Architecture

```text
app/src/main/java/calino/malinov/ski/poc/
  MainActivity.kt                  route host and top-level UI state
  data/model/                      event, task, and journal models
  data/parser/                     local Quick Add parser
  data/repository/                 fixture repository and snapshots
  design/                          colors, typography, motion, shapes
  state/                           navigation-state helpers
  ui/components/                   shared Compose components and gestures
  ui/home/HomeScreen.kt            calendar, pagers, zoom, and agenda
  ui/surfaces/                     Tasks, Journal, Settings, calendar accounts, and modals
  util/                            formatting and recurrence helpers
```

`FixtureRepository` is intentionally process-local. It implements the small
`CalinoRepository` interface and supports the local event, task, and journal
mutations needed by the prototype.

`home/CalendarHome.kt` is a deprecated compatibility wrapper. Do not create a
second calendar implementation there; use `ui/home/HomeScreen.kt`.

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

The debug APK is:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The documented emulator is:

```bash
emulator -avd calino-poc-api36
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am force-stop calino.malinov.ski.poc
adb -s emulator-5554 shell am start -W -n calino.malinov.ski.poc/.MainActivity
```

For user-visible changes, build checks are not enough. Use the emulator to
inspect the actual rendered surface, especially after layout or gesture work.
Capture screenshots at rest and during transitions when visual geometry is the
subject of the change. Do not claim physical-phone validation from emulator
results.

The physical phone may be used only when the user explicitly requests a
deployment or phone check. Its current wireless serial is
`physical-device:45095`, but the address can change.

## Testing expectations

Existing unit tests live under `app/src/test/` and cover fixture contracts,
formatting, recurrence, navigation rules, Quick Add parsing, journal drafts,
and task rules.

Add or update tests for user-visible behavior where a behavior-level test is
practical. Prefer assertions about committed dates, route state, visible
content, and semantics over private pixel coordinates. Add Compose/device tests
for high-risk interactions as the test harness is expanded:

- Month cell and week-day date selection.
- Day/week/month paging and cancellation.
- Month-to-week morph target selection.
- Modal/editor downward dismissal and spring-back.
- Quick Add return-target restoration.
- Task completion, reschedule, and undo.
- Journal create/edit/delete and mode changes.
- Settings state retention.
- Dock indicator destinations and accessibility bounds.

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

Do not silently expand scope into sync, server writes, or production release
work. Read-only CalDAV plus the account list and its encrypted credentials is
the agreed extent.

## Git and handoff rules

- Keep commits focused and descriptive.
- Do not rewrite or reset user work destructively.
- Keep generated files ignored.
- Leave the working tree clean when handing off, unless clearly reporting
  intentional uncommitted work.
- Update `HANDOFF.md` for architectural changes, newly completed features, or
  newly discovered risks.
