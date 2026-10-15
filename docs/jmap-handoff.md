# JMAP calendars (and contacts) for Calino Android: handoff and manual

Written 2026-10-06 from the Calino **web** implementation, which shipped in
`Ivan-Malinovski/calino` as squash commit `8075a082` (PR #203, closes issue #15)
and was verified against a local Stalwart 0.16. Nothing described here exists in
this repository when it was written. It has since been implemented (see
"Implementation status" below). This is an implementation brief, in the same spirit as
[`custom-dav-headers-handoff.md`](custom-dav-headers-handoff.md): it records what
was learned, so the Android app can get the same support without rediscovering
every trap.

The web checkout is a sibling directory (`../calino`). File paths below that
start with `src/`, `docs/`, `e2e/` or `scripts/` and say "web" are in that
repository, and are the reference to port from. The web docs
(`docs/JMAP.md`, `docs/jmap/*.md`, `docs/JMAP_TESTING.md`) are more detailed than
this file on the converters; read them next to the code you are porting.

## 0. Read this first

1. **Scope.** [`AGENTS.md`](../AGENTS.md) says remote calendar and contact access
   is CalDAV and CardDAV only. JMAP is a different protocol to the same
   user-entered server, with no new third-party host and no telemetry, but it
   still needs an explicit scope update in `AGENTS.md` (and the matching line in
   `HANDOFF.md`) **approved by the user** before code lands. The user asked for
   this handoff; they have not yet approved changing the rule. Do that first.
2. **Goal.** Same as web: the user types a server URL and credentials, the app
   works out whether the server speaks JMAP or CalDAV, and everything the UI does
   with a CalDAV account works with a JMAP account. An account stored without a
   `protocol` is CalDAV and must behave exactly as today.
3. **Strategy: translate at the boundary.** This app's whole data path is already
   iCalendar-shaped (raw `.ics` cache, `ICalMapper`, `ICalPatcher`, `ICalWriter`,
   `WriteQueue` payloads, `RecurrenceRules`, reminders, widget, AppSearch,
   Calendar Provider). Do **not** teach any of that JSCalendar. The JMAP backend
   speaks iCalendar upward and JSCalendar downward, with opaque `href` and `etag`
   strings in between, exactly as `CalDavFetcher`/`CalDavWriter` do today.
4. **Status of the specs** (2026-10). JMAP Core (RFC 8620), JMAP Contacts
   (RFC 9610), JSContact (RFC 9553), JMAP Sharing (RFC 9670) and JSCalendar
   (RFC 8984) are published. **JMAP Calendars is still an Internet-Draft**
   (`draft-ietf-jmap-calendars`) and JSCalendar is being revised
   (`draft-ietf-calext-jscalendarbis`). Servers already ship the draft, so the
   wire format can still move. Code that touches it must tolerate both
   vocabularies (section 4).
5. **Only Stalwart 0.16 was verified**, via a throwaway local container. Cyrus,
   Fastmail and others were not tried. Anything here described as "Stalwart does
   X" is an observation, not a spec guarantee.
6. Do not push, and do not put credentials in the repo, in logs, or in this file.
   Stalwart's dev credentials come from the dev script's `env` output; they are
   throwaway values and are deliberately not copied here.

## Implementation status (Android)

Phases 0 to 10 are built, each as its own commit. Where this document and the code
disagree, the code and its tests are right.

| Area | Where it lives | Notes |
| --- | --- | --- |
| Seam | `data/caldav/CalendarBackends.kt`, `ContactBackends.kt` | `Protocol*` routers choose by `DiscoveredCalendar.protocol` / `DiscoveredAddressBook.protocol`; a JMAP collection with no backend fails loudly instead of speaking DAV |
| Transport, detection | `data/jmap/JmapClient.kt`, `JmapDetector`, `ProtocolDiscovery` | Always the origin the person typed; loopback URLs Stalwart advertises are rebased |
| Calendars | `JsCalendarToIcs`, `IcsToJsCalendar`, `JsCalendarDiff`, `JmapCalendarFetcher`, `JmapCalendarWriter` | Synthetic `/.jmap/<account>/<calendar>/` hrefs; etag is a hash of the event |
| Scheduling | `JmapClient.schedulingInfo`, writer | `sendSchedulingMessages` when the account advertises it |
| Push | `JmapPushController` | EventSource while the app is visible, only ever a hint to run the normal sync |
| Contacts | `VCardToJsContact`, `JsContactToVCard`, `JsContactDiff`, `JmapContacts*` | Flat RFC 9610 cards on write; the `jsCard` envelope is tolerated on read |
| Dev server, live test | `scripts/live-jmap/`, `JmapLiveTest`, `JmapLiveSchedulingTest` | Throwaway Stalwart; the live tests skip without `CALINO_JMAP_*`. Two-user invitation, reply, cancellation and event-stream wake-ups pass against Stalwart 0.16 |

Decisions taken (change them with the person, not silently):

- **Tasks and journals stay out of JMAP.** JMAP calendars advertise `VEVENT` only, so the
  existing `accepts("VTODO")` checks already route new tasks to a CalDAV or device
  calendar, or show the "no task calendar" message. Nothing is queued forever.
- **Inline file attachments are off for JMAP calendars** (`CalinoCalendar.inlineAttachments`):
  no blob upload yet. Links work, and embedded `data:` attachments already on an event are
  still read and kept.
- **Free/busy (`Principal/getAvailability`) is not built**: Calino has no surface to show it.
- **Settings sync** is not ported: Android has no hidden-calendar settings sync.
- **Blob-only contact photos** are neither downloaded nor shown (no binary transport) and
  are left alone on the server, because patches are diff-only. Inline `data:` and URI photos
  are read and written.
- **Vendor and `X-` properties** do not travel over JMAP, for events or contacts.
- **Ids and search.** Record ids and the deep links built from them are href-derived for
  CalDAV already; JMAP adds the same kind of value, never a new host. The JMAP code does not
  log, and credentials stay in `KeystoreCredentialStore`.

Found only by the live test, not by any fake: `isSubscribed` is true on a person's own
calendar (fixed in `discoveredCalendar`; see 5.2).

Still unverified: iMIP delivery to addresses outside the server; Cyrus, Fastmail and other
servers; groups on Stalwart; attendee-copy RSVP of detached instances; the emulator pass
(`emulator-5554` only). Not started: JMAP blob upload/download for attachments and photos.

## 1. Plan

Each phase is its own change with its own checks, per the repository rules. Order
matters: every phase leaves CalDAV accounts untouched and the build green. Add the
phases to `TODO.md` only when the user asks (the backlog order is theirs).

| Phase | Deliverable | Gate |
| --- | --- | --- |
| 0 | Scope sign-off, `AGENTS.md`/`HANDOFF.md` wording | User approval |
| 1 | Extract a backend seam behind fetcher/writer/discovery; add `protocol` to the account model and JSON | CalDAV behaviour byte-identical; existing tests green |
| 2 | JMAP transport: session, templates, batching, errors, redirects | MockWebServer tests |
| 3 | Protocol detection on connect and the account-sheet wiring | Detection decision table tests; emulator connect to a local Stalwart |
| 4 | Read path: `JsCalendarToIcs`, calendar list, windowed event fetch, cache, full sync (read-only JMAP account) | Paired fixtures pass; live read test; emulator shows events |
| 5 | Incremental sync via `CalendarEvent/changes`; cursor and skip rules | Fake-server and live sync tests |
| 6 | Write path: `IcsToJsCalendar`, diff to PatchObject, create/update/delete, same-account move, queue and 412 integration | Live lifecycle test; queue replay tests |
| 7 | Scheduling: identities, `sendSchedulingMessages`, RSVP, existing invitations UI | Two-user live test |
| 8 | Push (EventSource) in the foreground, background worker unchanged | Stream tests; emulator |
| 9 | Contacts over JMAP (RFC 9610 + JSContact) | Live contacts test |
| 10 | UI polish, strings, release notes, docs | Review protocol in `AGENTS.md` |

The web order was the same, with contacts last. Phase 4 is the best first
user-visible milestone: a working read-only account proves transport, detection,
the converter and the cache before any write risk exists.

## 2. Mapping onto this codebase

### 2.1 Seams that exist today

`CalDavRepository` takes **concrete** classes: `CalDavFetcher`, `CalDavWriter`,
`CardDavFetcher`, `CardDavWriter`, plus `CalendarCache`. Discovery is behind the
small `CalDavClient` interface (`discover(form)`), implemented by `CalDavDiscovery`.
There is no calendar backend interface. The web app had the same shape and the
first step there was extracting `CalendarBackend`, sized to the methods the rest of
the app actually calls (about ten, plus settings sync and free/busy), with the
existing client implementing it unchanged.

Do the same: read every call into `fetcher`, `writer` and `DiscoveredCalendar` from
`CalDavRepository`, `CalDavConnectionManager`, background sync and the queue
replay, and extract an interface from **those call sites**, not from a guess.
Illustrative only:

```kotlin
interface CalendarBackend {
    suspend fun listCalendars(): List<DiscoveredCalendar>
    suspend fun fetch(calendar, window): FetchResult                  // full read
    suspend fun fetchIncremental(calendar, cursor): IncrementalFetchResult
    suspend fun put(calendar, prepared, precondition): WrittenCalendarResource
    suspend fun refreshResource(href): CalendarResource?              // bytes + validator
    suspend fun delete(href, etag): Unit
    suspend fun move(from, to, ...): WrittenCalendarResource          // JMAP: one patch
}
```

A factory (`createCalendarBackend(account, credentials)`) picks the implementation
from `account.protocol`; absent means CalDAV. The same split applies to contacts
(a `ContactsBackend`, web `src/features/carddav/client/ContactsBackend.ts`).

### 2.2 Where the JMAP code goes

Suggested package `calino.malinov.ski.data.jmap`, mirroring web `src/features/jmap/`:

| Web (reference) | Android target | Notes |
| --- | --- | --- |
| `client/session.ts` | `JmapSession.kt` | Parse, validate, URI templates, loopback rebasing |
| `client/JmapClient.ts` | `JmapClient.kt` over OkHttp | Reuse `DavHttp`'s manual-redirect and same-origin policy; add a binary path |
| `client/batching.ts` | `JmapBatcher.kt` | Limits, result references, `createdIds` |
| `client/errors.ts` | map into `CalDavErrors.kt` | Reuse the existing classification so queue policy needs no JMAP branches |
| `client/detect.ts` | `JmapDetector.kt` | Called before CalDAV discovery |
| `client/sse.ts` | `JmapEventStream.kt` | OkHttp streaming; see section 8 |
| `convert/jscalendarToIcs.ts` | `JsCalendarToIcs.kt` | Read boundary |
| `convert/icsToJscalendar.ts`, `jscalendarDiff.ts` | `IcsToJsCalendar.kt`, `JsonPatch.kt` | Write boundary and diff/apply |
| `backend/JmapCalendarBackend.ts` | `JmapCalendarBackend.kt` | The backend |
| `backend/eventData.ts` | `JmapEventData.kt` | Update construction (section 6.2) |
| `contacts/*` | `JmapContactsBackend.kt`, `JsContact.kt` | Phase 9 |
| `backend/__tests__/fakeServer.ts` | MockWebServer `Dispatcher` | In-memory JMAP server for tests |
| `convert/__tests__/fixtures/*` | `app/src/test/resources/jmap/` | Copy the paired fixtures verbatim |

### 2.3 Account model and persistence

- Add `protocol` (`caldav` | `jmap`) to `CalDavAccount`. `CalDavAccountJson.decode`
  must default a missing field to CalDAV. Keep the field out of nothing sensitive:
  the account JSON must still contain no secret.
- Calendar ids and URLs: JMAP calendars use **synthetic absolute URLs**,
  `<origin>/.jmap/<accountId>/<calendarId>/`, and events
  `<calendar url><eventId>`. They are nested and absolute so
  `requireSameDavOrigin`, `resourceIsInCollection`-style checks, cache keys and
  href bookkeeping keep working unchanged. Percent-encode the ids. The event id is
  a short server id and is **not** the iCalendar UID.
- `CalDavCalendar.ctag` is always `null` for JMAP and `syncToken` holds the
  `CalendarEvent` state string (section 5.3).
- `DavCredentials` (username, password, and gateway headers if the custom-headers
  handoff has landed) is reused. Basic auth only; the web implementation did not
  do OAuth and neither should the first Android cut.
- **Switching an account's protocol** (the web has a "Use CalDAV" override under
  Connection settings) must evict that account's calendar cache and cursors. DAV
  hrefs and JMAP synthetic hrefs differ and a sync token is not a state string.
  Reusing either across protocols produces silent nonsense.
