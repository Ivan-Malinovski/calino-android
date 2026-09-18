# Calendar Provider and system-account integration — product and architecture review

Status: **approved for implementation**, 2026-09-18.

This document is the precondition that `TODO.md` item Android 4 sets for itself:
the Android platform backlog calls Calendar Provider integration "a deliberate
exception only after its product and data ownership implications have been
reviewed; it must not become an accidental second synchronization path." What
follows settles those implications before any code is written.

## The problem

Calino's CalDAV calendars are private to Calino. They are not present in
Android's `CalendarContract`, so no other calendar client, system calendar
picker, Wear OS watch face, Android Auto surface, or third-party widget can see
a Calino event. An intent filter does not fix this: those surfaces read the
provider, not intents. Closing the gap means Calino becomes an Android calendar
account provider.

## The decision

Calino ships an **account authenticator and a two-way sync adapter**: opted-in
CalDAV calendars are projected into `CalendarContract` under a Calino-owned
Android account, other calendar apps may edit them, and each edit is ingested
back through Calino's existing write pipeline.

### The alternative that was rejected, and why it is still the fallback

The sister repository (`/home/ivan/dev/calino/android`, the Capacitor wrapper
around the web app) implements a deliberately narrower design: a one-way,
read-only mirror under `ACCOUNT_TYPE_LOCAL`, with no authenticator and nothing
flowing back. Its `CLAUDE.md` records the reasoning — a read-only mirror cannot
create a feedback loop, cannot duplicate a write, and never has to map a
provider edit of one occurrence back onto a `RECURRENCE-ID`.

That design was considered and not chosen here, because it cannot deliver the
interoperability this item exists for: a person cannot edit a Calino event from
another calendar app, which is most of the value. The two-way design is
therefore accepted **with** the three risks the mirror avoided, each of which
must be answered by construction rather than by care:

| Risk | Answer |
| --- | --- |
| Feedback loop | Calino writes only through `CALLER_IS_SYNCADAPTER`, which does not set `DIRTY`; only a foreign edit can dirty a row. A content hash in `SYNC_DATA1` makes re-projecting an ingested change a no-op. |
| Duplicate write | Ingest routes to the existing `CalDavRepository` entry points, which are already serialised by `writeMutex` and already conditional on an ETag. There is no second writer and no second queue. |
| Occurrence identity | Every projected row is one occurrence, carrying `ICalMapper.occurrenceId` (`uid@instant`) in `_SYNC_ID`, so an inbound occurrence edit resolves to `RecurrenceEditScope.This` by reading one column. |

If two-way ingest proves unsafe on device — an unbreakable loop, or an edit path
that cannot preserve foreign properties — the documented retreat is to keep the
outbound projection and set every projected calendar to `CAL_ACCESS_READ`. That
degrades to the sister app's proven design without removing the feature, which
is why the projection lands as its own commit before ingest is enabled.

### Series are projected already expanded

Amended 2026-09-18, during implementation. This review first called for the
provider's own exception model — a master row carrying `RRULE`, with detached
occurrences hanging off it by `ORIGINAL_ID` and `ORIGINAL_INSTANCE_TIME`. That
does not fit the data. `ICalMapper` expands every series before a
`CalinoSnapshot` exists, so there is no master in the snapshot to project: each
event is already one occurrence, identified by `occurrenceId(uid, instant)`.
Writing the series rule onto each of those rows would ask the provider to
re-expand the series once per occurrence.

Projected rows are therefore standalone and carry no `RRULE`. Two consequences,
accepted:

- The projection reaches only as far as Calino's fetch window, where a series
  expanded by the provider would have been unbounded.
- Another calendar app offers no "edit all occurrences" affordance, because
  each row is a single event as far as the provider is concerned. Series-wide
  edits stay in Calino.

In exchange, an inbound edit names exactly one occurrence, which is what
`RecurrenceEditScope.This` needs, and no second identity scheme exists to fall
out of step with `_SYNC_ID`.

### How an ingested row settles

Amended 2026-09-18, during implementation. An ingested row has its `DIRTY`
flag cleared **and its content hash cleared with it**, so the next projection
pass rewrites it from Calino's snapshot whatever the repository decided.

That single rule is what makes the paragraph below true in code rather than in
intention. An accepted edit comes back in its canonical form; a rejected one is
reverted without a second revert path that could fall out of step with the
first. The cost is one rewrite per foreign edit, which is bounded by how often
a person edits a Calino event from another app.

