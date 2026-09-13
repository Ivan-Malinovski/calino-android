# Calino Android — engineering handoff

This document is the working handoff for the standalone native Android app in
this repository. It is written for the next coding model or engineer who will
continue the UI work.

### Range drag paging and Today — 2026-09-13

The 3/7-day range pager now owns timed-event lift gestures above its pages,
matching the one-day rail's ownership model. Holding a lifted event within the
52dp left or right edge zone advances whole range pages every 420ms, keeps the
card mounted under the finger while old pages are disposed, and resolves the
drop against a column on the newly visible page. The range header also exposes
the shared animated Today shortcut whenever today is outside the visible span.

Pure range-rule tests cover edge detection and destination-column selection;
`RangeInteractionTest` covers paging away and returning with Today.

The timed-event drop marker now places its time label inside the 52dp hour
gutter, vertically centred on the destination line, rather than floating above
the event lanes.

While a timed event is lifted, holding it in the top or bottom 64dp of either
the one-day or 3/7-day timeline continuously scrolls the hour rail. The drop
calculation includes that programmatic scroll distance, so the displayed and
persisted target time remain the same.

### Apple travel time — 2026-09-13

`CalEvent.travelTimeMinutes` now round-trips the same
`X-APPLE-TRAVEL-DURATION` property as the neighboring web app. Positive RFC
5545/ISO durations are read as whole minutes (a partial minute rounds up), new
and patched events write the canonical Java duration form, and clearing the
editor choice removes the property. Invalid, zero, and negative values remain
unmodelled rather than appearing as a misleading choice.

The existing Travel time choices under the event editor's More options now
therefore persist for connected calendars. A populated value is also shown in
the event detail card. Stale-ETag rebasing now merges raw iCalendar properties
by experimental-property name: changing `X-APPLE-TRAVEL-DURATION` cannot erase
an unrelated `X-` property added remotely.

### `.ics` and Android intents — 2026-09-13

TODO item 9. `data/ical/IcsInterop.kt` owns event-only iCalendar interchange.
Inbound reads are capped at 5 MB, VEVENTs use the existing CalDAV mapper,
VTODO/VJOURNAL components are reported as ignored, and matching UIDs are
skipped rather than overwritten.

- Calendar VIEW and Settings Import lead to a review dialog and one writable
  destination calendar. Creates use `CalinoRepository`, including its durable
  offline queue and applied/queued/rejected outcomes.
- Settings Export requires one calendar. Connected export uses a dedicated
  unbounded VEVENT query, while fixture export serializes the selected fixture
  calendar. The Storage Access Framework owns both document paths.
- Event sharing uses a cache-scoped `.ics`, a non-exported `FileProvider`, and
  a temporary read grant. Recurring selections retain their series rule.
- `SEND text/plain` seeds Quick Add. Calendar INSERT/EDIT maps standard event
  extras into the editor; EDIT updates only with the package-namespaced
  `EVENT_ID` or `EVENT_UID`. No Calendar Provider permission was added.

Validated with `IcsInteropTest`, `test lintDebug assembleDebug`, all 56 device
tests on the API 36 emulator, and direct SEND and INSERT emulator intents. A
raw adb VIEW cannot reproduce a document provider's temporary URI grant; its
permission-denied path fails visibly and the real path shares the same reader
used by Open Document.

**The user has not tested or accepted this `.ics` and intent integration yet.**
Treat hands-on product review as outstanding despite the automated and emulator
checks above.

### Ranked and filtered search — 2026-09-13

TODO item 10. `data/search/CalinoSearch.kt` now owns a deterministic fuzzy
scorer and `CalinoSearchOptions`. Exact, title-prefix, and word-prefix matches
rank first; modest edit-distance typos and compact ordered subsequences rank
after them. Title matches always beat metadata, with date proximity and stable
identity as tie-breakers. Text is normalized for case, punctuation, whitespace,
and diacritics, and there is no new search dependency.

The search capsule exposes animated chips for record type, calendars, and Any
time/Past/Upcoming/Custom dates. Custom bounds are inclusive and use the shared
platform date picker. Filters run before each group's eight-result cap, reset
when the capsule closes, and never suppress date-navigation or Quick Add.
Calendar filters govern events and tasks only: `JournalEntry` has no calendar
identity, and this item deliberately did not expand the DAV/model contract to
invent one. Any active date filter hides undated tasks and contacts.

Search is still over `CalinoSnapshot`, not the server. Until TODO item 7 extends
the CalDAV window, connected-account search displays “Search covers downloaded
calendar data.” Recurring occurrences continue to collapse to one series result.
`CalinoSearchTest` covers matching, ordering and filter composition;
`SearchQualityTest` covers the real add-pill search gesture, a typo result,
filtering, navigation out of search, and reset-on-close. Validated on the API 36
emulator with all 56 device tests passing; `test lintDebug assembleDebug` also
passes. **The user has not tested or accepted this search change yet.** Treat
that product review as outstanding even though the automated and emulator
checks are green.

### Device tests — 2026-09-13

TODO item 6. There is now an `app/src/androidTest/` source set with a Compose
harness and 54 tests over the nine interactions `AGENTS.md` lists. Run them with
`./gradlew :app:connectedDebugAndroidTest` against a booted API 36 emulator.

**They run on the fixture repository, and that is the whole basis of their
determinism.** With no account connected `CalinoContainer` serves the frozen May
2026 data, `rememberCalinoNow(live = hasAccounts)` pins "today" to 2026-05-18,
and `PagerEpoch` is that same date, so every pager starts on its centre page. A
test that connects an account would lose all of it.

- `CalinoUiTest` is the base class. Its `RuleChain` puts `CalinoResetRule`
  **outside** the Compose rule on purpose: the Activity reads preferences during
  its first composition, so a `@Before` reset would be too late. The rule clears
  the preference and account SharedPreferences, re-asserts the
  notification-prompt flag (otherwise a *system* permission dialog can appear on
  the second resume, which no Compose matcher can dismiss), and calls
  `FixtureRepository.resetToFixtures()` -- a seam that exists only for this, as
  the container is process-scoped and all tests share one.
- `CalinoTestActions` holds the finders. Day labels re-derive
  `DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)` rather than importing
  the private constant: what these tests pin is the user-visible string.

Five traps, all of which cost a debugging round here:

1. **Pager date commits require a real gesture.** Every settle collector is
   gated on `isUserSettle`, fed only by a `DragInteraction.Start` on the pager's
   interaction source. `pagerState.scrollToPage()` moves the pager and commits
   nothing, so a test driving it that way passes while proving nothing. Use
   `performTouchInput { swipeLeft() }`.
2. **"Exactly one selected day cell" is false by design.** The pager keeps the
   adjacent page composed and that page marks its own equivalent day selected,
   so the selection reads as continuous while paging. Assert that *this* cell is
   selected (`assertDaySelected`), never that a search for `", selected"`
   returns one node.
3. **The month heading is preview state.** It mounts three `MonthHeadingLabel`s
   at once and follows `compactHeadingDay`, not the committed date. Never read
   the committed month from it.
4. **The floating add pill is drawn over the list.** A row whose centre falls
   under it takes the pill's click instead -- which opened a *new* journal draft
   and made an edit look like a duplicate. Tap rows and day cells away from
   their centre; `selectDayIn` and the journal helper both do.
5. **`closeAnimated {}` runs the write after a real-time delay.** Compose's
   idleness does not track that, so `waitForIdle` is a race there. The
   `awaitDescribed`/`awaitNoDescribed` helpers poll instead.

The app opens on `CalinoDefaultView.Default`, which is **Week** (zoom 0), not
Month -- anything asserting against the month grid calls `zoomTo(2)` first. Zoom
is driven through the handle rather than the vertical drag, because the drag
settles on a velocity-dependent level.

Four accessibility gaps were found by writing these and fixed in main source,
rather than worked around in the tests:

- `MenuButton` was a 40dp touch target on every surface with a header, under the
  44dp lane the UI requirements mandate. Now 44dp; the glyph is unchanged.
- A settings `Switch` carried its `contentDescription` on a bare `semantics {}`
  node layered *above* the switch's own toggleable node, so the label and the
  state were two separate nodes and neither was the whole control. Merged.
- `CompactMonthRow` was the only one of four day-cell producers that omitted
  `", selected"` while still being clickable, so the selected day lost its state
  partway through the morph.
- The global undo banner's action button had no description ("Undo" alone does
  not say what of), and the toast was not a live region despite appearing
  unprompted and leaving on a timer.

Three test tags were added where no user-facing label belongs: the two
`SwipeDownDismiss`/`SwipeEndDismiss` gesture containers, the undo banner, and
the range pager (for parity with the three calendar pagers).

### One recurrence engine — 2026-09-13

TODO item 5. Calino carried two recurrence implementations that disagreed:
`ICalMapper` expanded server series with biweekly's RFC 5545 iterator, while
`CalEvent.occursOn` re-implemented `FREQ`/`BYDAY`/`UNTIL` by hand. An unbounded
`FREQ=WEEKLY;INTERVAL=2` therefore rendered every week.

- `data/model/RecurrenceRules.kt` is now the single evaluator. It wraps the rule
  in a minimal VEVENT and walks the same iterator the expander walks, so the
  grid, the reminder planner and the widget cannot drift from what the server
  round trip produces. `INTERVAL`, `COUNT`, `BYMONTHDAY`, `BYSETPOS`, `BYMONTH`
  and ordinal `BYDAY` now all behave.
- `EXDATE=` parts are lifted out of the rule string into real properties before
  parsing. `CalEvent.recurrence` holds RRULE text, but a locally written rule
  may carry an EXDATE inside it, and biweekly would otherwise discard the whole
  rule rather than one part of it.
- **A monthly anchor past the length of a shorter month now skips that month**
  instead of clamping to its last day. RFC 5545 3.3.10 skips; the clamp was the
  hand-rolled engine's invention, and the expander never agreed with it.
- **`EventDateIndex` buckets conservatively.** A bucket may only narrow the
  candidate set when placement follows from `FREQ` alone, so any rule carrying a
  `BY*` part other than a weekly `BYDAY` is a candidate on every date and
  `RecurrenceRules` decides. Bucketing such a rule by its anchor day would hide
  occurrences the rule places elsewhere.
- Results are memoised per rule, anchor and year, bounded by an LRU, because
  `occursOn` is called once per visible event per visible day. A rule that fails
  to parse yields no occurrences beyond its anchor rather than throwing.

`RecurrenceRuleTest` pins the whole grammar, including the `INTERVAL` bug, and
fails against the old parser. Validated on the API 36 emulator: a weekly series
created in the editor renders on its weekday only across the month grid and
continues correctly into January 2027; deleting the series clears every
occurrence.

### 3-day and 7-day Range page — 2026-09-13

TODO item 4 landed as a dedicated `PockRoute.Range`, by explicit user choice,
instead of expanding the already gesture-dense month/day zoom continuum.

- `ui/range/RangeScreen.kt` is one root surface with a shared, persisted 3/7
  segmented toggle. Three-day pages are rolling; seven-day pages align to
  `CalinoWeekStart`; horizontal paging advances by the visible range width.
- The implementation deliberately borrows the shared calendar heading,
  segmented control, event index and the active one-day hour/event renderer.
  `HourRailContent` is now internal and has narrow-column/hour-gutter hooks so
  the range view does not fork event layout, styling, overlap rules, menus, or
  current-time rendering.
- The hour gutter is outside the equal-width columns. This matters on a phone:
  putting it inside day one technically fit seven columns but left Monday with
  almost no usable event lane. Narrow cards retain color and title semantics
  while secondary text naturally disappears.
- `CalinoDefaultView.Range` has no zoom value and selects the startup root;
  `CalinoRangeMode` independently remembers the last 3/7 choice. Existing
  stored Month/Week/Day names remain valid.
- Detail, task detail, Quick Add, search origin, sidebar and add-pill navigation
  all recognize Range, so pushed surfaces return to the page that opened them.

Validated with the range rule tests and the API 36 emulator in 3- and 7-day
portrait layouts plus 7-day dark landscape. Seven columns, the detached hour
gutter, event placement, all-day summary, current-time marker, paging and the
persisted toggle were inspected.

### Home screen widget — 2026-09-12

TODO item 3. A resizable Glance agenda widget, under `widget/`.

- `widget/WidgetAgenda.kt` is the pure core, and exists for the same reason
  `notify/ReminderPlan.kt` does: a Glance composable is unreachable from the
  plain-JUnit suite, and this code runs in whatever process the launcher wakes.
  A snapshot and a date in, a list of rows out. It borrows every rule rather
  than restating one -- `EventDateIndex` for recurrence and multi-day spans,
  `tasksDueOn` for due tasks, `AgendaScreen`'s ordering, and its
  "Nothing scheduled" string. A widget that disagreed with the grid behind it
  would be worse than no widget.
- **The widget never touches the network.** `CalDavConnectionManager.restore()`
  was two things in one: seed the sources from the stored collection list so the
  repository publishes its disk cache, *then* rediscover against the server.
  The first half is now `restoreFromCache()`, reached through the new
  `CalinoContainer.ensureCachedData()`. Its flag is deliberately separate from
  `connected`, so a widget render cannot stop a later foreground from syncing.
- Publishing is asynchronous and `observe` calls back immediately with the
  *current* snapshot, which on a cold start is the empty one. So
  `awaitCachedSnapshot` waits for a publish that actually carries records, with
  a 2-second ceiling: an account whose cache is genuinely empty must still draw
  something, and the timeout is the honest answer there.
- `widget/CalinoWidgetBridge.kt` is `ReminderSchedulerBridge` again, and for the
  same two reasons: `CalDavRepository.publish()` is the single funnel every
  change passes through, so observing it *is* "update on sync"; and one reload
  publishes several times, so emissions are conflated or each would be a full
  render. It no-ops when no widget is bound. Started from the UI only, beside
  `startReminderScheduling()`.
- **No poll loop.** `updatePeriodMillis` is 0. "Today" moves because the
  receiver also accepts `DATE_CHANGED`, `TIME_SET` and `TIMEZONE_CHANGED` --
  all on the exemption list that still lets an implicit broadcast be declared in
  a manifest. `ReminderActionReceiver` additionally pokes the widget after a
  shade action, because that runs in a process with no Activity and therefore no
  bridge; without it the widget would keep showing a task the user just
  completed.
- Record rows reuse the reminder deep link unchanged, so they inherit its
  id -> uid + day -> next-occurrence degradation and need no new navigation
  code. Day headers needed a target that is not a record, so `AgendaDeepLinks`
  (host `agenda`, `?day=<epochDay>`) sits beside it in the same file -- the
  scheme stays written down once, and both parsers are `java.net.URI`-based and
  therefore testable.
- With no account the widget shows "No calendar connected" rather than the
  fixture agenda: the fixtures live around May 2026, so a real "today" against
  them is permanently empty and reads as broken. Reminders decline fixture mode
  for the same reason.
- `visibleCalendarIds` / `taskCalendarIds` were extracted next to
  `CalinoCalendar` and the planner now calls the first. The two Compose call
  sites still inline theirs because they also subtract the fixture-only
  in-memory hidden sets, which are not durable and so cannot be part of a rule a
  receiver or widget applies.

**Two Glance traps this cost a round of debugging each**, both with the same
symptom -- the widget renders correctly once and then silently never changes,
which reads as a broken bridge rather than what it is:

- `provideGlance` runs once per **session**, not once per update. A snapshot
  captured before `provideContent` is frozen for the life of that session. The
  data has to be read *inside* the composition; `currentSnapshot` does it.
- Recomposition only re-runs the scopes an update **invalidated**. `LocalDate.now()`
  in a composable reads nothing observable, so the scope is skipped and the
  widget keeps yesterday's heading however many updates arrive. Hence
  `widget/WidgetClock.kt`, which makes the date real state.

Two more things to know before touching it:

- **Only RemoteViews' whitelisted view classes may appear in the static
  layouts.** `res/layout/calino_widget_preview.xml` used a plain `<View>` for
  its colour bars and the picker rendered "Can't load widget" with only a
  `failNotAllowed` in logcat. They are `ImageView`s now.
