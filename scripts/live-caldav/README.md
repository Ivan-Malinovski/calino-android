# A local CalDAV server for live tests

Calino's live tests and most of its emulator checks need a real server on the
other end. `radicale.sh` runs one — [Radicale](https://radicale.org) — on this
machine, in its own virtualenv, with its own storage. Nothing it creates is
committed: everything lives under `state/`, which is gitignored and can be
deleted at any moment.

The point is to never again reach for a real calendar to test against. The
`VALARM` round trip, the write queue, the rebase after a 412, and now reminder
delivery are all things that want a server they are allowed to break.

## Use

```bash
scripts/live-caldav/radicale.sh start     # installs on first run, then serves
scripts/live-caldav/radicale.sh status
scripts/live-caldav/radicale.sh stop
scripts/live-caldav/radicale.sh reset     # wipe the stored collections
scripts/live-caldav/radicale.sh env       # the CALINO_CALDAV_* exports
```

Addresses:

| From | URL |
|---|---|
| this machine | `http://127.0.0.1:5232/` |
| the emulator | `http://10.0.2.2:5232/` |

Credentials are `calino` / `calinopass`, throwaway and local. Override with
`CALINO_RADICALE_USER`, `CALINO_RADICALE_PASS` and `CALINO_RADICALE_PORT`.

## Running the JVM live tests against it

```bash
eval "$(scripts/live-caldav/radicale.sh env)"
distrobox enter android-sdk -- bash -lc \
  './gradlew :app:testDebugUnitTest --tests "*LiveTest*"'
```

Without those variables the live tests skip themselves, which is why the suite
reports a handful of skips on a normal run.

## Connecting the app on the emulator

Settings → Sync → Add calendar account, with `http://10.0.2.2:5232/` and the
credentials above.

Plain HTTP is deliberate. The alternative is a self-signed certificate, and an
emulator with a locked bootloader will not trust one without being rooted
first. Instead the **debug build only** permits cleartext to `10.0.2.2`,
`127.0.0.1` and `localhost` — see
`app/src/debug/res/xml/network_security_config.xml`. That file does not exist
in the release source set, so a shipped build still refuses plain HTTP
everywhere, and even in debug a real account cannot be configured over it.

## Creating fixtures by hand

The server has no collections until something makes one. To create a calendar
and drop an event into it:

```bash
curl -u calino:calinopass -X MKCALENDAR http://127.0.0.1:5232/calino/default/ \
  -H 'Content-Type: application/xml' --data-binary '<?xml version="1.0" encoding="utf-8"?>
<C:mkcalendar xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
  <D:set><D:prop><D:displayname>Live test</D:displayname>
    <C:supported-calendar-component-set><C:comp name="VEVENT"/><C:comp name="VTODO"/></C:supported-calendar-component-set>
  </D:prop></D:set></C:mkcalendar>'

curl -u calino:calinopass -X PUT \
  http://127.0.0.1:5232/calino/default/live-reminder-1.ics \
  -H 'Content-Type: text/calendar; charset=utf-8' --data-binary @event.ics
```

A reminder that fires shortly after the app syncs wants a `DTSTART` a few
minutes out and a `TRIGGER` that lands a minute or two from now:

```ics
BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//Calino//live-test//EN
BEGIN:VEVENT
UID:live-reminder-1
DTSTAMP:20260912T210000Z
DTSTART:20260912T212000Z
DTEND:20260912T215000Z
SUMMARY:Live reminder check
LOCATION:Emulator
BEGIN:VALARM
ACTION:DISPLAY
TRIGGER:-PT10M
DESCRIPTION:Live reminder check
END:VALARM
END:VEVENT
END:VCALENDAR
```

Then watch the device side:

```bash
adb -s emulator-5554 shell run-as calino.malinov.ski.poc cat files/reminder-schedule.json
adb -s emulator-5554 shell dumpsys alarm | grep -A3 calino.malinov.ski.poc
adb -s emulator-5554 shell cmd notification list | grep -i calino
```
