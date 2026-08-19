# Player review fixes

## Goal

Fix the four defects found while reviewing `codex/player-ux-tablet-live-discovery`, then produce a verified debug APK for device testing.

## Non-goals

- Redesign player gestures, volume controls, or search results.
- Change playback backends, release signing, or unrelated branch behavior.
- Perform broad cleanup or upstream synchronization.

## Current context

The branch is based on commit `14575e7c`. The reviewed defects are: Watch Live eligibility checks channel ID instead of the login required by playback; large-surface gesture feedback applies alignment to the wrong view/layout parameters; stream-volume mute/unmute does not remember the current non-zero value; and the delayed overlay callback can outlive the fragment view.

Relevant guidance: `docs/PLAYER.md`, `docs/TESTING.md`, and `docs/MANUAL_QA.md`.

## Files likely involved

- `app/src/main/java/com/github/andreyasadchy/xtra/repository/ChannelSearchMapper.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerSurfacePolicy.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerFragment.kt`
- focused unit tests under `app/src/test/java/`

## Risks

- Search results could incorrectly enable or disable direct playback.
- Gesture feedback could regress on compact or RTL layouts.
- Volume overlay state could become stale across mute/unmute or view teardown.

Risk level: medium

## Human approval

Required before implementation: yes

Reason: Player UI and lifecycle behavior are high-risk. The user explicitly approved implementing the reviewed fixes.

## Implementation steps

1. Correct Watch Live eligibility and its unit-test matrix.
2. Apply gesture-feedback gravity, margins, and width to the actual pill container while preserving root vertical placement.
3. Introduce a small testable volume-overlay state helper and use it for mute/unmute restoration.
4. Remove delayed overlay callbacks during view destruction and make the callback binding-safe.
5. Review the diff and run the required Gradle checks.
6. Verify, hash, and hand off the corrected debug APK.

## Verification

Automated checks:

- [x] `assembleDebug`
- [x] `test` (274 tests, 0 failures)
- [x] `lintDebug`
- [x] APK signature and alignment verification

Human QA required:

- [ ] Search live result opens playback and missing-login result is disabled
- [ ] Brightness/device-volume feedback is edge-aligned on a tablet or wide window
- [ ] Stream volume at a non-default level survives mute/unmute
- [ ] Closing or rotating with the volume overlay open does not crash
- [ ] Live and VoD playback
- [ ] Stream switching, minimize/restore, close/reopen, and PiP/background behavior
- [ ] Speed, quality, gestures, and floating chat

Human QA completed:

- [ ] None; no physical device or emulator is attached.

## Progress log

- 2026-08-17: Created plan after explicit user approval.
- 2026-08-17: Corrected Watch Live eligibility to require the channel login consumed by playback and expanded the identity test matrix.
- 2026-08-17: Moved large-surface gravity, margins, and width to the actual feedback pill's `FrameLayout.LayoutParams`.
- 2026-08-17: Added testable stream-volume state, seeded it from the current volume, and made delayed dismissal safe across view teardown.
- 2026-08-17: `assembleDebug`, 274 unit tests, and `lintDebug` passed; APK signature and alignment verified.

## Decisions

- Decision: Keep the fixes narrow and avoid changing playback-session ownership.
  Reason: The defects are localized UI/state issues and do not require player backend churn.
  Alternatives considered: broader player refactoring was rejected as unnecessary and risky.
- Decision: Model remembered non-zero volume in a small view-independent state class.
  Reason: It makes first-open mute/unmute behavior deterministic and directly unit-testable.
  Alternatives considered: retaining an untested integer field in `PlayerFragment`.
- Decision: Preserve the full-width feedback root for vertical placement and align the child pill itself.
  Reason: The root must span the player for top/vertical positioning, while the child owns edge alignment and its 280dp cap.
  Alternatives considered: changing the root to `wrap_content`, which would complicate centered feedback and transitions.

## Final PR summary draft

Summary: Fix direct-live eligibility, large-surface gesture-feedback placement, and stream-volume state/lifecycle regressions.
Tests: `assembleDebug`, `test` (274 passed), `lintDebug`; APK signature and alignment verified.
Human QA: Required; not completed locally.
Risks: Player overlay positioning and lifecycle behavior require device confirmation.