- **`adb shell am force-stop` is not a cold-start test for a widget.** It puts
  the package into Android's stopped state and the framework masks widget
  updates until the app is launched again ("Updating package stopped masked
  state ... isStopped true" in logcat). Reboot instead. `APPWIDGET_UPDATE` also
  cannot be broadcast from the shell -- it is a protected broadcast.

Glance 1.1.1 brings WorkManager in transitively; that is where the
`androidx.work` receivers in a `dumpsys package` listing come from.

**Journal/Contacts availability, honestly:** the widget honours calendar
visibility and `showTasksInViews`. The Journal and Contacts flags are read into
`WidgetAgendaOptions` from `CalinoPreferenceStore`, and currently gate nothing,
because the widget shows events and tasks and neither of those. They are wired
rather than removed so there is one place to reach for if it ever shows more --
not because the requirement is met.

Validated on the API 36 emulator against a local Radicale with a real connected
account, driving real records rather than the fixture: placement and picker
preview; an `adb reboot` re-render with zero `MainActivity` records in the
process; an identical render with Radicale **stopped**, which is what proves the
disk-cache path; taps from a cold process into an event, a task, and a day
header; a server-side delete and an in-app "mark done" both reaching the widget
through the bridge with no manual refresh; a calendar hidden and re-shown; a
framework time-set moving "today" and emptying the day; the no-account prompt
after removing the account; and light and dark.

### A local CalDAV server for live tests — 2026-09-12

`scripts/live-caldav/radicale.sh` runs Radicale on the developer's machine for
the live tests and for emulator checks that need a real account. It installs
itself on first run and keeps everything under a gitignored `state/` directory,
so it can be deleted and recreated at will.

- Plain HTTP, at `http://10.0.2.2:5232/` from the emulator. The alternative was
  a self-signed certificate, which an emulator with a locked bootloader will not
  trust without being rooted first. Instead `app/src/debug/` carries a network
  security config permitting cleartext to `10.0.2.2`, `127.0.0.1` and
  `localhost` and nothing else. It does not exist in the release source set.
- Credentials are throwaway (`calino` / `calinopass`). The rule from the
  `VALARM` work still stands: a live test works inside a collection it creates,
  and never points at a real calendar.
- `radicale.sh env` prints the `CALINO_CALDAV_*` exports the JVM live tests
  read, which is why the suite reports skips without them.
- `scripts/live-caldav/README.md` also records how to plant a resource by hand,
  which is how reminder delivery was validated end to end.

### Local notification delivery — 2026-09-12

TODO item 2. A reminder used to sync and then notify nobody. It now fires.

- `notify/ReminderPlan.kt` is the pure core: a snapshot plus a clock in, a
  deterministic sorted list of firings out. No Android in it, so it is covered
  by the plain-JUnit suite, and -- more importantly -- so the same decision can
  be made from a boot receiver where there is no Activity and no repository.
  Expanded occurrences are used as they arrive from `ICalMapper`; only an
  unexpanded master is day-walked, through `CalEvent.occursOn`, so a
  notification cannot land on a day the grid does not show. Since TODO item 5
  that predicate is the RFC 5545 engine, so there are no gaps left to inherit.
- All-day records anchor at 09:00, never midnight. A "0 minutes before"
  reminder on an all-day event otherwise arrives in the middle of the night.
- `notify/ReminderScheduleStore.kt` is the durable plan, written with the
  atomic temp-file-and-rename idiom the CalDAV write queue established
  (extracted as `notify/AtomicJsonFile.kt`; `FilePendingChangeStore` keeps its
  own older copy on purpose). It holds the firings plus the two things a
  planner cannot know because they are history: what was delivered, and what
  was snoozed. Receivers read only this file.
- **One chained alarm, not one per reminder.** The next firing is armed; when
  it lands, everything `due` within a two-hour grace window is posted and the
  next one armed. A per-reminder scheme would have to diff every new plan
  against the last, and an orphaned `PendingIntent` cannot be cancelled without
  a second bookkeeping file to remember it by.
- `SCHEDULE_EXACT_ALARM`, not `USE_EXACT_ALARM`. A calendar qualifies for the
  latter, but it cannot be revoked by the user, and this app is not on Play.
  `canScheduleExactAlarms()` is asked on every arm and delivery degrades to
  `setAndAllowWhileIdle`, which the Notifications screen says out loud rather
  than letting a late reminder look like a bug.
- **The data layer became process-scoped.** `data/CalinoContainer.kt` now owns
  what `PocRepositoryViewModel` used to construct, and the ViewModel is a
  Compose-state facade over it. This was forced by the shade actions: "Mark
  done" arrives in a receiver with no Activity and must write through the same
  repository and the same durable queue, and two instances of either would race
  over one file. Nothing touches the network on construction --
  `ensureConnected()` is the opt-in, and a process woken only to re-arm reads a
  JSON file and goes back to sleep.
- Actions: Snooze 5 min is local and moves the firing in the store; Mark done
  and Tomorrow are real CalDAV writes through `setTaskDone` / `rescheduleTask`,
  and park in `notify/ReminderActionQueue.kt` when the record cannot be
  resolved inside the receiver's time budget. Every path replaces the
  notification with a line saying what happened, including "will sync" when the
  write was queued offline. The mock's "Directions" action was dropped: there
  is no maps integration to hand it to.
- Permission is requested on the **second resume** of the process, once ever,
  behind a persisted flag -- not on the first frame of a first launch, where
  there is nothing yet to say yes to. The Notifications screen is the fallback
  and also shows the permanent-denial route into Android's settings.
- The Notifications surface is no longer a mock. Status, the next five
  scheduled reminders, real channel state read from `NotificationManagerCompat`,
  and the dontkillmyapp caveat; the illustrative cards remain under a heading
  that admits what they are. "Daily brief" was removed from Settings rather
  than left as a switch that promises a summary nobody built.
- Deep links are `calino.malinov.ski.poc://reminder/{event,task}?id=&uid=&day=`
  on the existing scheme. Resolution degrades from exact id, to uid plus
  occurrence day, to the next occurrence at or after that day, and finally to
  the calendar on that date -- a notification tapped days later must still land
  somewhere sensible.

Known limitation, deliberate: a timezone change can only re-arm the stored
instants, because a receiver has no repository to re-plan with. The next
foreground re-plans in the new zone.

Validated live against a local Radicale (`scripts/live-caldav/`) on the API 36
emulator, driving a real connected account rather than the fixture. A `VALARM`
planted on the server synced, planned, armed exactly
(`exactAllowReason=permission`) and fired one millisecond after its armed time.
An `adb reboot` re-armed it from the stored schedule alone, with no Activity
running -- which is the whole reason the schedule is a file. On the task side,
all three actions were pressed in the shade of a process with no Activity:
**Mark done** wrote `STATUS:COMPLETED` / `PERCENT-COMPLETE:100` to the server,
**Tomorrow** moved `DUE` by a day and the new date came back through sync into
the next plan, and **Snooze** cancelled the notification, recorded a snooze five
minutes out and re-armed for exactly that instant. Each replaced the reminder in
place with what happened rather than leaving the user guessing.

### Reminders round-trip as VALARM — 2026-09-12

TODO item 1. Reminders used to be a model-and-UI-only concept: the editor
collected them, `LocalOverlay` and `FixtureRepository` carried them, and nothing
reached the server. They now map to and from `VALARM` in both directions.

- `data/caldav/ICalAlarms.kt` is the single definition of which alarms belong to
  Calino, asked by the reader, the writer and the patcher alike. An alarm is
  ours only when `ACTION` is `DISPLAY` or `AUDIO`, `TRIGGER` is a relative
  duration anchored to the start, that duration is prior-or-zero and a whole
  number of minutes, and there is no `REPEAT`/`DURATION`.
- Everything else is **foreign**: never read into the model, never rewritten,
  never removed. An absolute trigger, a `RELATED=END` trigger, an `EMAIL` alarm
  and a repeating alarm all stay on the resource untouched. `Reminder` is still
  just `minutesBefore`, and deliberately so -- see the decisions in the plan.
- Writes are *idempotent*, which matters more than it looks. An alarm whose lead
  time did not change is left exactly as it is rather than deleted and rebuilt,
  so an unrelated edit does not churn its `DESCRIPTION` or another client's
  parameters -- and does not make the rebase below believe the reminders moved.
- `CalTask` keeps a single nullable `reminder`; the longest lead time wins on
  read and any further alarm is foreign passthrough.
- `ICalPatcher.mergeComponent` diffed by property class only, so sub-components
  were invisible to the three-way rebase after a 412 and a local alarm edit was
  silently replaced by the server's. It now merges `VALARM` as a set as well.
  Scoped to `VALARM` on purpose: a `VTIMEZONE` is shared scaffolding, the same
  reason `removeComponent` works off the typed accessors.
- Tests: ownership predicate cases in `ICalMapperTest`, write/read round trips in
  `ICalWriterTest`, and the load-bearing ones in `ICalPatcherTest` -- an alarm
  Calino did not author survives an edit, a new reminder lands beside it, a
  clear removes only ours, and the rebase keeps whichever side actually changed.
- Validation: `./gradlew test lintDebug assembleDebug` green (466 tests, none
  skipped); clean install and launch on the API 36 emulator; and the live round
  trip against Radicale via `CalDavAlarmLiveTest`. That test works inside a
  throwaway collection it creates and removes, so it never writes to a real
  calendar -- copy its shape rather than pointing a new live test at real data.
- The live test earned its keep twice while being written. Seeding the cache
  empty made the writer fall back to rebuilding the resource from the model,
  which silently dropped `ORGANIZER`, the `X-` property and the foreign alarm:
  the writer patches from the **raw cache**, so a live test that fetches around
  the cache measures the fallback instead of the patch.
- Local delivery is still absent -- a reminder now syncs but nothing notifies.
  That is TODO item 2.

### Calendar connection early-stage warning — 2026-09-12

- The first step of the add-calendar-account sheet now begins with a prominent
  warning that Calino is early-stage and not thoroughly tested. It advises
  keeping a reliable calendar backup and not relying on Calino for the only
  copy of a primary calendar yet.

### Performance foundation and calendar date index — 2026-09-12

- `:benchmark` is a Macrobenchmark/Baseline Profile producer targeting the real
  app. Its shared journey covers fixture launch, bidirectional day paging,
  zoom and month paging, timeline scrolling, and event-preview open/dismiss.
  Resource-visible Compose tags at the calendar and pager boundaries make
  readiness deterministic without changing user-facing semantics.
- Generate profiles on one explicitly selected device with
  `ANDROID_SERIAL=<serial> ./gradlew :app:generateBaselineProfile
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile`.
  Generated release inputs live in
  `app/src/release/generated/baselineProfiles/`. Run metrics with
  `ANDROID_SERIAL=<serial> ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark`.
  An emulator diagnostic additionally needs
  `-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR`.
- The first post-index API 36 emulator diagnostic (`sdk_gphone64_x86_64`,
  Android 16/API 36, 1080x2400 at 420 dpi, `benchmarkRelease`, Baseline Profile
  required) measured cold TTID min/median/max 1193.8/1286.4/1335.4 ms. The
  combined calendar journey measured CPU frame duration P50/P90/P95/P99
  24.2/29.0/33.0/35.9 ms and frame overrun 7.7/12.7/24.7/27.4 ms. These are
  smoke-test figures only. A same-device physical before/after run is pending
  explicit authorization; there is no trustworthy pre-change physical result.
- Release uses optimized R8 and resource shrinking and builds without broad
  app keep rules. Compose compiler reports are emitted under
  `app/build/compose_compiler`; the calendar root, month pager/grid, week strip,
  and day pager are reported skippable, so no blanket stability annotations
  were added.
- `EventDateIndex` is remembered once per visible event-list revision. Direct
  and detached events (including multi-day spans) map to dates; unbounded
  recurrence masters use frequency-specific candidate buckets and the existing
  `occursOn` rule for final verification. Day, week, active month, split-pane,
  agenda-preview, and timeline pages reuse it. Repository order,
  expanded-month priority, midnight-end handling, and DAV contracts stay the
  same. A pending timeline move gets a temporary index only while previewed.
- Remaining work is physical-device before/after capture and trace review. Do
  not restructure snapshot publication or add stability annotations unless
  those measurements identify a material path.

### Pill morph visual continuity — 2026-09-12

- Once a modal claims the shared pill lane, the outgoing root add pill remains
  measured as the handoff anchor but no longer paints. This prevents the two
  translucent pill surfaces from overlapping for a retained transition frame,
  which briefly doubled the light-theme shadow and made the glass fill look
  more opaque during shape morphs. The lane retains the root pill's backdrop
  until the modal records its own, so the incoming pill has matching glass on
  its first frame instead of briefly falling back to an opaque fill.

### Sidebar hierarchy and mini-calendar — 2026-09-12

- The sidebar now groups its primary calendar destinations in a quiet `VIEWS`
  panel with a physical accent rail for the selected route, while secondary
  destinations sit under `ORGANIZE`. Upcoming Tasks starts collapsed so dense
  task content no longer pushes the calendar list out of the initial viewport.
- Calendar visibility controls are grouped into a secondary management panel
  with calendar-coloured check controls and compact overflow actions. The
  orphaned Privacy label was removed, and Settings now has the same contained
  footer treatment as the other navigation groups.
- The mini-calendar uses seven fixed columns for both weekday headings and
  dates. Incomplete final weeks therefore retain their weekday alignment, and
  the headings/grid follow the configured Monday or Sunday week start. The
  selected date paints a compact inner disc while its larger invisible lane
  remains available for input.

### Journal detail and editor — 2026-09-12

- Saved journal entries now open in a traditional read-only detail surface.
  The former Write / Read segmented control is gone; the shared bottom pill
  presents Cancel, Edit, and Delete while reading, then Cancel, Save, and
  Delete while editing. New-entry drafts still open directly in edit mode.
- Journal titles use the same fixed, tinted icon header as the event and task
  editors. Read and edit modes share one title field with identical geometry,
  so the title stays aligned with the note icon instead of shifting when it
  becomes editable. The date, word count, and Markdown body remain a quieter
  detail hierarchy, and editing turns the body into a simple bordered writing
  page rather than a separate Material form.
  Cancelling a changed edit retains the discard confirmation and returns a
  saved entry to its read view after discard.

### Editor title header — 2026-09-12

- The full event/task editor now keeps its editable title in the fixed sheet
  header, matching the web app's phone modal. The redundant `Edit event/task`
  heading was removed, so the title is the first content at the top and stays
  visible while the form scrolls.
- The task detail/editor uses the same hierarchy: its editable title and close
  treatment replaces the old Back / `Task details` / fixture-copy header. Its
  tint includes the sheet's drag-handle band and the redundant close icon is
  omitted. Category, notes, subtasks, and due-date choices are compact icon-led
  rows below it; due dates offer the quick presets plus the platform date
  picker. The existing Cancel, completion, and Save pill behavior is unchanged.

### Month selection continuity — 2026-09-12

- The compact week-strip selection pill now dissolves into a date-sized dark
  selector as the half-month grid opens. That selector remains visible in both
  the half-month and fully expanded month views; today's amber marker remains
  independent when a different date is selected. While the day surface is
  swiped, the open-month selector reads the day pager's live fractional offset
  and follows the finger instead of waiting for the destination to commit. At
  a week boundary it exits through the adjacent edge and re-enters from the
  opposite edge; it never walks backwards across the full row from 6 to 0.
  The destination week row does not take ownership until the selector is off
  screen, preventing an intermediate jump to the same weekday in that row.
  Fast flings preview only the adjacent page in their current direction rather
  than adopting `PagerState.targetPage` several days early; the visible week
  therefore advances with pager travel and cannot flash a predicted week. The
  open-month marker itself is derived from the day pager's fractional page
  distance, including a two-stage edge crossing, so target-page changes during
  a fast settle cannot move it to a speculative row.
- The header reserves the Today shortcut's width at all times and fades its
  paint and semantics in or out. Returning to today therefore no longer pops
  the label away or shifts the month/year heading.

### Pager-linked headers and settings navigation — 2026-09-12

- Calendar and Agenda month headings now read the same live `PagerState` as
  the content below them. Neighboring month labels translate at the pager's
  exact fractional offset, rather than starting a separate transition only
  after the selected date commits.
- Settings category pills derive their visible selection from the pager's
  `currentPage`, so emphasis changes at the pager's own page handoff instead
  of after settling. The persisted/committed section still changes only after
  settling.
- At the compact Calendar endpoint the heading follows the day or week pager
  that actually owns the gesture; at the full-month endpoint it follows the
  month pager's exact fractional offset.
- Month-title opacity follows its distance from the center, fading outgoing
  and incoming labels instead of leaving both solid.
- At the split month/agenda endpoint, a month swipe previews only the incoming
  lightweight agenda page at the month pager's offset. It deliberately does
  not duplicate `DayPagerSurface` (and its timeline, hit testing, and gesture
  state); the real day pager is handed to the preview date on settlement.

### Legacy-style event preview — 2026-09-12

- Event taps now open a tinted, rounded adaptive preview inspired by the
  legacy Calino/Samsung card: a compact drag-handle header, editable title and
  icon-led metadata rows, and optional keyword illustration (including
  mountain artwork for climbing/hiking titles).
- Title, date, time, location, and description edits remain page-local until
  Save is pressed. Every dismissal path discards them. Open event first saves
  a valid dirty draft and only enters the full editor after the repository
  applies or queues the write; rejected writes leave the preview visible.
- Inline conversion preserves calendar/server identity, recurrence, attendees,
  reminders, availability, metadata, and every field the preview does not
  edit. Recurring saves ask for This, This and future, or Entire series;
  expanded/detached occurrences default to This.
- Horizontal event paging is retained and each keyed page owns its own draft,
  validation, recurrence prompt, artwork, and pill actions. The preview keeps
  the shared morphing pill: Cancel and Open remain stable, while the final
  action is Delete for a clean draft and Save for a dirty one.
- Event previews calculate a preferred height from their rendered metadata and
  estimated wrapped description/attendee lines, between a compact 420dp base
  and a 560dp cap. Shorter foldable displays may use up to 86% of their height
  to reach that target. Event preview reserves only the pill's actual lane, so
  ordinary descriptions stay visible without an empty body gap; exceptionally
  long content remains scrollable.

### Compact event and task previews — 2026-09-11

- Tapping an event or task now uses the dedicated `Preview` surface geometry:
  a shorter bottom sheet on phones and a centered, 420×560dp-capped window on
  medium and expanded layouts. Full editors retain their larger geometry.
- Event detail has one overflow affordance. The header button was removed and
  More is now the trailing segment inside the shared bottom action pill, after
  Cancel and Edit.
- Events without location, notes, attendees, or recurrence use a tighter
  400×360dp preview cap (half-height at most on a phone), avoiding an empty
  full-height body while keeping richer events scrollable in the regular
  preview.

### Sidebar polish — 2026-09-11

- The navigation drawer starts with the web-matched Calino diamond and
  wordmark. Upcoming Tasks now appears before the calendar list.
- The calendar section presents `CALENDARS`, the visible-count label, and an
  accessible refresh button on one line. The old GitHub footer link, completed
  task toggle, and sidebar sync-status copy were removed.

## Current state

### Full-month vertical gesture ownership — 2026-09-11

- Vertical zoom now observes the final pointer pass once the month grid is
  visible. Empty month space can therefore expand the split month into the
  full calendar and collapse it again, while an armed event-chip drag keeps
  ownership through its consumed movement. The previous blanket guard treated
  every touch on the month grid as an event drag and disabled both directions.

### Expanded-month event ordering — 2026-09-11

- Expanded month cells use a stable priority: multi-day spans first, recurring
  events and recurrence instances second, and one-off events last. Repository
  order is preserved within each priority group, and the same ordered list
  drives both painted chips and their input lanes.
- Timed events whose duration crosses a date boundary appear on every date
  they occupy (without treating an exact midnight end as occupying the next
  day). Only the real current date gets the solid today disc; a selected day
  carried across month paging gets no expanded-month cell highlight. Multi-day
  chips join edge-to-edge as one uninterrupted weekly bar. Their title is
  drawn once per weekly run across its available width, and arrow-shaped ends
  show when the span continues across a week-row boundary. Each weekly run
  gets one continuous hairline outline, and its title is measured at the exact
  run width so glyphs stay crisp and ellipsis happens cleanly when necessary.

### Timed empty-slot creation — 2026-09-11

- At the compact week-strip/day-rail endpoint, tapping empty timed space opens
  the event editor at the nearest 30-minute start. The seeded time is marked as
  an explicit editor choice, so typing a title cannot cause Quick Add parsing
  to replace it.
- Holding empty timed space reuses the rail-wide event-drop line and time badge.
  It appears after the same short hold activation, follows the finger in
  15-minute detents with haptic ticks, and opens the editor at the selected
  time on release. Movement before activation remains owned by timeline scroll;
  event cards retain their existing tap, menu, and 15-minute move gestures.

### Compact day-swipe selector continuity — 2026-09-11

- Sunday/Monday day swipes now move the compact selector directly and
  linearly between columns 6 and 0. The previous staged off-screen
  `6 -> 7 -> -1 -> 0` wrap (and its mirror) could expose the selector at the
  opposite edge for a frame; both live pager travel and the one-shot settle
  handoff now use the same uninterrupted across-row trajectory.
- Boundary travel remains derived from the day pager on every crossing. It
  must not fall back to the selector `Animatable` when the raw weekday index
  passes outside `0..6`; that clock happened to match the first transition but
  drifted from the pager when reversing through the same boundary.
- Compact month/day swipes keep pager ownership until the settled date is
  committed. This removes the one-frame flash of the date being left without
  activating the week pager's opaque preview layer: day-swipe animation stays
  in the single month canvas, preserving its transparency and exact geometry.
- Live selector travel is read directly from `PagerState`. It is deliberately
  not copied into an `Animatable` on every frame; a one-shot synchronous
  handoff value bridges only the settled-page commit, avoiding both the stale
  old-day frame and redundant animation-state writes during a swipe.
- The live selector value is a deferred read consumed inside the compact
  canvas draw pass (and its small week-strip subtree when that pager owns the
  lane). Pager frames therefore repaint the selector without invalidating the
  full `HomeScreen`, month event lanes, or day rail composition.
- Compact-row date text always uses selector weight, including its exact-zero
  endpoint. It must not fall through to committed-selection styling on the
  final pre-commit frame, which flashes the departed number once. Today's
  accent disc and foreground color stay constant whether or not the moving
  selector currently overlaps them.
- The selector now follows the full distance of a fast multi-page fling rather
  than freezing beyond the old one-page cutoff. Today retains its independent
  accent disc in both the idle month canvas and the active swipe layer, even
  while the selection pill is on top of it.

### Durable create identities — 2026-09-11

- Connected CalDAV/CardDAV creates now use UUID-backed local IDs. The prior
  process-local counter restarted at `local-event-1` (and equivalent task,
  journal, and contact IDs), so a resource left on the server from an earlier
  app launch caused the next launch's first create to fail with a false-looking
  “already exists” message.

### Native AI photo import — 2026-09-10

- Android now has the sister app's opt-in BYOK image-to-event flow. Settings →
  AI Photo Import configures Anthropic, OpenAI, or a custom compatible API root,
  including model listing, a real image-capability probe, and a Keystore-encrypted
  API key. Custom URLs containing an `/anthropic` path segment use Anthropic's
  request shape; other custom URLs use OpenAI's chat-completions shape.
- A configured account adds a Photo action beside the calendar Add pill and a
  dynamic launcher shortcut. The flow accepts a camera image, a picked image,
  Android `SEND`/`SEND_MULTIPLE` image shares, and the shortcut entry point.
  Inputs are sampled and resized to a maximum 1600px dimension, encoded as
  JPEG, and sent directly to the configured provider. Calino runs no proxy.
- Extraction shows staged sending/reading/slow progress, then an animated review
  surface. It supports up to five distinct candidates, selection of any subset,
  per-candidate Event/Task correction, low/medium-confidence labels, and
  sequential review in the existing full editor. Failed and empty extraction
  offer the existing manual editor; authentication failures link back to AI
  settings.
- The review is a responsive bottom sheet on compact phones (a centered window
  on larger displays) with a close affordance, readable formatted dates, and
  the same candidate helper copy and selection labels as the sister app. While
  the photo is being sent, the calendar remains visible through a light blur
  and the progress message sits in a small surface instead of a flat full-screen
  color block.
- The response parser accepts the requested JSON array, fenced JSON, surrounding
  prose, and a bare object. Unit coverage lives in `AiVisionClientTest`.

### Modal action pills — 2026-09-10

- Event detail, task detail, journal entry, contact detail, and contact editor
  all reuse the event editor's source-pill morph: the root add label resolves
  into a bottom action pill with Cancel plus the surface's actions. Task detail
  uses Mark as done (or Mark as open) and Save; journal and contact actions
  retain Delete alongside Save/Edit. The task completion action saves before
  dismissal.
- The root Contacts add pill is hidden while a contact detail card is selected,
  so it cannot overlap the detail card's action pill.

### The pill lane — 2026-09-11

- The root add pill and a modal's action pill are one object, not two. The
  editor, task detail, journal editor, and contact editor hand their pill to
  `BottomDetailCard(pill = ...)`, which hosts it in `AdaptiveSurfaceHost`
  outside the card's visibility transition and outside its dismissal drag. The
  pill therefore stays in the lane -- the same spot, to the pixel, the root add
  pill occupies -- while the card slides in and out behind it.
- `CalinoPillLane` (`ui/components/PillMorph.kt`, provided once in
  `MainActivity`) is what makes the two read as one. It carries the root pill's
  measured **bounds in root**, so a modal pill is placed against where that
  pill actually laid out; a claim count, so the root pill leaves and returns
  with `EnterTransition.None`/`ExitTransition.None` instead of sliding under
  its own replacement; and `dismissDrag`, written by `SwipeDownDismiss`, so a
  drag toward dismissal returns the pill to its add shape as the finger moves
  and re-expands it when the card springs back.
- Nothing about the shape or the spot is declared. The lane anchors the pill to
  `addPillBounds` (a zero-sized box at that pill's bottom centre, with the pill
  aligned against the point), which is why it lands correctly in the right-hand
  lane of a wide screen without this code knowing such a lane exists.
  `ModalActionPill` is a `Layout` over both forms: in the lane the add shape is
  the root pill's own `addPillLabel` at the size that label measured
  (`addPillBounds`, trusted only while `addPillBoundsLabel` still matches --
  otherwise the label is measured here), the expanded shape comes from the
  actions' `maxIntrinsicWidth` (equal weights, so that is the widest action
  three times over) floored at that width, and the pill interpolates between
  the two. A modal must not name its own add label for the lane: event detail
  used to put the *event's* date there, so the shape that handed the lane back
  said a different day, and a different width, than the pill that took it --
  which is what the blink at the end of the morph actually was. Per-action-count widths used to stand in for
  this, and they were wrong for any label, density or window they had not been
  chosen against.
