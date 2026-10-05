# Calendar endpoint reveals

The compact calendar has a continuous vertical path: Agenda, compact week
(level 1), partial month (level 2), detailed month (level 3), Year. Pull down
to expand and continue into Year; pull up to collapse and continue into Agenda.
The three density stops, handle tap cycle, selected dates, horizontal pagers
and the wide split layout retain their existing behavior.

Home's container recognizer owns the entire stream. It claims vertical travel
only after axis arbitration, lets event-card drags retain consumed movements,
and lets the day list scroll before taking over at its corresponding edge.
A piecewise screen-distance mapping uses 280dp per density step and 220dp for
an endpoint reveal. Crossing and reversing a boundary preserves exact travel.
At an endpoint, the destination mounts on pointer-down to measure before the
first outward frame. Its semantics and input stay suppressed during preview.

Month contracts with uniform scale, an interpolated rounded clip and position
into the selected month's actual Year tile. Year stays stationary underneath;
the month begins fading at 8% of the contraction and is fully dissolved by
82%, with a smooth easing into the tile's heat-grid rendering.
Agenda keeps the shared month heading anchored. The week strip rolls up 80dp,
the outgoing calendar retracts through a 56dp feathered alpha edge, and the
month agenda rises with slight parallax to fill it. The mask is applied to an
isolated offscreen layer so it cannot erase the destination underneath. All geometry reads the same live progress sample.
The draw transformations leave the recognizer's full-size input path intact.
The host clips drawing below the safe top inset.

`CalendarEdgeTransition` settles with `CalinoMotion.standardSpatial`, or returns
with `gestureReturn`. Slow releases require 45% travel. An outward fling of
650dp/s requires at least 6%; an inward fling returns. Consumed releases
(Compose's representation of Android cancellation) and a second pointer cancel
rather than navigate. A successful boundary release records the reached
calendar density, including a single pull that crossed an earlier stop.
Another root choice cancels a pending settle.

MainActivity alone commits the route. The destination uses the same movable
reference in preview and at rest, and the route transition is suppressed after
the geometric settle. Agenda cannot publish its reading date until it owns the
route. Year previews cannot commit dates. Root and calendar predictive-back
handlers do not compete with an active edge reveal.

Each upward reveal snapshots the calendar's committed date into a numbered
`AgendaStartRequest`. It supersedes both a saved pager month and the saved
list position, including day 1. The day list scrolls before reading-line focus
can publish, and the regular month-sync effect waits for the explicit entry
jump. The request is consumed once; subsequent reading-date changes do not
scroll back to the entry day. This also prevents a zero-progress preview made
before tapping another calendar date from restoring today's list position
when the person later reveals Agenda.

## Verification

Unit coverage checks travel reversal across both endpoints, clamping, slow
release thresholds and fling intent. `CalendarEdgeGestureTest` exercises slow
pulls, short returns, direction reversal, fast flings, cancellation in both
directions, and a single pull from partial month through detailed month into
Year followed by Back. Follow-up tests check a May 6 reveal, re-entry on May 1
with today's Agenda already saved, and a June 6 reveal with a different month
saved in Agenda. The ordinary reading-line focus check remains covered.
Existing paging and Year tests cover horizontal
ownership, selected dates and destination actions/recreation.

Opt-in light/dark capture driver: `scripts/calendar-edge-motion-capture.kt`.
Copy it to `app/src/androidTest/java/calino/malinov/ski/CalendarEdgeCapture.kt`,
run its two classes on pinned `emulator-5554`, and remove the copy afterward.
It writes drag and settle screenshots to `/sdcard/Download/calendar-edge-*.png`.
The API 36 emulator uses native 1080x2400 / 420dpi; it is not a physical-phone
motion review. This session's frames are outside the repository under
`/home/ivan/reports/calino-calendar-edge-motion/`.

The existing `CalendarZoomMorphTest.handleWrapsFromTheMonthLevel` failure
(expected level 2, still level 3 after a tap) reproduced in the broader run.
It was already documented in HANDOFF's September 28 run. It does not exercise
the new endpoint gestures.

Final check on 2026-10-05: `test lintDebug assembleDebug` passed across the
configured modules. The revised build passed all 30 focused API 36 device checks (13 endpoint
checks, eight paging checks, six Year checks, one reading-line focus check,
two light/dark capture runs).
The three new JVM rules tests passed. Rendered drag and settle frames were
reviewed in both themes; the revised 34 frames are archived outside the repository
under `/home/ivan/reports/calino-calendar-edge-motion/revision/`. Both the preview
and resting Agenda show May 6 first after revealing it from compact May 6.
The temporary capture test copy was removed after the run. `git diff --check`
passed. The full instrumented suite was not run. Before committing, an isolated
snapshot containing only this change passed app JVM tests, the debug build,
Android test compilation and `lintDebug`, independently of the localization
work left in the shared workspace.

Changed implementation files: `ui/home/HomeScreen.kt`, the new
`ui/home/CalendarEdgeTransition.kt`, `MainActivity.kt`, the new
`ui/components/CalendarEdgeMorph.kt`, `ui/year/YearScreen.kt`, and
`ui/surfaces/AgendaScreen.kt`. Endpoint hints live in `strings_calendar_edge.xml`. The workspace's in-progress
Danish and German localization was preserved and is excluded from this change. Added `CalendarEdgeGestureTest`,
`CalendarEdgeTransitionTest`, and the opt-in capture script. Updated the local
`HANDOFF.md` and this note. Existing workspace changes were preserved. The follow-up changed `MainActivity.kt`,
`CalendarEdgeMorph.kt`, `AgendaScreen.kt`, the endpoint device test and capture
script, and the two handoff notes.

Debug artifact SHA-256:
`7ac11abc5ae151628036efec73950253171909882daac3a3a5fca20aabf3fa03`.
Installed this debug APK successfully on the connected Fold (SM-F971B) with
`adb install -r`, preserving app data, and verified the installed
`calino.malinov.ski.nativeDebug` package. The Fold was not used for instrumented
tests or a gesture review; motion review was on the emulator.
