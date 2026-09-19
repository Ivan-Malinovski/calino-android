# Changelog

Notable changes per release. Releases before 0.2.1 are recorded in the git
history and their tags.

## 0.2.1 — 2026-09-19

### Fixed

- A side panel's card draws its whole shadow. The event preview puts each event
  on a pager page, and a pager clips its pages, so the shadow used to end at a
  hard vertical line a few dp out from the card.
- An event swiped out of a side panel fades out at that same edge instead of
  being cut by it and vanishing an edge at a time.
- A modal's status-bar slice now follows a dismissal drag the whole way with the
  scrim below it, rather than clearing in a single frame as the drag begins. The
  scrims no longer ripple when tapped.
- A contact card's list, and the editor's, runs the full height of its card and
  passes under the floating pill, instead of stopping a lane's height short
  against a band of empty card.
- A month cell no longer drops a card to make room for the "+n" line: the line
  is charged as the 14dp of text it is rather than as a whole card.

### Changed

- Creating an event on empty timeline space is a press and hold, not a double
  tap. A scroll or a page swipe that begins on blank space is left alone.
- A multi-day range's add pill reads "New event" rather than naming a day nobody
  picked, and opens a blank editor anchored on the first day on screen.
- The fully expanded month does the same. The tablet split is the exception: its
  month is pinned beside a day pane, so the pill keeps naming that day until the
  pane is collapsed.
- Paging the agenda by month carries the date sideways with the finger, the way
  every other month change in the app does, instead of relabelling once the page
  has settled. An arriving page reports the day its own list is resting on.
- A multi-day run opens the way its days do, unrolling from its first day's card
  across the week. Grid markers and the cards they become now share one swatch,
  so nothing shifts color across the morph.
- The expanded month keeps its selected-day marker only where it means
  something: beside a day pane, driven by that pane's own travel. On a phone the
  marker leaves together with the expansion.
- The top of Contacts is slimmer — no subtitle, a search pill in place of the
  Material box, and a hairline parting the header from the names.
