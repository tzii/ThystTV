# Swipe to show chat (#72) and extended zoom with pan (#73)

## Goal

Two player gesture features for 1.5.2, in three stacked PRs:

1. **PR 1, #72:** in full-screen landscape, a sideways swipe shows or hides
   chat. Live streams accept the swipe anywhere on the video. VODs, clips,
   downloads and live rewind accept it only from a narrow strip on the chat side
   of the video, so seek and speed swipes keep the rest of the surface.
2. **PR 2, #73a:** with the new "Extended zoom" setting on, pinching past Fill
   keeps magnifying (up to a fixed cap). Up to Fill nothing changes: the picture
   stays centered. Past Fill the content under the fingers stays under them.
3. **PR 3, #73b:** while magnified past Fill, one-finger drags pan the picture.

## Non-goals

- No change to Fit/Fill/kept-zoom behavior below Fill, or to its persistence.
- No interactive chat drag that follows the finger (chat opens once the swipe
  passes a threshold, using the existing show/hide paths).
- No double-tap zoom reset: double tap keeps toggling chat.
- No player lifecycle, Cast or source-ownership changes (#71 is separate).
- No magnification in portrait, the mini-player, PiP, Stretch or Android 6.

## Current context

- Touch entry: `PlayerFragment` `dragView` touch listener feeds
  `PlayerGestureArbiter` (single owner per sequence) and the
  `PlayerGestureListener` detector. Single-finger gestures only run when the
  controls were hidden at gesture start; with controls visible a vertical drag
  minimizes.
- `PlayerGestureListener.onScroll` locks vertical (brightness/volume) or
  horizontal; horizontal is used only when the video type is not `STREAM`
  (speed upper zone, seek lower zone via `PlayerGestureZonePolicy`).
- Touches starting inside the system gesture insets (or a 30dp fallback) are
  ignored (`isInEdgeZone`), protecting Android's Back gesture.
- Sidebar chat sits at the layout's end edge (`Gravity.END`, `marginEnd` on
  `playerLayout`), so it is on the left in RTL.
- Pinch zoom: `PinchZoomController` maps a cumulative pinch scale to Fit, an
  in-between zoom or Fill, clamped at Fill with elastic overshoot.
  `canonicalSurfaceScale`, `finalizePinchSurface`, `refreshDisplayZoom` and
  `animatePinchSurface` own surface geometry. `supports(ratio)` requires Fill to
  be about 1.12x Fit; otherwise pinch uses the Fit/Fill toggle controller.

## Files likely involved

- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerFragment.kt`
- `.../ui/player/PlayerGestureListener.kt`
- `.../ui/player/PlayerGestureArbiter.kt`
- `.../ui/player/PlayerChatSwipePolicy.kt` (new)
- `.../ui/player/PinchZoomController.kt`
- `.../ui/player/PlayerGestureEducation.kt`, `PlayerGestureGuideDialog.kt`
- `.../util/C.kt`, `res/xml/player_preferences.xml`, `res/values/strings.xml`
- Tests under `app/src/test/java/com/github/andreyasadchy/xtra/ui/player/`
- `docs/PLAYER.md`, `docs/MANUAL_QA.md`, `CHANGELOG.md`

## Risks

- Gesture conflicts: chat swipe vs seek/speed on VODs, pan vs every one-finger
  gesture, pinch takeover from pan, Back gesture near the edge strip.
- Surface geometry: magnified scale and translation must never survive into
  portrait, the mini-player or PiP, and must re-clamp on resize.
- Chat layout churn when chat opens mid-gesture (player width changes).

Risk level: medium

## Human approval

Required before implementation: no

Reason: the maintainer asked for all three PRs on 2026-10-29 and approved the
behavior proposed in chat. Release packaging for 1.5.2 is a separate step.

## Implementation steps

1. PR 1: `PlayerChatSwipePolicy` (start eligibility, direction, threshold),
   `Owner.CHAT_SWIPE`, listener wiring, fragment show/hide, setting (default on),
   guide row, tests, docs.
2. PR 2: extended range in `PinchZoomController` (Fill capture band, magnified
   zone, cap), session magnification on top of Fill, finger-anchored scale with
   translation, reset on minimize/PiP/portrait/display-mode choice, setting
   (default off), tests, docs.
3. PR 3: `Owner.MAGNIFIED_PAN`, listener pan path, pinch takeover from pan, normalized pan
   offset re-clamped on resize, tests, docs.

## Verification

Automated checks:

- [ ] `./gradlew assembleDebug`
- [ ] `./gradlew test`
- [ ] `./gradlew lintDebug`

Human QA required: see `docs/MANUAL_QA.md` sections "Swipe to show or hide
chat" and "Extended zoom".

Human QA completed:

- [ ] (none yet)

## Progress log

- 2026-10-29: Created plan; started PR 1 on `feat/72-swipe-chat`.
- 2026-10-29: PR 1 committed (0e25aa1f9). `assembleDebug test lintDebug` passed
  in a clean worktree at that commit (BUILD SUCCESSFUL, exit 0).
- 2026-10-29: PR 2 on `feat/73-extended-zoom`: magnified range in
  `PinchZoomController`, `PlayerMagnifyGeometry`, fragment magnification and pan,
  setting, guide copy, More menu label, tests, docs. Focused tests pass.
- 2026-10-29: PR 2 committed (4f4902bae). `assembleDebug test lintDebug` passed
  in a clean worktree at that commit (BUILD SUCCESSFUL, exit 0).
- 2026-10-30: PR 3 on `feat/73-zoom-pan`: `Owner.MAGNIFIED_PAN`, listener pan
  path that re-anchors on finger-count changes, pinch takeover from the pan,
  `PlayerMagnifyGeometry.pan`, guide row, tests, docs. Focused tests pass.
- 2026-10-30: PR 3 committed (dd0e7ffd0). `assembleDebug test lintDebug` passed
  in a clean worktree at that commit: 1,220 tests, 0 failures; lint 0 errors.
  Branches pushed and stacked PRs opened (1 → master, 2 → PR 1, 3 → PR 2).
- 2026-10-30: Review by GPT-6.1 Terra, xhigh (user-requested lane, Codex via T3
  delegate_task; snapshot f55a6b064). Verdict blocking, three findings:
  - R1 major, confirmed: a VoD strip swipe toward the chat-side edge could not
    travel the live trigger, so floating chat could not be hidden on wide
    players. Fixed in PR 1 (18179eb5d): strip swipes act after 24dp, capped at
    80% of the room to the edge; new policy and fragment tests.
  - R2 major, confirmed: a pinch cancelled after PiP entry restored its starting
    magnification, which reappeared after PiP. Fixed in PR 2 (08be165b3):
    `finishPinchZoom` treats PiP like portrait/minimized; the new test fails
    without the fix.
  - R3 minor, confirmed but pre-existing on master: during an owned pinch, a
    third finger plus lifting a tracked finger freezes the pinch until another
    lift. Deferred to a follow-up; not introduced by this stack.
  Fixes merged up the stack (no force-push).
- 2026-10-30: Emulator QA by the agent, not human QA (Medium Phone API 36.1,
  landscape, build d484536e9). Touches went through the emulator console; zoom
  and pan were read from the SurfaceFlinger source crop. Passed: live swipe
  hides and shows side chat; pinch past Fill magnifies at the fingers; drag
  pans 1:1 and clamps at the edges without opening chat; a second finger landing
  and lifting mid-drag does not jump; pan hands over to a pinch; a pinch near
  Fill snaps back; pinching back recenters; minimize, portrait, PiP and PiP
  mid-pinch all clear the magnification; VoD upper and lower swipes change speed
  and seek; strip swipes show and hide side chat and hide floating chat; a
  magnified drag in the seek zone pans without seeking.
  - Found: the gesture guide did not scroll, so a phone in landscape cut off
    rows past the sixth (the VoD swipe row from PR 1, and double tap too once
    PR 3 adds the pan row). Fixed in PR 1 (c85941d46): the rows scroll. Checked
    on build 78040f7a1 in the eight-row Settings guide.
  - One black PiP window after entering PiP mid-pinch did not reproduce over
    four timed captures; left for human QA.

## Decisions

- Decision: live streams accept the chat swipe anywhere; seekable media only
  from a 56dp strip just inside the edge exclusion on the chat side.
  Reason: live has no horizontal gesture to protect, and a strip-only rule
  would push live users toward the Back gesture zone.
  Alternatives considered: strip everywhere (consistent but harder on live);
  live only (leaves VODs without the gesture).
- Decision: swipe toward the chat side's opposite edge shows the sidebar; swipe
  toward the chat side hides sidebar or floating chat. Never cycles modes.
  Reason: swipes are directional; cycling would make the result depend on state.
- Decision: chat swipe defaults on; extended zoom defaults off.
  Reason: horizontal live swipes did nothing before, while magnifying past Fill
  changes what an existing pinch does.
- Decision: past Fill, zoom keeps the content under the fingers; up to Fill it
  stays centered, and returning to Fill recenters.
  Reason: the use case is corner details (minimap); centered magnification
  would push them off-screen.
- Decision: the magnified pan may reach the parts of the video Fill crops.
  Reason: on wide phones Fill crops the top and bottom, where minimaps and
  scoreboards often sit.
- Decision: magnification starts from Fill's saturated resistance (no jump),
  releases within 10% of Fill return to Fill, and the cap is max(4x Fit, 2x Fill).
  Reason: a continuous wall feels like today's Fill, and the detent keeps an
  accidental breakthrough from leaving a 5% magnification behind.
- Decision: magnification is session-only and clears when leaving full-screen
  landscape (minimize, portrait, PiP) or choosing a display mode.
  Reason: a saved off-center magnification would surprise the next stream.
- Decision: while magnified, every one-finger drag (controls hidden, outside
  the edge zone) pans; no feedback pill, haptic or fling. Double tap and taps
  keep their actions, and a second finger can take the pan into a pinch.
  Reason: a magnified picture needs free movement, and brightness, volume,
  seek or speed mid-pan would be surprising. Pinching back to Fill restores
  them.

## Final PR summary draft

Summary:
Tests:
Human QA:
Risks:
