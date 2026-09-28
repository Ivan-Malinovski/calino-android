# Invitations and RSVP: implementation plan

Implemented 2026-09-28 for TODO item 25. This document records the source review,
scope, and the assumptions used for the implementation.

## Source review

- [Nextcloud Calendar's attendee model](https://github.com/nextcloud/calendar/blob/main/src/models/attendee.js) retains `PARTSTAT`, `ROLE`, `RSVP`, `CN`, and the attendee URI. Its [CalDAV service](https://github.com/nextcloud/calendar/blob/main/src/services/caldavService.js) also discovers an inbox and outbox; it currently chooses the first inbox under the home set, while acknowledging that the principal's `schedule-inbox-URL` is the proper lookup.
- [SabreDAV's scheduling plugin](https://github.com/sabre-io/dav/blob/master/lib/CalDAV/Schedule/Plugin.php), which Nextcloud uses, advertises `calendar-auto-schedule`, registers a calendar-object-change handler, exposes principal inbox/outbox/default-calendar properties, and delivers local scheduling messages to the recipient's inbox/default calendar. A calendar PUT can therefore cause server-side scheduling; the client must not invent device-side email delivery.
- [DAVx5's manual](https://manual.davx5.com/introduction.html) says DAVx5 synchronizes the Android Calendar Provider and a separate calendar app edits events. Its [technical notes](https://manual.davx5.com/technical_information.html) say it imports `ORGANIZER` on group events to restrict event editing to the organizer, maps `ATTENDEE` through Calendar Provider, and does not send invitation emails itself. This is an interoperability comparison, not a claim that DAVx5 owns the invitation UI.
- [A Nextcloud server report](https://github.com/nextcloud/server/issues/57883) shows `schedule-default-calendar-URL` on the principal but absent on the inbox in a tested version. Query principal properties first; do not assume the inbox supplies every scheduling property.

## Calino gaps and proposed sequence

1. **Discovery and identity.** `DiscoveredAccount` stores principal and home-set URLs but no `calendar-user-address-set` or scheduling capability. Discover the principal's address set and the server's `DAV:` scheduling feature. Persist only account identity/capability metadata, with no credential duplication. If identity is ambiguous or absent, display attendee status but disable RSVP and explain why.
2. **Read model.** `Attendee` currently contains only name/email; `ICalMapper.mapAttendees` drops status, role and RSVP. Add organizer and attendee scheduling fields with conservative defaults for absent parameters. Match “me” against the discovered address set using normalized calendar-user addresses, preserving URI forms for writes. Keep arbitrary attendee parameters in raw iCalendar.
3. **Read UI.** Show organizer, per-person status and counts on event detail. Offer Accept/Maybe/Decline only for a writable attendee copy whose `ATTENDEE` matches the account identity. A `NEEDS-ACTION` list can be derived from downloaded events; do not build inbox polling until testing proves a server requires it. Keep this separate from the accountless fixture.
4. **RSVP write.** Make a narrow patch that changes only the matched attendee's `PARTSTAT`, retaining every other parameter and attendee. Use the existing durable UPDATE queue, ETag and original `baseData`; on 412, rebase the one parameter against fresh bytes. If that same parameter changed remotely since the base, surface a conflict instead of silently overwriting it. Test whole-series and detached-instance responses separately.
5. **Organizer edits.** Review the existing writer's attendee reconciliation: it copies matching lines but currently removes every unmatched line. New attendees need standard `RSVP=TRUE;PARTSTAT=NEEDS-ACTION`; edits must not set another attendee's status. Define which significant changes bump `SEQUENCE` and test scheduling propagation. Avoid a bump for unrelated edits or RSVP.
6. **Interop validation.** Unit-test foreign parameter preservation, duplicate/alias identity, queue replay and stale ETag cases. Then use two throwaway Nextcloud users for organizer creation, delivery, each response and organizer-copy update; Radicale alone cannot prove server scheduling. Run the repository's focused checks and API 36 emulator detail/editor pass when implementation begins.

## Decisions

- Support servers that advertise `calendar-auto-schedule`; let the server deliver invitations. Derive pending invitations from downloaded attendee copies. Do not poll the inbox or implement client-side iTIP/iMIP. A server that needs inbox processing is outside this first implementation and must not be presented as working.
- Organizer edits and attendee responses are separate permissions and write paths. On an attendee-owned copy, show event fields read-only and offer only RSVP. If identity cannot be proven from `calendar-user-address-set`, show statuses read-only.
- RSVP uses one matched `ATTENDEE` line and one `PARTSTAT` parameter. Its queued raw update must preserve the original `baseData`; replay and 412 handling must recheck that same parameter before rebasing. No bulk rewrite of attendees for a response.
- A recurring response must target the chosen instance when the person chooses “This occurrence,” and the master for “Whole series.” Confirm Nextcloud's exact detached-instance shape with two test accounts before enabling the instance control. Never silently promote an instance answer to a series answer.
- `SEQUENCE` bumps for organizer changes to date/time or location and for attendee-list changes. RSVP and unrelated metadata edits keep the sequence. Test what the server itself changes after PUT rather than assuming the submitted bytes are the final organizer copy.

## Implemented behavior and assumptions

- Discovery saves the principal's `calendar-user-address-set` and the server's
  `calendar-auto-schedule` advertisement. A confirmed `mailto:` identity is
  required to create invitations; RSVP requires exactly one matching attendee
  line. An attendee copy is read-only except for that person's response.
- Invitations use the ordinary calendar PUT path and durable queue. A response
  changes one attendee line in the raw resource. The queued UPDATE retains its
  original bytes and ETag for a three-way rebase; concurrent changes to that
  attendee or the same detached instance stop the rebase.
- Pending invitations are derived from downloaded, visible attendee copies
  whose own status is `NEEDS-ACTION`. There is no inbox polling. This assumes
  the advertised auto-scheduling server delivers attendee copies into a
  calendar Calino already syncs. Servers that require a separate inbox workflow
  need a later interoperability task.
- The organizer address is the first discovered `mailto:` address. If the
  principal has several addresses, the server's preferred sending identity is
  not exposed here; the user can still RSVP only when one attendee matches.
- A one-occurrence response creates a detached VEVENT when needed. Nextcloud
  live tests with two throwaway users confirmed series and one-occurrence
  replies reach the organizer, and that the other occurrences retain their
  status. The app does not send iTIP or iMIP itself.