- Two frame-ordering traps, both of which show as the pill blinking:
  `AdaptiveSurfaceHost` claims the lane at the **top of the function**, not
  where the pill is composed -- everything below it is inside a
  `BoxWithConstraints`, which subcomposes during layout, a phase too late for
  the root pill that frame. And the root pill's *visibility* must depend only
  on `pillVisible`, never on the claim: `pillVisible` is already false for
  every lane-hosting surface and flips with the route, while the claim is
  released a frame later at disposal. Gate visibility on the claim as well and
  there is one frame with no pill in the lane at either end of the morph.
- A pill hosted in the lane must pass `inPillLane = true`; that flag, not
  `morphFromAddPill`, is what claims the lane, couples the pill to the
  dismissal drag, and gives it the root pill's frosted material. Every modal
  pill is now in the lane, event detail and contact detail included.
- The lane's placement converts out of root coordinates **during placement**,
  from the anchoring layout's own `coordinates` (`RootAnchoredPill`), never
  from an origin recorded by an `onGloballyPositioned` in an earlier frame: the
  recorded origin is still zero the first time the pill is placed, so the pill
  spent that frame a status-bar inset (128px on the test device) below the root
  pill it is continuing from. That was the second pill flashing under the first
  as a modal opened. Draw-time reads of a recorded origin -- the blur's -- are
  fine, because the layout pass writes them before anything draws.
- `ModalActionPill` latches `morphFromAddPill` in a `remember`: it is a fact
  about where the pill came from, not live state. Read live, it flips as a host
  tears its modal down -- `dismissQuickAdd` clears `quickAddMorphFromAddPill`
  while the sheet is still composed -- and a pill with no add shape snaps to
  its expanded form, which is the flash back to the modal shape at the end of
  the dismissal.
- Releasing a committed dismissal drag is a handoff between two things that
  disagree for one frame: `dismissDrag` resets the moment the card is let go,
  while the morph animation has not started and still reads 1. So
  `ModalActionPill` clamps: once `expanded` is false the shape may only
  continue toward the add pill, never back (`lastShown`), and the morph-back
  snaps to what the finger left on screen and takes proportionally less time
  for the rest. Without the clamp there is one frame of the modal shape in the
  middle of a dismissal the person has already watched most of.
- A lane box must report the *pill's* size and nothing else. Event detail's
  `Layout` reports the pill's width and height and lets the overflow button
  hang outside those bounds; reporting the taller of the two shifted the pill
  a couple of pixels off the anchor at the handoff.
- Event detail's overflow button is placed *against* the pill by a `Layout`
  that reports the pill's own width and places the button past it: the pill
  keeps the lane's anchor point and the button rides off its trailing edge,
  fading in with `lane.morphProgress`. A plain row would push the pill off the
  anchor to make room for the button.
- The lane's anchor is only recorded while the root pill *owns* the lane:
  `setAddPill` returns early once `claimedByModal` is set. A root pill on its
  way out is still laid out on every frame it spends sliding away, and taking
  those bounds the modal pill chases it off the bottom edge and stays there --
  which is what the contact detail pill stuck at the bottom of the screen was.
- Position and size are two separate questions about the anchor. The lane is
  the same lane whatever the root pill says, so the modal pill is *placed*
  against `addPillBounds` whenever they exist; the recorded *size* is used only
  while `addPillBoundsLabel` still matches, and otherwise the pill measures its
  own add form. Gating both on the label sent the pill to the bottom-centre
  fallback whenever the label it was returning to was not the one that had been
  measured.
- Where a modal returns to has to be settled before the morph back starts, not
  when its write lands. `EditorSurface` reports `onSaveStarted` as Save is
  pressed, and that is where `MainActivity` points `quickAddOrigin` at the
  journal screen for a journal draft. Deciding it in the write's callback left
  the pill morphing back into the *calendar's* add pill and then snapping to
  the journal's once the record was saved.
- Journal's local editor identity and `MainActivity`'s
  `journalEditorVisible` mirror are updated in the same callback. Deferring the
  mirror through a `LaunchedEffect` unmounted the modal pill first and kept the
  root `New entry` pill hidden for three 120 Hz frames (25 ms) after dismissal.

- Contact detail's card now animates out (`detailShown`) instead of vanishing.
  Without that window there is nothing for the pill to morph back into.
- `MainActivity` decides the root pill's silent exit from the **route**
  (`laneOwningSurface`), not from the lane claim: the claim lands a frame after
  the route changes, and by then the exit transition has already been chosen.
  Add any new lane-hosting surface to that `when`, or its opening will slide
  the root pill away underneath the morph.

### Web-parity interactions — 2026-09-11

- Calendar events and tasks expose long-press action menus shared with their
  detail surfaces. Events support edit, duplicate, convert-to-task, and a
  scope-aware delete;
  tasks support edit, subtask creation, promotion, date shortcuts, completion,
  duplicate, convert-to-event, and delete. Recurring event moves and unsafe
  hierarchy edits are rejected with a safe explanation.
