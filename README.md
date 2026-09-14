# Calino Android

Calino Android is a standalone Kotlin/Jetpack Compose sister project to the
[Calino web app](https://github.com/ivan-malinovski/calino). It is a native
Android project, with no WebView or Capacitor dependency.

This repository is mostly a place to explore the Android version of Calino in
the open. It is still a work in progress, but the app can connect to CalDAV
and CardDAV servers and also works with a built-in fixture when no account is
connected.

## Identity and scope

- APK/application ID: `calino.malinov.ski`
- App label: `Calino`
- With no account connected, the app uses a frozen May 2026 fixture. A
  connected account uses CalDAV/CardDAV for events, tasks, journal entries,
  and contacts.
- Offline or retryable writes stay in a durable queue. Recurring event edits
  and deletes support THIS, FUTURE, and ALL scopes.
- Reminders are delivered locally through `AlarmManager`.
- AI Photo Import is opt-in and uses a bring-your-own API key. Images go
  directly to the provider and model selected by the user; no provider key is
  bundled here.

## Screenshots

The screenshots below use the built-in May 2026 fixture and contain no account
data.

![Calendar day view](docs/screenshots/calendar-day.png)

![Three-day range view](docs/screenshots/range.png)

![Tasks](docs/screenshots/tasks.png)

![Settings](docs/screenshots/settings.png)

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

The generated APK is `app/build/outputs/apk/debug/app-debug.apk`.

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
