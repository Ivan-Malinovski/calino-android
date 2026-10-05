# Navigation pill motion audit

2026-10-05. Covers the regular add pill, extended view pill, menu, dock,
create-type row, and shared modal action pill.

The implemented states and transitions are shown in the offline
[frame review](../artifacts/navigation-pill-motion-review.html). It contains
186 rendered frames from the API 36 emulator, with regular/extended and
light/dark selectors. Images are cropped screenshots of the actual app.

## Findings and fixes

| Finding | Result |
| --- | --- |
| Root and modal pills had separate write animation clocks. A handoff restarted the segment or confirmation ring. | `CalinoPillFeedback` owns one trace above the route host. Every pill reads that same animation while drawing. |
| The moving segment vanished and a new confirmation stroke appeared elsewhere. | The existing segment extends into one complete ring, pauses, and dissolves. Its phase survives ownership changes and interrupted writes. |
| Saving used an accent trace before changing to the outcome color. | Saves use green; removals use rose throughout the write and confirmation. Color changes between write kinds animate. |
| Modal Save appeared instantly and confirmation changed width instantly. | Action lanes expand/shrink and fade. Their actual animated measurement determines the pill's width. |
| Fading the whole action row made unchanged close/delete controls blink. | Stable keyed lanes retain those controls. Only changing words, status glyphs and added/removed lanes animate. The destructive question still transitions as a whole face. |
| Inline saves kept the modal open without showing Saved text there. | Its primary lane reports Saving/Saved, then withdraws or restores the primary action. Status is announced politely and has no click action. |
| Outgoing confirmation controls could change again during dismissal. | The departing face is retained through the return morph. Outgoing faces are inaccessible and cannot fire actions. |
| Delete countdowns disappeared abruptly after cancellation/expiry. | The last drawn segment fades for 160ms. Rearming starts a fresh countdown. |
| Editors opened from the create-type row used an older resting-pill source. | The measured type row is the arrival source. Dismissal returns to the current resting face or dock. |
| View glyphs and dock icon tint changed instantly. | Glyphs crossfade within their fixed touch lane; foreground and selection colors animate. |
| Root drag handlers could retain confirmation/Undo state from their first composition. | They read current state. Confirmations, Undo and in-flight writes block label navigation. Cancellation uses `CalinoMotion.gestureReturn`. |
| A fresh successful write during an earlier failure's minimum display interval inherited the failure. | Each new non-overlapping write run resets its outcome. Overlapping writes still wait for every result and retain any failure. Timing uses a monotonic clock. |
| Labels overlapped during relabels, and colored modal text lost contrast against glass. | Incoming words arrive after the outgoing words mostly dissolve. Labels use full `OnFloat`; state color remains on glyphs and outlines. |
| Preview Open replayed the root/dock face before the clean editor's down arrow. | A one-use action-face handoff carries the preview lanes into the editor. Trash and Open dissolve and contract directly, while the normal dismissal return remains intact. Departing lanes expose no accessibility actions. |

## Transition coverage

| Surface or situation | Reviewed behavior |
| --- | --- |
| Regular pill | Date/generic labels, horizontal route preview and return, upward Search preview and cancellation, editor entry/return, write and Undo feedback. |
| Extended pill | Current-view glyph, menu opening/closing, scrub selection, Back, downward menu dismissal, hold-to-dock, dock selection, type-row opening, editor source handoff and dock restoration. |
| Event preview | Clean/dirty actions, inline save while remaining open, scoped recurring deletion, one-off confirmation, expiry, confirmed removal and cancelled hold. |
| Full editor | Initial Save, input validity, closing save, cancellation, editor-to-root outline continuity and outcome text. |
| Task detail | Dirty Save alongside completion, delayed save/delete, rejected-write restoration, completion/Undo and expiry. |
| Journal | Read/Edit/clean/dirty action changes, Save, cancellation/discard, delete confirmation, editor return and entry paging. |
| Contacts | Detail actions, detail-to-editor handoff, clean editor actions; the same shared lanes govern dirty Save and deletion. |
| Day and other root routes | Shared modal morph, generic/calendar-date return faces, Agenda's directional date label, sidebar/navigation visibility and existing predictive-back handoff. |
| Interrupted writes | Fast local writes, overlapping writes, partial failure, another write during failure/result display, Undo yielding to a deliberate write and failure fade-out. |

