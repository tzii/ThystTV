# Gesture feedback styles and swipe distance

## Goal

Replace the player's gesture feedback with two clean, Material 3 Expressive
styles, chosen in settings, and make the seek swipe cover the same time for the
same finger travel on every VoD.

- **Sliders (default):** seek, playback speed and pinch zoom show a horizontal
  Expressive slider (Medium: 40 dp track, 4 dp handle, 6 dp gaps, 24 dp inset
  icon) at the top of the video. The only text is the value inside the track; the
  icon and value move across the handle when their side runs short. A dot marks
  where the gesture started. Brightness and volume show a vertical Expressive
  slider at the finger's edge with no number.
- **Number in the center (option):** every gesture shows one number in the
  center of the video on a soft shadow, no box. Seek adds the target time in
  small type underneath; brightness and volume add their icon above the number.
- Thumbnails stay on the controls' seek bar only. A swipe seek keeps seeking
  live with no thumbnail.
- Seek swipe distance no longer scales with VoD length: a fixed accelerating
  curve (fine near the start, about an hour across a landscape phone), never
  more than the whole video across the full player width.

## Non-goals

- No change to which gestures exist, their zones, the start detent, the
  horizontal lock, haptic kinds, pinch behaviour or the controls' seek bar.
- No change to the message pill (hints, "caught up").
- No player lifecycle, Media3 or seek-bar preview changes.

## Current context

Research and the approved mock: https://claude.ai/artifact/2S8BHYMXVbEEnVySrAGe3t
(two earlier designs, a wide top card and a centered scrub card, were rejected
on 2026-11-20). This builds on the uncommitted gesture work in this worktree
(haptics, spring entrance, scrub policy, edge margins).

## Files likely involved

- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerGestureFeedbackState.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerSurfacePolicy.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerGestureSliderView.kt` (new)
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerGestureValueView.kt` (new)
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerGestureLevelView.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerGestureScrubView.kt` (removed)
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerGestureListener.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerScrubPolicy.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerFragment.kt` (pinch feedback only)
- `app/src/main/res/layout/layout_player_gesture_feedback.xml`
- `app/src/main/res/xml/player_preferences.xml`, `values/strings.xml`, `values/arrays.xml`
- Tests under `app/src/test/java/com/github/andreyasadchy/xtra/ui/player/`
- `docs/GESTURE_SYSTEM.md`, `docs/PLAYER.md`, `docs/MANUAL_QA.md`, `CHANGELOG.md`

## Risks

- UI risk: feedback over bright video, cutouts, split-screen and short players.
- Gesture feel: the new seek distance changes how far a swipe goes.
- No lifecycle risk: views and policies only, one overlay per player as before.

Risk level: medium

## Human approval

Required before implementation: no

Reason: the user chose the direction and the three open decisions on
2026-11-20 (sliders on the finger's side, keep the target time under a
centered seek, pick the best swipe distance).

## Implementation steps

1. Presentation model: a style, a gesture reading (position, start, value,
   detail) and the top-slider and center-value layouts.
2. Views: horizontal slider, vertical slider rebuilt to match, center value.
3. Placement and presentation in `PlayerSurfacePolicy`; layout XML.
4. Listener and pinch feed readings; setting `player_gesture_feedback_style`.
5. Seek distance curve and haptic steps that follow it.
6. Tests, rendered previews, docs.

## Verification

Automated checks:

- [x] `./gradlew assembleDebug`
- [x] `./gradlew test`
- [x] `./gradlew lintDebug`

Human QA required:

- [ ] VoD: swipe-seek in the bottom half both ways, small and large drags, back
      to the start dot; both styles; spoiler-free VoDs on and off.
- [ ] VoD: speed swipe in the top half, 0.25x to 4x; both styles.
- [ ] Live and VoD: brightness (left) and volume (right), including Auto
      brightness and mute; both styles.
- [ ] Pinch Fit/Fill and free zoom; both styles.
- [ ] Controls' seek bar still shows storyboard thumbnails; swipe seek does not.
- [ ] Bright and dark video, light/dark/AMOLED app themes, cutout side,
      side chat open, split screen, tablet.
- [ ] Haptics on: ticks feel even across a long seek drag.
- [ ] Minimize/restore and close/reopen during and after a gesture.

Human QA completed:

- [ ] none yet

## Progress log

- 2026-11-20: Created plan after the user approved the direction.
- 2026-11-20: Implemented steps 1–6. Added `PlayerGestureSliderView` and
  `PlayerGestureValueView`, rebuilt `PlayerGestureLevelView` and removed
  `PlayerGestureScrubView` and `PlayerGestureHelper.calculateResponsiveSeekPosition`.
  Also added the setting, the seek curve with pace-following haptic steps, and
  a log-scale speed track. Raised the number's glow to 60% black at its center,
  so white text keeps 4.5:1 over white video. Player unit and render tests pass,
  and the rendered previews were checked by eye. Docs updated.

## Decisions

- Decision: Seek slider spans the whole VoD with a dot at the start position;
  spoiler-free VoDs keep a relative track centered on the start.
  Reason: matches the seek bar's meaning; the relative track never reveals
  length or position.
  Alternatives considered: relative track for every VoD (the rejected card).
- Decision: Seek distance = sensitivity x min(0.3 s/dp + 10 s x (dp/100)^3,
  whole video per player width).
  Reason: precise small seeks (20 dp is about 6 s instead of 3.5 min on a 6 h
  VoD), about an hour across a landscape phone, and short videos never waste
  travel.
  Alternatives considered: share of VoD length (today), fixed time per dp.

- 2026-11-20: `test assembleDebug lintDebug` passed with JDK 21 (1503 unit
  tests, 0 failures). Lint reports no new issues in changed files.

## Final PR summary draft

Summary: Two Material 3 Expressive gesture indicator styles. Sliders (default):
a vertical slider at the swiped edge for brightness and volume, and a top
slider for seek, speed and pinch, with a start dot and a "Cancel" state.
Number in the center: one clean centered number for every gesture. The seek
swipe uses a fixed accelerating distance curve with pace-following haptic
steps.
Tests: `./gradlew test assembleDebug lintDebug` (JDK 21); rendered previews in
`app/build/ui-previews` checked by eye.
Human QA: see Verification above and docs/MANUAL_QA.md "Expressive gesture
feedback".
Risks: seek swipe feel changes on every VoD; feedback over bright video,
cutouts and split screen needs device checks; the seek curve could ship as its
own commit.