- The Keystore-backed credential store, `CalinoContainer` ownership, and
  `BackgroundSync` all stay as they are; background refresh replays the durable
  queue first, then reads, regardless of protocol.

## 3. JMAP in one page (what the spec says, as used here)

All of this is JMAP Core, RFC 8620, unless noted. Calendar details come from the
draft and are marked.

**Session.** `GET <origin>/.well-known/jmap` with credentials. Stalwart answers
307 to `/jmap/session`. The JSON has `capabilities`, `accounts`, `primaryAccounts`,
`username`, `apiUrl`, `downloadUrl`, `uploadUrl`, `eventSourceUrl`, `state`.
Require `urn:ietf:params:jmap:core`. Calendars are present when
`capabilities` has `urn:ietf:params:jmap:calendars` **and** the primary account
for that capability exists and advertises it in `accountCapabilities`. Contacts use
`urn:ietf:params:jmap:contacts` and may map to a *different* primary account.
Core limits to honour: `maxCallsInRequest`, `maxObjectsInGet`, `maxObjectsInSet`,
`maxSizeRequest`, `maxSizeUpload`.

**Request** (`POST apiUrl`, `Content-Type: application/json`):

```json
{
  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:calendars"],
  "methodCalls": [
    ["CalendarEvent/query", {"accountId": "b", "filter": {"inCalendar": "c1",
        "after": "2026-10-01T00:00:00", "before": "2026-12-01T00:00:00"}}, "q"],
    ["CalendarEvent/get", {"accountId": "b",
        "#ids": {"resultOf": "q", "name": "CalendarEvent/query", "path": "/ids"}}, "g"]
  ]
}
```

