# Stop Cast from Settings

## Goal

Expose an existing Cast session at the top of Settings without initializing Cast. Preserve the existing VoD Stop casting and play here offer.

## Non-goals

No release, broad player lifecycle, upstream or unrelated cleanup changes.

## Current context

Based on published 1.6.0 c461b3264. User authorized all follow-up fixes.

## Files likely involved

CastSessionPreference, SettingsActivity, root_preferences, strings and tests

## Risks

Risk level: medium. Native UI and existing data/session boundaries must remain intact.

## Human approval

Required before implementation: no. Narrow reversible changes already authorized.

## Implementation steps

1. Repair the identified boundary using existing APIs.
2. Add focused regression coverage and manual QA instructions.
3. Build, test, lint and inspect the diff; open a separate PR.

## Verification

- [x] assembleDebug test lintDebug (1,457 tests; no failures/errors/skips; lint 0 errors)

Human QA required: Physical receiver: stop with player open/closed and retained session; remote disconnect, rotation, wide/TV; live/VoD, switching, minimize/restore, close/reopen, PiP/background, quality/speed, gestures and floating chat.

Human QA completed: none yet.

## Progress log

- 2026-11-15: Recovered scope; implemented narrow fix and regression tests.

## Decisions

- Reuse existing session/download APIs and persist no new settings or schema.

## Final PR summary draft

Summary: Expose an existing Cast session at the top of Settings without initializing Cast. Preserve the existing VoD Stop casting and play here offer.
Tests: pending gate.
Human QA: Physical receiver: stop with player open/closed and retained session; remote disconnect, rotation, wide/TV; live/VoD, switching, minimize/restore, close/reopen, PiP/background, quality/speed, gestures and floating chat.
Risks: physical hardware/account checks remain explicit.

- 2026-11-15: New worktree initially lacked the existing debug key; copied it
  locally without tracking it. Full suite caught the static root's navigation
  contract: moved Stop casting to a runtime-only action, so static search cannot
  index an unavailable session command. Existing structure tests are unchanged;
  added coverage for absent/reused runtime rows. Final full gate running.
- 2026-11-15: Android 16 phone emulator with Cast off: Settings opens normally,
  no Stop casting row or crash. Anonymous capture in private QA evidence.
  Live receiver stop/handoff remains physical-hardware QA.

- 2026-11-15: Final full gate passed. Existing structure tests pass unchanged, and runtime action tests pass. Diff reviewed; receiver QA remains.
