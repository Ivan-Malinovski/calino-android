# Changelog

Notable changes per release. Releases before 0.2.1 are recorded in the git
history and their tags.

## 0.9.0 — 2026-09-30

Calino 0.9.0 adds a Year view, invitations with Accept/Maybe/Decline, proactive sync-problem alerts and a redesigned search sheet.

### Added

- **Year view.** Twelve mini-month tiles shaded by how busy each month is, with week numbers beside the grid. Tap a tile and the month grows out of it; go back and you return to Year. It adapts to phones, tablets and wide screens, where a day agenda sits beside the grid. Year appears after Month in the sidebar and the pill menu.
- **Invitations and RSVP.** On CalDAV servers that support scheduling, events you organize now send invitations, and events you are invited to show the organizer and each guest's response. Accept, Maybe or Decline from the event, for a whole series or a single occurrence. Pending invitations appear in Agenda.
- **Sync problems surface on their own.** The calendar heading dot turns rose when a saved change needs you and amber while changes wait to sync. A one-time alert ("'X' couldn't sync · Review") opens the queued changes, and background sync posts a silent "Sync problems" notification that clears once resolved.
- **Redesigned queued changes.** The card moved to the top of Calendars with a severity header, per-change rows showing calendar, status and details, and Try again, Revert and Retry all actions.
- **Choose which views the pill offers.** A new Pill views setting hides individual views from the pill's menu, dock and swipe. They stay in the sidebar, and Month always stays.
- **New task shortcut.** The launcher gains a New task shortcut, and the shortcuts have new terracotta and cream icons that match the app icon.

### Changed

- **Search sheet redesign.** One pill field carries the filter toggle. The empty state offers Today, Tomorrow, Next week and a date picker, followed by your five most recent searches (kept on the device and clearable from the sheet). Dragging down over a scrolled result list scrolls it back to the top instead of dismissing the sheet.
- **Range date line follows your swipe.** The date under the month title tracks the page you are swiping toward and animates only the parts that change.
- **Step the 3- and 7-day Range one day at a time.** Swiping the weekday bar drags the days with your finger and settles on the nearest day. The hour column stays put while the days move. Screen readers get Previous and Next day actions.
- **Task details match event details.** Tasks get the same masthead, ordering and styling as events, with tappable subtasks, a progress slider with haptic ticks, and tap-to-edit notes. In the side panel, the task card swipes down to dismiss like the event card.
- Side panels pulled down now leave downward instead of off their edge, and attachment rows are laid out as label over name with the type and size at the end.
- The journal's month grid uses the same month calendar as the date picker.
- Pill view toggles in Settings fold into a single row of chips.
- The meeting Join pill is slimmer and the add-description prompt is softer.

### Fixed

