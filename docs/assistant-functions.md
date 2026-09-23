# Assistant functions and phone search

Calino exposes a small set of Android [AppFunctions](https://developer.android.com/ai/appfunctions)
so an assistant such as Gemini can read the person's calendar and prepare new
records. The API is experimental (`androidx.appfunctions` 1.0.0-alpha11), and
Gemini's support for third-party app functions is in private preview.

## Consent

- **Off by default.** Settings → Data → Assistants → "Let assistants use
  Calino" is the only way on. The row is shown on Android 16 (API 36) and newer.
- **The switch is the service component's enabled state**
  (`AssistantAccess`), not a preference beside it. The manifest ships
  `CalinoAppFunctionService` with `android:enabled="false"`, and Android indexes
  no functions for a disabled service. Off means an assistant cannot call Calino
  or discover that it offers anything. Every function also re-checks the state
  and throws `AppFunctionDisabledException` as defence in depth.
- What an assistant reads may be sent to its own cloud service. The Settings
  description says so.

## What is exposed

| Function | Does |
|---|---|
| `getAgenda(startDate, days)` | Events and tasks on 1–31 days |
| `searchCalendar(query)` | Calino's ranked, typo-tolerant search; events and tasks only, max 20 |
| `openItem(itemId)` | Shows an item in Calino (the `itemId` is Calino's own record deep link) |
| `openDay(date)` | Shows the agenda on a day |
| `draftEvent(title, start, end, allDay, location)` | Opens the event editor prefilled |
| `draftTask(title, dueDate, dueTime)` | Opens the task editor prefilled |

Items carry title, date, start/end or due time, all-day and done flags,
location and calendar name. Nothing else.

## Invariants

- **Never journals, contacts, notes, attendees, credentials, raw iCalendar or
  vCard, server URLs or account identity.** Adding any of them needs its own
  privacy review.
- **Visibility matches the calendar views.** Hidden calendars, and task lists
  kept off the views, are invisible (`AssistantCalendar` uses the same
  `visibleCalendarIds`/`taskCalendarIds`/`EventDateIndex`/`tasksDueOn` as the
  widget).
- **No writes.** Drafts return a `PendingIntent` to Calino's own editor. Only
  the person's Save commits, through the repository and its durable queue.
  Do not add a function that writes, completes, moves or deletes without a
  separate review of confirmation UX.
- **Fixture data is refused.** With no account connected the read functions
  throw `AppFunctionNotSupportedException`; the May 2026 sample calendar is never
  presented as the person's schedule.
- Reads cover downloaded data only, and say so in their KDoc.

## Platform notes

- The build generates the schema with KSP (`appfunctions-compiler`) into
  `calino_app_function_service.xml`, referenced by the service's
  `android.app.appfunctions.v2` property. The KDoc on each `@AppFunction` is the
  description an assistant reads; keep it accurate.
- The library needs AGP 9.1+ and `compileSdk` 37.
- The API 36 emulator image (BE2A.250530) predates the v2 schema: its AppSearch
  indexer reads only the legacy `android.app.appfunctions` property, so nothing
  is indexed there. `AssistantFunctionsTest` therefore runs on API 37 only.
- As of 2026-09-23 there is no usable API 37 emulator: the `android-37.0`
  image (CE2A.260420.019, rev 6) crash-loops in SurfaceFlinger
  (`Assertion failed: !rcEnc->featureInfo()->hasReadColorBufferDma`) on
  emulator 37.1.11 and 37.3.1, in the distrobox and on the host, with every
  `-gpu` mode and with `-feature -GLDMA,-GLDMA2`. So the device-level test has
  not run yet; the read logic is covered by `AssistantCalendarTest`.

## Phone search

A second, separate surface: the phone's own search, such as Samsung Finder's
"Apps to search in". This is Android's legacy global search, not AppSearch or
AppFunctions; Finder queries each app's search suggestion provider.

- Settings → Data → "Show in phone search", off by default and independent of
  the assistant switch. `PhoneSearchAccess` toggles `PhoneSearchProvider`,
  which ships disabled, and the provider also re-checks that state on every
  query: a provider already published in a live process stays reachable after
  its component is disabled.
- Calino is **always listed** as searchable: `PhoneSearchAlias` (an
  `activity-alias` of `MainActivity` with `ACTION_SEARCH` and
  `@xml/searchable`, `includeInGlobalSearch`) is permanently enabled.
  SearchManager rebuilds its list only on whole-package changes
  (`PackageMonitor` ignores single-component changes), so toggling the alias
  left Finder stale until the next update or reboot. Finder's own "Apps to
  search in" toggle is the listing control; Calino's switch controls data.
- The provider is readable only with `android.permission.GLOBAL_SEARCH`,
  which only system search components hold. It is not limited to Finder.
- It returns `AssistantCalendar.search` results, so the same invariants apply:
  events and tasks on visible calendars, never journals, contacts, notes or
  fixture data. Rows carry title, "when · calendar", and Calino's record deep
  link as intent data.
- The authority is `${applicationId}.search`; `searchable.xml` reads it from
  the `calino_search_authority` resValue, which must match per build type.
- Nothing leaves the device. Samsung decides what Finder shows and whether it
  shows anything before unlock. Google has deprecated much of this API;
  if Samsung drops it the provider is simply never queried.
- `PhoneSearchTest` checks the listing and authority through
  `SearchManager.getSearchablesInGlobalSearch`, and that the provider answers
  nothing while switched off. On fixture data the provider is empty anyway, so
  the switched-off check cannot fail there; the in-query gate is what matters
  on a real account.
