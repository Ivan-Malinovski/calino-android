# Handoff: Journal redesign (list page + read/edit modals)

## Overview

Three connected changes to Calino Android's Journal surface
(`app/src/main/java/calino/malinov/ski/ui/surfaces/JournalScreen.kt`):

1. **List page** — remove the `All entries` / `By month` segmented control and replace it
   with a two-level header: a **month scrubber** (collapsed, default) that pulls open into a
   **month grid** (expanded), using the calendar's existing zoom-handle gesture vocabulary.
2. **Read modal** — currently title + date + word count + body. Two directions were designed;
   pick one before implementing (see *Directions*).
3. **Edit modal** — currently a single `BasicTextField` in a panel box. Gains markdown
   formatting affordances and photo attachments.

The editing model is unchanged: explicit **Read → Edit → Save**, actions stay in the
`ModalActionPill` in the pill lane.

## About the design files

`Journal Modals.dc.html` in this bundle is a **design reference written in HTML** — a static
prototype of the intended look and behaviour. It is not production code and nothing in it
should be ported literally. The task is to implement these designs in the existing
**Kotlin + Jetpack Compose** codebase, using its own primitives: `CalinoColors`,
`CalinoTypography`, `CalinoSpacing`, `CalinoShapes`, `CalinoSegmented`, `CalinoMotion`,
`CalinoIcons`, `BottomDetailCard`, `ModalActionPill`, `CompactSegmentedControl`,
`CalinoMarkdown`.

**Never hard-code a colour, size or text style from the HTML.** Every value in the prototype
was read out of `design/CalinoTheme.kt`; in Compose, read it from the palette/typography
objects so both themes and any future palette continue to work. The HTML is light-theme only —
the implementation must work in `PaperDark` too, which means no literal hexes and no shadows
that ignore `CalinoColors.elevationAlpha`.

Open the file in a browser. Sections are stacked newest-first:
turn 3 (collapsing header), turn 2 (two list-page options), turn 1 (two modal directions),
then `0a`, the current UI recreated from source as the baseline.

## Fidelity

**High-fidelity.** Geometry, type and colour are taken from the real theme, so the
recreation should be pixel-accurate — but expressed in theme tokens, not literals.
The one deliberately loose part is the animation timing of the new header collapse
(specified below in intent; match `HomeScreen.kt`'s existing zoom behaviour rather than
inventing numbers).

---

## Part 1 — List page

### What is removed

In `JournalSurface`:

- the `JournalMode` enum, `modeName`/`mode` state, and the `CompactSegmentedControl`;
- `JournalRecentList` (the ungrouped variant) and the `AnimatedContent` that swaps the two;
- `JournalMonthList` keeps its grouping logic but no longer renders per-month `Text` labels —
  the sticky month rule replaces them.

Rationale: the toggle occupied a 48dp lane and its only effect was inserting month separators.

### What replaces it

A two-level header above the list, with the level persisted per surface
(`rememberSaveable`, the way calendar zoom level is remembered):

**Level 0 — month scrubber (default).** A horizontally scrolling row of month chips,
newest to the right, one chip per month in the entry range.

- Row height 48dp, content padding horizontal 20dp, gap 6dp between chips.
- Chip label: `CalinoTypography.labelSmall` (mono, 10sp, 600, 1.2sp tracking), uppercase,
  e.g. `MAR`. The selected chip shows month + 2-digit year: `MAY 26`.
- Unselected with entries: `CalinoColors.Ink2`, padding 5dp × 9dp, no background.
- Unselected with no entries: `CalinoColors.Ink3`, same metrics.
- Selected: `CalinoColors.SelectionFill` background, `CalinoColors.OnSelection` label,
  `RoundedCornerShape(CalinoSegmented.SegmentRadius)` (9dp), padding 5dp × 11dp.
- Under each chip, a 4dp dot with 4dp gap: `CalinoColors.Accent` if that month has entries,
  `CalinoColors.SelectionFill` under the selected chip, otherwise nothing drawn
  (`Color.Transparent`, so the row does not go ragged).