The sync adapter therefore ingests first and projects second, and it projects
unconditionally — a rejected write publishes no snapshot, so nothing else
would ever put the row back.

### One pass at a time

Amended 2026-09-18, after the first run against a real CalDAV server. A
projection pass reads the provider, works out a delete-and-reinsert plan, and
applies it. Two passes running at once both plan against the same pre-state
and both insert, leaving two rows under one `_SYNC_ID`.

This is not hypothetical: it happened on the first ingested foreign edit,
because the sync adapter projects explicitly at the same moment the
repository's own publish wakes the bridge. Passes are therefore serialised.
Serialising rather than de-duplicating the callers is deliberate -- a pass is
idempotent, so whichever one waits finds its work already done and writes
nothing.

The reconcile also drops a row that duplicates one it has already kept, so a
provider that is already in that state can be brought back into shape rather
than carrying the duplicate forever.

## Authority: who owns which row

- The **CalDAV server** is the source of truth. Nothing here changes that.
- **Calino** is authoritative for content. Its cached raw resource plus
  `ICalPatcher` is what makes an edit a patch rather than a rebuild, and that is
  the only reason foreign properties survive an edit at all.
- The **Android provider** is a projection plus an inbound edit inbox. It is
  never the only copy of anything. A provider row can be deleted and rebuilt
  from the Calino snapshot at any time without data loss.

It follows that a provider edit is a *request*, not a fact. It is applied when
the repository accepts it, and reverted from the provider on the next
projection when the repository rejects it. The provider must never be left
holding a change Calino refused.

## Account ownership

One Android account per connected CalDAV account, account type
`calino.malinov.ski`, keyed by the same account id that `CalDavAccountStore`
already uses. The account name is the account's display name.

Credentials are **not** given to `AccountManager`. Calino's passwords stay in
`KeystoreCredentialStore`, encrypted with an AndroidKeyStore key. The
authenticator's token methods are stubs, and `addAccount()` routes the person
into the existing CalDAV add-account flow rather than presenting a second,
divergent one.

## Permissions

`READ_CALENDAR` and `WRITE_CALENDAR` are requested at the moment someone opts a
calendar in, never at launch, and never before there is something to project.
The disclosure states that they are used to publish the person's own calendars
to the device's calendar store and to read back edits made elsewhere; no
calendar data leaves the device by this path.

Refusal is a supported state: projection stays off, the toggle reflects that,
and Calino continues exactly as it does today. Opting *out* never asks, so a
person who later revokes the permission can still turn the projection off.

### The opt-in is the only switch

Amended 2026-09-18, during implementation. This review assumed two controls: a
per-account projection switch and a per-calendar opt-in. There is one, the
per-calendar opt-in, and the feature is on exactly while that set is
non-empty.

Two switches can disagree -- calendars opted in with projection off is a state
that means nothing, and one that teardown and restore both have to handle.
Deriving the flag removes the state rather than handling it. Practically it
also reads better: a person turning off their last published calendar means
"stop publishing", and does not then have to find a second switch to say so.

`READ_SYNC_SETTINGS` / `WRITE_SYNC_SETTINGS` are required to register the sync
adapter. A `<queries>` entry for `ACTION_INSERT` on `Events.CONTENT_URI` is
required so the installed-calendar-app probe is not a false negative on API 30
and above.

## What the provider cannot represent

`CalendarContract` is an event and reminder store. The rule for everything it
cannot hold is that it is **not projected**, never flattened into something it
is not:

- **`VTODO` / `VJOURNAL`** — tasks and journal entries stay in Calino. There is
  no provider table for them, and projecting a task as a timed event would
  misrepresent it and invite a foreign app to "edit" it into one.
- **Contacts** — out of scope entirely; CardDAV is untouched by this feature.
- **Foreign iCalendar properties** — `ORGANIZER`, `CLASS`, `X-` properties,
  foreign parameters and `VTIMEZONE` have no provider columns. They survive
  because ingest patches the cached resource rather than rebuilding it. Ingest
  must never construct a resource from scratch; that is the single rule that
  keeps this feature from quietly destroying data.
- **Alarms Calino does not model** — absolute triggers, `RELATED=END`, `REPEAT`,
  and non-`DISPLAY` actions are already deliberately unmodelled by
  `ICalAlarms.kt` and are left alone rather than projected as `METHOD_ALERT`.
- **Non-projected calendars** — a calendar that is disconnected, hidden, or not
  opted in has no provider rows.

## Reminder ownership

