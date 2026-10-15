# A local JMAP server for live tests

Calino's JMAP live test and emulator checks need a real server. `jmap-server.sh`
runs a throwaway [Stalwart](https://stalw.art) in rootless podman, bound to
`127.0.0.1`, with its data under `state/` (git-ignored, safe to delete at any time).
Nothing it creates is committed.

The image is about 300 MB; run `df -h /` first, this machine's disk is small.

## Use

```bash
scripts/live-jmap/jmap-server.sh up       # first run bootstraps Stalwart and creates two users
scripts/live-jmap/jmap-server.sh down     # remove the container, keep the data
scripts/live-jmap/jmap-server.sh reset    # wipe the data and start fresh
scripts/live-jmap/jmap-server.sh env      # the CALINO_JMAP_* exports
```

Defaults: container `calino-stalwart`, port `18080`. Override with
`CALINO_JMAP_PORT`, `CALINO_JMAP_NAME`, `CALINO_JMAP_IMAGE` and `CALINO_JMAP_DATA`.
(If another Stalwart already holds 18080, pick a free port, for example
`CALINO_JMAP_PORT=18081`, on every command.)

The users are `alice@example.org` and `bob@example.org` with throwaway, local-only
passwords printed by `env`. Stalwart replaces its temporary bootstrap admin with a
generated one; the script keeps that secret in `state/admin-secret` (mode 600).
If that file is lost, `reset`.

## Running the live test

```bash
eval "$(scripts/live-jmap/jmap-server.sh env)"   # bash/zsh; fish: run it from bash -c
./gradlew :app:testDebugUnitTest --tests "*JmapLiveTest*"
```

`JmapLiveTest` creates, re-creates (a lost-answer retry must not duplicate), edits
and deletes an event and a contact. Without the variables it skips itself, which is
why a normal run reports a couple of skips.

## Connecting the app on the emulator

```bash
adb -s emulator-5554 reverse tcp:18080 tcp:18080
```

Then Settings, Sync, Add calendar account, with `http://127.0.0.1:18080` and one of
the users. Always pass `-s`, the phone is attached too. The debug build permits
cleartext to loopback (`app/src/debug/res/xml/network_security_config.xml`); a
release build refuses plain HTTP unless the person typed it in themselves.

## Facts that cost time

- `Calendar/get` with `accountId: null` is rejected; Calino always sends the id.
- A new account has one default calendar, and Stalwart flags it `isSubscribed: true`
  even though it grants every write right. The rights decide, not that flag.
- `/.well-known/jmap` answers a 307 to `/jmap/session`; CalDAV lives at `/dav/cal/`.
- JMAP Calendars is a draft. Re-run the live test on every Stalwart upgrade, and pin
  `CALINO_JMAP_IMAGE` when results matter.
