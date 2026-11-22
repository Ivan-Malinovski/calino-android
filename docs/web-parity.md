# Web parity

Calino Android has to be at least 1:1 with the web app (`~/dev/calino`) in
features, feel and polish, and better where the platform allows. This file is the
checklist that claim is measured against, so a gap is a written-down gap rather
than an opinion.

Keep it current: when either app grows a surface, add a row here. When a
difference is deliberate, say so and say why — "deliberate" is a claim that has
to survive review.

## Views

The web has eight views behind a tab strip. Android has the same destinations,
but the calendar itself is one morphing surface with three endpoints (week strip,
split, month grid) instead of separate Week/Month pages, because the zoom gesture
is the way between them.

| Web view | Android | Notes |
|---|---|---|
| Month | Calendar at the month endpoint (zoom 3) | Android's month grid is the paper/ink surface: day dots, category styles, week rail. The web draws chips in a table. |
| Week | Calendar at the week-strip endpoint (zoom 1) + the selected day's agenda | The agenda under the strip is the web's `DayView` role; Android has no separate hour-grid day page. |
| Day | Split endpoint (zoom 2) + the day sheet (`OPEN DAY`) | Web `DayView` is an hour grid; Android's day is the agenda plus a sheet. The hour grid lives in Range. |
| Agenda | `AgendaScreen` root (month list; day pane beside it in landscape) | Same reading list, with a focus line that owns the add pill. |
| Year | `YearScreen` root (twelve month tiles, day dots) | |
| Tasks | `TasksSurface` root | Rows carry the notes excerpt, priority chip, category and due cue, as the web's list does. |
| Journal | `JournalScreen` root | Entries carry the web's tags as category chips (editor, cards, reader) on VJOURNAL `CATEGORIES`, so a tag set on either side survives an edit on the other. |
| Contacts | `ContactsScreen` root | |
| — | **Range** (1 / 3 / 7 days) with the week-task shelf | Android only. The web has no multi-day range surface; planning "sometime this week" happens here. |

## Feature areas

| Web (`src/features/…`) | Android | Verified by |
|---|---|---|
| `caldav` | `data/caldav/` (discovery, fetch, write, patch, conditional writes), `data/repository/CalDavRepository` | `CalDavLiveTest`, `CalDavRepositoryIncrementalTest`, unit suite |
| `carddav` | the same DAV stack (`data/repository`, `data/caldav`) | `CalDavLiveTest` (CardDAV case), unit suite |
| `jmap` | `data/jmap/` (`.well-known/jmap` detection, calendars, scheduling, push, contacts) | `docs/jmap-handoff.md`, unit suite |
| `nlp` | `data/parser/PocQuickAddParser.kt` | `PocQuickAddParserTest`, `PocQuickAddParserNaturalLanguageTest` |
| `commandPalette` | the search sheet (`ui/surfaces/SearchSheet.kt`) plus named commands and the pill's view menu | `SearchCommandsTest`, `SearchCommandsUiTest`, `SearchQualityTest` |
| `onboarding` | the first-run tour (`state/CalinoTour.kt`, `ui/tour/`) | `CalinoTourTest`, `TourTest`, `TourOfferFromRangeTest` |
| `settings` | `ui/surfaces/SettingsScreen.kt` and its sub-screens | `SettingsRetentionTest`, `NavigationDestinationsTest` |
| `setup` (accounts) | the account sheet in `ui/surfaces/CalDavScreen.kt`, plus the account authenticator | live suite, manual against Radicale |
| `webcal` | `ui/surfaces/WebcalSubscribe.kt` + `data/sync` | unit suite |
| `aiVision` | `data/ai/` (photo import; key in Android Keystore) | unit suite, manual |
| `webmcp` | **Web-only by nature.** Android answers it with AppFunctions (`platform/assistant/`) and the phone-search provider (`platform/search/`) | `AssistantFunctionsTest`, `PhoneSearchTest`, `PrivateAppSearchIndexTest` |

Web-shell concerns have no Android counterpart by design: cookie consent (no
cookies), the MCP server (above), and the browser error boundary (Android reports
write failures in the pill lane and Settings → Calendars & sync).

## Native extras the web cannot have

- Home-screen widgets (`widget/`): scrolling agenda, size-aware, themed.
- Wear OS companion (`wear/`): the phone stays authoritative; local Data Layer only.
- Android Calendar Provider: outbound projection (`platform/CalendarProjection*`)
  and device-calendar import/write-back (`docs/calendar-provider.md`).
- AppFunctions and phone search (Samsung Finder and similar), both off by default.
- Reminders and notifications with actions, overdue-task nagging (`notify/`).
- Undo offers and the 30-day local trash.
- The offline queue: a write lands locally and flushes on the next sync
  (verified end to end — see `HANDOFF.md`, "Offline, verified end to end").
- First-run tour, keyboard shortcuts (⌘/Ctrl+K, ⌘/Ctrl+N, Esc), a generated
  baseline profile, and the whole paper/ink visual language.

## Deliberate divergences

These are decisions, not omissions:

- **The paper/ink design language** (Krellis heritage) replaces the web's plain
  app shell. Same information, different voice.
- **One morphing calendar** instead of Month/Week pages: the zoom gesture is the
  navigation. Range is the multi-day surface.
- **Actions live on the pill**, not in toolbars: save, delete, dismiss and undo
  are the same object changing shape, with gestures for the frequent ones.
- **The tour teaches gestures the web does not have** (hold to create, slide the
  time, swipe or hold the pill), so it has no web equivalent to copy.
