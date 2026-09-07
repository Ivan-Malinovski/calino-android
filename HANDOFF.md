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

## What currently works

### Calendar

`ui/home/HomeScreen.kt` is the primary calendar surface. It currently includes:

- A calendar header with month navigation, current month/year context, week
  context, and a Today action.
- A detailed month grid with weekday headings, out-of-month days, event marks,
  all-day indicators, journal indicators, and event density handling.
- A compact month/day-agenda state in which the selected week is reused from the
  month surface while the other weeks collapse away.
- A week-strip state with seven day cells, event dots/marks, a moving selected
  day indicator, and horizontal paging.
- A selected-day agenda/timeline with hour rails, event cards, all-day area,
  current-time line, and event click targets.
- Three zoom levels driven by vertical drag, with spring settling and a tap
  handle. The month-to-week transition is intended to morph the existing month
  surface instead of replacing it with a separate bar.
- Horizontal date/week/month paging with committed selection state separated
  from in-progress pager preview state.
- Day selection from month cells, week cells, and agenda navigation.
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
- Event editing through the UI-only editor dialog.
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
date, time, duration, and location. The UI also exposes title/body editing,
calendar/color choices, and save/cancel actions. The parser is deliberately
local and simplified; it is not a CalDAV or natural-language production
implementation.

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

The current working tree includes a scoped visual polish pass in
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
- Event editor state
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
  ui/surfaces/                     Journal, Tasks, Settings, modal surfaces
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