- Months far from the selection fade (`alpha .55`) toward the row's edges.

**Level 1 — month grid.** A `CalinoShapes.Card` (22dp) panel, `CalinoColors.Panel` fill,
1dp `CalinoColors.Line` border, margin 20dp horizontal, padding 12dp/14dp/14dp.

- Title row: chevron-left / month name / chevron-right. Month name
  `CalinoTypography.titleMedium` (Newsreader 23sp), centred; chevron buttons 32dp square,
  `CalinoIcons.ChevronLeft` / `ChevronRight` at 16dp, tint `CalinoColors.Ink2`.
  Touch targets must still be ≥44dp — expand the clickable area beyond the 32dp visual.
- Weekday header: 7 columns, `labelSmall`, `CalinoColors.Ink3`, respecting
  `LocalCalinoPreferences.current` week start (do **not** hard-code Monday-first as the
  prototype does).
- Day cells: 40dp tall, 2dp gaps, `RoundedCornerShape(CalinoShapes.DayBlock)` (10dp).
  Number is 13sp sans; `CalinoColors.Ink` if the day has an entry, `Ink2` otherwise.
  Weekend cells take `CalinoColors.WeekendWash`; out-of-month cells `OutsideMonthWash`
  (these are bands that abut, never stack — see the palette doc comment).
  Under each number, a 4dp dot in `CalinoColors.Accent` when that day has an entry.
- Today / selected day: `CalinoColors.SelectionFill` cell, `OnSelection` number,
  and the dot flips to `CalinoColors.Canvas`.

**The handle between them.** Reuse the calendar's zoom-handle vocabulary verbatim — see
`ZoomHandle` in `ui/home/HomeScreen.kt` (~line 7108). Same construction, two levels
instead of three:

- `requiredHeight(44.dp)` touch lane (`ZoomHandleTouchHeight`), `padding(horizontal = 20.dp)`,
  `Arrangement.spacedBy(8.dp)`, vertically centred.
- Leading and trailing rules: `Box(Modifier.width(26.dp).height(3.dp)` filled
  `CalinoColors.Ink.copy(.25f)`.
- Caption: 10sp, `letterSpacing = 1.sp`, `CalinoColors.Ink3`, one of
  `PULL FOR MONTH` (collapsed at rest) / `RELEASE FOR MONTH` (dragged past threshold) /
  `PULL UP TO COLLAPSE` (expanded at rest).
- Level dots: two boxes, 5dp tall, `RoundedCornerShape(3.dp)`; active is 14dp wide in
  `CalinoColors.Accent`, inactive 6dp wide in `CalinoColors.Ink3.copy(.3f)`.
- Tap toggles the level; vertical drag scrubs it continuously. The grid should unroll
  week-row by week-row as the drag progresses (the middle frame in turn 3 shows ~1.5 rows
  revealed with the scrubber faded to ~.45 and the list dimmed to ~.55), matching how the
  calendar's own month/detail zoom behaves. Use `CalinoMotion.expressiveSpatial()` for the
  settle and `CalinoMotion.gestureReturn()` for an abandoned drag.
- Accessibility: `contentDescription = "Change journal overview, level N of 2"`, matching
  the calendar handle's phrasing.

**Sticky month rule.** Directly under the handle, a row: month name in `labelSmall`
`CalinoColors.Ink`, a 1dp `CalinoColors.Line` rule filling the remaining width, and the
entry count in `labelSmall` `CalinoColors.Ink3` (`7 ENTRIES`). Collapsed, it sticks as the
list scrolls and names the month currently under the viewport, which also drives the
scrubber's selection. Expanded, it reads `7 ENTRIES IN MAY` with no count on the right.

**Header additions.** The subtitle becomes a real total — `41 entries since March 2025` —
in `bodyMedium` / `Ink2`, and a 44dp search button (`CalinoIcons.Search`, 20dp, `Ink2`)
sits at the trailing edge of the header row, opening the existing `SearchSheet`.