- Tasks carry `parentTaskId` through fixture, CalDAV, overlay, and iCalendar
  (`RELATED-TO`) paths. Task views render a cycle-safe collapsed hierarchy;
  full-row long-press dragging supports reparenting with a visible “Make
  subtask” target state, while promotion remains an explicit menu action, and
  task detail can add a subtask without leaving the current flow.
- Calendar and agenda event cards, plus task rows, use one shared gesture:
  tap opens, a stationary long press opens the action menu, and movement after
  the short activation delay drags the row under the finger. The old visible
  drag handles were removed. Live drag translations use z-order and reset on
  release/cancel; date moves preserve event/task fields and use the repository
  write path, so connected accounts retain conditional writes and offline
  queue behavior.
- Journal entries, event descriptions, and task notes use the shared
  CommonMark/GFM renderer and editor preview. Markdown remains stored as the
  original source text.
- The navigation drawer now starts with the mini month and Today, keeps
  per-calendar visibility/task filters, rename/color controls, sync controls,
  and an independently collapsible Upcoming Tasks card. Category chips are
  hidden like the web sidebar; completed-task visibility, sync status, and
  footer links remain below the card, with Settings at the bottom.
  Account creation and destructive calendar management remain in the existing
  Accounts surface; webcal subscriptions and automatic update polling remain
  intentionally out of scope.

### In-app feedback toasts — 2026-09-11

- Write errors and undoable changes now use the shared compact `CalinoToast`
  surface. It follows the web app's transient Sonner placement, stays above
  the floating add pill, and uses a palette-aware panel with a small accent
  icon and action instead of a full-width dark banner.
- Undo and error feedback both expire after five seconds; errors also expose
  an immediate Dismiss action. Task-menu date changes now produce the same
  undo feedback as inline rescheduling.

### Event drag reliability — 2026-09-11

- Month event chips now have real input lanes sized from the rendered month
  geometry. Only visible, non-overflowed events get a lane, and stacked lanes
  share the painted chip pitch with later lanes on top so the event under the
  finger remains the event being moved.
- A held month drag follows the finger in a lifted card drawn outside the
  individual day-cell clips. Releasing outside the finite visible month clip
  (including the overlapped rail area) cancels and restores the source; valid
  drops commit through the existing repository write path. Timed day-rail
  moves now use the same lifted-card treatment at the rail layer, so the card
  remains visible after leaving its original bounds in either direction. The
  source input node stays mounted throughout the hold, and valid releases snap
  to 15-minute starts while preserving the event's day when the user is only
  adjusting its time. While the drag is engaged, the card scales up with a
  stronger accent edge and shadow; a rail-wide snap line and `DROP · <time>`
  badge show the exact destination before release.
- Inactive rail and agenda layers now receive null callbacks rather than
  no-op gesture handlers, so they do not compete with the active scroll or
  month layer. The shared long-press recognizer keeps early movement available
  to scrolling and claims only a held drag.

This is a Kotlin + Jetpack Compose Android application. It is a standalone
repository and does not load the Calino web app, WebView, Capacitor, or webcal.

It now carries **real CalDAV and CardDAV integrations**: OkHttp transport,
independent principal/calendar-home and address-book-home discovery, calendar
and addressbook REPORTs, iCalendar mapping via biweekly, vCard mapping via
ez-vcard, conditional server writes, RFC 6578 incremental sync, a durable
offline write queue, and Keystore-encrypted credential storage. The app
declares `INTERNET`. With no account connected it still serves the frozen May
2026 fixture data, so the sample surfaces stay reachable.

### CalDAV/CardDAV writing and sync — 2026-09-10

- Event, task, journal, and contact create/update/delete operations now reach
  the connected server. Existing resources are patched from their cached raw
  iCalendar/vCard text and sent with conditional ETags; unrelated server
  properties survive a write.
- Recurring event edits and deletes support THIS, FUTURE, and ALL scopes. A
  selected expanded occurrence defaults to THIS in the editor and delete
  confirmation. Standalone detached resources safely allow THIS only.
- Every delete entry point asks for that scope. The detail card expands the
  confirmation inline and the long-press overflow menu opens it in
  `EventDeleteSheet`; both render `EventDeleteConfirmBody` and take their
  starting scope from `defaultEventDeleteScope`, so neither can silently
  delete a whole series from a single occurrence.
- A cross-calendar move writes the destination before deleting the source.
  Failed source cleanup is retained as a separate `DELETE_HREF` queue item;
  a failed destination never removes the source.
- Retryable or offline writes are persisted atomically in
  `filesDir/caldav-write-queue.json`, replayed FIFO with bounded backoff, and
  restored into the optimistic overlay after a process restart. Dead letters
  are visible under Calendars with Retry and Discard actions.
- Refresh uses RFC 6578 `sync-collection` when a complete cached snapshot and
  committed token are available. Changed resources are fetched concurrently
  with a four-request bound; 404/410 responses are tombstones. Invalid or
  partial reports fall back to a full read without advancing the old token.
- A stale ETag is refreshed and the local modeled fields are rebased onto the
  current server resource. Conflict comparison is based on iCalendar
  `SEQUENCE`, never timestamps; the current product policy is local-wins after
  a safe rebase. The optimistic overlay is not itself cached.
- Queued UPDATE entries retain the original raw server representation as
  `baseData`, enabling the same three-way rebase during replay after a 412;
  legacy entries without a base are dead-lettered safely. Editing a queued
  CREATE replaces its payload in place, preserving FIFO order.
- A queued CREATE that gets a 412 verifies the resource before dead-lettering:
  an exact raw-payload match acknowledges a server write whose response was
  lost, a missing resource retries the original create, and a different
  payload becomes an explicit collision. Calendar and contact creates follow
  the same rule.
- Queued moves retain the source raw payload. If source cleanup or destination
  recovery must be deferred, the new queue item is inserted before dependent
  later writes; source deletion remains conditional and is never inferred from
  destination failure alone.
- Live opt-in tests create a throwaway Radicale collection/address-book,
  round-trip writes, and clean up their resources. They read credentials only
  from `CALINO_CALDAV_*` environment variables.

### Launcher icon — 2026-09-10

- The manifest now declares an adaptive `ic_launcher` and `ic_launcher_round`.
  Their foreground is the generated transparent calendar page at
  `app/src/main/res/drawable-nodpi/ic_launcher_foreground.png`, with the fixed
  espresso `launcher_background` behind it.
- The art is pre-padded into the adaptive safe zone. A launcher renders only
  the center 72dp of the 108dp layer, a 1.5x zoom, so foreground art that fills
  the layer gets its edges masked away. The page occupies about 45% of the
  layer, which lands near 68% of the visible icon under any mask. Do not
  regenerate this PNG edge-to-edge -- the rings and the page corners are the
  first things a squircle eats.
- The selected detail variant adds a restrained 3-by-3 date grid with one
  terracotta highlighted cell; the API 36 emulator app drawer keeps it legible
  without changing the padded scale.
- The earlier edge-to-edge art was confirmed clipped in a physical phone's app
  drawer. The padded art has not been checked on hardware yet.

Recurrence is expanded **on the client**, so a repeating event lands on every
occurrence in the fetch window regardless of what the server will do.

See "CalDAV (read/write)" and "Contacts (read/write)" for the detailed
contracts and remaining exclusions.

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
client. Its **fixture** date/data contract is frozen around Monday, 18 May 2026
so that visual and gesture behavior is deterministic. That anchor applies only
when no account is connected; a connected account opens the calendar on today.

The initial standalone repository snapshot is commit `32c0664`.

## NEXT TASK — post-write product review

The CalDAV/CardDAV write implementation is complete. The next pass is product
review of the adaptive directory/detail/editor motion, queue messaging, and
live account path, followed by the intentionally deferred contact features
listed in "Contacts (read/write)". Older read-only notes below are historical
context and must not be treated as current scope.

### Day-rail ownership and timeline density — 2026-09-10

- The compact week-strip surface now leaves one-finger vertical drags on the
  time rail alone. A downward pull from that lower surface expands the
  calendar only when the rail is already at scroll offset zero; upward pulls
  and downward pulls from any later hour stay with the rail. The parent also
  yields as soon as a second pointer appears, so it cannot steal a timeline
  pinch.
- The timeline supports a two-finger pinch from 0.65x through 1.8x of the
  normal 62dp/hour spacing. Grid lines, labels, event positions/heights, and
  the current-time marker use the same scale. The scroll offset is adjusted
  after remeasurement to keep the hour under the pinch centroid stable.
- Expanded event detail cards retain the outward side-panel swipe and also
  accept a downward dismissal, matching the phone card gesture. Other end
  panels keep their header-only outward dismissal contract.
- Pure coverage for the rail boundary and pinch bounds is in
  `HomeGestureRulesTest`. The API 36 emulator checked compact rail scrolling,
  top-boundary calendar expansion, and expanded event-card dismissal. Pinch
  input was not exercised through a two-pointer device harness; no
  physical-phone check was requested.

### Adaptive large-screen surfaces — 2026-09-10

- Shared `AdaptiveSurfaceHost` presentation rules now classify the available
  width as compact (under 600dp), medium (600–839dp), or expanded (840dp and
  above). Compact surfaces remain bottom sheets; medium surfaces become
  centered floating cards; expanded detail/editor/day surfaces become logical-
  end panels while search remains a bounded centered window.
- Day details, event/task details, the event and journal editors, Quick Add
  editor entry points, search, and the CalDAV account sheet use the adaptive
  presenter. The underlying calendar remains mounted and receives a scrim, so
  the large-screen context is preserved rather than replaced.
- Expanded side panels are inset from the window edge, fully rounded, and
  elevated so they read as floating cards, with a narrower sub-half-window
  width target so more calendar context remains visible; event-detail headers use
  the tighter layout in every presentation mode.
- Dismissal follows the presentation edge: downward swipe for compact sheets,
  outward horizontal swipe from the panel header for expanded panels, and
  the same downward swipe plus Back/close/scrim for centered floating windows.
  The rules run through `calinoLayoutSpec(width, height, posture)`, one entry
  point over the width buckets and the split-pane predicate so the two cannot
  disagree about the same device. Posture comes from `androidx.window`, reduced
  to `CalinoFoldPosture` by the pure `foldPostureOf(...)`; a half-open book
  posture splits into two panes from 600dp rather than 720dp, because the crease
  already divides the surface, and the pane rule then places the divider inside
  the hinge band so neither pane straddles it.
- Pure boundary/mode coverage lives in `AdaptiveWindowRulesTest`. API 36
  emulator checks covered the compact baseline, centered medium cards,
  expanded day/detail/editor panels, bounded search, short landscape, and
  side-panel dismissal. No physical-phone validation.

### Fold morph on the month root — 2026-09-10

- Unfolding used to be a cut: `HomeScreen` early-returned into a separate
  `SplitHomeLayout` tree, so the grid was torn down and rebuilt at the new size
  and the day pane arrived already open. It now settles instead. A `morph`
  `Animatable` runs 0..1 over `CalinoMotion.FoldMorphMillis` and drives a
  clamped scale from the geometry the layout had into the one it now has, plus
  the day pane's width, so the pane grows with the grid rather than after it.
- It is keyed on the width the grid actually gets -- the window less the pane
  -- and not on the split decision, with `MinFoldMorphRatio` (4%) filtering out
  inset shuffling. Keying it on the arrangement was the first attempt and was
  wrong on the device it was built for: a Z Fold's inner display in portrait is
  about 673dp, under `SplitPaneMinWidthDp`, so the most common unfold left the
  arrangement identical and nothing moved.
- The zoom continuum survives the fold. Entering split, the compact surface is
  already gone, so the zoom is snapped to the pinned endpoint and the outgoing
  value stored in `zoomBeforeSplit`; folding back animates from the endpoint to
  that stored value, so the calendar reopens where it was left.
- On a device that reports `Sensor.TYPE_HINGE_ANGLE` the fold is not an
  animation at all: the layout follows the angle. `hingeOpenness(degrees,
  sensorRange)` normalises to 0..1 and `foldSplitProgress(openness)` ramps from
  flat (.97) to properly bent (.70), so the panes part as the device parts and
  stop wherever the hinge stops. Jetpack's `FoldingFeature` cannot do this -- it
  only ever says FLAT or HALF_OPENED, which is a jump, not a move.
- What the progress drives: the month root's day pane grows from its resting
  360dp to an even share of the window (`(width - 44dp) / 2`, the rule being
  44dp), and a fold begun on a window at least `BookPostureSplitMinWidthDp` wide
  creates the split even where width alone would not -- the crease is doing the
  dividing. The transient surfaces take the same cap in `AdaptiveSurface` and
  `SearchSheet`, so a sheet, a floating window or an end panel is down to half
  the window by the time the calendar behind it has parted, rather than lying
  across the crease. The side panel's own width animation stays for window-size
  changes, with the fold cap applied after it so it tracks the hinge instead of
  chasing it.
- The value is quantized to one percent before it reaches layout. The sensor
  streams finely and the month grid is expensive to remeasure; a percent is well
  under what an eye can see and well over what a pager wants to be remeasured
  at.
- The timed morph stays underneath for the moment the window size actually
  changes, and is all there is on a device with no such sensor
  (`getDefaultSensor` returns null and the whole path is skipped). It is
  suppressed while the hinge is driving, so the app never animates on its own
  while the user is still moving the device. There is no blur: it only ever had
  one visible level in practice and was not wanted.
- What this is *not*: the panel handoff itself belongs to the system, which
  hands the app a new window size and nothing else. Nothing here animates across
  that swap the way a foldable iPhone does -- the blur exists to cover the frame
  in which the new arrangement lays out. Do not describe it as a shared-element
  transition; there is no shared node between the two arrangements.
- Pure coverage in `FoldPostureTest` (posture mapping, book-posture split
  threshold, keep-out band, preserved width buckets). Nothing here is exercised
  by an instrumented test -- there are no Compose UI tests in this repo. The
  morph itself is verified only by an emulator resize (`wm size`), which stands
  in for a window change and cannot exercise the hinge sensor at all. The
  hinge-driven path -- which is now most of the behaviour -- has never run on
  hardware, because no emulator here reports a hinge angle.

### Pill-morph search and configurable event window — 2026-09-10

- Swiping upward on the floating Add pill now reveals a Search destination and
  commits into a floating search capsule. The capsule starts at the pill's
  bottom-centre geometry, then animates its width, height, corner radius and
  fill before focusing the keyboard; closing reverses the handoff before the
  Add pill returns. Horizontal pill navigation and taps retain their existing
  ownership, with dominant-axis locking deciding the gesture once.
- Search is local and offline over the active `CalinoSnapshot`. It groups
  events, tasks and journals, matching titles plus useful metadata (event
  notes/location/categories/calendar, task notes/category, journal body).
  Expanded CalDAV events de-duplicate by UID and each group is capped.
- A pure English date phrase offers `Go to …`; event-like text offers an Add
  event proposal while existing matches remain below it. Selecting the
  proposal seeds the existing full editor rather than saving immediately, so
  the read-only CalDAV/local-overlay posture is unchanged. The parser now
  covers relative dates, weekdays, ISO/month dates, times, durations,
  locations and common recurrence phrases.
- Event/task results open their existing detail cards and Back restores the
  same search query. Journal results open the exact entry. Search dismissal
  clears the query; opening and returning from a result preserves it.
- `CalinoEventSyncRange` is a persisted preference under Settings → Sync:
  ±6 months, ±1 year, ±2 years (default), or ±5 years. A change updates the
  repository window at runtime and reloads the cache/network through the
  existing generation-ordered read path. Tasks and journals remain unbounded
  REPORTs; there is no server search or write behavior.
- Pure behavior coverage lives in `CalinoSearchTest`, the parser tests, the
  preference tests and the adjusted repository cache-window assertion. API 36
  emulator checks covered pill opening, keyboard/insets, grouped results,
  editor seeding and Back restoration, plus the Sync setting layout.

### Simplified editor card — 2026-09-10

