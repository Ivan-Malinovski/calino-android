# Calino Android agent guide

This is the standalone Kotlin/Jetpack Compose Android app. It does not use the parent Calino repository, WebView, Capacitor, or webcal.

Before substantial work, read [`HANDOFF.md`](HANDOFF.md) for current behavior, architecture and known gaps. [`TODO.md`](TODO.md) is the priority-ordered backlog; unless the user specifies a task, take its next unfinished item and work one item at a time.

## Scope and privacy

- Keep the frozen May 2026 fixture contract. With no account connected, `FixtureRepository` remains the default.
- Remote calendar/contact access is CalDAV and CardDAV only. Do not add webcal, telemetry, or other hosts.
- Approved exceptions are narrowly scoped: user-triggered AI photo import to the provider the person configured; the in-app stable-release check against Calino's GitHub repository; and local Android integrations (`CalendarContract`, `.ics`/intents, reminders, widget, and Wear OS Data Layer). They must not become general networking or send calendar/account data to new services.
- Assistant access (AppFunctions) is off by default and turned on only by the person in Settings. It exposes downloaded events and tasks on visible calendars, and drafts that open Calino's editor; never journals, contacts, notes, attendees, credentials, raw iCalendar or server data, and never a write without the person's Save. Phone search (a `GLOBAL_SEARCH`-guarded suggestion provider, for Samsung Finder and similar) follows the same rules, stays on the device, and has its own switch, also off by default. Rules for both are in [`docs/assistant-functions.md`](docs/assistant-functions.md).
- AI photo import stays opt-in and foreground-only. Keep its key encrypted in Android Keystore; never log or commit keys.
- Android device-calendar import/write rules are in [`docs/calendar-provider.md`](docs/calendar-provider.md). Keep imported calendars off by default, enable writes only for provider-writable calendars (with a per-calendar opt-out), keep those writes out of the CalDAV queue, and exclude Calino's own projection using the documented account-type predicate.
- Wear OS uses only the local Data Layer. The phone stays authoritative; the watch gets no direct network/DAV access, credentials, contacts, journals, or reminder ownership. See `docs/wear-companion.md`.
- The release application ID is `calino.malinov.ski`. Changing it requires an explicit migration/release task.
- Never commit credentials, local environment files, keystores, generated build output, `.gradle/`, `.kotlin/`, or `local.properties`. Do not edit sibling Calino checkouts or the old `android-native-poc` copy.

## Shared state and data ownership

`CalinoContainer` owns repositories, caches, credential stores, queues, reminder scheduling and widget bridges. `PocRepositoryViewModel` is a state facade, not a place to construct parallel stores: notification receivers write without an Activity and must use the same durable queue.

`FixtureRepository` is process-local. `CalDavRepository` is selected for a connected account. Keep `MainActivity.kt` as the route host and `ui/home/HomeScreen.kt` as the only calendar implementation.

## CalDAV and CardDAV invariants

- Keep server resources conditional. Patch existing records from raw cached bytes and their ETags. After 412, refresh bytes and validator, then safely rebase modeled fields while preserving foreign properties. Use iCalendar `SEQUENCE`, not timestamps, for conflict policy; local-wins applies only after a safe rebase.
- Queued UPDATEs retain their original raw `baseData` for three-way rebase. Reject legacy entries without it. Verify a queued CREATE after 412 with GET before treating it as a collision or retry.
- A cross-calendar move writes the destination first, then conditionally removes the source. Keep failed cleanup as its own queued `DELETE_HREF`; never delete the source after a failed destination write. Preserve the source payload and ETag proof for recovery.
- `LocalOverlay` is optimistic process state, not durable storage. The write queue is durable; discarding its dead letter must remove an edit that never synced.
- Cache raw iCalendar/vCard text, not mapped occurrences. This permits recurrence re-expansion and preserves properties Calino does not model. Cache contents are read-only; credentials never belong in the cache.
- Client-side recurrence expansion lives in `ICalMapper`; do not restore server `<c:expand>` requests.
- CardDAV uses the same conditional-write, cache and queue protections. Do not add vCard import/export, duplicate merging, contact picking or group editing without separate review. Contact-derived reminder events remain local.

Credentials live in `KeystoreCredentialStore`; account JSON and logs must not contain passwords. Live tests use environment variables and the throwaway Radicale server described in `scripts/live-caldav/README.md`, never a real calendar.

## UI, animation and gestures

Animation is required for visible state changes. Use `CalinoMotion` and shared components from `ui/components/`; do not add subtly different one-off controls. Keep committed route/date state separate from gesture preview state, mount pager preview content before swipes, and animate enter/exit states. Controls need a 44dp touch lane and useful accessibility semantics.

Before changing a gesture, identify its pointer-stream owner. Check conflicts between `HorizontalPager` and zoom, list scrolling and downward dismissal, preview and committed pager state, and child drag offsets and host exit animations. Test slow drags, fast flings and cancellation.

## Tests and handoff

- Markdown-only/documentation-only edits do not require Gradle builds or tests: `.md` files are not Android source/resource inputs. Do not launch a rebuild for a diff containing only Markdown.
- Prefer behavior-level assertions for committed dates/routes, visible content and semantics over private coordinates. Device tests use fixtures with the clock and pager epoch frozen at 2026-05-18; read `CalinoUiTest` and the device-test notes in `HANDOFF.md` before extending them.
- Use the Android SDK in the `android-sdk` Distrobox when available. From the repository root, run the focused checks and handoff build:

  ```bash
  distrobox enter android-sdk -- bash -lc './gradlew test lintDebug assembleDebug'
  ```

- Instrumented tests are separate and require the pinned emulator. The `ANDROID_SERIAL` pin is mandatory so tests cannot run on an attached phone:

  ```bash
  distrobox enter android-sdk -- bash -lc 'ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest'
  ```

- For user-visible UI changes, inspect the rendered surface and relevant animation frames on the API 36 emulator; do not claim phone validation from emulator results. Use a physical phone only when explicitly requested.
- Review the diff for ownership and gesture conflicts. Update `HANDOFF.md` when an architectural invariant, active risk, or operational instruction changes. Report changed files, checks, device validation and uncertainty.

Keep commits focused and descriptive, preserve user work, keep generated files ignored, and leave the worktree clean unless intentional changes are reported.
