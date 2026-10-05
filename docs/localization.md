# Localization

Calino uses Android string and quantity resources, with English in `values/`,
Danish in `values-da/`, and German in `values-de/`. Calendar record titles,
notes, categories, contact names and the frozen May 2026 fixture content remain
user data; changing the language does not rewrite them.

On Android 13 and newer, Settings > Display > Language opens Android's app
language settings. On Android 12, Calino follows the system language. Unsupported
languages fall back to English. Week start and 12/24-hour preferences keep their
existing storage keys and regional-default rules; a language choice does not
replace explicit preferences.

## Adding or changing text

Use `stringResource` / `pluralStringResource` in Compose. Resolve text before
entering a semantics block, a `remember` calculation or an event callback;
those callbacks are not composable. Android receivers, notifications and
provider integrations use their own `Context.getString` / quantity resources.
Glance uses `androidx.glance.LocalContext`, not the ordinary Compose local.

Use numbered format arguments such as `%1$s` and `%2$d`, and quantity resources
for count-dependent grammar. Keep accessibility descriptions translated too.
Test tags, route/preference keys, DAV field names, recurrence rules and wire
values are stable identifiers and must never be translated.

Date labels use the effective app resource locale. `LocalCalinoLocale` and
`localizedDateFormatter(pattern)` are the Compose boundary. Android-free
formatters retain deterministic English defaults for JVM callers and accept
an explicit locale where needed. `CalinoLocalizedFormats` supplies context
versions of duration, recurrence and undo labels. The typed date field accepts
English, Danish and German month names; typed times accept the localized
12-hour markers displayed by the editor. Quick Add's natural-language grammar
continues to understand English phrases; localized UI hints keep its English
examples rather than promising Danish or German parsing. Server-provided
names and unknown diagnostic messages are shown as supplied. Repository
refusals carry pure `WriteRejectionCode` identities and translate at the Android
presentation boundary. Known persisted queue-policy defaults translate at
display time without rewriting the queue.

Reminder schedules retain optional structured date/time/all-day/category facts
so notification subtitles are formatted in the delivery context's language.
Older schedules without these fields retain their stored subtitle until the
next normal replan. This is an additive change to schedule JSON version 1;
notification identity, delivery watermarks, snoozes and alarm instants remain
unchanged. Widgets read their locale inside Glance composition, including
active sessions, and redraw on `LOCALE_CHANGED` without remote work.

## Validation

Debug builds enable Android's `en-XA` expansion and `ar-XB` RTL pseudolocales.
They are test languages, not advertised release translations.
`LocalizationResourcesTest` exercises resource fallback, substitutions,
recurrence weekdays and pseudolocale behavior. The Danish, German, expanded and
RTL UI tests launch the real fixture app in each language, capture calendar and
settings and task editors, including an editor-enter animation frame, and
check locale/preference retention after Activity recreation. A separate
language-change test preserves the committed calendar date across recreation.
Snapshots are written under the debug app's external files `localization/`.

The ordinary fixture UI harness explicitly selects English before launch and
restores the previous app language afterwards. All device tests must still be
pinned to the API 36 emulator using `ANDROID_SERIAL=emulator-5554`.

The large Compose files can make lint spend substantial time in garbage
collection with the default 2 GB Gradle heap. The 2026-10-04 verification used
`-Dorg.gradle.jvmargs="-Xmx6g -Dfile.encoding=UTF-8" --max-workers=2`; this is
a command-line override, not a change to project or device preferences.
