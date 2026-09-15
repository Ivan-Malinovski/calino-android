# Calino Android

Calino is a native Android calendar for people who want to keep their calendar
and contacts on a standards-based server. It is written in Kotlin and Jetpack
Compose and connects directly to CalDAV and CardDAV services, with no WebView
or Capacitor layer.

This is the standalone Android sister project to the
[Calino web app](https://github.com/ivan-malinovski/calino). It is under active
development rather than presented as a finished release. A built-in sample
dataset keeps the interface explorable without connecting a server.

## Features

- Month, day, 3-day, 7-day, and agenda calendar views, with fluid paging and
  zoom transitions.
- Event, task, and journal creation and editing, including recurrence,
  reminders, attendees, categories, travel time, priorities, and completion.
- Contact browsing and editing through CardDAV.
- Global search and Quick Add for getting to records or creating them with
  less navigation.
- Local reminder notifications with event and task actions, deep links, exact
  alarm support, and an inexact fallback where Android requires it.
- Resizable agenda, calendar-card, and task home-screen widgets backed by the
  same local cache as the app.
- Android calendar intents, `.ics` import and sharing, map intents, and dynamic
  launcher shortcuts.
- Adaptive layouts for phones, tablets, and landscape split panes. Early
  foldable posture and hinge handling is also included.
- Optional AI Photo Import using a provider and API key chosen by the user.
  Images are sent directly to that provider; Calino does not bundle a key.

## How it works

Calino talks directly to CalDAV and CardDAV servers; it does not require a
Calino account, companion service, or hosted backend. Server resources are
cached locally for reading, and retryable edits stay in a durable queue until
the connection returns.

Writes use ETags and conditional requests. When server data has changed,
Calino rebases the fields it edits while preserving properties it does not
understand. Recurring events remain recurring series and can be edited for one
occurrence, this and future occurrences, or the entire series.

The interface and its platform integrations are native Android. Credentials
are stored with Android Keystore, cached calendar data never contains account
passwords, and the app does not include telemetry.

With no account connected, Calino uses a frozen May 2026 fixture. Once an
account is connected, CalDAV and CardDAV data replaces the fixture across the
relevant screens.

## Screenshots

The screenshots below use the built-in May 2026 fixture and contain no account
data. The second group is from the API 36 emulator in the landscape tablet
layout and light mode.

<table>
  <tr>
    <td><img src="docs/screenshots/calendar-day.png" alt="Calendar day view" width="100%"></td>
    <td><img src="docs/screenshots/range.png" alt="Three-day range view" width="100%"></td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/tasks.png" alt="Tasks" width="100%"></td>
    <td><img src="docs/screenshots/settings.png" alt="Settings" width="100%"></td>
  </tr>
</table>

<table>
  <tr>
    <td><img src="docs/screenshots/calendar-day-landscape-light.png" alt="Calendar day view in landscape tablet light mode" width="100%"></td>
    <td><img src="docs/screenshots/range-landscape-light.png" alt="Three-day range view in landscape tablet light mode" width="100%"></td>
  </tr>
  <tr>
    <td><img src="docs/screenshots/event-open-landscape-light.png" alt="An event open in the landscape tablet light layout" width="100%"></td>
    <td><img src="docs/screenshots/settings-landscape-light.png" alt="Settings in landscape tablet light mode" width="100%"></td>
  </tr>
</table>

## If a reminder never arrives

Calino schedules reminders with an exact alarm, but several manufacturers --
Samsung, Xiaomi, Huawei, OnePlus and others -- shut background apps down
aggressively to save battery, and an app that has been shut down does not get
its alarm. If reminders stop arriving, exempt Calino from battery
optimisation; <https://dontkillmyapp.com> has the exact steps per manufacturer.

Two other things can hold a reminder back, and both are reported on the
Notifications screen inside the app:

- Android's notification permission has not been granted, so nothing is shown.
- Exact alarms are not permitted, so delivery falls back to an inexact alarm
  and can be up to about fifteen minutes late, and later during Doze.

## Build and install

From the repository root:

```bash
./gradlew assembleDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```

The generated debug APK is `app/build/outputs/apk/debug/app-debug.apk` and is
labelled **Calino Debug**. Release remains `calino.malinov.ski`; debug is
`calino.malinov.ski.nativeDebug`, so both can be installed at once. Release signing
is not configured yet, so `assembleRelease` produces an unsigned release APK
until a release key is added.

The app is routinely validated on the `calino-poc-api36` API 36 emulator for
build, unit tests, install/launch, and accessibility hierarchy. Live CalDAV and
CardDAV write probes are opt-in and use only environment-provided credentials.

## Emulator

Start the documented API 36 emulator with:

```bash
emulator -avd calino-poc-api36
```

Then install with the `adb` command above (use the emulator's serial from
`adb devices` if it is not `emulator-5554`).

The unit tests cover the frozen fixture/date contract, formatting and
recurrence behavior, zoom rest-state/selected-date continuity, and task bucket
rules. Run them with:

```bash
./gradlew test
```