- The editor remains a rounded bottom card, but its contents now follow a
  flatter calendar-editor hierarchy: one title row, a two-column start/end
  time block, and hairline-separated rows for all-day, location, calendar,
  reminder, repeat, and description.
- Optional recurrence, reminder choices, availability, travel time, attendees,
  related tasks, categories, and color controls are still present but stay
  collapsed or grouped under `More options` until needed. The natural-language
  parser still seeds the same `EditorDraft`; no repository or CalDAV behavior
  changed.
- The full-width save button became a floating `Cancel | Save` pill over the
  card's scroll layer, matching the Samsung Calendar reference treatment. The
  scroll content reserves `CalinoSpacing.PillClearance` below its final row, so
  the pill can overlay the form without making the last controls unreachable.
- When the main add pill opens a new event or task, the same floating surface
  briefly carries the add label and morphs into `Cancel | Save`; editors opened
  from detail cards, search, or other surfaces keep the direct action state.
- The editor scroll state remains hoisted so the existing guarded
  downward-dismiss gesture keeps its ownership rules.
- API 36 emulator checks covered new event, task, existing event, time-picker,
  reminder expansion, and keyboard-visible states. No physical-phone
  validation.

### Scroll-aware editor dismissal and full calendar zoom lane — 2026-09-10

- `SwipeDownDismiss` accepts an optional `canStartDismiss` guard. The full
  editor hoists its `ScrollState` and only gives the sheet the downward gesture
  when the editor is at offset zero; once a gesture starts in the list, the
  child keeps the pointer stream, so scrolling back up cannot dismiss the
  modal.
- The calendar zoom recognizer remains on the stable host container and now
  accepts vertical drags from the lower day surface as well as the week/month
  surface. Axis locking still leaves horizontal pager swipes and taps to their
  existing owners. API 36 emulator checks covered lower-surface expand and
  collapse, editor scroll-back, and top-of-list dismissal.

## Historical task list — fixture-backed calendar functionality after the interaction polish pass

The zoom performance pass, swipable week strip, swipable Settings categories,
and task detail/editor flow are complete. Preserve the frozen May 2026 fixture contract and the three
calendar rest states:

- UI 1 is `zoom == 0f`: the seven-day week endpoint and selected-day hour rail.
- UI 2 is `zoom == 1f`: the compact month grid and selected-day agenda.
- UI 3 is `zoom == 2f`: the detailed month grid.

The month Canvas owns the idle compact endpoint. The week `HorizontalPager`
remains hoisted for interaction and draws only while previewing another week,
so the selected row physically continues into the month geometry. The pager
has exclusive opaque ownership during horizontal preview; the Canvas and
pager share compact-week metrics and one selector position. Keep committed
date/route state separate from pager preview state, keep boundary previews
mounted, and keep one pointer owner per gesture.

The historical next pass was task/calendar/settings behavior coverage: add
Compose/device tests for week paging, compact hit-target gating, task
completion/undo/detail editing, calendar rescheduling, Settings category
paging, and boundary cancellation, then continue with other fixture-backed
Calino surface gaps. Event/task
drag-and-drop remains deferred until those state and gesture contracts are
reviewed. That advice predates the completed CalDAV/CardDAV write and sync
implementation; current boundaries are in the write section near the top.

For each meaningful UI change, use the required full check:

```bash
distrobox enter android-sdk -- bash -lc './gradlew test lintDebug assembleDebug'
```

Inspect the changed surface on `calino-poc-api36`, including slow, fast,
cancelled, boundary, and reverse gestures. Use the zoom handle semantics
`Change calendar zoom, level … of 3` for repeatable bounds. Physical-phone
validation requires an explicit request and must not be inferred from emulator
results.

### Offline read cache — 2026-09-09

A connected calendar now renders from disk on launch and stays readable with no
network. Nothing calendar-related survived a restart before: only the account
list and the encrypted password reached disk, so every cold start showed an
empty calendar until discovery plus three REPORTs per collection came back, and
offline it never filled in at all.

Fetching and mapping were split to make it possible. `CalDavFetcher` now
returns the server's raw `CalendarResource(href, etag, ics)` and does no
mapping; `ICalMapper.mapAll` maps a collection for a given window; the
repository owns both. `FileCalendarCache` stores the resource text per calendar
as gzipped JSON, and the repository loads and maps it before making any
request, then refetches behind it. Caching text rather than mapped occurrences
is the point: a series re-expands into whatever window is current, so the cache
does not go stale as the calendar date moves.

`SyncState.Loading` gained `cachedAt`, so a refresh over a full calendar says
what is on screen rather than implying it might be empty.
`CalDavConnectionManager.restore` now seeds sources from the persisted calendar
list before discovery, so the cache is not gated behind a network round trip.
`LocalOverlay` deliberately still does not persist.

Verified: 175 unit tests; the live read-only test against the maintainer's
Baikal server round-trips all 350 events through the cache byte-identically; on
the emulator a cold start with WiFi and mobile data off renders the full
calendar in about two seconds, the Calendars screen says the refresh failed and
it is showing the last data read, and removing the account leaves
`files/caldav-cache/` empty. 24 KB on disk for three collections.

### Client-side recurrence and a real clock — 2026-09-09

Two user-reported defects, both fixed and both verified on the emulator against
the maintainer's live Baikal server.

**Recurring events showed only on their first date.** `<c:expand>` is gone from
the event query; `ICalMapper` expands instead. The whole story, including the
rules that expansion depends on, is under "Recurrence" in the CalDAV section —
read that before touching `ICalMapper.expandSeries` or `applyExceptions`.

**"Today" was a constant, in four different places.** The day rail's red
current-time marker was drawn at a literal `11.33f * 62` dp — an unmoving
11:20, painted on *every* day page rather than on today's. Separately,
`FixtureDate`/`May18` was copied into `HomeScreen`, `MainActivity`,
`FixtureRepository` and `SecondarySurfaces`, and `isToday` highlights, the
Today button, the Today/Tomorrow/Next week reschedule presets and the overdue
tint all compared against it. With an account connected the app therefore
opened on the real date while insisting today was in May 2026.

- `state/CalinoClock.kt` is the single source: `CalinoNow(today, time)`,
  `LocalCalinoNow`, and `rememberCalinoNow(live)`. It re-reads on the *minute
  boundary* rather than on an interval, so the marker lands on the minute and
  the date turns over exactly at midnight.
- `live` is `pocViewModel.hasAccounts`. With no account it holds `FixtureNow`
  (18 May 2026, 11:20), which keeps the fixture contract deterministic and
  keeps the marker where it has always been drawn.
- `CalinoApp` split into a thin shell plus `CalinoAppContent`, so the provider
  can wrap the content without re-indenting the host.
- The marker now draws only when `day == now.today` and carries a
  `contentDescription`.
- **`PagerEpoch` in `HomeScreen.kt` is deliberately still a constant.** It is
  the origin for day/week/month page-index arithmetic, not "today". If it ever
  starts advancing, every mounted pager silently renumbers its pages at
  midnight. It was renamed away from `FixtureDate` to make the two roles
  impossible to confuse.
- `May18` survives in `SecondarySurfaces.kt` for sample records and preview
  defaults only, and is now derived from `FixtureNow.today`.

Covered by `CalinoClockTest`. Emulator (`calino-poc-api36`, clock at
2026-09-09 08:30 CEST): the marker sits between 08:00 and 09:00 on today's rail
and is absent on 4 Sep; `💰 Work` renders on 9 Sep and on Mon–Thu of that week
but not Sat/Sun; 4 Sep is empty of it, honouring `EXDATE:20260904T060000Z`; the
Today button appears and targets the real date. The full
`test lintDebug assembleDebug` check passed. No physical-phone validation.

### Landscape split view on the month root — 2026-09-08

- The activity is no longer pinned to portrait. `AndroidManifest.xml` drops
  `screenOrientation` and declares `configChanges` for orientation/size, so a
  rotation resizes the composition instead of recreating the activity.
- `shouldSplit(widthDp, heightDp)` in `state/PocNavigationState.kt` gates the
  layout: landscape *and* at least `SplitPaneMinWidthDp` (720dp) wide. A compact
  phone turned sideways deliberately stays on the portrait zoom surface.
- When it applies, `HomeScreen` renders `SplitHomeLayout` instead of the zoom
  continuum: the month grid pinned at its detailed endpoint on the left, a
  `DayPane` for the selected day on the right, and a 44dp rule between them
  carrying the pane's collapse control. The week strip, the day rail and the
  vertical zoom gesture are not composed in this layout, so `MonthPager` is the
  only owner of the pointer stream over the grid.
- In the split, a tap on a month cell *selects* the day into the pane; only a
  tap on the day already showing there opens the day modal.
- `DayPane` and the month agenda share `AgendaDayBlock` (`AgendaScreen.kt`), so
  the row set, sort order and "Nothing scheduled" case cannot drift.
- `DayPane` is a `HorizontalPager` over the already-hoisted `dayPagerState`.
  Its pages use the shared epoch date key, `EventDateIndex`/due-task lookups,
  and independent vertical scroll states. The existing real-user settle
  collector in `HomeScreen` remains the only owner of the committed date;
  `day-pane-pager` is the device-test boundary for this interaction.
- `dayPaneCollapsed` lives in `HomeScreen` as `rememberSaveable`. The split
  layout reports through `onSplitPaneChanged`, and `MainActivity` keeps the add
  pill in the same right-side lane even while that pane is collapsed, so the
  pill does not recenter when the pane is toggled.
- The root add pill uses that same right-side lane on every add-capable root in
  tablet landscape (Agenda, Tasks, Journal, and Contacts as well as Month).
  The host derives this from the shared `shouldSplit(widthDp, heightDp)` rule,
  while the month callback still covers foldable split layouts.
- Landscape damage pass on the other roots: Agenda, Tasks, Journal and the
  sidebar were undamaged. Two fixes were needed — `SettingValue` no longer fills
  the row width (it was starving the label into one-character wrapping in the
  inline Settings row, which only landscape is wide enough to reach), and
  `BottomDetailOverlay` gives the card 96% of a short (<520dp) window instead of
  86%.
- Covered by `SplitPaneRulesTest`.

### Week-strip selector follows a boundary crossing — 2026-09-08

- A day swipe that crosses a week boundary no longer parks the pill on the
  incoming week's Monday and then snaps to the real day. `WeekStrip` used to
  draw any non-committed page's indicator at the committed weekday, so the
  previewed week rendered the wrong column until the date committed and the
  column changed in one frame.
- The displayed week (`compactBoundaryDay ?: selected`) now owns the moving
  indicator, and the selector spring aims at the previewed boundary day from
  the moment the preview starts, so the pill travels with the incoming week.
  Weeks that are only sliding past keep the committed weekday.
- Frame-tracked pill x-centre, Mon 18 May to Sun 17 May: previously
  109 → (page) → 109 held for ~200ms → 971 in one frame; now
  109 → 238 → 485 → 651 → 849 → 967 → 971. The forward crossing and a plain
  week swipe were checked for regressions and stay continuous.

### Week-strip selector handoff fixed — 2026-09-08

- Releasing a day swipe no longer snaps the compact week pill back to the
  previous weekday before animating to the committed one. The live preview
  now seeds the selector spring while the drag is in flight, so when the
  settle consumes the gesture the spring's resting value already matches the
  previewed position and the pill simply continues into its settle.
- Verified by frame-tracking a screen recording of the pill's x-centre: the
  previous build ran 107 → 251, jumped back to 108, then re-animated to 251;
  the current build is monotonic with no reversal.

### Agenda root view added — 2026-09-08

- Agenda is its own root destination (`PockRoute.Agenda`), separate from the
  zooming month surface. `ui/surfaces/AgendaScreen.kt` renders one month per
  `HorizontalPager` page, listing every day of that month with its events and
  due tasks, a per-day `+ Add`, and a "Nothing scheduled" line on empty days.
- Order is `Month, Agenda | Tasks, Journal, Settings` in the sidebar (with a
  rule between the two calendar views and the rest) and
  `Month → Agenda → Tasks → Journal` in the add-pill swipe order. The former
  "Calendar" sidebar label is now "Month".
- The agenda shares the calendar's month-page arithmetic (`monthPageFor` /
  `monthForPage`), its per-day event index (`monthEventIndex`) and its header,
  which moved to `ui/components/CalinoMonthHeading` — `HomeScreen.MonthHeading`
  now delegates to it and supplies the `Week N · …` subtitle. Those helpers and
  `FixtureDate` became `internal` for this.
- `AgendaTaskRow` in `ui/components/CalinoComponents.kt` is the agenda shape of
  a due task: the event row's rail/time/card geometry with the completion circle
  as a trailing control. The checkbox-first `AgendaRow(task, …)` remains the
  Tasks-surface shape.
- Agenda times are 24h so they fit the row's mono time column, matching the
  day rail. Event rows pass their own day to the detail route, since an agenda
  row is not necessarily the selected date.
- `PocReturnTarget.Agenda` carries the origin so detail, task detail, and Quick
  Add all return to the agenda.

### Zoom, week-navigation, and calendar-task pass completed — 2026-09-08

- `HomeScreen.kt` keeps the month/day surfaces at stable measured sizes during
  zoom. The compact-to-detailed month path now uses one Canvas: the selected
  week collapses/expands into the compact grid, then rows and event markers
  interpolate into detailed rows and event chips. This removes the old
  duplicate-grid crossfade and its ghosted intermediate frames.
- The day rail→agenda transition uses an opaque clipped agenda reveal instead
  of compositing two full-screen alpha layers. This keeps event cards from
  showing through one another while the surface grows.
- Month date hit targets use the same interpolated row geometry as the painted
  grid and retain date/event/journal semantics. The current emulator check
  tapped May 25 and committed May 25 correctly.
- The week strip is an independent seven-day `HorizontalPager`. It keeps the
  selected weekday while swiping a whole week, previews neighboring weeks,
  and commits the selected date only when the week pager settles. The current
  emulator check committed Week 21 → Week 22 and preserved Monday selection.
- Settings are real. `CalinoPreferences` now carries twelve values, all
  persisted and all defaulting to what the app did before the setting existed:
  theme, time format, pull bar, first day of week, event density, week numbers,
  default view, default event duration, default reminder, hide completed tasks,
  show end times, show locations. Rows with nothing behind them yet --
  accent, font size, timezone, date format, language, notifications, sync
  frequency, categories CRUD, import/export -- are tagged PLANNED, dimmed, and
  genuinely inert rather than moving without meaning anything.
- Dark mode is real, and the palette is a theme registry rather than a
  boolean. See "Theming" below before touching any color.
- Adding a preference is still the six-step pattern in `CalinoPreferences.kt`.
  The trap is `SettingSegmented`, which keeps the selection in its own
  `rememberSaveable`: wired segmented rows use `SettingChoiceRow`, which binds
  `CompactSegmentedControl` straight to the preference.
- First day of week is real, and Monday is no longer baked into the grid.
  `util/CalinoWeek.kt` owns the vocabulary -- `startOfWeek`, `weekdayColumn`,
  `dayOfWeekForColumn`, `weekdayLetters`, `weekendColumns`, `gridStart`. Three
  things to know before touching it:
  - `LocalDate.with(DayOfWeek.SUNDAY)` is **not** "previous or same". It moves
    within the Monday..Sunday ISO week, so for a Monday it jumps six days
    forward. `startOfWeek` uses `previousOrSame`; `WeekStartTest` fails loudly
    if anyone simplifies it back.
  - `monthGridRows` returns 5 or 6 for the *same* month depending on the week
    start, so every cache sized `rows * 7` moves with it. They key on
    `monthGridGeometry(month, weekStart)` rather than the month; treat a
    surviving `remember(month, ...)` in a renderer as a bug.
  - The week pager's page index for a date is not stable across the change --
    for a Sunday it shifts by one. The three pager states are wrapped in
    `key(weekStart)` and seeded from the live `selectedEpoch`. Without that a
    restored pager reads back a date a week off and the settled-page collector
    commits it, which is a wrong date rather than a cosmetic glitch.
