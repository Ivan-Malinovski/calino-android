# Calino Android

This is a standalone Kotlin/Jetpack Compose Android application. It has no
WebView or Capacitor dependency.

## Identity and scope

- APK/application ID: `calino.malinov.ski.poc`
- App label: `Calino POC`
- With no account connected, the app keeps its frozen May 2026 fixture. A
  connected account uses real CalDAV/CardDAV reads and conditional writes for
  events, tasks, journal entries, and contacts.
- Offline or retryable writes are retained in a durable queue and can be
  retried or discarded from the Calendars account surface. Recurring event
  edits/deletes support THIS, FUTURE, and ALL scopes.
- The APK can be installed alongside the existing Calino Capacitor APK.
- The primary surfaces are reached from the animated bottom dock.
- Reminders are delivered locally: a reminder set on an event or a task is
  scheduled with `AlarmManager` and posted to one of two notification channels.

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
distrobox enter android-sdk
cd <repo-root>
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