Gesture owners remain the pill's existing menu handler, label drag handler,
and `SwipeDownDismiss` for cards. Calendar/pager motion still drives its own
preview geometry directly; the pill does not add a second clock to that
finger-driven date strip.

The minimum write beat is 450ms, the result holds for 1600ms, and the outline
closes for 420ms, pauses for 120ms and fades for 420ms. The modal's return morph
finishes within its 200ms allowance. The glass fill remains 0.68 with the shared
8dp light shadow.

## Changed files

- Shared pill rendering: `ui/components/CalinoComponents.kt`,
  `PillMorph.kt`, and new `PillFeedback.kt`.
- Shared motion specifications: `design/CalinoTheme.kt`; provider mounting:
  `MainActivity.kt`.
- Regression checks: `PillMotionTest.kt`, `PillInlineFeedbackTest.kt`,
  `PillGestureCancellationTest.kt`, `PillWriteFeedbackTest.kt`, and
  `QuickAddReturnTargetTest.kt`.
- This audit, `artifacts/navigation-pill-motion-review.html`, the optional
  capture driver, and the local ignored `HANDOFF.md` motion instructions.
- Open-handoff follow-up: the same route/lane/shared renderer, a regression in
  `PillMotionTest.kt`, `scripts/pill-open-motion-capture.kt`, and the separate
  [Open frame review](../artifacts/navigation-pill-open-handoff-review.html).

## Validation

- `test`, `lintDebug`, `assembleDebug` passed in the Android SDK Distrobox.
- 63 focused behavioral/motion tests passed on `emulator-5554`; four additional
  light/dark capture runs exercise both variants through the rendered flows.
- Motion tests assert intermediate painted widths, shared write-clock
  continuity, failure fade-out, current confirmation state and inline result
  restoration. Unit tests cover write overlap, failure tails and interruption.
- Device coverage includes slow cancelled route/Search drags, a cancelled
  delete hold, existing fling/short-drag dismissal tests, menus, editors,
  journal, tasks, failures, Undo and Agenda date focus.
- One existing return-to-Tasks assertion was stale: Buy flowers was offscreen
  before opening the editor. A separate probe reproduced that initial state.
  The test now waits for the real-time close and scrolls to its fixture.
- All 186 embedded images and report controls were checked in Chromium,
  including the 390px layout and absence of JavaScript errors.
- The Open-handoff follow-up passed `test`, `lintDebug`, `assembleDebug` and
  41 focused emulator checks (35 behavioral checks plus six capture runs).
  Its regression samples the contraction and verifies the normal return;
  rendered frames cover regular, extended and dock modes in both themes.
  All 48 follow-up report images and frame controls also passed the desktop
  and 390px Chromium checks. The resulting debug APK was installed on the
  Fold without clearing app data; transition validation used the emulator.
- Before committing, the motion changes were staged separately from the
  concurrent localization work and exported into an isolated checkout.
  That exact Kotlin source passed `test`, `lintDebug`, `assembleDebug` and
  all 35 focused behavioral emulator tests without the localization changes.

Rendered frames and tests establish state, continuity, clipping and control
behavior. This audit does not claim physical-phone frame pacing, live DAV
latency validation, or a full instrumented-suite run. Concurrent localization
work in the shared checkout was preserved.

## Repeat the capture

The opt-in driver is [scripts/pill-motion-capture.kt](../scripts/pill-motion-capture.kt).
Copy it temporarily into the app's androidTest source directory and run its
four `PillAudit*Test` classes, pinned to `emulator-5554`, then remove that
temporary test source. It writes `pill-*.png` into the emulator's Download
directory. The driver selects visible event title fields because pager
neighbors retain their own offscreen semantics.

Capture positions ending in 080/160 are animation-clock samples after an
action. The repository and host close delays also use real time, so these
positions are not wall-clock performance measurements.