- The weekend wash is computed, not hardcoded. Under a Sunday start the weekend
  is columns 0 and 6 -- two bands, not one -- so `monthWashPlan` returns regions
  in column space with per-corner flags and the draw code just renders them.
  Regions abut rather than overlap: two translucent washes on one cell compound
  into a patch far darker than either. `MonthWashPlanTest` sweeps both settings
  across every month of 2026 asserting no cell is washed twice.
- Event density caps what a month cell shows, layered over the measured
  geometry and never replacing it, so Dense means "everything that fits". The
  morph paths keep their two-chip ceiling on purpose -- it is a performance
  limit on the hot zoom path -- so they use `min(2, density.maxItems)`. Every
  "+n" is `eventCount - monthCellShownCount(...)` against that renderer's own
  cap; no renderer subtracts a literal.
- Settings has a landscape layout: above `shouldSplit` the section chips become
  a side rail with the content pane beside them. Same `sectionName` and pager
  state as portrait, so chip taps and swipes cannot disagree between
  orientations.
- At the compact endpoint the day rail reaches up behind the zoom handle and
  the week strip (`CompactLaneOverlap` in `HomeScreen.kt`) instead of starting
  below them, so hours slide under a frosted lane rather than stopping at a
  hard edge. `CompactLaneScrim` paints that lane: opaque canvas, a subtle
  blurred copy of the rail over it (`GraphicsLayer` + `BlurEffect`, Android 12+
  only; below that the lane is simply opaque), and a short gradient at the
  lower edge so content emerges instead of clipping on a line. The rail erases
  itself inside the lane band: drawn twice, once crisp and once blurred, it
  read as smudged text rather than as something underneath. The scrim tint
  turns fully opaque as the month grid takes the lane, which is what keeps the
  rail from bleeding through the grid.
- The `AddPill` uses the same glass. `MainActivity` records the root
  `AnimatedContent` into a `GraphicsLayer` and hands it to the pill, which
  blurs its own patch of it and lays the ink over at 93%. The pill is a sibling
  of that stack, never a child, so the recording cannot recurse. Its position
  comes from `positionInRoot` on both nodes.
- The zoom handle occupies a half-height band (`ZoomHandleHeight`, 22dp) but
  keeps a 44dp touch lane (`ZoomHandleTouchHeight`) that overflows that band
  evenly, so compacting the bar did not shrink what a finger has to hit.
- The zoom handle occupies a half-height band (`ZoomHandleHeight`, 22dp) but
  keeps a 44dp touch lane (`ZoomHandleTouchHeight`) that overflows that band
  evenly, so compacting the bar did not shrink what a finger has to hit. It can
  be turned off entirely -- Settings > Calendar > "Show pull bar", on by
  default, persisted through `CalinoPreferences.showZoomHandle`. Hidden, it
  gives its band back to the day surface.
- The zoom drag is hosted on the calendar container (`calendarZoomGesture`),
  not on the layers it moves. Two bugs lived in the old arrangement, and both
  made a continuous morph behave like a switch:
  - The week strip leaves the composition at `MonthEndpointBlendEnd`, so a drag
    that started on it died a fifth of the way through. The container outlives
    every layer.
  - `positionChange()` returns zero once the gesture has consumed a change, so
    after the first frame the drag contributed nothing and only the settling
    fling changed a level. It reads `positionChangeIgnoreConsumed()`.
  It watches the initial pointer pass and claims only once the drag is
  decisively vertical, leaving taps and the pagers' horizontal swipes to the
  children. The host now accepts those vertical drags from the lower day
  surface too, so the strip, grid, handle, and lower space share one zoom
  gesture. The strip, the grid, and the handle no longer carry gestures of
  their own. Turning the pull bar off depends on this.
- The all-day / due-task strip is now an overlay on that lane rather than a row
  above the rail. It still never scrolls. With neither tasks nor all-day events
  it renders nothing at all -- the old "NO ALL-DAY EVENTS" placeholder is gone,
  since an absent row already says it.
- Dragging the `AddPill` reveals the destination view's name on the edge the
  pill vacates; the chip fills in at the commit threshold, so the swipe is no
  longer a blind commit. Route names come from `pockRouteLabel` in
  `NavSidebar.kt`, shared with the sidebar.
- The month heading's subtitle is the week number only; the selected date it
  used to repeat is already shown by the grid's selection pill.
- The idle compact endpoint is painted by the same month Canvas as the
  month-to-week morph. The week pager remains hoisted for taps and horizontal
  preview, but its separate renderer is transparent at rest; this removes the
  reported month-fades/week-appears regression.
- Compact month hit targets follow the painted row geometry and remain
  interactive while a row is visibly present; collapsed rows are removed from
  both taps and accessibility only after their lane falls below 20dp. The
  month pager also relinquishes horizontal ownership at the week handoff,
  including during reverse zoom settling.
- Boundary week preview synchronization now returns a canceled visual preview
  to the committed week and keeps one-shot suppression scoped to the settle
  lifecycle. Day rail/agenda ownership follows the actually visible clipped
  surfaces and disables both owners once the day surface is offscreen.
- Calendar tasks are indexed by date. Open-task counts appear in week cells,
  task markers/counts persist in the month Canvas, and the week/day surfaces
  show labelled due tasks with completion controls. Completion uses the local
  repository and the existing undo banner; undated tasks remain in Tasks.
- Calendar due-task rows now expose an animated reschedule control with
  Tomorrow, Next week, and No date choices. Each choice goes through the same
  local repository mutation and undo banner as the Tasks surface.
- The selected-week pill is driven by one hoisted spring position shared by
  the compact Canvas and pager. Week-strip preview uses an opaque backing lane
  so the moving pager cannot reveal a stuck Canvas row underneath.
- Compact-week geometry uses shared height, inset, pill, and center metrics in
  the Canvas, pager, and hit-target layout. Boundary preview suppression has a
  generation/tombstone state and invalidates on a real horizontal week gesture.
- Settings categories are hosted by a dedicated `HorizontalPager`; the chip
  rail remains an accessible shortcut and a chip destination replaces an
  in-progress pager settle rather than being overwritten by it.
- Task rows in both the calendar and Tasks surface open a fixture-backed Task
  Details editor. Title, category, due preset, and completion state save via
  `FixtureRepository.updateTask`, preserving the task ID and origin route.
- Commit `199a5ac` is the verified milestone. Its APK is installed and
  running on the API 36 emulator. The requested physical-phone push was
  attempted without stopping the phone app, but wireless ADB was unavailable
  (`physical-device:5555` and the documented `:45095` endpoint both returned
  no route); do not infer physical-phone validation from this pass.
- On the warmed API 36 `calino-poc-api36` emulator, the latest single-renderer
  slow-drag reports (0→1, 1→2, 2→1) were respectively: 76/16/16/2,
  75/12/16/2, and 76/16/16/2 for total frames / 50th percentile / 95th
  percentile / janky frames. These are emulator results, not physical-phone
  validation. The latest captures show one compact endpoint and no duplicate
  month/week header or grid ghosting.
- The final debug APK was installed on both `emulator-5554` and the explicitly
  requested phone at `physical-device:5555` without issuing a force-stop to the
  phone. No physical-device smoothness claim is made.
- The full `test lintDebug assembleDebug` handoff check passed after the final
  endpoint, ownership, and calendar-task reschedule changes. Drag-and-drop
  remains deferred.

### Month-to-week continuity correction — 2026-09-08

- Selected-row date/event positions interpolate from their month positions
  into the centered week content, removing the layout switch at drag onset.
- One weekday heading set travels from the month header to the week strip;
  it is drawn above the selection pill without a fade-out/fade-in handoff.
- The month Canvas now paints events only: task counts/dots and journal dots
  are removed. Due-task rows and week-pager task counts remain available.
- When the active week is the first row, its center and date positions move
  directly between endpoint layouts instead of following the expanding row
  center, removing the down/up bounce.
- The full `test lintDebug assembleDebug` check passed and the APK was
  installed on the API 36 emulator. Ivan confirmed both transition corrections
  work. Comprehensive emulator gesture/frame review was interrupted; no
  physical-phone validation was performed in this pass.

### Bottom detail cards and event paging — 2026-09-08

- Event details, task details, and journal entries now open as rounded cards
  sliding up over their mounted originating page and a dimmed backdrop.
- `ui/components/BottomDetailCard.kt` shares the backdrop, entry/exit motion,
  card shape, and grab handle. Android Back and backdrop taps use the same
  animated dismissal as the card controls. Journal unsaved-edit confirmation
  remains in place; save/delete/discard also animate before removing the card.
- Events page horizontally through the selected day's occurrences, all-day
  first and then by start time. Each pager page owns a complete rounded card,
  including the event-colored handle area. There are no Previous/Next buttons
  or page-counter row. Neighboring cards remain mounted before a swipe; the
  selected event ID commits on pager settlement and Edit targets that event.
- `SwipeDownDismiss` now reads the latest dismissal callback, preventing a
  long-lived pointer handler from bypassing newly dirty journal state.
- Ivan confirmed whole-card swiping and Android Back work. API 36 emulator
  spot checks covered event/task/journal card rendering, horizontal swipes,
  short downward spring-back, slow/fast dismissal, and event Android Back via
  key and edge swipe. Comprehensive gesture automation, keyboard layouts,
  and physical-phone validation remain outstanding. No phone deployment was
  performed for this change.
- The full `test lintDebug assembleDebug` check passed for the card/pager work.

### Calendar selection feedback correction — 2026-09-08

- Pager settlement now commits a date only for an explicit navigation action:
  a drag registered by that pager's interaction source, or a month arrow.
  The action remembers its originating selection, so a later date click also
  invalidates an older settling gesture. Programmatic day/week/month alignment
  cannot feed intermediate dates back into the selected date.
- Day-driven selector travel and boundary previews only follow a user-owned
  day gesture. The agenda catching up after a week swipe no longer sends the
  selector to another weekday and back.
- Moving week cells now use the resting Canvas's event-only marker count,
  all-day bars, date typography, spacing, and selection-pill width. Task rows
  and task accessibility information remain available.
- Full `test lintDebug assembleDebug` passed. API 36 emulator checks covered
  slow forward and fast reverse week swipes, short canceled swipes, and seven
  month date selections across different rows/weekday columns. Recorded week
  transition frames were inspected. No phone deployment or validation.
- Repeat the selection regression with
  `python3 scripts/check_calendar_selection.py` after installing the debug APK;
  it uses the SDK Distrobox and explicitly targets `emulator-5554`.
- Automated Compose interaction coverage and exhaustive interrupted/boundary
  gesture coverage remain outstanding.

### Compact calendar task rows — 2026-09-08

- Calendar-context task rows use a compact single-line presentation with the
  category inline and no repeated due date; the full Tasks surface keeps its
  existing date metadata.
- The selected-day agenda now puts the date and `OPEN DAY` action on one
  compact line, with a tappable `TASKS DUE · … OPEN` header that animates the
  task rows open and closed.
- Agenda task labels keep their full touch lanes while using a tighter visual
  offset, and event cards retain a small separation instead of touching.
- The compact rows retain the 44dp interaction lane and completion,
  reschedule, detail, and accessibility behavior.
- Full `test lintDebug assembleDebug` passed. The API 36 emulator was
  inspected at the compact month/selected-day state; no physical-phone
  validation was performed.

### Future only — concrete drag-and-drop implementation checklist

Do not start this checklist until the zoom measurements and Ivan’s visual
approval are complete. It is a staged fixture-only plan, not permission to add
remote sync, persistence, a broad refactor, or a new dependency.

1. Add a small drag state/helper with stable event/task IDs and explicit
   `idle`, `armed`, `dragging`, `settling`, and `cancelled` phases. Put host
   callbacks in `MainActivity.kt` beside the existing `HomeScreen` and `Tasks`
   callbacks. Put the shared long-press recognizer/overlay primitives in
   `ui/components/CalinoComponents.kt` (or a narrowly scoped adjacent helper).
   Use measured root bounds, viewport and scroll offsets, and the pointer grab
   offset. One component owns the pointer stream; disable pager/zoom only after
   activation, then restore it on settle/cancel. Reset to `idle` on route
   disposal and every pointer cancellation.

2. First milestone: move one nonrecurring, timed event within the visible day
   rail. Wire `HomeScreen.kt`’s `HourRailContent`/private `EventChip` and
   `DayPagerSurface`; the rail currently uses 62dp per hour. Convert with the
   current density (`pxPerMinute = density * 62f / 60f`) and calculate:

   `startMinute = round(((pointerRootY - railViewportTop + scrollPx - grabOffsetPx) / pxPerMinute) / 15) * 15`

   Clamp the result to `0..1439`. Preserve the event duration, allow a move to
   cross midnight without shortening it, and reject an invalid drop or cancel
   without writing anything. Commit one accepted drop through the host callback
   to repository `updateEvent`.

3. Build the event `NewEvent` explicitly from the original `CalEvent` before
   calling `updateEvent`: preserve title, color, duration, location, notes,
   attendees, calendar ID, and recurrence fields. `FixtureRepository` currently
   rebuilds through `eventFromInput`, and `UndoableChange`/`ChangeKind` support
   only tasks, so event undo is unavailable until a deliberate extension or a
   dedicated reversible move is implemented. Never silently lose metadata.

4. Second event milestone: drop onto a visible month day. Use `MonthGrid` in
   `HomeScreen.kt` to expose measured date bounds, and target only a rendered
   day. Preserve the event’s local start time and duration; an all-day event
   remains all-day and moves by date only. Recurring events are unsupported in
   this stage: reject them with an explanation and direct the user to the
   existing edit flow until occurrence-versus-series behavior is designed.

5. Task milestone: add a long-press drag in `TasksSurface` in
   `ui/surfaces/SecondarySurfaces.kt` and its private active `TaskRow` at
   `SecondarySurfaces.kt:939`. The same-named `TaskRow` in
   `ui/components/CalinoComponents.kt` is an unrelated shared wrapper; do not
   edit it assuming it is the Tasks screen row. Accept only explicit visible
   Today, Tomorrow, Next week, and No date trays. Call `rescheduleTask(id, due)`
   or `rescheduleTask(id, null)` through the host and retain its existing undo
   banner. Moving must never complete a task. Do not fake ordering until the
   model/repository has a rank field.

6. Keep cross-route drops for a later stage. If they are added, the host owns
   the drag state and every target is visible and explicit; there are no
   invisible drop zones. Add auto-scroll and dwell-based pager changes only
   after stationary drops are reliable. Every cancel has no mutation and every
   accepted drop has one atomic mutation. Provide keyboard and TalkBack
   alternatives for the same move/reschedule action.

7. Add pure mapping/state tests beside `HomeGestureRulesTest.kt` and
   `PocStateRulesTest.kt`, then device/Compose gesture tests for long-press,
   slow, fast, cancelled, invalid, boundary, all-day, recurring, and undo
   cases. Assert IDs, committed dates/times, preserved metadata, no write on
   cancel, and route restoration; do not assert private pixel coordinates.

## What currently works

### Calendar

`ui/home/HomeScreen.kt` is the primary calendar surface. It currently includes:

- A calendar header with month navigation, current month/year context, week
  context, and a Today action.
- A detailed month grid with weekday headings, out-of-month days, event marks,
  all-day indicators, journal indicators, and event density handling.
- A compact month/day-agenda state in which the selected week is reused from the
  month surface while the other weeks collapse away through one Canvas morph.
- A week-strip state with seven day cells, event dots/marks, a moving selected
  day indicator, and horizontal paging.
- A selected-day agenda/timeline with hour rails, event cards, all-day area,
  current-time line, and event click targets.
- Three zoom levels driven by vertical drag, with spring settling and a tap
  handle. The month-to-week transition morphs the existing month surface into
  its selected week instead of replacing it with a separate bar.
- Horizontal date/week/month paging with committed selection state separated
  from in-progress pager preview state.
