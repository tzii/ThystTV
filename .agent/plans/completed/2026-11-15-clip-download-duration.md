# Keep clip download durations

## Goal

Carry duration through all three clip download entry points; hide unknown or legacy zero lengths.

## Non-goals

No release, broad player lifecycle, upstream or unrelated cleanup changes.

## Current context

Based on published 1.6.0 c461b3264. User authorized all follow-up fixes.

## Files likely involved

DownloadDialog, clip-list fragments, PlayerFragment, DownloadsAdapter and tests

## Risks

Risk level: medium. Native UI and existing data/session boundaries must remain intact.

## Human approval

Required before implementation: no. Narrow reversible changes already authorized.

## Implementation steps

1. Repair the identified boundary using existing APIs.
2. Add focused regression coverage and manual QA instructions.
3. Build, test, lint and inspect the diff; open a separate PR.

## Verification

- [x] assembleDebug test lintDebug (1,455 tests; 0 failures/errors/skips; lint 0 errors, 342 warnings)

Human QA required: Download from channel/category/player; offline playback/replay chat; VoD segment lengths and offsets; spoiler hiding.

Human QA completed: none yet.

## Progress log

- 2026-11-15: Recovered scope; implemented narrow fix and regression tests.

## Decisions

- Reuse existing session/download APIs and persist no new settings or schema.

## Final PR summary draft

Summary: Carry duration through all three clip download entry points; hide unknown or legacy zero lengths.
Tests: pending gate.
Human QA: Download from channel/category/player; offline playback/replay chat; VoD segment lengths and offsets; spoiler hiding.
Risks: physical hardware/account checks remain explicit.

- 2026-11-15: Initial gate required the existing development debug key in this new
  worktree. Copied it locally (ignored); full gate passed 1,455 tests and lint.
  Final review retained zero as the worker's existing unknown-length sentinel,
  while the card hides nonpositive lengths. Rerunning for that final change.
- Player human QA additionally covers live/VoD, stream switching,
  minimize/restore, close/reopen, PiP/background, speed/quality, gestures and floating chat.

- 2026-11-15: Final full gate passed, including the unknown-length worker sentinel.

- 2026-11-15: Android 16 emulator: downloaded Monstercat's 22-second public
  “We did it Reddit!” clip from channel Clips (All time), chat replay off. Downloads
  correctly shows 00:22. This exposed the existing missing-offset sentinel as
  Start 00:-1; normalized unknown offsets at dialog submission and hid legacy
  negative source offsets on the card. Rerunning the gate for those two lines.

- 2026-11-15: Updated APK on Android 16 emulator: the existing test clip still
  shows 00:22 and no source labels; the existing 10:09 VoD retains Start 00:00 /
  End 10:09. The downloaded clip decoded through its 22-second endpoint (chat
  replay deliberately not downloaded). Added real recycled-card regression
  coverage for known zero/legacy negative offsets and absent/nonpositive lengths.

- 2026-11-15: Full final app gate passed (1,455 tests, no failures/errors/skips;
  lint 0 errors / 342 warnings). Added recycled-card case was saved after that
  run compiled its tests, so it was compiled and run separately: all three
  ClipDownloadDurationTest cases pass. Native card and playback evidence is
  private; other download history is not published in PR screenshots.
