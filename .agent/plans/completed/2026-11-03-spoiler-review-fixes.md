# Spoiler Shield review corrections

## Goal

Correct verified issues from the maintainer-supplied reviews for issues #78/#79.

## Non-goals

No release, commit, upstream sync, authentication redesign, real clip creation,
device-data changes or unrelated baseline fixes.

## Current context

Uncommitted feature worktree based on 7c6ded4ab. Raw supplied reviews and their
disposition are preserved under the root checkout's ignored .private/reviews/.
The maintainer corrected the first review's reasoning setting to max.
No new independent review lanes are being launched.

## Files likely involved

Feature implementation, focused regression tests, strings, PLAYER.md or
MANUAL_QA.md, and CHANGELOG.md. The combined-qa checkout receives the corrected
source only after each feature's edits are complete.

## Risks

Risk level: medium. Preserve player/session ownership and existing authentication
flow. UI dialog and list lifetimes need focused regression coverage.

## Human approval

Required before implementation: no. The maintainer asked to continue the authorized
feature work and supplied reviews; these corrections are narrow and reversible.
Publication and authenticated Twitch mutations are outside this work.

## Implementation steps

1. Verify each supplied finding against the code and primary documentation.
2. Cover team cards and clickable long-press targets; avoid retaining destroyed lists; apply rule edits to current preferences; re-evaluate the channel title; measure compact-card reveal bounds.
3. Run focused regressions and required debug assembly, unit tests and lint.
4. Update combined-qa and provide a new debug APK, with remaining human QA stated.

## Verification

- [x] Focused regression checks
- [x] assembleDebug
- [x] test
- [x] lintDebug
- [x] Diff reviewed

Human QA required: live/VoD playback, stream switching, minimize/restore,
close/reopen, PiP/background, speed/quality, gestures and floating chat.
Also verify the corrected feature on compact/wide, TalkBack, TV, RTL and large
fonts where relevant. Real clips and authenticated sign-in need maintainer QA.

Human QA completed: none in this correction pass.

## Progress log

- 2026-11-03: Recovered both supplied reviews and verified the primary defects.
  Twitch's guide says 15 seconds but its endpoint reference says 60 seconds;
  retain 60 and document the discrepancy. Current reference maps disabled
  clipping to 403; do not infer that every 503 means clips are disabled.
- 2026-11-03: Covered team cards and child long-press targets, removed the new
  strong list-view reference, applied rule edits to current preferences and made
  settings/channel title state observe protection changes. Native measurements
  found no compact reveal clipping at 360dp, normal/200% fonts, LTR/RTL; kept the
  original layout.
- 2026-11-03: Full checks caught an attachment requirement during channel-title
  observer cleanup. Store the exact preferences instance at view creation and
  unregister it safely at destruction. Existing header tests and a new actual
  preference/reveal binding regression now pass; no fixture/gate was weakened.
- 2026-11-03: Final required checks passed: 1,269 tests, zero failures/errors/
  skips, lint zero errors/333 warnings. Combined source passed 1,279 tests plus
  debug assembly and lint. Raw reviews, failed/final logs and the disposition
  are preserved privately. Corrected combined APK verified with matching prior
  QA certificate; device/Twitch QA remains listed. No commit, PR or release.

## Decisions

- Keep each feature's corrections in its existing worktree; preserve the rewind
  worktree unchanged. No new review delegation or external repo upload.

## Final PR summary draft

Correct verified feature-review issues. Validation and residual manual QA will
be recorded after the checks finish; no release or authenticated API use.
