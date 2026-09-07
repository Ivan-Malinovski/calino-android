# Calino Android

This is a standalone Kotlin/Jetpack Compose Android application. It has no
WebView or Capacitor dependency.

## Identity and scope

- APK/application ID: `calino.malinov.ski.poc`
- App label: `Calino POC`
- Fixture-only visual POC: data is frozen to May 2026 and is not connected to
  CalDAV/CardDAV, accounts, credentials, or network services.
- The APK can be installed alongside the existing Calino Capacitor APK.
- The primary surfaces are reached from the animated bottom dock.

## Build and install

From the repository root:

```bash
distrobox enter android-sdk
cd <repo-root>
./gradlew assembleDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```

The generated APK is `app/build/outputs/apk/debug/app-debug.apk`.

The POC has been validated on the `calino-poc-api36` API 36 emulator for
build, unit tests, install/launch, and accessibility hierarchy. The visual
and gesture acceptance harness is included but still needs a clean rerun after
the latest pager fixes; it is not evidence of complete gesture validation yet.

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