- Day selection from month cells, week cells, and agenda navigation.
- Due task markers/counts in calendar cells and due-task rows in the week/day
  surfaces, with completion/reopen/reschedule controls and the existing undo
  banner.
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
- Event editing through the shared full editor (`ui/surfaces/EditorSurface.kt`),
  opened as the Quick Add route seeded from the saved record.
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
date, time, duration, and location. The parser is deliberately local and
simplified; it is not a CalDAV or natural-language production implementation.

Below the typed line the same sheet is a full editor (`EditorSurface`), so the
natural-language field pre-fills a form rather than replacing it:

- Events: start/end date and time, all-day, availability, recurrence (daily,
  weekly with BYDAY, monthly, yearly, each with an optional UNTIL), location,
  calendar, categories, description, and a collapsed More section with travel
  time, reminders, related tasks, and attendees.
- Tasks: due date and time, category, description, one reminder, and colour.
- Journal: title and note, unchanged.

The draft lives in `data/model/EditorDraft.kt`, outside Compose, so the merge
and mapping rules are unit-tested (`EditorDraftTest`). A field the person edits
by hand is marked touched and the parser stops writing to it, so later typing
cannot undo a deliberate edit. Opening the editor on a saved record seeds the
draft with every touched field and the host calls the matching update instead of
an add.

Fixture calendars and the shared category list live in `FixtureRepository` and
reach the editor through `CalinoSnapshot`; Settings reads the same list.

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
- Tappable task bodies with a bottom-card detail/editor route.
- Local task title/category/due/completion edits that preserve task identity.
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

The page includes representative controls for regional defaults, accent,
font size, calendar display, event defaults, reminders, categories,
import/export placeholders, and danger-zone placeholders. Controls change
local Compose preview state only, with one exception: the Sync section's
connected-accounts group reads live `CalDavAccountStore` state, and both its
per-account `Manage` row and its `+ Add calendar account` button navigate to
`PockRoute.Accounts`.

The Settings layout has an important defensive rule: every setting row gets a
full-width label measurement on phone-sized layouts; controls are placed below
the label when needed. This prevents the former `Timezone` failure where the
label was measured at intrinsic width and rendered one character per line.
Category bodies are swipable through the dedicated pager; the horizontal chip
rail remains available as a direct-access and accessibility shortcut.

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

The current app includes a scoped visual polish pass committed as `a5b3a41` in
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
- Selected task and task-detail origin
- Editor draft seed and edit target
- Quick Add origin/type
- Journal editor/review state
- Notification origin
- Undo banner state

`FixtureRepository` implements the small `CalinoRepository` interface and
stores a `CalinoSnapshot` in Compose state. It publishes local snapshots to
listeners and supports add/update/delete for the POC’s event, task, journal,
and contact flows. It has no disk persistence and is the no-account path; the
connected path is `CalDavRepository` with CalDAV/CardDAV read caches.

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
  ui/surfaces/                     Journal, Tasks, Settings, editor, modal surfaces
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

1. CalDAV and CardDAV writes, ETag-aware rebasing, RFC 6578 incremental sync,
   and the durable offline queue are implemented. Webcal, telemetry, and other
   remote hosts remain out of scope. `LocalOverlay` is intentionally process
   local; only the pending operation is durable, so a discarded write cannot
   masquerade as server data.
2. The queue is durable, but successful event/task/journal/contact records are
   not independently persisted outside the server/cache. The fixture
   repository remains process-local, and a server-confirmed change is expected
   to be recovered by the next read/cache cycle.
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
12. A recurring VTODO still shows once at its due date; `CalTask` has no
    recurrence field. Events are expanded, tasks are not.
13. Loading, error, partial-read, write-status, and offline-queue states exist
    in the snapshot/accounts surface. The calendar surfaces themselves still
    do not show a global sync banner; a failed refresh is reported under
    Calendars while the last data remains visible.
14. Reminders are delivered locally as of TODO item 2. What remains: a
    timezone change re-arms stored instants only and the anchors are corrected
    on the next foreground; the planner uses `CalEvent.occursOn` for
    unexpanded recurrence masters, which since TODO item 5 is the same RFC 5545
    engine the expander uses; and the "Daily brief" summary the Notifications mock used to advertise was
    removed rather than built.
15. `Reminder` models a lead time and nothing else. Absolute triggers,
    `RELATED=END`, `REPEAT`/`DURATION` and non-display actions are preserved on
    the resource but are invisible in the editor, so a person cannot see or
    clear an alarm another client set. Deliberate, and recorded here because
    "the reminder row looks empty" is otherwise a bug report waiting to happen.

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

## CalDAV (read/write)

The app reads a connected account's events, tasks, and journal entries over
HTTPS. With no account connected `FixtureRepository` still serves the frozen
May 2026 sample data, so the sample surfaces stay reachable. CalDAV writes are
conditional and recurrence-aware; unavailable writes are queued durably.

### Layers

`data/caldav/`

| File | Role |
|---|---|
| `DavHttp.kt` | OkHttp transport. Not `HttpURLConnection`: it raises `ProtocolException` on `PROPFIND` and `REPORT`. |
| `DavCredentials.kt` | Basic auth, UTF-8 per RFC 7617. `toString()` masks the password. |
| `DavXml.kt` | DOM parsing for multistatus, namespace-aware **with a local-name fallback**. |
| `CalDavDiscovery.kt` | Well-known probe, principal, calendar-home, collection listing. Implements the existing `CalDavClient` seam. |
| `CalDavFetcher.kt` | The three component queries and partial-result handling. Returns the server's raw `CalendarResource`s; it does not map. |
| `ICalMapper.kt` | biweekly → `CalEvent` / `CalTask` / `JournalEntry`, plus recurrence expansion. `mapAll` maps a whole collection for a given window. |
| `CalendarCache.kt` | The on-disk read cache: `FileCalendarCache` (one gzipped JSON file per calendar) and `CalendarCacheJson`. |
| `CalDavWriter.kt` | Conditional create/update/delete, raw-resource refresh, safe href resolution, and ETag handling. |
| `ICalWriter.kt` / `ICalPatcher.kt` | Model-to-iCalendar creation and minimal patches that preserve foreign properties. |
| `RecurrenceEdit.kt` | Pure THIS/FUTURE/ALL transforms for masters and detached occurrences. |
| `IncrementalSync.kt` | RFC 6578 report parsing, cursor validation, tombstones, and token invalidation. |
| `CalDavErrors.kt` | `CalDavErrorCode` and the status/throwable classifiers. |
| `CredentialStore.kt` | `KeystoreCredentialStore` (AES-GCM under an Android Keystore key) and an in-memory one for tests. |
| `CalDavAccountJson.kt` | Account-list persistence in private `SharedPreferences`. |
| `CalDavConnectionManager.kt` | Turns the account list into the repository's sources. |
| `WriteQueue.kt` | Atomic durable FIFO queue, retry/backoff policy, dead letters, and replay metadata. |

`data/repository/CalDavRepository.kt` implements `CalinoRepository`, so
`rememberRepositorySnapshot` consumes it unchanged. `PocRepositoryViewModel`
holds both repositories and swaps `activeRepository` when an account connects.

### Behaviour that is load-bearing

Each of these was paid for once; do not undo them casually.

- **Well-known probing is judged by status (207 or 401), never by the URL it
  lands on.** Radicale redirects `/.well-known/caldav` to `/` and on to its web
  UI; a path check accepts that HTML page as the DAV root. A 401 counts because
  an auth challenge proves the endpoint understood `PROPFIND`.
- **XML lookups fall back to local-name matching.** Strict namespace matching
  alone breaks against real servers.
- **Address books and scheduling collections are filtered out** of the calendar
  home by `resourcetype`. Both test servers list them.
- **A collection reporting no privilege information is writable**, not
  read-only. Absent metadata is not a denial.
- **`supported-calendar-component-set` is a hint, not a gate.** All three
  components are always requested. A Baikal calendar advertising `VTODO` only
  still returned 81 events to a plain query; gating on that property hid the
  whole calendar and, since nothing had *failed*, reported the result as
  complete.
- **Component queries are independent.** One failing must not lose the others,
  and a partial result is never authoritative about what the server no longer
  holds.
- **Events are time-ranged; tasks and journals are not.** A `VTODO` may carry
  no `DTSTART` or `DUE` at all, and a time-range filter drops exactly those.
- **`<c:expand>` is never requested.** See "Recurrence" below.
- **`DTEND` is exclusive.** All-day spans carry an inclusive `CalEvent.endDate`
  instead, converted once in the parser.
- **`ICalMapper.toLocalDateTime` is separate and directly tested.** Expanded
  instances arrive in UTC; a 23:00 Europe/Copenhagen event arrives as `21:00Z`
  and must not slide onto the next day.
- **VTODO takes its value type from `DTSTART` over `DUE`.** Trusting `DUE`
  flips a timed task to all-day.
- **A leading BOM is stripped before parsing**, or the parse silently yields
  zero components.

### The read cache

A connected calendar renders from disk on launch and stays readable with no
network. Before this, `CalDavRepository.fetched` was a plain in-memory field:
every cold start showed an empty calendar for two network round trips
(discovery, then three REPORTs per collection), and offline it never filled in
at all.

`FileCalendarCache` keeps one gzipped JSON file per calendar under
`filesDir/caldav-cache/`, named by the SHA-256 of the calendar URL. The
maintainer's three collections -- 350 events, 38 tasks, 3 journals -- come to
24 KB on disk.

Load-bearing rules:

- **What is cached is the server's own iCalendar text, not mapped
  occurrences.** Expansion is a function of the fetch window, so cached
  occurrences would be frozen to the window they were fetched under. Caching
  the resources and re-running `ICalMapper.mapAll` against a window computed
  from *today* means a series re-expands into the current window on its own.
  It is also far smaller: one master rather than 198 occurrences.
- **The event query is time-ranged, so cached coverage is not unbounded.** A
  recurring series re-expands anywhere, but a one-off event outside the cached
  window was never fetched and cannot appear until a refresh succeeds.
- **Fetching and mapping are separate.** `CalDavFetcher` returns
  `CalendarResource(href, etag, ics)` and does no mapping; the repository maps.
  That split is what makes the cache possible at all.
- **A generation counter orders the cache load against the fetch.** They race
  by construction and the cache is the older answer; the counter is what stops
  a slow disk read from overwriting a fetch that already landed.
- **`setSources` is a no-op for an unchanged source set.** A cold start calls
  it twice -- once from the persisted account list, once when rediscovery
  confirms it -- and the second call must not discard the cache or refetch.
- **`CalDavConnectionManager.restore` seeds sources from the persisted calendar
  list before discovery runs**, so the cache is not gated behind a network
  round trip. The provisional `DiscoveredCalendar` carries `components =
  emptySet()`, which is safe only because the fetcher queries all three
  components regardless.
- **An unreadable cache yields nothing and the calendar refetches.** Corrupt,
  truncated, and wrong-version files all decode to null. This runs on the
  launch path; throwing here would be a crash on startup.
- **Writes go to a `.tmp` and are renamed**, so a kill mid-write leaves the
  previous copy rather than a truncated one.
- **Only the read side is cached.** `LocalOverlay` still lives and dies with
  the process. Persisting unsyncable edits would be a half-built offline queue.
- **Removing an account, or disabling a calendar, evicts its cached content.**
  Verified on the emulator, not only in a unit test.

Nothing secret is cached: resource text only. The password stays in
`KeystoreCredentialStore`, the account list in `CalDavAccountJson`.

### Recurrence

Expanded by `ICalMapper`, not by the server. The event query asks for plain
`calendar-data`; a `CalDavFetcherTest` case asserts that no request contains
the string `expand` at all.

**Why the server is not trusted with it.** sabre parses a collection's
`calendar-timezone` property while expanding. A calendar storing a bare
`Europe/Copenhagen` there, rather than a whole `VCALENDAR` wrapping a
`VTIMEZONE` as RFC 4791 requires, makes the expanded query fail:

```
HTTP 500  Sabre\VObject\ParseException: This parser only supports VCARD and VCALENDAR files
```

Two of three calendars on the maintainer's server are in that state. Other
servers ignore `<c:expand>` silently instead, which is worse: the master comes
back with its RRULE intact and a weekday series renders as one event. Both
failures are gone now; expansion costs the same on every server.

The engine is `Google2445Utils.getDateIterator`, Google's rfc2445 code shaded
into biweekly 0.6.8. **No new dependency was added.** It unions RRULE + RDATE
and subtracts EXRULE, so INTERVAL, COUNT, UNTIL, BYDAY, BYMONTH and BYSETPOS
all come for free, and `advanceTo` skips an old series forward to the window
without materialising the years between.

Load-bearing rules, each paid for once:

- **The window bounds expansion, and it is not optional.** The maintainer's own
  `FREQ=DAILY;BYDAY=MO,TU,WE,TH,FR` has no UNTIL and no COUNT. It is infinite.
  `MaxOccurrencesPerSeries` (2000) is a second stop for a pathological rule.
- **VEVENTs are grouped by UID before anything else.** One `.ics` resource
  carries the master and its `RECURRENCE-ID` detached instances together, and
  neither can be read without the other. Ungrouped, a master and its override
  both render and the day shows the occurrence twice.
- **An override beats an EXDATE naming the same instant** (RFC 5545 §3.8.5.1).
  Moving an occurrence and cancelling it are different acts.
- **EXDATE matching is two-tier: exact instant first, then whole day.** EXDATEs
  are lifted off the component and applied by `applyExceptions` rather than by
  the iterator, precisely so the second tier is possible. Real files carry
  EXDATEs written at the wrong time of day -- the maintainer's weekday series
  is stored at `06:00Z` with eight of its fourteen EXDATEs stamped `T000000Z`
  -- and under a strict reading those cancelled days reappear as work days. A
  day-level fallback applies only to an EXDATE inside the window that cancelled
  nothing exactly.
- **`CalEvent.recurrence` stays null on expanded instances.** `occursOn()` in
  `data/model/CalinoModels.kt` evaluates the rule itself whenever `recurrence`
  is set (through `RecurrenceRules`, the same biweekly engine used here).
  Populating it would place every occurrence twice. If a series summary is ever wanted on the detail card, add a
  display-only field that `occursOn` does not consult.
- **Occurrence id is `"$uid@$instant"`**, so occurrences are individually
  addressable while `CalEvent.uid` still names the series for editing. A
  detached instance is keyed on its `RECURRENCE-ID`, not its own moved start,
  so its id survives a reschedule. A non-recurring event keeps `uid` as its id.

Verified against the live server: the `💰 Work` series now expands to 198
occurrences over the ±6-month window, where it previously produced one.

### Write posture

`CalinoRepository` carries the app's write methods and live UI paths call them,
so direct operations call `CalDavWriter`/`CardDavWriter` and apply their result
to `data/repository/LocalOverlay.kt` immediately. Retryable failures enqueue a
complete replay payload in `WriteQueue`; permanent failures remain visible as
record status and/or a dead letter. The queue is FIFO and bounded, and its
Accounts surface exposes Retry and Discard for dead letters.

Existing resources are patched from the raw cached server text. The writer
checks the cached ETag, refreshes on a 412, and retries a model-preserving patch
against the current text. This is local-wins for fields Calino edits and
foreign-property-preserving for fields it does not know. `SEQUENCE` is the
conflict signal; timestamps are never used to decide a winner.

Moves are two-resource operations: destination PUT first, conditional source
DELETE second. If the source DELETE fails after destination success, a
`DELETE_HREF` item is queued. A destination error never deletes the source.

Replay keeps the original raw server snapshot on UPDATE and MOVE entries. A
stale replay ETag therefore uses the same three-way, model-preserving patch as
an immediate edit; an old queue format without that snapshot is rejected
instead of guessed around. CREATE replay handles a lost successful response by
checking an exact resource match after a 412, while a different resource at
the same URL is surfaced as a collision. MOVE cleanup/recovery entries are
ordered ahead of later dependent queue entries, and a source cleanup retains
its original conditional ETag and raw payload for recovery.

The overlay is intentionally not cached. A queue entry survives a process
restart and restores its optimistic record, but Discard removes that optimistic
record when no later queue item still protects it.

### Sync state