- **Rejected changes no longer block the queue.** A change the server refuses as invalid (such as Baikal's 415 response) was retried up to ten times and held up everything behind it. It is now dropped with a clear message, and an unrelated failing change no longer stops the rest from syncing.
- Content the server rejects is automatically resent with normalized line endings, or rebuilt from the server's current copy, before giving up.
- Swiping an item down to dismiss no longer clips its shadow.
- The filter button in the search field no longer flashes a ripple over the pill.

## 0.8.0 — 2026-09-27

Calino 0.8.0 grows event details out of the event you tap, lets you resize events in Range by dragging their end, and redesigns the long-press action menu.

### Added

- **Details grow from the event.** In 3- and 7-day Range and in month view, event details grow out of the tapped event and shrink back into it when closed. Lists and the single-day view keep the slide-up sheet. A new Advanced section in Settings can turn this off.
- **Resize events in Range.** Drag an event's bottom edge to change its length on the quarter-hour grid, with a live end-time label and haptic ticks.
- **Swipe to dismiss pickers.** Date and time pickers close with a downward swipe on their header.

### Changed

- **New long-press menu.** Edit, Duplicate and Share (or Edit, Done and Duplicate for tasks) appear as tiles above the remaining actions, with Delete last. The menu opens under your finger and can be swiped down to dismiss.
- Narrow Range views show longer weekday labels.
- Tap feedback is softer, and calendar controls no longer flash a rectangular highlight.
- The selected day in month view uses the same shade as the week strip, so the selector no longer changes colour when you pull between them.
- The event preview card sizes itself correctly when it has meeting or attachment rows.

### Fixed

- Editing the end of an event that spans several days no longer resets its dates, and the date picker opens on the right date.
- The date picker's month heading stays beside its navigation arrows.
- The Range header no longer replays its scrim animation.

## 0.7.0 — 2026-09-27

Calino 0.7.0 replaces the system date and time dialogs with Calino's own pickers, lets you slide and type event times in place, shows travel time on the timeline, and asks before completing a task with open subtasks.

### Added

- **Calino date and time pickers.** The framework dialogs are replaced by Calino's own cards. The time picker has snapping, looping hour and minute wheels (with AM/PM in 12-hour mode) and follows Calino's 24-hour setting. The date picker is a swipeable month grid that uses your week start, always shows the year, shows week numbers when that setting is on, and swaps to month and year wheels when you tap its heading. Each wheel responds to a swipe anywhere on its side of the card.
- **Month and year from calendar headings.** Tapping a calendar heading opens the month and year picker.
- **Slide and type event times.** Slide an event's clock sideways to move it by quarter hours, or long-press to type its times and dates in place. This works on the event card as well as in the editor; sliding one clock moves only that time, while sliding the rule between them moves both.
- **Travel time on the timeline.** Events with travel time show a faint band in the event's colour before them in the day view and Range. The band is labelled when there is room and opens the event when tapped.
- **Finishing open subtasks.** Completing a task that has open subtasks asks whether to mark them done too, with one undo that reopens them all. New subtasks start with the parent's due date, calendar, category and colour.

### Changed

- The sample calendar is explained by a dismissible notice.
- The recurrence scope choice ("this event" or "all events") is styled as a detail prompt.
- The task list's own completion banner is gone; completing tasks there uses the add pill's undo like every other surface.
- The Wear app uses the Calino launcher icon, and Wear edge complications show the event name alongside its countdown.
- The pill's view menu scrolls when the window is too short to show every row. Markdown headings scale to body text and lists sit closer together.

### Fixed

- Editing one occurrence of a recurring event in the sample calendar no longer affects the whole series.
- The end panel's downward dismiss no longer interrupts scrolling up through its content.

## 0.6.2 — 2026-09-26

Calino 0.6.2 improves event attachments and time zones, and restores due-time and reminder editing in the task detail screen.

### Added

- **Event attachments.** Open inline files from connected calendars, edit attachment links, and attach files to events. Attachment edits preserve files or links added by another client during a concurrent change.
- **Event time zones.** View an event's own clock, edit its time zone, and label hours in a second time zone. A New York time-zone example was added to the fixtures.
- **Android calendar links.** Standard calendar VIEW links now open Calino on the relevant day or an already visible event.

### Changed

- Categories in Settings list the categories in your calendars instead of sample names. Text fields and light and dark colors have been refined.

### Fixed

- **Task editing (#9, #10).** The task detail screen now shows and edits due time and reminders, including on tasks downloaded from CalDAV. The earlier fix handled reading and writing these fields, but this screen still hid the controls.
- Feed recurrence uses the supplied time-zone rules, and a failed feed refresh keeps the last valid subscription data.
- Event attachment changes rebase as individual additions and removals, preserving unrelated server changes.

## 0.6.1 — 2026-09-24

Calino 0.6.1 adds Agenda as an opening view, a search box in Settings and device-only reminders for read-only events, and fixes a round of CalDAV, task and month-view issues.

### Added

- **Agenda as the opening view.** Agenda can now be chosen as the view Calino opens on.

- **Settings search.** Settings are regrouped, and a search field at the top finds any setting by name.

- **Reminders for read-only events.** Events from subscriptions, read-only CalDAV calendars and non-writable imported calendars can now take a reminder from the detail card. The reminder is kept on the device and never written to the event.

### Changed

- **Search grows out of the pill.** Search opens from the add pill's own shape and shrinks back into it when closed, with the pill's glass over a blurred background.

- **Delete confirmation on the pill.** Contacts and journal entries now ask on the pill before deleting, like events and tasks, with a trash icon beside the question.

- **Dark mode cards.** Sheets, floating windows and side panels have a faint outline in dark mode so they stand apart from the page behind them.

- **Month view motion.** The month title stays level while sliding and fades at its edges, and the selected day fades with its page instead of sliding across unrelated dates.

### Fixed

- Nextcloud task due times and repeating reminders are read and written correctly, and task and alarm mapping now follows RFC 5545.
- Newly added calendars on the server are picked up, and incremental sync handles more server responses correctly.
- Stricter handling of server origins, the local cache, recurrences and cross-calendar moves.
- Multi-day events keep one lane across a month week row instead of splitting their bar.
- The event detail list runs to the card's edge instead of being cut off above the pill.
- A repeated Save no longer leaves the pill's progress running, and an overlapping failed write no longer reports Saved.

## 0.6.0 — 2026-09-23

Calino 0.6.0 adds a menu pill for moving between views, background sync for connected accounts, faster private search, and opt-in access for assistants and the phone's own search.

### Added

- **Background sync.** Connected CalDAV and CardDAV accounts refresh in the background, hourly by default. Settings offers 4-hour, 12-hour and 24-hour intervals, or Off. Queued edits are sent before new data is fetched, and reminders and the widget update from the result. Android may delay a sync to save battery.

- **Faster search.** Search for a connected account uses a private on-device index. The index stays inside Calino and is never shown to the system.

- **Menu pill.** The root add pill gains a view button showing the current view's icon. Tap it or swipe up from it to open a view menu (swipe up and release on a view to jump straight there; swipe the menu down to dismiss). Hold it for a dock with every view one tap away and an add button for events, tasks and journal entries. The label keeps its tap-to-add, sideways swipe between views and swipe up to search. Settings → **Menu pill** turns it off to keep the swipe-only pill.

- **Assistant access (experimental).** On Android 16 and newer, Settings → Data → **Let assistants use Calino** lets assistants such as Gemini read your events and tasks, open them in Calino, and prepare new events and tasks in Calino's editor for you to save. Off by default; journals and contacts are never shared, and nothing is saved without your Save.

- **Phone search.** Settings → Data → **Show in phone search** lets the phone's own search, such as Samsung Finder, find your events and tasks. Calino appears in Finder's app list either way, but answers only while this is on (off by default). Everything stays on the phone, and journals and contacts are never included.

### Changed

- **Navigation pill.** The collapsed pill shows just "+ <date>". The extended pill has a divider between the calendar views and Tasks, Journal and Contacts, and swiping right on it opens the sidebar.

- **Wear OS complication.** When an event is in progress, the complication shows its title and counts down the time left. Tasks show as an open ring in the watch agenda, so they are easy to tell apart from events.

- **Android 12 or newer is now required.** Search uses Android's built-in AppSearch instead of a bundled copy, which brings the APK back to about 4.4 MB. The search index is rebuilt after updating and stays private to Calino.

### Fixed

- Background retries of queued edits now report their result correctly.
- The watch app no longer reads its state or sends commands on the UI thread. If no phone accepts a request, the Phone button now shows a message.

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