**List behaviour at both levels.** One continuous reverse-chronological `LazyColumn` —
the level never filters it. Tapping a dotted day in the grid, or a month in the scrubber,
**scrolls** the list (`animateScrollToItem`) rather than filtering. Changing month in the
grid moves the scrubber and vice versa: one piece of state.
Keep `contentPadding` bottom at `CalinoSpacing.PillClearance`.

### List cards

Keep `JournalCard`'s structure and metrics (Card radius 22dp, `Panel` fill, 1dp `Line`
border, 15dp padding, 45dp date column, `Ink3` `labelSmall` weekday at 9sp, `titleLarge`
`Accent` day number, 5dp accent dot). Two changes:

- Add a 5dp full-height accent spine on the leading edge, inside the clip:
  `CalinoColors.Accent` for the most recent entry, `CalinoColors.AccentSoft` otherwise.
  The existing 1dp × 64dp `Line2` divider between date column and text is removed.
- Replace the redundant `Sat, 30 May` line (the date column already says it) with a
  meta-chip row under the excerpt: `CalinoColors.Side` fill,
  `RoundedCornerShape(CalinoShapes.Chip)` (6dp), padding 3dp × 7dp, `labelSmall` `Ink3`,
  6dp gap — `2 PHOTOS`, `148 W`. Omit a chip when its count is zero; omit the row entirely
  when both are.

---

## Part 2 — Modals: pick a direction

Both keep `BottomDetailCard`, `CalinoSurfaceKind.Editor`, the `headerTint`
(`CalinoColors.AccentSoft.copy(alpha = .42f)`) handle strip, the dismissal gesture and the
`ModalActionPill` exactly as they are. Only the card's content changes.

### Direction 1a — "The page"

Read mode becomes an editorial page.

- Header: no icon, no tinted band beyond the handle strip. A date line in `labelSmall`
  `CalinoColors.Accent` (`SATURDAY 30 MAY 2026`), then the title in
  `CalinoTypography.headlineLarge` (Newsreader 33sp), then a meta line of `labelSmall`
  `Ink3` — `148 WORDS · 1 MIN READ` with a 3dp dot separator — then a 1dp `Line` rule.
  Content padding 24dp horizontal.
- Body: `CalinoMarkdown` as today, unchanged.
- Photo band: full-bleed horizontal row below the body, 116dp tall cells,
  `RoundedCornerShape(14.dp)`, 6dp gaps, bleeding past the 24dp gutter.
- Footer: a 1dp rule, then `EDITED 30 MAY, 21:04` in `labelSmall` `Ink3`, and at the trailing
  edge two 36dp buttons (`ChevronLeft` / `ChevronRight`, 16dp glyph, `Panel` fill, 1dp `Line`,
  `CalinoShapes.Row` radius) that page to the adjacent entry — again with ≥44dp touch lanes.

Edit mode drops the panel box: the `BasicTextField` sits directly on `CalinoColors.Canvas`
at full width, `bodyLarge` with `lineHeight = 25.sp`, so typing reads as writing on the page.
A formatting rail is pinned above the keyboard:

- Rail background `headerTint`, 1dp `Line` top border, buttons 44dp × 40dp,
  `RoundedCornerShape(CalinoShapes.Row)`, active button `Panel` + 1dp `Line`.
- Buttons, in order: H (Newsreader 17sp), **B**, *I*, bullet list (`CalinoIcons.AgendaList`),
  checklist (`CalinoIcons.ListChecks`), quote glyph; then a 1dp `OnFloat`-style divider and,
  at the trailing edge, `CalinoIcons.Camera` tinted `CalinoColors.Accent`.
- Under the rail, a caption row: `148 WORDS` left, `MARKDOWN` right, `labelSmall` `Ink3`.
- **The pill needs its own lane.** The rail and the `ModalActionPill` must not share vertical
  space: reserve `CalinoSpacing.PillClearance` above the rail so the pill floats clear of it.
  (This was the one collision found in review; do not let it recur.)

