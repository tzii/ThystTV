# Apply Extended zoom preference changes to the visible player

## Goal

Turning Extended zoom off immediately clears the current magnification and pan,
including on API 24 and devices without PiP. Turning it on again starts at plain
Fill rather than reviving the old crop.

## Non-goals

No lifecycle/source-ownership changes, mini-player animation redesign, release
metadata, dependency changes or broad test cleanup.

## Current context

The maintainer supplied the independent source review of base 7bfdbc694 to
ad1b1dcc9 and identified Freebuff DeepSeek V4.1 Flash 10/17 with max reasoning
from the harness. It found no blocker or major defect. Its confirmed minor issue
is that canonical geometry reads the preference but the drawn surface does not
observe it. The existing observer is PiP-only and absent before API 26 or on
devices without PiP. The existing zoom-off test manually refreshes geometry,
masking the missing preference notification path.

## Files likely involved

- PlayerFragment.kt: view-owned preference observer and pinch commit guard
- PlayerPinchZoomTest.kt and PlayerPipRequestTest.kt: production notification path
- docs/PLAYER.md: immediate preference reset behavior

## Risks

Risk level: medium. Keep the observer scoped to the view, retain teardown, and
avoid PiP callbacks on unsupported devices. An in-progress pinch must not retain
the old magnification after the setting is disabled.

## Human approval

Implementation is authorized by the release request and instruction to finish.
Exact signed-candidate approval is a separate release gate.

## Implementation steps

1. Reproduce the preference notification failure on API 24 and 28 without a
   manual geometry refresh.
2. Register the view-owned presentation preference observer independently of PiP
   support. Clear magnification and finalize the surface when zoom is disabled.
3. Prevent an in-flight pinch from recommitting disabled magnification.
4. Run focused tests, all required Gradle checks, and review the focused diff.
5. Keep this player fix separate from the metadata PR, preserve attributed raw
   review/dispositions outside source, and require final signed-candidate review.

## Verification

- [x] Failure reproduced through actual SharedPreferences notification
- [ ] API 24 and 28 regression coverage; old magnification stays cleared
- [ ] test, assembleDebug, lintDebug, assembleRelease (unsigned local)
- [ ] Focused diff reviewed

Human QA required: toggle off while magnified in multi-window, return and toggle
on again; live/VoD, switching, minimize/restore, close/reopen, PiP/background,
speed/quality, gestures and floating chat in the combined signed candidate.
No new physical-device QA is claimed.

## Progress log

- 2026-11-02: Saved the user-supplied raw review out of tree. Replaced the manual
  geometry refresh in the zoom-off regression with actual preference delivery
  and added API 24 coverage. Initial sandbox launch could not reach Gradle;
  reran with the normal approved cache/network access.
- 2026-11-02: Both API 24 and 28 failed at the actual surface assertion:
  expected scale 1.0, actual 1.8749999 after disabling the setting. Registered
  the presentation observer independently of PiP and cleared magnification
  before finalizing geometry. Added an in-flight pinch commit guard and a
  re-enable assertion. Full required Gradle verification is running.

## Decisions

- Fix the confirmed product issue now. Treat placement/animation concerns and
  test-quality observations separately; do not claim an unobserved frame jump
  or use a source review as signed-artifact approval.

## Final PR summary draft

Disabling Extended zoom resets the visible surface and stored session pan even
without a controller reconnect or PiP support. Preference delivery is covered on
API 24 and 28; publication remains subject to the exact signed-candidate gates.