`CalinoSnapshot.sync` is `Idle | Loading | Ready(fetchedAt, warnings) | Failed`.
`Ready.warnings` names each affected calendar and what is missing; the status
card in `CalendarAccountsSurface` renders them in red. A read that is short of
the whole calendar must say **which part** is missing — reporting it as
complete is the worse failure, and was the reason the Baikal bug took a
reproduction to find rather than a glance.

A failed refresh keeps the previously fetched data on screen. Blanking the
calendar because a refresh failed is worse than stale data beside a clear error.

### Credentials

The password exists in the sheet's draft state (plain `remember`, never
`rememberSaveable`) and in `KeystoreCredentialStore`, encrypted under a
Keystore key that never leaves the Keystore. `CalDavAccount` has no password
field by construction, and the persisted account JSON carries no secret. Never
log a password; `DavCredentials.toString()` masks it.

### Account surface and navigation

`PockRoute.Accounts` renders `CalendarAccountsSurface`
(`ui/surfaces/CalDavScreen.kt`), reachable from the sidebar's calendar group and
from Settings → Sync. `AddCalDavAccountSheet` is the three-step add flow —
credentials, connecting, choose calendars — hosted in `BottomDetailCard` and
stepped with `AnimatedContent`.

`state/CalDavRules.kt` (URL normalization, per-field validation, default display
name, stable account id) is pure Kotlin and transferred to the real client
unchanged. `FixtureCalDavClient` remains for tests and for exercising the
sheet's states without a server.

Origin handling mirrors `notificationOrigin`: `accountsOrigin` sends back to
Settings or the calendar, `accountsAutoAdd` opens the sheet on arrival from the
Settings add button, and `accountsFocusId` scrolls a Settings `Manage` row's
account into view.

### The calendar's anchor date

`selectedDate` starts at `FixtureNow.today` (May 2026) only when no account is
connected; with one it starts at today, and connecting the first account
mid-session moves it. Landing a connected account on the fixture month shows an
empty calendar and reads as a broken integration.

### Tests

`app/src/test/resources/caldav/` holds verbatim responses captured from a live
Radicale server, so parsing tests run against what a server actually sends.

| Test | Covers |
|---|---|
| `CalDavDiscoveryTest` | Collection filtering, colours, privileges, prefix variation, error mapping |
| `ICalMapperTest` | Day bucketing, exclusive `DTEND`, VTODO value type, BOM, expand verification |
| `CalDavFetcherTest` | Query shapes, partial failures, the VTODO-only regression, the sabre-500 fallback |
| `CalDavRepositoryTest` | `observe` contract, sync transitions, source filtering, overlay posture |
| `CalDavViewingTest` | Month-grid placement, ordering, account JSON round trip, credential hygiene |
| `CalDavWriterTest` / `CardDavWriterTest` | Conditional resource writes, ETags, raw-property preservation, vCard writes |
| `ICalPatcherTest` / `VCardMapperTest` | Three-way patching, recurrence-safe resource edits, malformed-resource handling |
| `WriteQueueTest` | Atomic persistence, FIFO ordering, retry/dead-letter policy, replay metadata |
| `CalDavMoveRepositoryTest` | Destination-first moves, conditional source cleanup, ordered recovery |
| `CalDavQueuedRebaseTest` / `CardDavQueuedRebaseTest` | Stale queued updates and lost-response CREATE recovery |
| `CalDavRepositoryIncrementalTest` | RFC 6578 deltas, tombstones, token invalidation and fallback |
| `CalDavLiveTest` | Opt-in, against a real server |

MockWebServer routes by **query body, not queue order** — the three component
queries run concurrently, so an enqueued queue matches responses to the wrong
component from run to run.

The live test is gated on environment variables and skips without them:

```bash
CALINO_CALDAV_URL=https://example.com/dav.php \
CALINO_CALDAV_USER=you CALINO_CALDAV_PASS=... \
  distrobox enter android-sdk -- bash -lc './gradlew test --tests "*CalDavLiveTest*"'
```

## The sync marker

`snapshot.sync` is rendered in two places, and they say different amounts on
purpose. The Calendars screen keeps the full story -- a timestamp, the
warnings, and Refresh. Every calendar surface gets one dot.

The dot lives in `CalinoMonthHeading`, which Month/Day, Range and Agenda all
share, so there is exactly one marker and it cannot drift between views. It
reads `LocalCalinoSync`, provided once in `CalinoAppContent` from
`snapshot.sync`; a composition local rather than a parameter because no layer
between the root and the heading has any business carrying it.

`state/SyncIndicatorRules.kt` holds the decision as a pure function:

| State | Marker |
|---|---|
| `Idle` (no account -- the fixture app) | nothing |
| `Loading` | a 13dp spinner |
| `Ready`, clean, newer than `SyncStaleAfter` | nothing |
| `Ready`, clean, older than that | a neutral dot |
| `Ready` with warnings | a rose dot |
| `Failed` | a rose dot |

Things to know before changing it:

- **`SyncStaleAfter` is 30 minutes, and the threshold is the whole point.**
  Nothing refreshes on a timer; a read happens at launch, on connect, and when
  a person asks. Without an age rule an app left open overnight shows
  yesterday's calendar with no outward difference from a fresh one. The dot
  appears on its own because the rule is recomputed as `LocalCalinoNow` ticks,
  not at the next interaction.
- **A `fetchedAt` in the future counts as fresh**, not stale. Clock skew would
  otherwise make the marker a guess.
- **A warning outranks age**: a recent read that is missing a calendar says
  "incomplete", because which part of the calendar is absent matters more than
  how old it is.
- **The marker costs the heading 26dp of layout, not 44dp.** A fourth
  full-width control wrapped "2026" onto its own line on a narrow phone, with
  the Today button and both chevrons showing. It keeps its 44dp touch lane by
  overflowing the slot -- `Row` does not clip, and hit testing uses the node's
  own bounds -- and the overflow leans left into the title, which is not
  clickable, rather than right into the next chevron. If the heading gains
  another control, re-check that case first.
- Tapping it opens Calendars and sets `accountsOrigin` from the current root,
  so back returns to Range or Agenda rather than always to Day. That `when` in
  the Accounts back handler exists for this.

The device tests do not cover it: they run with no account connected, which is
`Idle`, which by design shows nothing. `SyncIndicatorRulesTest` covers the rule
instead, and the states were checked on the emulator against a local Radicale.

## Historical CalDAV deferred work

These were the pre-write handoff items. They are retained as history; the
implemented counterparts are documented in the current write section above.

1. **Recurring tasks.** `CalTask` has no recurrence field, so a repeating
   VTODO still shows once at its due date. Events are handled; tasks are not.
2. **The write path.** Complete in `CalDavWriter`, `ICalPatcher`,
   `CardDavWriter`, `WriteQueue`, and `CalDavRepository`; see the current
   section above.
3. **Incremental sync.** Complete in `IncrementalSync`, `CalDavFetcher`, and
   `CardDavFetcher`; changed resources are bounded-concurrent GETs and invalid
   reports fall back safely.
4. **A sync indicator on the calendar surfaces.** Done on 2026-09-13; see
   "The sync marker" below. It used to read: `snapshot.sync` is only rendered
   under Calendars, so a failed refresh is invisible from the month or agenda
   view, and a stale copy looks identical to a fresh one outside the Calendars
   screen.
5. **Fetch-window paging.** The window is today ±6 months
   (`CalDavRepository.DefaultWindowMonths`) and does not extend when the user
   pages beyond it — events simply stop. Note the cache changes the shape of
   this: a cached *series* re-expands into whatever window is asked for, so
   only one-off events outside the fetched range are missing.
6. **`monthEventIndex` cost.** It is O(events × 42) per month page with three
   months composed at once, and a real expanded calendar is far larger than the
   38 fixtures it was written against. Not yet observed to be slow; measure
   before rewriting.
7. **Multiple accounts** are modelled but only lightly exercised; only one has
   been used at a time.
8. **A task with a midnight `DUE` renders as `00:00`** in the agenda rather
   than as an all-day task. Cosmetic, seen on real data.
9. **No instrumented tests.** There is still no `androidTest` source set, so
   none of this is covered at the Compose layer.

## Safe continuation rules

- Keep the implemented CalDAV/CardDAV write contract intact: conditional
  ETags, raw-resource patching, recurrence scopes, FIFO queue replay, and
  destination-first moves. Do not add timestamp conflict heuristics or make
  the optimistic overlay durable.
- Never commit credentials. `CalDavLiveTest` reads them from
  `CALINO_CALDAV_URL` / `_USER` / `_PASS` and skips when they are unset.
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

# Connected physical device, only when explicitly requested.
# The wireless serial changes; take it from `adb devices`. The Galaxy Z Fold
# has multiple displays, so screencap needs an explicit display id:
#   adb -s <serial> exec-out screencap -p -d <id> > shot.png
# with the id from `adb -s <serial> shell dumpsys SurfaceFlinger --display-id`.
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug APK is:

`app/build/outputs/apk/debug/app-debug.apk`


## Theming

`design/CalinoTheme.kt` owns the whole design system. What used to be
`object CalinoColors`, a set of static literals every surface read directly, is
now three things:

- `CalinoPalette` -- an `@Immutable data class` holding one theme's colors.
- `CalinoThemes` -- the registry. `PaperLight` and `PaperDark` ship; `all` and
  `byId` are what a settings picker and a stored preference talk to.
- `CalinoColors` -- a `@Composable @ReadOnlyComposable` property returning
  `LocalCalinoPalette.current`.

The property kept the old name **and** the old capitalised field names on
purpose. That is what let ~483 existing call sites keep compiling untouched
while the palette became a provided value; the ~50 that could not -- draw
scopes, non-composable helpers, default arguments -- were enumerated by the
compiler rather than found by grep. Do not "fix" the capitalisation.

Values track `src/themes/built-in.css` in the Calino **web** repository, which
is the same design system. That repo is reference only; never edit it from
here. Three of its rules are implemented here:

- **The warm neutral rule.** No flat grey, no pure black, no pure white.
- **The flat-dark rule.** Shadows are a light-mode device. `elevationAlpha` is
  `0f` in dark, and elevation is expressed by the `Canvas`/`Panel`/`Side` steps
  and the hairlines instead.
- **Event tint.** A mix that reads as a whisper over paper is invisible over
  ink, so `eventTintScale` pushes it to 1.6x in dark.

### Things that bit, and will bite again

- **`Text` with no explicit color.** Material defaults `LocalContentColor` to
  black outside a `Surface`, which was right on paper and invisible on ink.
  `CalinoTheme` now provides `LocalContentColor` from the palette, which is
  what carries every implicit `Text` into a dark theme. The month heading was
  the one that showed it.
- **Scrims built out of `Ink`.** At night `Ink` is nearly white, so a scrim
  made of it *lightens* what it is meant to push back. Use
  `CalinoColors.scrim(alpha)`; each surface keeps its own weight and the
  palette's `scrimBoost` makes dark heavier.
- **`Color.White` on a selected surface.** Three kinds live in the tree and
  only one is a bug: alpha masks inside `BlendMode.DstIn` gradients are
  theme-independent and must stay; real paint on an accent or ink fill must be
  `OnAccent`/`OnInk`. Watch `WeekDay`, where the pill is `Ink` and its date is
  `OnInk` -- move one without the other and the date disappears.
- **Inverting a solid fill literally.** The add pill is [Ink] on paper, which
  defines itself with no edge. Flipping that to cream at night made it the
  loudest thing on screen. `FloatFill`/`OnFloat`/`FloatBorder` let dark keep the
  pill dark and give it a brighter hairline instead; light's border is
  `Transparent`, exactly as it always drew. `SelectionFill`/`OnSelection`/
  `SelectionBorder` do the same for the selected day in the week strip and its
  compact pill. Both fills are animated, so the border animates with them --
  an edge that switches on while its block fades in reads as a snap.
- **Event colors are data, not tokens.** `Event.color` is a raw ARGB `Long`
  from the fixtures and from whatever a CalDAV server chose. `eventColor(Long)`
  is the single seam: it runs `CalinoPalette.forEvent`, which maps the six
  hues the app ships to designed dark counterparts and lifts anything else off
  the canvas. The editor's swatch row deliberately *stores* the light hues, so
  picking Rose at night does not persist a value that reads washed out by day.

### Adding a theme

Add a `CalinoPalette` to `CalinoThemes` and list it in `all`. Nothing else --
the settings cards paint their own previews from palette values. `CalinoPaletteTest`
asserts every **dark** palette clears WCAG AA 4.5:1 for `Ink`/`Ink2`/`Ink3` and
the two on-colors, which is what stops an unreadable port from shipping.

`CalinoThemeChoice` names a *mode* (System/Light/Dark), not a theme. Once the
registry holds more than the two built-ins, follow the web's shape and add
`lightThemeId`/`darkThemeId` preferences beside it rather than adding enum
entries.

### Known gaps

- **Light mode's contrast was left alone, deliberately.** `PaperLight.Ink3`
  (`#A39D93`) measures about 2.7:1 and fails WCAG AA; the web's `built-in.css`
  has since corrected the same tokens to `#655F57` and `#756D62`. Adopting them
  changes how light mode looks on every surface, so it is its own pass with its
  own emulator review. `CalinoPaletteTest` exempts light palettes and says why.
- **Cold-start flash.** `values-night/colors.xml` gives the launch window a dark
  background, so a dark *system* launches dark. Someone who forces Dark while
  the system is Light still gets one light frame: the launch theme is resolved
  from resource qualifiers before Compose runs and cannot see an in-app
  preference. Fixing it means persisting the choice somewhere the launch theme
  can read, which was out of scope here.
- The accent-color picker is still PLANNED. `Accent` is a palette field, so
  wiring it is small, but each of the five accents needs a soft/contrast pair
  per mode.

## Contacts (read/write)

Contacts are now a first-class root destination after Journal. The fixture
repository includes a small frozen address book with alphabetic and `#`
sections, a group, a photo-less contact, and a May 2026 birthday. The
directory, detail card, and editor are implemented in
`ui/surfaces/ContactsScreen.kt`; create/edit/delete operations call CardDAV when
an address book is connected and use `LocalOverlay` for immediate display.
Unavailable operations use the durable queue.

CardDAV discovery is independent from CalDAV discovery: it probes the
address-book well-known endpoint, resolves the principal's
`addressbook-home-set`, and filters a depth-1 listing to address-book
collections. `CardDavFetcher` returns raw vCard resources; `VCardMapper` uses
ez-vcard and keeps date-only BDAY/ANNIVERSARY values as `LocalDate`. Quoted
ETags are normalized before storage, BOMs are stripped, namespace lookups have
the local-name fallback, and missing privilege metadata remains writable.
Address-book sources and their enabled flags are persisted with accounts.

Raw vCards are cached as gzipped one-file-per-address-book payloads under the
private CalDAV cache directory. The cache stores server text rather than mapped
contacts, so photos and date expansion are handled only after loading. Removing
an account or disabling a book evicts its cached content. CardDAV uses the same
RFC 6578 delta, queue, ETag, and partial-read safeguards as calendars.

Journal and Contacts default to disabled preferences and are auto-enabled when
the active snapshot contains journal entries or contacts. Settings → General
has real toggles; the fixture snapshot turns both on at first composition so
the sample surfaces remain reachable. Navigation, the add-pill route order, and
global search all filter through those flags. A saved disabled root falls back
to Month.

The directory is adaptive through the existing `calinoLayoutSpec`: compact
windows use the full-width list with detail/editor bottom sheets, medium uses a
floating detail window, expanded portrait uses an end panel, and wide
landscape/folded layouts split the list rail and detail pane. The hinge keep-out
band is respected. Birthday and anniversary actions create yearly all-day
events tagged `calino:contact:<id>` (or `:anniversary`) through the same local
overlay; deleting a contact withdraws those local marker events.

Deferred follow-up: duplicate merging, `.vcf` import/export, a contact picker,
and group-membership editing. Birthday/anniversary reminder events remain
local app conveniences and are not sent to CardDAV as VEVENTs.