Amended 2026-09-18, during implementation, after looking at what the Android
CalDAV ecosystem actually does. The original text here said the provider owns
the alarm for every projected event and Calino stops scheduling its own, with
an automatic fallback to local scheduling when no calendar app is installed.
That is not the convention, and the fallback cannot be made to work.

**What the ecosystem does.** The division of labour is between two apps, not
two code paths. [DAVx⁵](https://manual.davx5.com/settings.html) — the
reference implementation of this exact architecture, an authenticator plus a
calendar sync adapter — writes reminder rows into the provider and
[deliberately never posts a notification](https://github.com/bitfireAT/davx5-ose/discussions/491),
on the stated grounds that this is the calendar app's job and DAVx⁵ is a
silent background app. Etar, Fossify Calendar and Google Calendar are the
other half: they read the provider and notify. Exactly one component speaks
because the two components are different apps.

Calino is both halves at once, so that convention gives it no answer.

**What Calino does.** Reminder rows are always projected, as `METHOD_ALERT`
— DAVx⁵'s own long-standing reminder bug is that it writes `METHOD_DEFAULT`,
which Android does not alert on, so this is a case where matching the
convention means matching what works rather than what the reference does.
Other calendar apps, Wear faces and Android Auto need those rows regardless of
who notifies.

Calino keeps delivering its own reminders **by default**, which is what every
calendar app does for its own data. One user-visible setting, off by default,
hands event delivery for projected calendars to whichever calendar app the
person uses.

**The `hasCalendarApp()` fallback is dropped.** Detecting an installed
calendar app and silently switching who notifies fails in both directions: an
app that resolves `ACTION_INSERT` may never post a notification (a widget, a
viewer, a picker), and a person with Etar installed may still want Calino's
reminders. A silent, automatic change to whether a notification arrives at all
is the worst kind of thing to get wrong, because the failure is invisible —
nothing appears, and nothing says why. Asking once is more durable than
inferring continuously.

Two things remain as they were:

1. **Tasks are unaffected.** `CalTask` reminders have no provider
   representation, so handing one over would hand it to nobody. The setting
   filters events only.
2. **The Notifications screen says who is delivering**, next to the existing
   exact-alarm disclosure. With delivery handed over, the schedule is legitimately
   near-empty, and a screen that showed that without explanation would look broken.

## Removal and reversibility

Settled before implementation, because an integration whose teardown is
undefined is not reversible in practice:

- **Turning projection off** removes every Calino-owned calendar and its events
  from the provider, and restores local reminder scheduling. CalDAV data,
  cached resources, queued writes and credentials are untouched.
- **Removing the Android account** does the same. It does not remove the Calino
  account and does not touch its credentials.
- **Removing the Calino account** removes its Android account and its projected
  calendars, in addition to the existing behaviour (credentials cleared, caches
  evicted).
- **Nothing here deletes data on the CalDAV server.** Provider teardown is a
  local operation in every case.

One pre-existing gap is adjacent enough to record: queued writes for a removed
account survive in `caldav-write-queue.json` and are dead-lettered with "That
calendar is no longer connected" rather than dropped, so an account's queued
payloads outlive its removal. Projection teardown must not silently discard
them either — they stay visible in the pending list. Fixing that gap is not
part of this item.

## Ownership boundary

Every query, update and delete is scoped by `ACCOUNT_TYPE` **and**
`ACCOUNT_NAME`, as the sister implementation's `ownCalendarSelection()` does.
This is the guarantee that a bug in this feature cannot reach the person's
Google, Exchange, or locally-created calendars. It is not a convention to be
relaxed for convenience in a later change.

## Reading the device's calendars

Everything above describes Calino publishing outward. The reverse direction —
showing the Google, Exchange and locally-created calendars the device already
holds inside Calino — is a separate feature with a separate data-ownership
question, and this section settles it before any of it is built.

The two directions are not symmetric in cost. Inbound has no ETags, no
conditional writes, no three-way rebase, no durable queue and no
dead-lettering, because the provider is local and always available. And
`CalendarContract.Instances` returns occurrences already expanded, which is the
same shape `ICalMapper` produces client-side. The saving is real, and it is why
inbound can be read-only and still be worth having.

### Authority: the source app owns every imported row

An imported row is a **read-only view**. The app that owns the account —
Google Calendar, the Exchange client, whatever created it — is authoritative
for it in every respect. Calino stores no copy beyond the in-memory snapshot,
caches nothing to disk, and in v1 never writes to a foreign row at all.

This is the mirror image of the outbound rule and has the same consequence: no
row in this feature is ever the only copy of anything. Turning an import off
loses nothing, because there was nothing of Calino's there to lose.

An imported `CalinoCalendar` is therefore constructed `readOnly = true`, and an
imported `CalEvent` carries **no** CalDAV identity: `uid`, `href`, `etag`,
`sequence` and `recurrenceId` all stay null. A provider row has no such
identity, and giving it a synthetic one would make it indistinguishable from a
real CalDAV record to every piece of code downstream. The model already permits
this — those fields are nullable — so nothing has to change to accommodate it.

### Read-only in v1, with the seam for later

Write-back is deliberately deferred, not designed out. It would add the
provider's recurrence-exception model (`ORIGINAL_ID` plus
`ORIGINAL_INSTANCE_TIME`), delete-versus-tombstone semantics, and conflict
handling against another app's sync adapter — roughly triple the work of the
read path, and the part where other apps push back.

Two choices are made now so that adding it later is an extension rather than a
rewrite:

- Writes route through a composite repository that delegates by calendar
  ownership. In v1 a write naming an imported calendar returns
  `WriteResult.Rejected`. In v2 the same branch performs provider operations.
  Nothing above the repository changes.
- The id scheme is `android:<eventRowId>@<beginMillis>`, deliberately the same
  `id@instant` shape as `occurrenceId(...)`. A future write-back recovers both
  `ORIGINAL_ID` and `ORIGINAL_INSTANCE_TIME` by parsing the id alone, with no
  lookup table — the same reasoning that put identity in `SYNC_DATA1..5`
  outbound.

### The loop, and the one predicate that prevents it

Calino publishes calendars into the provider. If Calino then read every
calendar in the provider back, it would import its own projection, which the
projection would then re-project, and the person would see each event twice and
climbing.

The whole defence is one selection, the exact inverse of the ownership scoping
used outbound:

```
Calendars.ACCOUNT_TYPE != <Calino's own account type>
```

read from `CalinoAccounts.accountType(context)` so that the debug variant's
suffix is handled rather than hard-coded. This predicate is load-bearing and
gets a test of its own: a calendar inserted under Calino's own type must not be
discovered.

It is guarded twice on purpose. The outbound projection also skips `android:`
calendars explicitly, rather than relying on the incidental fact that they have
no CalDAV account to map to. A later refactor of that mapping must not be able
to quietly turn the app into an echo chamber.

### Reminder ownership, inbound

Outbound, Calino hands alarm delivery to the calendar app that displays the
projection. Inbound, the same rule points the other way: **the source app
notifies, and Calino stays silent.** Google Calendar already alarms for its own
events, and the existing `providerOwnedCalendarIds` suppression handles this
with no new mechanism — an imported calendar simply joins that set.

A per-calendar toggle, **off by default**, hands delivery to Calino instead.

The honest caveat, which the toggle's helper text must state: Calino cannot
stop the source app notifying for its own events. Turning this on risks two
notifications rather than moving one. That is the person's call to make
knowingly, which is why it is off until they make it.

### What is not imported

- `VTODO` and `VJOURNAL` — the provider has no table for either, so an imported
  calendar declares `components = setOf("VEVENT")` and contributes no tasks or
  journal entries.
- Attendees, organiser and any property the provider does not model. As
  outbound, these are **not** flattened into something else; they are absent.
- Anything outside the same past/future window the projection uses.

### Reversibility

Turning an imported calendar off drops its events from the next snapshot and
restores whatever reminder behaviour applied before. There is nothing to clean
up in the provider, because Calino never wrote there. Turning the last one off
unwinds the composite repository entirely, so a person who never opts in pays
nothing at all.

Revoking `READ_CALENDAR` degrades to an empty import with a visible
explanation. It is not a crash, and it is not silent.

Nothing in this direction ever deletes or modifies a Google, Exchange or other
foreign row.

## Scope note

The outbound projection is a third sanctioned exception to the scope rules in
`AGENTS.md`, alongside TODO items 1–3 and 10 and the optional AI photo import.
It adds no new remote host and no outbound traffic: it is a local projection of
data Calino already holds.

Reading the device's calendars is a **fourth, separate** exception, and it is
recorded separately because the data-ownership question is not the same one.
The projection sends the person's own CalDAV data outward, where it never
leaves the device by that route. The import brings third-party account data
*in* — into Calino's snapshot, its search index and its widget. Same mechanism,
different question, so it gets its own approval rather than inheriting the
projection's.

Neither direction adds a remote host. webcal, telemetry and other remote hosts
remain out of scope.
