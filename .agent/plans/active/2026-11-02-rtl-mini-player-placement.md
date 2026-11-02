# Keep the RTL mini-player inside its window

## Goal

Place and constrain the minimized video using its actual measured child origin,
so an RTL extra-wide window does not shift the video outside the screen.

## Non-goals

No new gesture behavior, source/lifecycle changes, viewport ratio or animation
redesign, dependencies, release versions, signing tools or protection changes.

## Current context

The final GPT-6.1-sol/high review of signed candidate8d74b84de found one P2:
PlayerLayout's16:9 viewport is narrower than an extra-wide landscape parent,
and FrameLayout START gravity places it at the right in RTL. Placement assumes
its origin is the left/top system inset. In2400x1080 at0.3scale, a480px child
offset adds144px displacement. The assumption is repeated in minimize, init,
snap-back and free-movement bounds. Existing tests measure dimensions only.

Candidate8d74b84de is on hold and must not be tagged/promoted. Its software,
signature and manifest checks passed, but source correction requires a new
freeze/signed RC/reviews/QA. Raw review is preserved outside source. The user
declined the superseded Freebuff request for now; finish the correction and
Sol review before providing replacement manual-review material.

## Files likely involved

- PlayerFragment.kt: actual playerLayout.left/top in translation/bounds
- PlayerMiniPlayerPlacementTest.kt: inflated RTL geometry and real entry points
- docs/PLAYER.md and docs/MANUAL_QA.md: placement invariant and RTL matrix

## Risks

Risk level: medium. Preserve physical corner/margins and existing uniform scale,
navigation/keyboard/cutout handling, freely moved bounds and touch ownership.
Keep UI math separate from player source ownership.

## Human approval

Authorized by the existing release/fix instruction; no new approval is needed
for this narrow correction. Exact replacement signed-APK acceptance remains a
publication gate. No device QA is inferred from the old candidate or debug APK.

## Implementation steps

1. Reproduce clipping with actual inflated RTL views and transformed bounds.
2. Extract the existing mini-player release positioning into a production method
   so snap-back and free-bound paths can be exercised without network startup.
3. Use the measured child left/top consistently in minimize, init and both
   release-position paths. Preserve destination positions and timing.
4. Check production minimize/init/snap-back/free movement with RTL/LTR, extra-wide
   windows and nonzero child origins; preserve viewport/restoration tests.
5. Run all four Gradle gates and a focused diff review, normal protected-master
   PR, then a new frozen signed candidate and required reviews/exact-byte QA.

## Verification

- [x] Original RTL clipping reproduced in native Android view measurements
- [ ] Production placement paths pass RTL/LTR/padding regressions
- [ ] assembleDebug, test, lintDebug, unsigned assembleRelease
- [ ] Focused diff reviewed; no signing/protection change

Human QA required: live/VoD, switching, minimize/restore, close/reopen, PiP/
background, speed/quality, gestures and floating chat. Check RTL extra-wide
minimize/restore, drag/release at every boundary, insets/keyboard, rotation,
tablet/fold/window sizes and transition frames on the replacement signed APK.
No new physical-device QA completed.

## Progress log

- 2026-11-02: Saved the actual Sol/high final review and put8d74b84de on hold.
  Started a focused source-fix branch from that master; no official tag/release.
- 2026-11-02: Five production-path regressions failed with the original math.
  Real RTL minimize/init/snap bounds ended at2538 instead of2394 in a2400px
  window (144px displacement); padded/free-bound cases also clipped. Extracted
  only the existing release-position block to exercise its production path;
  no geometry change was part of that failing baseline. Replaced all12 origin
  subtractions with measured left/top across both init branches, minimize,
  snap-back and free-movement bounds. Full required checks are running.
- 2026-11-02: Open a focused draft PR while local checks run so protected CI can
  validate the same source in parallel. Record completed check results in that
  PR/external evidence before merge; never mark a pending check passed. Preserve
  the superseded candidate and source review records without editing them.

## Decisions

- Preserve measured gravity/layout and fix coordinate conversion rather than
  forcing a different RTL layout or changing viewport dimensions.
- Keep all invalidated candidate artifacts/review output; do not overwrite or
  treat old phone/manual-review results as approval of a replacement candidate.

## Final PR summary draft

Correct mini-player placement for measured RTL child origins on extra-wide
windows. Cover actual fragment placement paths with transformed Android bounds;
retain16:9 sizing and existing physical corner/inset behavior.
