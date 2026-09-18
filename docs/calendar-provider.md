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
| Occurrence identity | Detached occurrences use the provider's own exception model (`ORIGINAL_ID` + `ORIGINAL_INSTANCE_TIME`) rather than the mirror's flattening, so an inbound occurrence edit resolves to `RecurrenceEditScope.This`. |

If two-way ingest proves unsafe on device — an unbreakable loop, or an edit path
that cannot preserve foreign properties — the documented retreat is to keep the
outbound projection and set every projected calendar to `CAL_ACCESS_READ`. That
degrades to the sister app's proven design without removing the feature, which
is why the projection lands as its own commit before ingest is enabled.

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
and Calino continues exactly as it does today.

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

For projected events the **provider owns the alarm**, and Calino stops
scheduling its own for those calendars. The alternative — both paths live —
means two notifications for one event, which is the defect this section exists
to prevent.

Two consequences must be handled rather than assumed away:

1. The provider stores reminders but does not post notifications. In AOSP the
   calendar *app* receives the provider's broadcast and raises the
   notification. On a device with no calendar app installed, handing reminders
   over would silently drop them. Calino therefore keeps local scheduling when
   no calendar app is present, and re-plans on the transition in both
   directions.
2. Tasks are unaffected. `CalTask` reminders have no provider representation and
   remain entirely local, through the existing `notify/` path.

The Notifications screen states which component is delivering, next to the
existing exact-alarm disclosure, so the answer is visible rather than inferred.

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

## Scope note

This is a third sanctioned exception to the scope rules in `AGENTS.md`,
alongside TODO items 1–3 and 10 and the optional AI photo import. It adds no
new remote host and no outbound traffic: it is a local projection of data
Calino already holds. webcal, telemetry and other remote hosts remain out of
scope.