The response has `methodResponses` (`[name, result, callId]`, or `["error", {...},
callId]`), `sessionState`, and optionally `createdIds`. A `sessionState` that
differs from the session's `state` means the session changed: refetch it before
the next request. `"#prop": {resultOf, name, path}` is a result reference
(JSON-pointer path, `*` wildcard allowed).

**Objects.** `Foo/get` (`ids`, `properties`; returns `state`, `list`, `notFound`),
`Foo/changes` (`sinceState`, `maxChanges`; returns `oldState`, `newState`,
`hasMoreChanges`, `created`, `updated`, `destroyed`; method error
`cannotCalculateChanges` when the server cannot answer), `Foo/set` (`ifInState`,
`create`, `update`, `destroy`; returns `oldState`, `newState`, `created`,
`updated`, `destroyed` and per-object `notCreated`, `notUpdated`, `notDestroyed`
with SetError `type`s such as `forbidden`, `notFound`, `invalidProperties`,
`invalidPatch`, `overQuota`, `tooLarge`, `rateLimit`, `stateMismatch`).
Per-object errors live inside a *successful* response and must be inspected.

**PatchObject** (RFC 8620 5.3, used by every `update`): keys are JSON-pointer
paths without the leading slash (`participants/p1/participationStatus`), with `~`
as `~0` and `/` as `~1`. A value replaces; `null` **deletes** the member. All
parents must exist and no two paths may overlap. Consequences used throughout:
arrays and objects are replaced wholesale, so for a nested array change send the
whole array; a literal JSON `null` can never be assigned.

**Blobs.** `POST uploadUrl` (template with `{accountId}`) returns
`{accountId, blobId, type, size}`. `downloadUrl` is a template with
`{accountId}`, `{blobId}`, `{name}`, `{type}`. Expand templates by percent-encoding
each variable individually.

**Push.** `eventSourceUrl` template with `{types}`, `{closeafter}`, `{ping}`:
`GET` it with `Accept: text/event-stream`; events named `state` carry
`{"@type":"StateChange","changed":{"<accountId>":{"CalendarEvent":"<state>"}}}`.
Browsers' `EventSource` cannot send `Authorization`, which is why the web used
`fetch` streaming; OkHttp has no such problem.

**Calendars (draft).** `Calendar/get|changes|set`; properties used: `name`,
`color`, `sortOrder`, `isVisible`, `isSubscribed`, `isDefault`, `myRights`.
`CalendarEvent/get|changes|set|query`. An event is a JSCalendar object plus
`id`, `calendarIds` (a **map** `{ "<calendarId>": true }`, so an event can be in
several calendars), `isOrigin`, `isDraft`. Query filters: `inCalendar`, `after`,
`before` (UTC date-times in the web's use), and, per the draft, `uid` (the web does
not use it; see section 6.3). `CalendarEvent/set` accepts
`sendSchedulingMessages` (section 7). Free/busy is `Principal/getAvailability`
under `urn:ietf:params:jmap:principals:availability`. Scheduling identities are
`ParticipantIdentity/get`.

## 4. Wire format notes and server quirks (Stalwart 0.16.25)

These all cost time. Each is handled in the web code; none is in the specs.

- **`accountId: null` is rejected.** Every method needs the real account id from
  `primaryAccounts[...]`. Never send `null` or omit it.
- **Advertised loopback URLs.** Stalwart reports `https://localhost/...` for
  `apiUrl`, `uploadUrl`, `downloadUrl` and `eventSourceUrl` whenever its hostname
  is `localhost`, even on a plain-HTTP dev server reached at another address. When
  an endpoint's host is loopback (`localhost`, `127.x`, `::1`), rebase it onto the
  origin the session was actually fetched from. Never rewrite non-loopback hosts.
  On the emulator this is what makes `http://10.0.2.2:<port>` or an `adb reverse`d
  `127.0.0.1` work.
- **Vocabulary is the newer JSCalendar.** `recurrenceRule` (singular object),
  `calendarAddress` (not `sendTo`/`email`), `organizerCalendarAddress`,
  `calendarIds` (map). The RFC 8984 spellings (`recurrenceRules[]`, `sendTo`,
  `replyTo`, `calendarId` forms) must also be **read**. Write the draft form;
  keep an `rfc8984` switch in the writer (the web writer has one) so another
  server can be supported by flipping a flag instead of forking.
- **Vendor properties are rejected on write** (`invalidProperties`) and `X-`
  properties are hidden. Unknown iCalendar data therefore **cannot round-trip
  through JMAP**. This breaks this app's "foreign properties stay byte-identical"
  invariant for JMAP accounts. It is a protocol/server limit, not a bug to chase.
  Patch-based updates keep server-side data intact; never invent or send vendor
  properties. Tell the user in the docs, not with a runtime error.
- **`recurrenceOverrides`** is keyed by *local date-time in the master's own
  zone/form* and carries both modified occurrences (PatchObjects) and exclusions
  (`{"excluded": true}`). The server turns them into detached VEVENTs and
  EXDATEs. All-day masters use the date at midnight.
- **`updated` mirrors DTSTAMP**, so it is not a modification time and not an
  ETag.
- **Event ids are not UIDs.** `uid` is a separate JSCalendar property.
- **Never send `descriptionContentType`** unless there is a description, and never
  an explicit `text/plain`: Stalwart then stores `STYLED-DESCRIPTION`, which plain
  CalDAV clients do not display.
- **RDATE with a PERIOD** becomes an override with `duration` but no `start`;
  Stalwart then writes a VEVENT with **no DTSTART**. Repeat the override key as
  `start` in such patches.
- **Server-side losses** (not converter bugs): one delegate per participant;
  relation names upper-cased; the `required` role dropped; `data:` link hrefs
  mangled; VALARM `DESCRIPTION`/`SUMMARY`/`ATTENDEE` not carried (JMAP alerts have
  no text or recipients; the converter fills in what RFC 5545 requires from the
  event title and attendees); per-attendee `SCHEDULE-SEQUENCE` dropped.
- **Inline `ATTACH` (base64) does not survive.** It comes back as a link whose
  `href` is the decoded bytes. The editor here embeds picked files inline; for a
  JMAP account gate that off (or implement blob upload and verify it) rather than
  silently corrupting attachments.
- **`CONCEPT` is URI-valued** (RFC 9253): do not TEXT-escape commas. Stalwart's
  own CalDAV output escapes them; fixtures use comma-free concepts.
- **No JMAP tasks.** Stalwart advertises no tasks capability: JMAP calendars are
  VEVENT-only. See section 9 for the UI consequence.
- **`CalendarEvent/query` filter**: Stalwart takes `inCalendar: "<id>"` and rejects
  the older `inCalendars: [..]` with `unsupportedFilter`. Send `inCalendar`, fall
  back to `inCalendars` only on `unsupportedFilter`.
- **Calendar state is not event state.** `Calendar` and `CalendarEvent` states are
  opaque and differ. `Calendar/get` state does not move when events change.
- **CORS is irrelevant on Android** (and the CORS proxy, `X-Follow-Redirects`, the
  mixed-content and CSP checks in the web diagnostics have no native equivalent).
  Do not port them. Keep the *classification* ideas: observed vs inferred
  evidence, and blame (credentials / server / network).
