# Changelog

Notable changes per release. Releases before 0.2.1 are recorded in the git
history and their tags.

## Unreleased

### Added

- **Menu pill.** The root add pill gains a view button showing the current view's icon. Tap it or swipe up from it to open a view menu (swipe up and release on a view to jump straight there; swipe the menu down to dismiss). Hold it for a dock with every view one tap away and an add button for events, tasks and journal entries. The label keeps its tap-to-add, sideways swipe between views and swipe up to search. Settings → **Menu pill** turns it off to keep the swipe-only pill.

### Changed

- **Android 12 or newer is now required.** Search uses Android's built-in AppSearch instead of a bundled copy, which brings the APK back to about 4.4 MB. The search index is rebuilt after updating and stays private to Calino.

## 0.5.0 — 2026-09-22

Calino 0.5.0 adds an experimental Wear OS companion, native Markdown rendering for event descriptions and task notes, and clearer recovery after a rejected task write.

### Added

- **Experimental Wear OS companion.** A paired watch receives a bounded agenda and open-task snapshot from the phone over the local Wear Data Layer. It includes Agenda and Tasks views, record details, a Tile, a complication, and task Complete/Tomorrow actions that the phone validates and applies.
- **Native Markdown in event descriptions and task notes.** Safe web/mail links and GFM task-list checkboxes render in the detail surfaces; checkbox edits use the existing calendar write path.
- **ADB sideload for Wear OS.** The separate `calino-wear-release.apk` is not distributed through Google Play. Connect ADB to a compatible watch and install it with `adb -s <watch-serial> install -r calino-wear-release.apk`.

### Fixed

- Rejected task saves and deletes restore the editor with the draft intact instead of leaving an invisible modal overlay blocking the app.
- An HTTP 404 during a task update no longer claims that the whole calendar disappeared; it identifies the missing calendar-or-task and asks the person to refresh.

### Known limitations