### Direction 1b — "The spine"

Keeps Calino's panel vocabulary; lower-risk to implement.

- Header band (`headerTint`) holds a 52dp date block on the leading edge — `Panel` fill,
  1dp `Line`, `RoundedCornerShape(14.dp)`, stacking `SAT` (labelSmall Ink3) /
  `30` (titleLarge Accent) / `MAY` (labelSmall Ink3) — beside the title in `headlineSmall`.
- Under the title, a wrapping row of meta chips: `Panel` fill, 1dp `Line`, 6dp radius,
  `labelSmall` `Ink2` — `148 WORDS`, `2 PHOTOS`, `EDITED 21:04`.
- Body stays inside a `Panel` card (22dp radius, 1dp `Line`, 18dp padding) rendered by
  `CalinoMarkdown`.
- Photos: a labelled section (`PHOTOS` in `labelSmall` `Ink3`) with an even 2-up grid,
  98dp tall, 14dp radius, 8dp gap.

Edit mode:

- Title stays in the header band with the `CalinoIcons.Note` glyph, as today.
- A `CompactSegmentedControl` with `Write` / `Preview` (`maxControlWidth = 200.dp`),
  using the component as-is so it matches every other segmented control in the app.
  The existing word-count chip stays at the trailing edge of that row.
- A markdown toolbar above the field: `CalinoColors.Side` track, 1dp `Line`,
  `CalinoShapes.Row` radius, 3dp inset, 40dp × 34dp buttons with a 9dp radius, active button
  `Panel` + 1dp `Line`. Same glyph set as 1a, minus the camera.
- The body field keeps its current treatment (Panel, 22dp radius, 1dp `Line`, 18dp padding,
  `bodyLarge` / 25sp line height, min height 260dp).
- Photo tray under the field: 62dp thumbnails, 14dp radius, plus a 62dp "add" cell with a
  1dp dashed `Ink.copy(.22f)` border, `CalinoIcons.Camera` in `Accent` and `ADD` beneath.

**Recommendation:** 1b for the smaller diff and stronger continuity with the rest of the app;
1a if Journal is meant to feel distinct from the calendar surfaces. They can also be mixed —
1a's read pane with 1b's editor is coherent.

---

## Interactions & behaviour

- **Read → Edit** — unchanged: `isEditing` flips, `AnimatedContent` slides panes horizontally
  (`tween(220)` in, `tween(180)` out, `fadeIn(170)` / `fadeOut(130)`), pill primary becomes
  `Save` and is only present when `dirty || isNewEntry`.
- **Dirty dismissal** — unchanged: `showDiscard` bar, `resetKey` springs the card back.
- **Delete** — unchanged: `confirmDelete` inline row in `Rose.copy(alpha = .09f)`.
- **Header level change** — tap or drag on the zoom handle; level persists across
  configuration changes and process death (`rememberSaveable`).
- **Markdown buttons** — wrap or prefix the current selection in the `TextFieldValue`;
  the word-count chip and (in 1b) the Preview pane update live.
- **Photos** — picker via the platform photo picker; thumbnails are content URIs.
  Attachments need a storage decision (see *Open questions*).

## State

Added to `JournalSurface`:

- `overviewLevel: Int` (0 scrubber / 1 grid), `rememberSaveable`.
- `visibleMonth: YearMonth`, `rememberSaveable` — shared by scrubber, grid and sticky rule.
- `dragProgress: Float` for the handle, transient `Animatable`.
- In the editor: `TextFieldValue` instead of `String` for the body (selection is needed for
  formatting), plus `photos: List<Uri>`.

Removed: `modeName` / `mode`.

## Design tokens

Do not copy hexes; these are listed so you can verify what the prototype used.
All live in `design/CalinoTheme.kt` (`CalinoThemes.PaperLight` / `PaperDark`).