- **Contacts:** Stalwart rejects `blobId` in photo `Media` and the pre-RFC `jsCard`
  envelope. Send the flat Card form, with inline `data:` photo URIs when blobs are
  refused (section 10).

## 5. Read path

### 5.1 Discovery and detection (`JmapDetector`)

Runs before CalDAV discovery on the connect action:

1. Trim the typed URL; add `https://` to a bare host; keep an explicit `http://`.
2. Probe `GET <origin>/.well-known/jmap` with Basic credentials. If the typed URL
   already points at a session resource (`/jmap/session`, `/.well-known/jmap`),
   probe that directly and also try its path-local `/.well-known/jmap`.
   At most two probes. Eight-second timeout each.
3. Follow redirects **manually**, same origin only (this repo's DAV rule), and
   parse JSON.
4. JMAP only if the session has core, the global calendars capability, **and** the
   calendars capability on the primary calendar account. **A server offering both
   protocols selects JMAP.**
5. Fall through to CalDAV discovery for: HTML or login pages, 404/405, non-JSON,
   an invalid session, missing capability, network error, timeout.
6. A **JSON 401** (JSON content type, usually with `WWW-Authenticate`) is a real
   JMAP authentication failure: surface a credentials error and **stop**. Falling
   through would produce a misleading CalDAV error. A 401/403 with another content
   type is not conclusive.
7. Provider guessing and subdomain rewriting stay in CalDAV discovery.

Persist the detected protocol on the account. Show it read-only in Connection
settings with a "Use CalDAV" override (web did exactly this; see
`docs/jmap/ui.md`). Keep the typed draft intact on failure, as the sheet does now.

Security rule to decide explicitly (recommendation: **require** the session's
`apiUrl`, `uploadUrl` and `eventSourceUrl` to match the origin of the final session
URL, after loopback rebasing; same rule as DAV hrefs). The web did *not* rewrite
non-loopback hosts but also did not forbid them; on Android you carry credentials
to whatever host you call. A legitimate split (for example blob hosts on another
domain) is the reason this is a decision, not a rule: if you must allow
cross-origin `downloadUrl`, send **no** `Authorization` header to it unless the
user opts in, and say so in the docs. Gateway headers (custom-headers handoff)
are secrets with the same origin rule.

### 5.2 Calendars list

`Calendar/get` with `ids: null`. Map name; `color` through the existing colour
normalisation; `isVisible`; `isSubscribed` (**not** a read-only signal: Stalwart sets it on the person's own default calendar, which grants every write right; the rights decide, and a subscribed calendar that states none is treated as a read-only feed); `isDefault`;
`sortOrder`; and `myRights` to `readOnly`: writable when any of `mayWriteAll` or
`mayWriteOwn` is true (current drafts and Stalwart; older drafts used per-item
add/modify/remove rights, which the web also accepts). Components are
VEVENT only unless a tasks capability is advertised. `ctag` stays `null`.
Capture the `CalendarEvent` state **before** listing calendars so the first full
sync starts from a valid `changes` cursor.

### 5.3 Events: windowed full read

- `CalendarEvent/query` with `inCalendar`, `after`, `before` (the fetch window the
  app already computes), paged with `position`/`limit`, then `CalendarEvent/get`
  through a result reference. Keep `expandRecurrences` false (the default): you get
  masters, and this app's `RecurrenceRules` expands them client-side from the cached
  raw text as the window changes. Do **not** use server-side expansion.
- The web sends `after` and `before` as UTC strings with a `Z` suffix
  (`2026-10-01T00:00:00Z`), converting from compact CalDAV form
  (`20261001T000000Z`) and ISO dates, and omits both for "all events". Do the same.
- If the query `state`/`queryState` changes between pages, treat the listing as
  non-authoritative and retry rather than commit a partial snapshot. This matches
  the cache rule that whole snapshots are accepted or rejected, never truncated.
- Split `get`s at `maxObjectsInGet`, and any batch at `maxCallsInRequest`. When a
  split `get` sees its `state` change between parts, fail as a conflict and retry
  the read.
- Convert each JSON object with `JsCalendarToIcs` and store `CalendarResource(
  href = synthetic URL, etag = hash, ics = converted text)` in the existing raw
  cache. The cached text is a **projection**; it is what `ICalMapper` reads.

**ETag.** JMAP has none. Use a quoted SHA-256 over the canonical JSON of the event
exactly as the server returned it: object keys sorted recursively, array order
kept, every property (including unknown ones) contributes, so any server-side
change moves it. Pitfall for Kotlin: `org.json` does not preserve or sort keys and
formats numbers inconsistently; write one canonicaliser over one tree type, and
hash the canonical string, never `JSONObject.toString()`. Compute it from the
parsed response of the same code path every time.

### 5.4 Incremental sync

`syncCollection` maps to `CalendarEvent/changes`; the **state string is the sync
token**, stored where `CollectionCursor` stores tokens today.

- Full sync: fetch the event state **before** the listing (so races are replayed
  next time), then list, then commit the cursor with the snapshot. This mirrors
  this repo's rule "the cursor is committed only after the complete snapshot".
- Incremental: follow `changes` through every `hasMoreChanges` page, merge
  `created`/`updated`/`destroyed`, `get` the current objects, and filter by
  membership in the requested calendar (`calendarIds`). Destroyed, missing, and
  *left this calendar* all become `Removed` tombstones. Tombstones for unknown
  resources are harmless.
- The cursor you return is the `changes` `newState`, never a later `get` state.
- **Any** error, including `cannotCalculateChanges`, returns "token invalidated" so
  the existing full-resync path runs. Reuse `SyncCollectionFallbackReason`.
- **Do not skip a collection because the ctag is unchanged.** `ctag` is always
  null for JMAP; make `shouldSkipCollection`/`canSkipWith` never skip JMAP
  collections, or events will never sync. (Web has the same trap: "UI integration
  must avoid skipping JMAP event sync solely on an unchanged ctag.")
- A change to an event also changes it for *every* calendar it belongs to; the
  per-calendar membership filter is what keeps results correct.

### 5.5 The read converter (`JsCalendarToIcs`)

Reference: `src/features/jmap/convert/jscalendarToIcs.ts` and
`docs/jmap/jscalendar-to-ical.md` (full mapping table; read it). Output is one
VCALENDAR: a master component plus detached components for modified recurrences.
Decisions worth repeating:

- **Required `uid`.** Throw rather than invent an identity.
- **Time.** `start` is a local date-time; `timeZone` null/absent means floating,
  `UTC`/`Etc/UTC` means `Z`, anything else is an IANA TZID. `showWithoutTime`
  means `VALUE=DATE`, exclusive end, zero duration becomes one day. `duration` to
  `DTEND`: add nominal weeks/days first, then elapsed hours/minutes/seconds in the
  start zone. A zero-length timed event uses `DURATION:PT0S` (DTEND must be later
  than DTSTART). `endTimeZone` (or an end-relative location with a zone) gives the
  DTEND `TZID`.
- **DST.** RFC 8984 1.4.5-6: in a fold a local time means its *first* occurrence;
  in a gap use the offset *before* the gap. `java.time` does this for you:
  `ZonedDateTime.ofLocal(ldt, zone, null)` takes the earlier offset in an overlap
  and shifts forward by the gap length, which is the same instant. The web needed
  date-fns-tz for this; you do not. Also use `java.time` for IANA and Windows-name
  resolution via the existing `ICalTimezones`.
- **Recurrence.** Prefer the singular `recurrenceRule` if both spellings exist; the
  array spelling can emit multiple RRULEs. Local `UNTIL` converts to UTC for a
  zoned DTSTART, to a DATE for all-day, to local for floating. `COUNT` wins if both
  bounds are given. `excludedRecurrenceRules` become EXDATEs when finitely
  enumerable (cap 100,000), else deprecated EXRULE is retained.
  `recurrenceOverrides[id].excluded` becomes EXDATE; other overrides become RDATE
  when outside the rule set and a **detached component with `RECURRENCE-ID`** when
  the patch actually changes something. Each occurrence starts as a deep copy of
  the master with `start` set to the override key, then the patch applies.
  Detached instances inherit the UID and never inherit RRULE/RDATE/EXDATE.
- **PatchObject application** (for overrides): slash paths decode `~1`/`~0`; null
  deletes; objects/arrays replace; missing parents, array traversal, bad escapes
  and overlapping paths reject the whole patch; keys like `__proto__` are plain
  keys. The input must never be mutated.
- **Participants and scheduling:** `organizerCalendarAddress`, then `replyTo`, then
  the first owner participant is the ORGANIZER; attendee address preference is the
  newer `calendarAddress`, then `sendTo.imip`/other, then `mailto:` of `email`.
  Roles to `ROLE`, `participationStatus` to `PARTSTAT`, `expectReply` to `RSVP`,
  `kind` to `CUTYPE`. Delegation maps may be keyed by calendar address (Stalwart
  sends the address as a string) or by participant id (RFC); accept both and
  ignore dangling references.
- **Alerts:** OffsetTrigger to `TRIGGER;RELATED=START|END`; AbsoluteTrigger to
  `TRIGGER;VALUE=DATE-TIME`. DISPLAY and EMAIL only. This feeds the existing
  `ICalAlarms`/reminder scheduling, which only edits alarms it can model as a lead
  time; the rest are preserved but invisible, as today.
- **Conference:** `virtualLocations[*].uri` to `CONFERENCE;VALUE=URI` (with
  `LABEL`) and `URL`. The existing `CalEvent.conferenceUrl` reader then works.
  Always emit `VALUE=URI` (see the note in `HANDOFF.md` about the biweekly
  `CONFERENCE` scribe).
- **Serialisation.** Fixed property order; map entries and overrides sorted by key
  for determinism; CRLF endings; fold at 75 **octets** (UTF-8, including the
  continuation space) without splitting a code point; RFC 6868 parameter escaping
  and RFC 5545 quoting; URI values keep syntax but CR/LF are percent-encoded to
  block content-line injection; truncate fractional seconds.
- **No VTIMEZONE blocks** from the converter. `ICalWriter.prepareForWrite` already
  builds one offline from tzdb for every written TZID; keep that path for writes.
  For reads, make sure `ICalTimezones.parse` can resolve a TZID without an embedded
  VTIMEZONE (the web had to reload them from a registry; here tzdb already knows
  them).
- Unsupported things throw rather than guess: custom JSCalendar `timeZones`,
  non-Gregorian recurrence scales, leap-month `BYMONTH`, unknown TZIDs.
- Known representability gaps, accepted in web: multiple physical locations
  collapse to one `LOCATION`/`GEO` (comma-joined, stable id order); more than one
  URL collapses to the first; HTML descriptions are plain text; unknown
  properties and vendor extensions are ignored.

## 6. Write path

All the invariants in `HANDOFF.md` ("Write path") still hold. Only their mechanism
changes. Keep the queue, `LocalOverlay`, `PendingChange` and `PendingChangePolicy`
**protocol-neutral**: payloads stay iCalendar, and translation happens inside the
JMAP backend at send time.

### 6.1 The one-line rule

**Never send a full replace; send a PatchObject computed against what the server
holds right now.** A replace would erase every server-side property Calino does not
model, and (see section 4) several properties cannot be re-sent at all.

### 6.2 Update algorithm

Web reference: `backend/JmapCalendarBackend.ts` and `backend/eventData.ts`
(`diffJscalendar`, `applyJmapPatch` in `convert/jscalendarDiff.ts`).

1. Re-read the event: `CalendarEvent/get` for the id. `S` = the server's JSON.
2. If the caller supplied an ETag and `hash(S)` differs, throw a status-bearing
   conflict (HTTP-412-like). **The existing 412 rebase machinery then runs
   unchanged**: refresh, three-way rebase of modelled fields onto the new bytes
   using `baseData`, `SEQUENCE` as the conflict signal, local-wins after a safe
   rebase. An empty ETag means unconditional.
3. Compute the projection `projected = toJs(toIcs(S))`: the server object pushed
   through both converters, i.e. what the app can *represent* of it. The editor's
   new ICS `N` (produced by the *existing* `ICalPatcher`/`ICalWriter` from the cached
   raw text) becomes `next = toJs(N)`. Start from `desired = clone(S)`. For every
   top-level key where `projected[key]` and `next[key]` differ, merge just that key
   into `desired` (a three-way reconcile of `S[key]`, `projected[key]`, `next[key]`;
   a key absent from `next` is deleted). Keys the user did not change are never
   touched, so the server's own representation, defaults and extensions survive and
   merely opening the editor rewrites nothing. Send `diff(S, desired)` as the
   PatchObject. (This is `eventPatch` in web `backend/eventData.ts`; port it with
   its tests rather than from this summary.)
4. Match participants **by calendar address** to keep server participant ids and
   unrepresented fields; reconcile alert, location and attachment ids against the
   individual server entries so ordinary edits and alarm removals keep their
   identity. Fields removed in the ICS are deleted (null). Server-owned audit
   fields (`id`, `isOrigin`, `isDraft`, `created`, `updated`) are ignored.
5. `CalendarEvent/set { update: { id: patch } }`. Check the per-object result:
   `notUpdated` carries the SetError; map `forbidden`, `notFound`,
   `invalidProperties`, `overQuota`, `stateMismatch`.
6. **No ETag in the response.** `set` returns only server-set properties. So, as
   this repo already requires for a PUT with no response ETag: re-`get`, and use
   that one response for **both** the cached bytes and the validator. Never pair a
   validator from one read with bytes from another.

Pitfalls found in the web diff code:

- JMAP `null` is deletion, not a value. A nested change *to* a literal null must
  replace the enclosing object. A root-level transition such as UTC to floating
  emits `timeZone: null`, which deletes the member, which *is* floating; assert
  semantic, not literal, equality for these.
- Maps recurse by key; arrays and the singular `recurrenceRule` replace wholesale.
  New map entries are whole objects, so their paths need no existing parent.
- Intrinsically lossy fields (joined locations) cannot be recovered by an edit;
  accept it and document it.
- Alarm identity: key alerts by the iCalendar alarm `UID` when it is a valid
  JSCalendar id, otherwise a deterministic hash, so editing a trigger keeps the
  same key. Resolve hash collisions deterministically.

### 6.3 Create

`CalendarEvent/set { create: { "<clientId>": event } }` with `calendarIds:
{ "<id>": true }` and the converter's output. Read back by the returned id, compute
etag from the re-read. Tasks and journals are **rejected** before submission:
never send a VTODO to the event method.

**Idempotency hazard (unverified, Android-specific).** CalDAV creates here are
verified with `If-None-Match` plus a GET after a 412. JMAP has no conditional
create, and a lost response to a successful `set` would be retried and duplicate
the event. Plan: before replaying a CREATE whose first attempt may have reached the
server, query by `uid` (`CalendarEvent/query` with the draft's `uid` filter, **not used or
verified by the web**; check Stalwart supports it, else list the window and match
`uid` locally) in the target calendar; present means "lost response", compare payloads exactly as the CalDAV
rule does; absent means retry. **Test what Stalwart does with a duplicate UID**
before relying on any server-side uniqueness.

### 6.4 Delete

Re-read, compare ETag, then `destroy`. Deleting an href from an event that belongs
to several calendars must remove only that membership (patch `calendarIds/<id>:
null`); deleting a stale source href after a move is harmless. Calendar deletion
sends `onDestroyRemoveEvents: true`.

### 6.5 Move between calendars

**Same-account JMAP moves are one atomic update**: patch
`calendarIds/<from>: null` and `calendarIds/<to>: true`; the id and UID are stable
and the new synthetic href is returned. The CalDAV rules "destination first, source
second, `DELETE_HREF` cleanup" are therefore **not** used for these; do not run
create-then-delete (it is not atomic and changes the id). Other memberships are
retained. The existing move planner is still the path for **cross-account and
CalDAV-to-JMAP** moves (web verified a CalDAV to JMAP transfer works through the
same create-then-delete route). Keep its create-only destination write, the
exact-payload GET on conflict, and the "never delete source after a failed
destination" rule.

### 6.6 Batching and partial failure

Partition explicit `get` ids and combined `set` create/update/destroy at
`maxObjectsInGet`/`maxObjectsInSet`; carry `createdIds` between batches; advance
`ifInState` to each preceding response's `newState` for conditional splits. Split
writes are sequential and may partially succeed: **there is no rollback and no
automatic replay of a write** (a successful write whose follow-up session refresh
failed must not be replayed). A `set` using `onSuccess...` arguments that would need
splitting must be rejected up front. Method-level `error` results throw and keep the
batch's responses on the exception.

### 6.7 Error mapping

Map into this app's existing classification so queue policy needs no JMAP branches
(check the actual names in `CalDavErrors.kt`; the web vocabulary was):

| JMAP / HTTP | Class | Status |
| --- | --- | --- |
| HTTP 401 | auth | 401 |
| HTTP 403, `forbidden`, `accountReadOnly` | forbidden | 403 |
| network failure | network | none |
| aborted | timeout | none |
| 429, `rateLimit` | rate-limited | 429 |
| 507, `overQuota` | quota | 507 |
| `accountNotFound`, `notFound` | not-found | 404 |
| `stateMismatch` / ETag mismatch | conflict | **412** |
| `serverFail`, `serverUnavailable` | server | 500/503 |
| `invalidArguments`, `unknownMethod`, `invalidResultReference` | unknown | 400 |
| `requestTooLarge`, `tooManyCalls`, `tooManyObjects`, `limit` | unknown | 413 |

Keep request-level problem JSON (`type` URN, including `notJSON`), body, HTTP status
and `Retry-After` on the exception. `Retry-After` is delay-seconds or an HTTP date,
clamped to one hour like DAV. Network failures keep no numeric status so the queue's
uncounted-retry rule still applies.

### 6.8 Concurrency honesty

The ETag check is "re-read then write": a concurrent writer between the two can
still win. JMAP's account-wide `ifInState` is too coarse to use as If-Match (any
change to anything invalidates it). Accept best effort, say so in `HANDOFF.md`, and
rely on `SEQUENCE`-based rebase after the fact. `LocalOverlay` and the write mutex
stay as they are; serialise sync and writes through the same lanes as CalDAV.

## 7. Scheduling and invitations

- **Server delivery must be requested.** A JMAP server does not send scheduling
  messages unless asked (verified: without the argument an attendee receives
  nothing). `create`, `update` and `destroy` on `CalendarEvent/set` carry
  `sendSchedulingMessages: true` when the event has participants, which is what a
  CalDAV server does implicitly. A pure move between calendars sends nothing. A
  server that rejects the argument with `invalidArguments` is remembered and the
  write retried without it.
- Verified live: invitation, update and cancellation reach a second local Stalwart
  user. **iMIP to external addresses was never verified** (it needs working mail
  delivery); say so in docs until someone tests it.
- **Identity.** The web detects scheduling from capability flags or
  `ParticipantIdentity/get`. This maps onto `CalDavAccount.calendarUserAddresses`
  (identity addresses) and `autoSchedule`. Fill both from the session/identities for
  a JMAP account; the existing "confirmed `mailto:` identity required to create
  invitations" rule still applies.
- **RSVP and incoming invitations.** This app already derives pending invitations
  from downloaded attendee copies and sends a one-`ATTENDEE`, one-`PARTSTAT` patch
  (see `invitations-rsvp-plan.md`). The web app has *no* invitations UI yet, so
  there is no reference for this path; it will go through section 6.2 and become a
  `participants/<id>/participationStatus` patch. **Test what Stalwart permits an
  attendee-owned copy to change** (it may reject anything but the response) and
  whether a detached-instance response needs the occurrence's override key.
- **Free/busy.** Optional. `Principal/getAvailability` with `id`, `utcStart`,
  `utcEnd`, `showDetails: false`; `using` must include principals and availability.
  Needs `urn:ietf:params:jmap:principals:availability`; otherwise return nothing.
  The calendar often has no `ownerPrincipalId` (Stalwart), so resolve the principal
  by email with `Principal/query` (there the principal id equals the account id).
  A created event came back as a `BUSY` period (`busyStatus: "confirmed"`).

## 8. Push and background

- Android is the easy platform for push: OkHttp can send `Authorization`, so there
  is no `fetch` workaround. Build a streaming call with `readTimeout(0)` for that
  call only (`client.newBuilder()`); do not change the shared client's timeouts.
  `okhttp-sse` is an option; a hand parser is also fine.
- Request `types=Calendar,CalendarEvent` (or `*`), `closeafter=no`, `ping=30`. The
  parser must handle comments, a BOM, multi-line `data:`, CR/LF boundaries, event
  ids, and the server's `retry:` field. Only a validated `StateChange` reaches the
  callback; malformed events are ignored; changes for other accounts are filtered.
- Reconnect with exponential back-off to 60 s, honour `Retry-After`, resume with
  `Last-Event-ID`, and use a silence watchdog (2x the ping interval plus 10 s).
  **Authentication or permission failures stop reconnecting.** Cleanup cancels the
  call and the timers.
- A state change is only a **hint**: trigger the normal serialized sync
  (`synchronize()`, behind `syncMutex`), never apply data from the event.
- Hold the stream only while the app is foregrounded. Doze and App Standby will
  kill it, and there is no FCM path here (JMAP `PushSubscription` needs a push
  service and is out of scope and out of the privacy rules). **WorkManager's
  periodic job remains the background mechanism**, unchanged; it already replays the
  queue then reads. Fixture mode and accounts without `eventSourceUrl` simply have
  no stream.
- The web proved a 60-second held stream through the hosted CORS proxy; that does
  not apply here, but a 60-second live hold against the local Stalwart (with pings)
  is still the right smoke test.

## 9. UI consequences

- **Protocol label** (read-only) and **"Use CalDAV"** override in connection
  settings. Both need strings; follow `docs/localization.md`.
- **Tasks and journals.** Stalwart offers none over JMAP, and this app has tasks and
  journals as first-class surfaces. For a JMAP account, create only VEVENT
  calendars, hide or disable task/journal creation into those calendars, and make a
  rejected VTODO write fail with a clear message instead of queueing it forever.
  Decide with the user whether tasks stay local-only for JMAP accounts.
- **Attachments:** disable the inline file picker for JMAP accounts until blob
  upload is implemented and verified (section 4). Links still work.
- **Editor honesty.** Events lose vendor/`X-` properties; document it once in the
  README rather than warning per edit.
- **Settings sync** (if this app syncs settings through a hidden calendar event): the
  web uses a dedicated calendar `Calino Settings` with one event, UID
  `calino-settings`, private and free, whose DESCRIPTION is
  `Calino settings v1:` plus a base64 payload. It survives Stalwart dropping
  X-properties. The event's `updated`/DTSTAMP is not a modification time, so the
  payload carries its own `syncedAt`. Only port this if Android has an equivalent.
- **Calendar Provider, widget, reminders, AppSearch, assistant functions** read from
  repository snapshots and should need no change. Verify, in particular, that the
  synthetic `/.jmap/` URLs and server ids never reach AppSearch rows, logs or
  provider columns (the search index must stay free of server URLs).
- **Diagnostics.** The web has a "Diagnose the connection" tool with JMAP-aware
  checks. Do not port it as is. The reusable idea is a ladder (session, API,
  calendars, contacts, push) that reports the first failing rung, with a JSON 401
  short-circuiting the rest.

## 10. Contacts over JMAP (phase 9)

Web reference: `src/features/jmap/contacts/*`, `docs/jmap/contacts.md`. This app's
CardDAV path (`CardDavFetcher`, `CardDavWriter`, `VCardMapper`, raw vCard cache)
is vCard-shaped, so the same boundary applies: JSContact down, vCard up.

- Capability `urn:ietf:params:jmap:contacts`; methods `AddressBook/get|set` and
  `ContactCard/get|set|query|changes` (RFC 9610). The contacts account can differ
  from the calendars account: use `primaryAccounts` for each.
- Synthetic URLs: `<origin>/.jmap/<accountId>/ab/<addressBookId>/` and
  `<book url><contactCardId>`. Card UID is independent of the id.
- Reads tolerate the early flattened Card form. **Writes use the flat Card form for
  Stalwart**; it rejects the older `jsCard` envelope and `blobId` in photo `Media`.
  Query with `inAddressBook`.
- Same ETag, re-read-before-write, patch-not-replace and `changes` rules as
  calendars. `AddressBook` state is a metadata hint, **not** a card changes cursor:
  return a null token from the book list, capture `ContactCard` state *before* a
  full listing, and never skip contacts sync on an unchanged ctag.
- Conversion is RFC 9553 / RFC 9555 (vCard to JSContact): `FN`/`N` to `name`,
  `EMAIL`/`TEL`/`ADR` with contexts and `pref`, `ORG`/`TITLE`/`ROLE`,
  `BDAY`/`ANNIVERSARY` to `anniversaries` with `PartialDate`, `PHOTO` to `media`,
  `KIND=group` + `MEMBER` to `kind`/`members`, `CATEGORIES` to `keywords`.
  Output defaults to vCard 4.0, folded at 75 octets.
- Photos: write inline `data:` URIs (fall back from blob upload); on read, download
  `blobId` photos only, cache per backend instance, and do not re-upload or patch an
  unchanged photo when another field is edited. Replace the serializer's blanket
  "JPEG" label with the real MIME.
- Existing limits in this app still apply: out of scope are vCard import/export,
  duplicate merging, picker integration and group-membership editing.
- Groups on Stalwart were **not live-verified**; the web code reports the server's
  set error if it rejects `kind`/`members`.

## 11. Pitfalls checklist

A condensed list; each item has a section above.

1. `accountId` must be real; contacts and calendars can use different accounts.
2. Rebase loopback endpoints (and only loopback) onto the session origin.
3. A JSON 401 during detection is a JMAP auth failure; stop there.
4. JMAP wins when a server offers both protocols; CalDAV is the fallback and the
   user override.
5. Event id != UID. Never derive one from the other.
6. `ctag` is null; never skip event sync on it; capture event state before listing.
7. `CalendarEvent/changes` errors, including `cannotCalculateChanges`, mean full
   resync; events leaving a calendar are removals there.
8. Use `inCalendar`; fall back to `inCalendars` only on `unsupportedFilter`.
9. Always PatchObject; `null` deletes; arrays replace; escape `~` and `/`.
10. Never send vendor or `X-` properties; they are rejected, and cannot round-trip.
11. No `descriptionContentType` unless there is a description; never `text/plain`.
12. RDATE PERIOD overrides need an explicit `start`.
13. `updated` is DTSTAMP, not a modification time or an ETag.
14. Inline attachments, tasks and journals are not supported on Stalwart's JMAP.
15. `sendSchedulingMessages: true` or no invitation is sent; retry without it on
    `invalidArguments`.
16. After a write, re-read once and use that response for both bytes and ETag.
17. Same-account moves are one `calendarIds` patch, not create-then-delete.
18. Switching protocol on an account evicts its cache and cursors.
19. Batch to the session limits; writes never auto-replay; per-object errors are
    inside successful responses.
20. Hash a canonical, key-sorted JSON string for the ETag; do not use
    `JSONObject.toString()`.
21. Unknown JSON must survive: parse events as a tree, not into fixed data classes.
22. Do not log request or response bodies, session JSON, URLs with account ids, or
    credentials. Event bodies are personal data.
23. Redirects stay manual and same-origin; decide the cross-origin blob rule
    explicitly.
24. Lost-response CREATE can duplicate (JMAP has no conditional create): check by
    `uid` first.

## 12. Tests

### 12.1 Layers, in the order to build them

1. **Pure unit tests (no network).**
   - Port the web's **paired fixtures** (`src/features/jmap/convert/__tests__/
     fixtures/`: `paired-events.json` plus its 17 `.ics` companions, and the
     `stalwart-*.json/.ics` captures) into `app/src/test/resources/jmap/`. They
     cover weekly COUNT, ordinal monthly and yearly rules, UTC/zoned/floating/DATE
     UNTIL, UTC/floating/Europe/Copenhagen/America/New_York events, all-day ends,
     exclusions, additional dates, moved and renamed detached events with changed
     alarms, start/end/absolute alarms, participants, locations/GEO,
     categories/CONCEPT, URI/inline attachments, metadata and escaped Unicode text.
     The captures are real Stalwart replies, so they are the best regression oracle.
     Run each pair through your converter and compare *semantically* (parse with
     biweekly and compare modelled fields), not byte-for-byte.
   - Extra converter tests: 75-octet folding with multi-byte code points, DST gap
     and fold, end time zones, every RRULE selector, PatchObject errors (escapes,
     overlap, missing parent, `__proto__`-style keys), Task fields (offline only).
   - `diff`/`apply` property: `apply(S, diff(S, T)) == T` for representable patches,
     with the null-transition caveat from section 6.2.
   - ETag canonicaliser (key order, nested arrays, number formatting, Unicode).
   - Session parsing, URI-template expansion (individual percent-encoding),
     loopback rebasing (and non-loopback left alone).
   - Detection decision table (section 5.1), each branch.
   - Error mapping table; `Retry-After` seconds and HTTP-date forms.
   - SSE parser against byte streams split at arbitrary points.
   - Batching: result references, splitting at the limits, `createdIds` carry-over,
     `ifInState` advance, split `get` state mismatch.
2. **Fake-server tests.** Port `backend/__tests__/fakeServer.ts` (session,
   `Calendar/get|set`, `CalendarEvent/get|set|query|changes`, identities, principal
   availability) as an in-memory MockWebServer `Dispatcher`. Use the real transport,
   converters and repository. Cover every backend method, pagination, conditional
   conflicts, minimal patches, unknown data surviving an edit, moves, multi-calendar
   membership, full and delta sync, invalidation, a listing race, settings (if
   ported), scheduling and `invalidArguments` retry, availability, push, and
   per-object error mapping. Extend `CalDavPhase4IntegrationTest`-style tests so a
   JMAP account goes through the repository, queue and 412 rebase.
3. **Regression tests for the seam.** Account JSON without `protocol` decodes as
   CalDAV; all existing CalDAV tests pass unchanged; protocol switch evicts cache.
4. **Live tests**, env-gated and skipped when unset, following the
   `CalDavWriterLiveTest` pattern. Suggested variables: `CALINO_JMAP_URL`,
   `CALINO_JMAP_USER`, `CALINO_JMAP_PASS`, and optional `CALINO_JMAP_USER2`/
   `CALINO_JMAP_PASS2` for the invitation test. Never embed credentials; never log
   them. The web lifecycle test (`backend/__tests__/live.test.ts`) is the template:
   create/rename/delete a calendar; create, patch, sync, move and delete a
   recurring event with an attendee and an alarm; stale-ETag errors; every cleanup
   attempted even after a failure; uniquely named resources per run.
   - The **converter oracle** is worth copying: Stalwart serves the same data over
     CalDAV and JMAP. Create each fixture over CalDAV, read it over JMAP, convert,
     and compare with Stalwart's own CalDAV rendering. Then create via JMAP from your
     converter's output and read it back over CalDAV. Ignore only the differences
     JMAP itself cannot carry (listed in section 4).
   - Include the invitation test (needs user 2), a 60 s SSE hold, and a contacts
     lifecycle (create, photo, update, conflict, sync tombstones, group probe).
5. **Emulator pass** per the repository's review protocol (API 36 emulator,
   `ANDROID_SERIAL=emulator-5554` for instrumented tests). Connect to a local
   Stalwart, see events, create, edit, drag, delete, move between calendars, edit a
   recurring occurrence, add an attendee, respond to an invitation, background
   refresh with the app closed, airplane-mode edit then reconnect (queue replay),
   protocol override and cache eviction. Android-specific checks: Keystore
   credentials survive process death, WorkManager still syncs, widget and reminders
   update from JMAP data, and AppSearch and Calendar Provider show no server URLs.

### 12.2 A local server

The web's `scripts/jmap-dev-server.sh` starts a throwaway Stalwart with rootless
podman, state in a git-ignored directory, bound to loopback. Copy it to
`scripts/live-jmap/` with a README mirroring `scripts/live-caldav/README.md`
(commands `up`, `down`, `reset`, `env`; defaults port 18080; image about 1 GB, so
run `df -h /` first; this machine's `/` is small). Do not remove the existing
`stalwart-jmap` container on this machine; the script uses its own container name
(`calino-stalwart`).

Facts that cost time:

- Stalwart starts in bootstrap mode with a temporary admin. The script pins it via
  `STALWART_RECOVERY_ADMIN`, completes bootstrap through the management API
  (`x:Bootstrap/set`; the `x:` prefix and `urn:stalwart:jmap` are required), keeps
  the generated permanent admin secret (returned once) in a mode-600 ignored file,
  and creates a user with `x:Account/set`. Lose the secret file and you must `reset`.
- Calendars are created lazily; a new account has one default calendar.
- `/.well-known/jmap` redirects to `/jmap/session`; CalDAV is at `/dav/cal/` with
  URL-encoded address paths.
- From the emulator, reach the host with `adb -s emulator-5554 reverse tcp:18080
  tcp:18080` and connect to `http://127.0.0.1:18080` (always pass `-s`). This works
  because the server is loopback-only and the app rebases loopback endpoints; or use
  `10.0.2.2` with the container bound more widely. Debug allows loopback cleartext,
  release allows user-entered cleartext.
- A second user is needed for invitation tests (`x:Account/set`, or the admin UI).
- JMAP Calendars is a draft: re-run the live tests on every Stalwart upgrade. The
  container image tag in the script is `latest`; pin it when results matter.

### 12.3 Verification commands

Per `AGENTS.md`, in the `android-sdk` distrobox:
`distrobox enter android-sdk -- bash -lc './gradlew test lintDebug assembleDebug'`.
Live tests only with the env vars set and only against a throwaway server.

## 13. Open items, unverified, and decisions for the user

- Scope wording in `AGENTS.md` and `HANDOFF.md` (section 0).
- Cross-origin `downloadUrl`/blob hosts and credentials (section 5.1).
- Whether tasks and journals stay local for JMAP accounts (section 9).
- Idempotent CREATE and duplicate-UID behaviour (section 6.3): unverified.
- Attendee-copy write rules and detached-instance RSVP on Stalwart (section 7):
  unverified.
- iMIP delivery to external addresses: unverified.
- Servers other than Stalwart 0.16 (Cyrus, Fastmail, ...): unverified; the draft may
  still change.
- Inline attachments over JMAP: needs blob-upload design and a live check.
- Settings sync parity and free/busy: port only if Android has the feature.
- JMAP tasks (`urn:ietf:params:jmap:tasks`): a separate draft, out of scope.
- `PushSubscription`/web push: out of scope.
- Web-side leftovers that Android should not copy blindly: the dialog title is still
  "Add CalDAV account", "Diagnose the connection" is hard-coded English, and the
  web has no invitations UI.

## 14. Definition of done

- A JMAP account can be added by URL and credentials with no protocol picker, shows
  its protocol read-only, and can be forced back to CalDAV.
- Calendars, events, recurrence and overrides, reminders, attendees, moves,
  deletes, the offline queue and background refresh behave as for CalDAV, within the
  limits in sections 4 and 9.
- CalDAV accounts, tests, queue entries and caches are untouched.
- Fixtures, fake-server tests and the live lifecycle test pass; the emulator pass is
  recorded; `AGENTS.md`, `HANDOFF.md`, `TODO.md`, `CHANGELOG.md` and the README
  describe JMAP (including that vendor properties and tasks do not travel over it).
- No credential, session body or event body is logged, stored in the repo, or
  indexed.
