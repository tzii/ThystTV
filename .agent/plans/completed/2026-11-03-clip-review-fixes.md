# Clip creation review corrections

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
2. Keep completed clip links until an explicit user action; show busy feedback; clarify sign-in cancellation; map 503 without asserting a permission failure; document conflicting Twitch timeout guidance.
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
- 2026-11-03: Corrected held-result lifetime, busy feedback, sign-in warnings and
  temporary 503 feedback. Confirmation requests cooperative cancellation at 60s;
  blocking repository calls can still finish later. Native dialog tests cover
  lifecycle versus explicit dismissal and held/busy paths.
- 2026-11-03: Full required checks passed: 1,267 tests with no failures/errors/
  skips; lint zero errors and 332 warnings. Combined corrected source passed
  1,279 tests plus debug assembly and lint (zero errors/333 warnings). Copied and
  verified the APK under .private/artifacts/features-78-79-reviewed in the root
  checkout. Same certificate as the previous QA APK; the original is preserved.
  No device/authenticated checks, commit, PR or release was performed.

## Decisions

- Keep each feature's corrections in its existing worktree; preserve the rewind
  worktree unchanged. No new review delegation or external repo upload.

## Final PR summary draft

Correct verified feature-review issues. Validation and residual manual QA will
be recorded after the checks finish; no release or authenticated API use.