- The Wear OS companion is an experimental personal project made to try Calino with a Pixel Watch. Its future and long-term maintenance are uncertain; the phone remains the only CalDAV client and source of truth.
- The reported Nextcloud task-update 404 can still prevent a task change from syncing; this release fixes the resulting app freeze but has not established why that server returns 404. (#6)
- Calino-local reminders for events in read-only calendars are not yet supported. (#5)

## 0.4.0 — 2026-09-21

### Added

- **Edit opted-in device calendars.** Google, Exchange, and other calendars
  imported from Android's Calendar Provider are writable by default when the
  provider permits it, and writing can be turned off one calendar at a time.
  Their creates, edits, occurrence changes, and deletions
  go directly through the owning provider and never enter Calino's CalDAV
  queue.
- **In-app update notices.** Calino can check its own GitHub releases for a
  newer stable version and open the matching release page. The check sends no
  calendar or account data.

### Fixed

- Device-calendar writes now verify ownership, runtime permission, and stale
  provider state; preserve unsupported reminder rows; reject fields Android
  cannot represent; and apply related event and reminder mutations atomically.
- Recurring device events now handle detached and canceled occurrences without
  offering the unsupported “this and future” scope or silently colliding with
  an exception created by another calendar app.
- Compact-week paging keeps its selected-day handoff continuous during both
  swipe and tap transitions.
- Shared `.ics` files with common non-conforming details import more
  tolerantly without weakening normal calendar parsing.

### Known limitations

- **Android Calendar Provider write-back is still lightly field-tested.** It
  has automated coverage, but has not yet seen much everyday use across the
  range of Google, Exchange, and manufacturer calendar providers. For now,
  keeping an imported calendar's **Allow editing in Calino** toggle off is the
  recommended conservative choice; the calendar will remain visible in
  Calino but read-only.

## 0.3.0 — 2026-09-20

### Fixed

- **Self-hosted DAV servers can use a CA installed by the phone owner.** Calino
  now honors Android's user certificate store as well as its system roots, so
  a private CalDAV or CardDAV server works after its CA certificate is
  deliberately installed on the device. Certificate-chain and hostname
  validation still apply, and release builds continue to reject cleartext
  connections. (#4)

## 0.2.2 — 2026-09-20

### Added

- **A journal month overview.** The All-entries / By-month control is replaced
  by a month scrubber that opens into a month grid, using the calendar's own
  zoom vocabulary. The level never filters the list — a tap scrolls to the
  month instead.
- **An entry can change its day.** The journal editor gained a date chip on the
  Write/Preview line, and a moved entry travels the write path that already
  existed, replacing `DTSTART` on the server's VJOURNAL.
- **Swipe between journal entries.** The read modal pages like the event
  detail does, so the two chevrons in its footer are gone.
- **A markdown toolbar and a preview.** The journal editor has a
  selection-aware toolbar and a Write/Preview control.

### Fixed

- **A repeating task that carries only a due date now shows up.** A VTODO with
  an `RRULE` but no `DTSTART` — what tasks.org and other task clients emit —
  expanded to nothing, and the task vanished from every surface instead of
  merely losing its repeats. One-time tasks were never affected. (#3)
- The journal month grid is clipped while it unrolls. It used to draw straight
  through the handle, the month rule and the list cards beneath it for the
  whole drag.
- The sticky month rule no longer ignores the first visible entry, so an entry
  moved into a new month updates the rule and the scrubber without waiting for
  a scroll.

### Changed

- Journal entry cards carry an accent spine and a word count, and drop the date
  line the date column already states.
- The journal read modal reads as an editorial page: an accent date line, a
  headline title, and words-and-read-time meta between rules.
- The journal month block closes on the same hairline the day group headers
  use elsewhere.
- Event and task card interactions were refined.

## 0.2.1 — 2026-09-19

### Added

- **Show the phone's own calendars.** Settings → Calendars lists the calendars
  other apps on the device already sync — Google, Exchange and any others — and
  a toggle each. An imported calendar is read-only wherever it appears: the app
  that owns a calendar is the one that may change it.
- **An imported calendar is quiet by default.** Whoever owns it is already
  notifying for it, and Calino cannot silence them, so *Also remind me in
  Calino* is the person's deliberate opt-in per calendar rather than two
  notifications for one event.
- **Let other calendar apps see Calino's events.** Opted-in CalDAV calendars are
  projected into Android's `CalendarContract` under a Calino-owned account, so
  the system calendar picker, a watch face, Android Auto or a third-party widget
  can read them, and an edit made in another app is ingested back through
  Calino's own write pipeline. `docs/calendar-provider.md` records the ownership
  rules this follows and the risks the design had to answer by construction.

### Fixed

- A side panel's card draws its whole shadow. The event preview puts each event
  on a pager page, and a pager clips its pages, so the shadow used to end at a
  hard vertical line a few dp out from the card.
- An event swiped out of a side panel fades out at that same edge instead of
  being cut by it and vanishing an edge at a time.
- A modal's status-bar slice now follows a dismissal drag the whole way with the
  scrim below it, rather than clearing in a single frame as the drag begins. The
  scrims no longer ripple when tapped.
- A contact card's list, and the editor's, runs the full height of its card and
  passes under the floating pill, instead of stopping a lane's height short
  against a band of empty card.
- A month cell no longer drops a card to make room for the "+n" line: the line
  is charged as the 14dp of text it is rather than as a whole card.

### Changed

- Creating an event on empty timeline space is a press and hold, not a double
  tap. A scroll or a page swipe that begins on blank space is left alone.
- A multi-day range's add pill reads "New event" rather than naming a day nobody
  picked, and opens a blank editor anchored on the first day on screen.
- The fully expanded month does the same. The tablet split is the exception: its
  month is pinned beside a day pane, so the pill keeps naming that day until the
  pane is collapsed.
- Paging the agenda by month carries the date sideways with the finger, the way
  every other month change in the app does, instead of relabelling once the page
  has settled. An arriving page reports the day its own list is resting on.
- A multi-day run opens the way its days do, unrolling from its first day's card
  across the week. Grid markers and the cards they become now share one swatch,
  so nothing shifts color across the morph.
- The expanded month keeps its selected-day marker only where it means
  something: beside a day pane, driven by that pane's own travel. On a phone the
  marker leaves together with the expansion.
- The top of Contacts is slimmer — no subtitle, a search pill in place of the
  Material box, and a hairline parting the header from the names.
