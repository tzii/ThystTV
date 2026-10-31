# Mini-player geometry on landscape phones, foldables and tablets

## Goal

A minimized 16:9 video has a 16:9 visible container on every window shape, without
the window-shaped empty bands reported by the maintainer. Restoring the player
keeps its normal full-player and fold layouts.

## Non-goals

No source/session/lifecycle protocol changes, zoom/gesture feature work, version
bump or official release. Other video aspect ratios retain normal Fit letterboxing.

## Current context

Issue: #81. Base: issue #71 test build 836fa65b8, branch fix/mini-player-ratio. The maintainer
reports the player tests are fine, with the scenario/device breakdown unspecified;
the screenshots show the mini-player defect on a near-square landscape device,
also reported on a tablet. This is a separate sizing follow-up to PR #80.

PlayerLayout only constrains portrait measurement to 16:9. Landscape minimization
therefore shrinks the window-shaped player (including its empty bands), using
different X/Y scales (0.3/0.325). Mini-player bottom placement also assumes the full
landscape window height. The same geometry is rebuilt during configuration changes.

## Files likely involved

- app/src/main/java/com/github/andreyasadchy/xtra/ui/view/PlayerLayout.kt
- app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerFragment.kt
- app/src/test/java/com/github/andreyasadchy/xtra/ui/player/PlayerMiniPlayerLayoutTest.kt
- app/src/test/java/com/github/andreyasadchy/xtra/ui/player/PlayerFoldLayoutTest.kt
- docs/PLAYER.md and docs/MANUAL_QA.md

## Risks

Risk level: medium. The change is limited to layout measurement, mini-player scale
and placement. Check minimized rotation/resizing, maximize, PiP and fold restoration.
On wide phones, fit the 16:9 base rectangle inside the sliding container so its
parent cannot clip the video before scaling.

## Human approval

Required before implementation: no. The reported bug authorizes the narrow,
reversible layout correction; no broad player lifecycle or release action is needed.

## Implementation steps

1. Add native-layout regression coverage for near-square, tablet and wide-phone
   mini-player geometry, restored full geometry and legitimate source letterboxing.
2. Give PlayerLayout an explicit minimized measurement state, synchronized through
   updateFoldLayout (used by minimize, maximize, init and PiP). Fit a 16:9 rectangle
   inside the measured container; use uniform mini-player scale.
3. Base landscape mini-player placement on the measured player width/height in
   minimize and configuration layout paths.
4. Update the geometry contract and focused human QA. Record the maintainer's
   prior player-test report without claiming unreported scenarios passed.
5. Run focused and full Gradle gates; inspect real emulator video in near-square
   and tablet/wide/portrait shapes, minimize/restore and PiP. Build a debug APK
   with the same existing debug key for a phone retest.

## Verification

- [x] Regression fails on the pre-fix implementation.
- [x] Focused mini-player/fold/zoom/PiP tests pass.
- [x] assembleDebug
- [x] test
- [x] lintDebug
- [x] assembleRelease
- [x] git diff --check and regression diff review
- [x] Emulator visible-video geometry and transitions

Human QA required: live/VoD, stream switching, minimize/restore, close/reopen,
rotation/resizing/fold changes, PiP/background, speed/quality, gestures and floating
chat. Specifically check the mini-player on the reporting device and tablet.

Human QA completed: the maintainer reports the previous #71 player tests are fine;
no physical-device QA of this sizing correction has been performed yet.

## Progress log

- 2026-10-31: Read screenshots and traced the landscape frame measurement, scale
  and placement. Created an isolated follow-up worktree from the tested #71 head.
- 2026-10-31: Reproduced on the existing clean APK in the Pixel 9 Pro Fold
  emulator, landscape (2152 x 2076): advancing VoD video inside a 645 x 675
  mini-player, with the same large empty bands. Baseline screenshot and UI bounds
  retained outside tracked files. Added four native-view regression cases; the
  first test run is compiling this new worktree from scratch.
- 2026-10-31: The four native regression tests all fail on the original compiled
  implementation (baseline run: 9m 59s). Implemented explicit minimized 16:9
  measurement within the parent, uniform landscape scale, and measured placement
  for both orientation paths. Fold restoration now keeps the mini-player frame
  rather than returning to the window-shaped landscape frame. Updated the one
  fold regression expectation that encoded the reported defect.
- 2026-10-31: Full debug suite: 1,195 tests in 137 suites, no failures/errors/skips;
  lint 0 errors and 332 existing warnings, none on changed lines. Debug assembly
  passes; the unsigned release assembly is still finishing R8/package tasks.
- 2026-10-31: Actual-video emulator checks on the same APK: landscape foldable
  mini-player 645 x 363 (previously 645 x 675), portrait foldable 1038 x 583,
  2560 x 1600 tablet-size window 768 x 432, 2400 x 1080 wide-phone-size window
  576 x 324. Frame and SurfaceView bounds agree, the picture fills them and the
  bottom stays above navigation. Resized one foldable emulator rather than using
  physical tablet/phone hardware. Maximize/restore returns the full landscape
  frame, including legitimate full-player Fit bars. VoD-to-live switching shows
  the new source in the same mini-player. PiP entry from full and minimized
  states, return and re-minimize work; settled PiP shows decoded live video.
  No AndroidRuntime or ExoPlayer errors were logged. Emulator audio was disabled,
  so this does not establish audible overlap. Display size/density and rotation
  settings restored; app stopped. Physical reporting-device/tablet QA remains.
- 2026-10-31: All gates completed: `assembleDebug test lintDebug assembleRelease`
  BUILD SUCCESSFUL in 17m 52s. Release APK is unsigned. Final debug APK SHA-256:
  `09aa212ef2866369e38ec5e1e7beccb0940d8396c322e98b12699f0289366336`.
  Signature verified: same debug certificate as the prior #71 phone-test APK
  (`f813860d362aae0f8a1479fb58442571306ca2421ca1d2a6d529c8f19b94c768`).
  Source diff reviewed; no session/protocol changes or new lint findings. Retest
  the exact APK on the reporting hardware before merge/release.

## Decisions

- Keep the established 16:9 mini-player viewport, rather than inheriting window
  proportions or stretching/cropping the source. Fit the base rectangle inside
  the parent before uniform scaling so extra-wide windows cannot clip it.
- Keep the #71 lifecycle patch and gesture branches unchanged; this follow-up is
  a separate branch based on the tested APK.

## Final PR summary draft

Fix landscape mini-player bands by measuring a 16:9 viewport, applying uniform
scale and positioning its measured bounds. Native layout coverage includes phone,
near-square and tablet sizes, source letterboxing and full-player restoration.
Physical reporting-device/tablet QA remains required.
