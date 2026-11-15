# Keep portrait live actions at the bottom

## Goal

Fix the raised portrait live controls reported on 1.6.0, including after force
stop and opening a fresh stream. Use exactly one bottom bar, with compact stats,
centered play/pause and native horizontal overflow for excessive controls/text.

## Non-goals

No player/session, decoder, stream ownership, chat list or gesture changes.
No official release publication. Preserve original user screenshots privately.

## Current context

Base c461b3264. PlayerLiveRewindControls unconditionally anchors both action rows
above streamInfoLayout in portrait live playback, and shifts play/pause upward.
The user confirmed the appearance on a fresh stream after force stop, so the
static layout itself reproduces the reported spacing without a PiP transition.
The existing combined debug APK was uploaded to a clearly labelled GitHub test
prerelease at the user's request, with this outstanding issue stated in its notes.

## Files likely involved

- PlayerLiveRewindControls.kt and PlayerTvFocus.kt (the controls root)
- PlayerLiveRewindControlsTest.kt
- PLAYER.md, MANUAL_QA.md, CHANGELOG.md

## Risks

Risk level: medium. This is control geometry only. Protect narrow widths, long
stats, enlarged fonts, RTL, optional buttons and landscape/VoD restoration.
Do not add observers that retain a fragment or change click/remote semantics.

## Human approval

Required before implementation: no. The user reported the bug in the ongoing
fix-and-test task; the change is narrow, reversible UI work.

## Implementation steps

1. Preserve native baseline previews and the original attachments.
2. Put the existing left actions and stats in one native horizontal viewport,
   keeping right-hand controls pinned. Reuse views/listeners and restore their
   XML parents/parameters outside portrait live playback.
3. Test actual inflated Android bounds and render representative before/after
   states; include cold start geometry and rotation/font/content changes.
4. Run build/test/lint, open a focused PR, and add the fix to the combined test APK.

## Verification

- [x] Native controls/TV cases: 18 passed in the full suite; inspected default/RTL/overflow pixels
- [x] assembleDebug test lintDebug: 1,457 tests passed, no failures/errors/skips; lint 0 errors, 342 warnings
- [x] Reviewed diff: control geometry only; no player/session changes
Distribution follow-up: apply this isolated fix to the existing combined test branch, rebuild and verify the APK, and upload a separate debug prerelease. This does not authorize an official release.

Human QA required:

- [ ] Fresh live stream after force stop; portrait default/optional controls.
- [ ] VoD, stream switching, minimize/restore, close/reopen and orientation.
- [ ] PiP/background, speed/quality, gestures and floating chat interaction.
- [ ] Enlarged text, RTL, compact/wide windows and TV remote focus.

Human QA completed: user supplied 1.6.0 screenshots and fresh-start reproduction;
no physical-device acceptance of the fix yet.

## Progress log

- 2026-11-15: Confirmed unconditional portrait elevation in the production helper.
  Created isolated fix branch from published 1.6.0, keeping other PRs separate.

## Decisions

- The user explicitly rejected a two-row fallback. Always use one row, with
  horizontal overflow when necessary; preserve text size and enabled actions.
- Use native layout passes for changing text/visibility; no deferred listener
  or fragment callback is needed for geometry.

## Final PR summary draft

Summary: one portrait live control bar with compact stats and horizontal overflow.
Tests: native bounds/renders, full Gradle gate and combined APK.
Human QA: matrix above; physical phone verification remains required.
Risks: compact/large-text layouts and transition restoration.

- 2026-11-15: The initial adaptive-row tests passed, but the user explicitly
  requested a single row even with limited space. Replaced the two-row fallback
  with a native horizontal viewport and pinned right-hand actions. Full text
  size and original view/click identity are retained. Focused tests running.

- 2026-11-15: Final single-row focused suite passed 17 tests. Default 360/412dp
  controls fit without scrolling, large-text/optional-action variants remain
  one row with reachable stats and pinned right actions. Fixed the test's
  coordinate conversion to use Android drawing rectangles for scrolled views;
  the reachability assertions remain intact. Full build/test/lint running.

- 2026-11-15: Attempted a phone emulator pass. The existing AVD twice displayed
  System UI not responding and refused installation with insufficient storage
  (404 MB free after cache trim). The new APK was not installed on that device;
  no real live-stream pass is claimed. Stopped the headless AVD started for this
  task. Physical-phone acceptance and touch/gesture checks remain required.

- 2026-11-15: Source fix complete. Full Gradle gate passed after guarding the
  Android 6 gravity getter; a dedicated API 23 regression case also passes.
  Packaging continues on test/post-1.6-single-row with the previous seven fixes.