| Token | Light value |
| --- | --- |
| `Canvas` / `Panel` / `Side` | `#FAF8F3` / `#FFFFFF` / `#F6F3ED` |
| `Ink` / `Ink2` / `Ink3` | `#2C2823` / `#6F6A62` / `#A39D93` |
| `Accent` / `AccentSoft` | `#B07D4F` / `#EFE7DB` |
| `Line` / `Line2` | `Ink` @ .09 / @ .05 |
| `WeekendWash` / `OutsideMonthWash` | `Ink` @ .045 / @ .022 |
| `Rose` / `Green` | `#C2697F` / `#5D9A78` |
| `FloatFill` / `OnFloat` | `#2C2823` / `#FAF8F3` |
| header tint | `AccentSoft` @ .42 over `Canvas` |

Shapes: `Chip 6` · `Row 11` · `DayBlock 10` · `Button 15` · `Card 22` · `Sheet 26` ·
`Pill 999`; detail card 28dp (`DetailCardSurface`).
Spacing: `Screen 20` · `PillClearance 96` · `ActionPillHeight 56`.
Segmented: `LaneHeight 48` · `TrackHeight 36` · `TrackRadius 12` · `SegmentRadius 9` ·
`Inset 3`.
Type: `displayLarge` 40/40 Newsreader −.8 · `headlineLarge` 33/37 · `headlineSmall` 23/28 ·
`titleLarge` 27/32 · `titleSmall` 19/23 · `bodyLarge` 14.5/22 · `bodyMedium` 13/19.5 ·
`bodySmall` 12.5/18.75 · `labelMedium` 13.5/18 medium · `labelSmall` mono 10/12 600 +1.2.

## Assets

None new. Every glyph in the prototype is the path data from
`ui/components/CalinoIcons.kt` (`Plus`, `Search`, `Menu`, `Note`, `Calendar`, `Trash`,
`Camera`, `ChevronLeft`, `ChevronRight`, `ChevronDown`, `AgendaList`, `ListChecks`).
Photo placeholders are striped fills standing in for real attachments.

## Open questions for the implementer

1. **Attachment storage.** Photos on a VJOURNAL need a decision: `ATTACH` with a URI to
   local storage, or base64 inline (large, and some servers reject it). `ICalMapper.kt` and
   `CalDavWriter` will both need work. Worth scoping separately from the layout change.
2. **Edited timestamp.** The read footer shows `EDITED 30 MAY, 21:04`; confirm
   `LAST-MODIFIED` is retained through the rebase path before displaying it.
3. **Entry paging** (1a's prev/next arrows) implies the editor knows its neighbours —
   the surface has the sorted list, so pass an index rather than re-querying.

## Files

- `Journal Modals.dc.html` — the design reference (open in a browser).
- Source read while designing, and the files most likely to change:
  - `app/src/main/java/calino/malinov/ski/ui/surfaces/JournalScreen.kt` (primary)
  - `app/src/main/java/calino/malinov/ski/ui/home/HomeScreen.kt` (`ZoomHandle`, `MonthGrid`,
    `WeekStrip`, `CompactWeekMetrics` — the gesture and grid vocabulary to reuse)
  - `app/src/main/java/calino/malinov/ski/design/CalinoTheme.kt` (tokens; read-only)
  - `app/src/main/java/calino/malinov/ski/ui/components/CalinoComponents.kt`
    (`ModalActionPill`, `CompactSegmentedControl`, `MenuButton`)
  - `app/src/main/java/calino/malinov/ski/ui/components/BottomDetailCard.kt`,
    `AdaptiveSurface.kt` (`DetailCardSurface`)
  - `app/src/main/java/calino/malinov/ski/ui/components/CalinoMarkdown.kt`
  - `app/src/main/java/calino/malinov/ski/data/model/JournalDraft.kt`
  - `app/src/androidTest/java/calino/malinov/ski/JournalFlowTest.kt` — will need updating;
    it currently exercises the mode toggle.
