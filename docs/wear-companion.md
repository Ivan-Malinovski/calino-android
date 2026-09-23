# Wear OS companion — binding decisions

## Watch presentation

The watch application follows the Wear Material 3 canonical scrolling pattern:
one `AppScaffold`, a `ScreenScaffold` per screen, and a `ScalingLazyColumn` with
native-style round-screen scaling, rotary input and scroll indication. Agenda and task records use
watch-native cards rather than phone rows;
details are a shallow second screen with full-width watch buttons and system
back handling. The UI is designed on black, keeps one vertical reading path,
and exposes schedule information in both visible labels and semantics.

The column deliberately avoids Material 3's `TransformingLazyColumn` and its
per-item `transformedHeight`/`SurfaceTransformation`. Physical Pixel Watch 2
profiling showed that path was UI-thread/input-bound and its default effect did
not match native apps on the device. The mature `ScalingLazyColumn` restores
native-like edge scaling and crown handling without that pipeline. Derived
agenda/task groups and formatted schedules are cached per snapshot as well.

Agenda and Tasks are sibling pages of a horizontal pager rather than a mode
toggle. Cards carry the calendar colour as a dot. Today's ended events are
dimmed and the agenda opens on what is happening now; a day with nothing
planned still shows its header. Complete and Tomorrow confirm with a success
dialog and return to the list; Tomorrow is hidden when the task is already due
then. Status blocks (setup, stale cache, recent action result) are plain text,
not tappable cards, and success notices drop out after ten minutes.

The Tile is a Material 3 (`protolayout-material3`) agenda projection: two or
three one-line rows (colour dot, start time or weekday, title) and an edge
button that opens the app with the overflow count. Rows open that record's
watch detail; task completion deliberately lives there rather than behind a
small tile target. The Tile publishes one timeline entry per event start/end
and midnight, so ended events drop off without a phone publish. Tiles can only
launch exported activities, so rows target `WearActivity` with an
`occurrenceId` extra.

The complication remains deliberately terse. Time is the primary datum: a live
countdown to the next event today, the weekday for later ones, "Late" for an
overdue task; the title is the label. A running event inverts this:
its title is the headline and the label is the time left ("3h"). The short
label is a few glyphs wide, so a leading emoji/symbol is dropped when words
follow (`WearFormatting.glanceTitle`), and compact 12-hour times omit ":00". It supports
SHORT_TEXT, LONG_TEXT and RANGED_VALUE (progress through the running event),
carries a monochrome icon, and publishes a timeline across event boundaries and
midnight. A tap opens that exact watch record, or the app when there is
nothing to show. Neither surface owns data or starts network work.

The Wear OS app is a companion, not another Calino client. `:wear` depends only
on the Android-free `:wear-contract`; it never depends on `:app`, DAV, phone
models, repository code, credentials, contacts, journals, raw iCalendar, or a
network permission. The phone's process-wide `CalinoContainer` remains the sole
authority for reads, recurrence, imported Calendar Provider events, writes,
queueing and reminders.

## Ownership and lifecycle

The phone owns every authoritative record and mutation. A watch snapshot is a
revocable display cache, and an optimistic watch edit is only presentation
state until the phone acknowledges it. Removing the phone account stops future
publication; removing the watch app removes its private snapshot/outbox/ACK
files. Removing a DataItem or clearing either app never deletes calendar data.
The phone never publishes fixture or pre-restore placeholder repositories.

The watch retains the latest snapshot until replaced, at most 100 outbound
commands, and the latest 20 acknowledgements. The phone retains unprocessed
inbound commands until a usable repository publication and a bounded ledger of
256 terminal UUID results. Commands expire after 48 hours.

The phone publishes `/calino/v1/snapshot` through the Wear Data Layer only when
an account exists. The projection covers today plus six days and open tasks
(overdue at most 30 days and undated), after calendar visibility and
`showTasksInViews` filtering. It contains compact display fields, schema,
persisted source epoch, monotonic sequence, generation time, phone zone,
time-format, sync/stale state and an explicit truncation bit. Truncation is
deterministic and the encoded payload is capped below 100 KB. Fixture data is
never published.

Commands and acknowledgements use versioned per-UUID DataItems. Commands carry
the exact occurrence key, record key, observed state, source epoch/sequence and
creation time; rescheduling also carries the explicit epoch day. The only
accepted operations are completing a task and moving it to a specified day.
The phone rejects expired (48 hour), stale/conflicting, missing, unsupported or
accountless work and keeps a bounded durable UUID ledger so replay returns the
same acknowledgement. Watch snapshots and the command outbox use atomic files.
Data Layer delivery is authoritative; no MessageClient correctness dependency
exists.

An action is optimistic on the watch. APPLIED, QUEUED and NOOP confirm it;
CONFLICT, NOT_FOUND, REJECTED, EXPIRED, NO_ACCOUNT and UNSUPPORTED roll it back
and remain visibly reported. ACKs carry the resulting phone source epoch and
sequence, and success stays optimistic until that projection reaches the watch.
Cold/loading state produces no terminal ACK: the durable phone inbox retries on
the next repository publication. The watch likewise republishes its durable
outbox at app start, peer reconnect and snapshot receipt. UUID deduplication
makes every replay safe.

The watch shows Agenda, Tasks and compact details. Events are read-only; tasks
offer Complete and Tomorrow, with richer work delegated to an exact phone deep
link via `RemoteActivityHelper`. The Tile shows at most three upcoming rows
(two on small screens) and the complication priority is overdue task, current/next event, then next task.
The phone remains the only reminder scheduler.

## Privacy payload

Allowed event fields are occurrence/record identity, title, calendar display
name/color, date/time/all-day span, location and write state. Allowed task
fields are occurrence/record identity, title, calendar display name/color,
due date/time, category, completion/progress and write state. The envelope adds
schema, source epoch/sequence, generated time, phone zone, time format,
stale/truncated and sync state. Notes, subtasks, attendees, contacts, journals,
hrefs, ETags, raw iCalendar/vCard, DAV URLs and credentials are forbidden.
Wear has no `INTERNET` permission and reads Tile/complication content only from
its private atomic cache.

## Distribution and versioning

Phone and watch use the same base application ID and signing identity, and the
same `.nativeDebug` suffix for debug. Wear version codes occupy the separate
2,000,000,000 range so they never collide with phone codes. The companion has
`standalone=false`, min SDK 30 and compile/target SDK 36. Release signing reads
the same private, gitignored keystore configuration as the phone; no keystore
or credential belongs in source control.

Device validation requires separate phone and Wear API 30+ emulators paired
through the Wear Data Layer. Never use an attached physical device for the
regression suite.
