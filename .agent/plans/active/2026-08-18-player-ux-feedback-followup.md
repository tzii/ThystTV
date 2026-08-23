# Player UX feedback follow-up

## Goal

Apply the four pieces of device feedback as a focused UX pass:

1. Keep a single canonical **Watch live** action on the channel profile and remove the duplicate inline action from live channel search results.
2. Swap the seekable-video horizontal gesture zones so the upper half changes playback speed and the lower half seeks, and update the gesture guide to match.
3. Replace wide-player brightness and device-volume feedback with compact vertical edge pills that show an icon and vertical level only, without labels or numeric text.
4. Make pinch Fit/Fill feedback visually clean, deterministic, and always show its progress bar while the pinch is active.

The screenshots are visual feedback only. Chat messages, usernames, stream content, timestamps, and other incidental details shown in them are not implementation instructions.

## Non-goals

- Do not change stream discovery data, channel-profile information architecture, or playback-session ownership.
- Do not remove the channel profile's **Watch live** action.
- Do not add double-tap seeking; double tap continues to toggle chat.
- Do not change VoD seek sensitivity, playback-speed limits, pinch thresholds, or the persisted Fit/Fill/Stretch modes unless testing proves a defect in those existing behaviors.
- Do not redesign the separate stream-volume overlay opened from player controls.
- Do not change compact/portrait-player feedback beyond any compatibility work required by the shared layout.
- Do not touch the playback backend, updater, release signing, website, or upstream synchronization.

## Current context

- Branch: `codex/player-ux-tablet-live-discovery`, based on commit `14575e7c`, with uncommitted review fixes already present. Those fixes cover playback identity, wide feedback placement, stream-volume mute restoration, and delayed-callback lifecycle safety and must be preserved unless made obsolete by this UX change.
- Search results currently expose a `watchLive` button in `fragment_search_channels_list_item.xml`; the result card itself navigates to the channel profile. The profile exposes its own `watchLive` action in `fragment_channel.xml` and `ChannelPagerFragment.kt`.
- The current seekable-player mapping is upper horizontal = seek and lower horizontal = playback speed in `PlayerGestureListener.kt`. The strings in `strings.xml` teach the same mapping.
- Brightness and device-volume feedback reuse one horizontal layout with icon, horizontal progress, and text. On large surfaces, `PlayerSurfacePolicy` attempts to place that pill at the left or right edge.
- The default feedback resource uses a `FrameLayout` root, while `layout-sw600dp/layout_player_gesture_feedback.xml` uses a `ConstraintLayout` root. `PlayerSurfacePolicy.applyPlacement()` only updates `FrameLayout.LayoutParams`, so tablet resource selection can bypass the intended child placement and width policy.
- Reusing the same feedback views allows visibility, width, minimum-width, progress orientation, and text state from one gesture kind to leak into the next unless every presentation explicitly resets them.
- Pinch feedback currently hides the progress indicator at zero/no-preview and shares the same horizontal presentation as the other gestures. This accounts for the bar sometimes being absent and makes the indicator less directly express the Fit-to-Fill transition.
- Existing pure tests cover gesture education, surface placement, pinch state transitions, and display-mode preview math, but horizontal zone selection and feedback visual state are not directly modeled.

## Files likely involved

- `app/src/main/java/com/github/andreyasadchy/xtra/ui/search/channels/ChannelSearchAdapter.kt`
- `app/src/main/res/layout/fragment_search_channels_list_item.xml`
- `app/src/main/java/com/github/andreyasadchy/xtra/repository/ChannelSearchMapper.kt`
- `app/src/test/java/com/github/andreyasadchy/xtra/repository/ChannelSearchMapperTest.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerGestureListener.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerGestureEducation.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerGestureGuideDialog.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerSurfacePolicy.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerFragment.kt`
- `app/src/main/res/layout/layout_player_gesture_feedback.xml`
- `app/src/main/res/layout-sw600dp/layout_player_gesture_feedback.xml`
- `app/src/main/res/values/strings.xml`
- focused player tests under `app/src/test/java/com/github/andreyasadchy/xtra/ui/player/`

Possible new focused files, only if they make Android-independent behavior testable without broad refactoring:

- `PlayerGestureZonePolicy.kt` and `PlayerGestureZonePolicyTest.kt`
- `PlayerGestureFeedbackState.kt` and `PlayerGestureFeedbackStateTest.kt`

## Risks

- Removing the search CTA could accidentally leave dead layout space, stale recycled-view listeners, or an unused eligibility mapper/test.
- Swapping gesture zones can make the guide disagree with actual playback behavior if code and strings are not changed together.
- A vertical progress indicator may regress tint, RTL edge placement, system-inset spacing, or compact layouts.
- Shared feedback views can retain state when moving between brightness, volume, seek, speed, and pinch.
- Pinch feedback changes can interfere with continuous preview, haptic arming, or final Fit/Fill commit even though those mechanics are not intended to change.
- Player changes require live-device validation for lifecycle, gesture conflicts, and floating chat.

Risk level: medium-high

## Human approval

Required before implementation: yes

Reason: The task changes player gesture muscle memory and wide-player overlay behavior. Per `.agent/PLANS.md` and `docs/PLAYER.md`, implementation should wait for explicit approval of this plan.

## Implementation steps

1. Remove the duplicate search-result playback CTA.
   - Delete `watchLive` from the search-result layout and remove all related binding, visibility, enabled-state, and click-listener code from `ChannelSearchAdapter`.
   - Keep the whole result card, avatar, and name navigating to the channel profile.
   - Keep the profile-level `watchLive` action and its existing live-stream launch behavior unchanged.
   - If `ChannelSearchMapper.canWatchLive()` becomes unused, remove it and its dedicated eligibility tests rather than retaining dead code. Preserve the rest of the live-discovery mapper and its tests.
   - Check live and offline recycled rows for balanced spacing after the trailing button is removed.

2. Make the horizontal gesture mapping explicit and swap it.
   - Change seekable playback so a horizontal drag beginning above `zoneSplit` controls playback speed, while one beginning below `zoneSplit` seeks.
   - Extract only the zone-selection decision into a small pure policy if needed so boundary cases can be unit-tested without instantiating Android gesture classes.
   - Update the gesture-guide strings to **Upper horizontal — Playback speed** and **Lower horizontal — Seek**.
   - Keep live playback omitting both horizontal rows, and keep settings copy qualified for supported non-live media.
   - Increment `PlayerGestureEducationState.GUIDE_VERSION` because the learned mapping materially changes; previously dismissed users should see the corrected guide once.

3. Normalize the feedback layout contract before restyling it.
   - Make the default and `sw600dp` feedback resources use the same root/container relationship and layout-param type, preferably one reusable layout unless a resource override remains genuinely necessary.
   - Ensure `PlayerSurfacePolicy.applyPlacement()` can deterministically place the actual pill at start, end, or center and can reset width/gravity/margins when switching kinds.
   - Add explicit presentation reset behavior for icon, progress visibility/orientation/value, text visibility, padding, minimum width, and container dimensions so a prior gesture cannot contaminate the next one.
   - Preserve inset-aware left/right placement and RTL start/end semantics.

4. Redesign wide-player brightness and volume feedback as vertical edge pills.
   - On large maximized surfaces, show a narrow rounded vertical pill centered on the active edge: brightness at start, device volume at end.
   - Use the existing brightness and mute/volume icons, a vertical determinate level indicator, and no label or numeric text.
   - Keep an accessible content description that includes the control name and current state/value even though visible text is removed.
   - Represent automatic brightness distinctly and accessibly (for example, zero level plus an `Auto brightness` content description) without reintroducing visible text.
   - Retain the existing compact top-centered horizontal feedback on smaller surfaces, because a vertical edge control has insufficient room and the supplied feedback is for the wide/tablet player.
   - Reduce the wide edge-pill width from the current 280dp card treatment to a compact touch-independent visual width; derive dimensions from resources/policy constants rather than hard-coded per-call mutations.

5. Give pinch Fit/Fill its own clean presentation state.
   - Keep feedback top-centered and independent from the edge-pill style.
   - Show aspect-ratio iconography, the current target label (`Fit` or `Fill`), and a determinate horizontal bar for the entire active pinch, including zero/no-preview states.
   - Map the bar monotonically to progress toward the target threshold, clamp it to 0–100, and show a completed state briefly when Fit or Fill commits.
   - Reset the bar and label on restore/cancel so the next gesture cannot inherit stale progress or the wrong target.
   - Keep `Stretch` as a persisted legacy/manual mode and preserve the existing rule that pinching from Stretch targets Fit inward and Fill outward; do not present Stretch as a third point on the Fit/Fill bar.
   - Preserve continuous video preview, arming haptic, mode persistence, hide timing, and cancellation behavior.

6. Review integration and lifecycle behavior.
   - Confirm feedback hide runnables and animations are cancelled during `onDestroyView()` and never dereference a destroyed binding.
   - Confirm the gesture arbiter still allows exactly one owner and that swapping zones does not affect pinch, double-tap chat, controller taps, minimize gestures, or system-edge rejection.
   - Review the complete diff, including the pre-existing uncommitted fixes, and avoid unrelated formatting or cleanup.

7. Build the device-test artifact after implementation approval.
   - Run the required Gradle checks and rebuild `app-debug.apk` only after all source changes are complete.
   - Verify the APK timestamp is newer than the latest edited source/resource, and verify alignment/signature before handoff.

## Verification

Automated checks:

- [x] Unit test: upper seekable zone maps to playback speed, lower zone maps to seek, and the exact `zoneSplit` boundary has a documented result. (`PlayerGestureZonePolicyTest`)
- [x] Unit test: live context still exposes neither horizontal gesture; seekable/settings guide content remains complete. (`PlayerGestureEducationTest`)
- [x] Unit test: guide version causes users with the prior version to see the corrected mapping once. (`PlayerGestureEducationTest`)
- [x] Unit test: compact feedback remains top-centered; large brightness/volume are start/end edge-aligned; Fit/Fill remains top-centered. (`PlayerSurfacePolicyTest`)
- [x] Unit test: feedback presentation resets between edge, centered, and pinch modes, if presentation state is extracted. (`PlayerGestureFeedbackStateTest`; hidden levels are forced to 0)
- [x] Existing `PinchDisplayModeControllerTest` and `PlayerDisplayModePreviewerTest` remain green, with added assertions if the feedback-progress mapping becomes pure logic. (unchanged, green; pinch bar mapping lives in `PlayerGestureFeedbackState.pinchPresentation`)
- [x] `./gradlew assembleDebug`
- [x] `./gradlew test` (289 tests)
- [x] `./gradlew lintDebug`
- [x] Debug APK alignment/signature verification (zipaligned; debug cert verified; APK newer than every source/resource)

Human QA required:

- [ ] Search live channel: no inline **Watch live** button; tapping the result opens the correct profile.
- [ ] Channel profile while live: exactly one **Watch live** action remains and starts the correct stream.
- [ ] Offline search/profile rows remain correctly spaced and do not show a live action.
- [ ] Seekable VoD/video: upper horizontal drag changes speed; lower horizontal drag seeks in both directions.
- [ ] Live stream: horizontal drags do not seek or change speed.
- [ ] Gesture guide teaches the corrected mapping; a user who dismissed version 1 sees version 2 once, then not again after dismissal.
- [ ] Wide/tablet landscape: brightness pill is compact, vertical, start-edge aligned, icon + level only, and respects cutout/system insets.
- [ ] Wide/tablet landscape: device-volume pill is compact, vertical, end-edge aligned, icon + level only, including muted state.
- [ ] RTL layout: brightness/volume follow start/end consistently.
- [ ] Compact landscape or resized window below 600dp: feedback remains readable and does not clip.
- [ ] Pinch inward/outward: bar is visible from gesture start, progresses smoothly, labels the correct target, commits Fit/Fill, and clears after cancel.
- [ ] Switch rapidly among brightness, volume, seek, speed, and pinch; no stale text, orientation, width, progress, or placement carries over.
- [ ] Live playback and VoD playback open and remain stable.
- [ ] Live-to-live switching has no old audio; close/reopen works.
- [ ] Minimize/restore and orientation change preserve one player session and correct display mode.
- [ ] PiP/background behavior remains correct.
- [ ] Speed and quality controls remain readable and functional.
- [ ] Floating chat opens, drags/resizes, remains readable, and does not conflict with gestures.
- [ ] Portrait phone, landscape phone, wide/tablet, and split-screen/resized-window layouts.

Human QA completed:

- [ ] None; implementation has not started and no device QA has been claimed.

## Progress log

- 2026-08-18: Created plan from four annotated device screenshots and inspected the current search, gesture-guide, gesture-feedback, surface-policy, and pinch-feedback implementation.
- 2026-08-18: Identified the default/`sw600dp` feedback parent mismatch and shared-view state leakage as likely contributors to inconsistent wide and Fit/Fill feedback.
- 2026-08-18: Implemented all steps. Removed the search CTA (and the now-unused `ChannelSearchMapper.canWatchLive` plus its tests, superseding that part of the uncommitted review fix); swapped zones via new `PlayerGestureZonePolicy` and bumped `GUIDE_VERSION` to 2; replaced both feedback layouts with one `FrameLayout`-root layout plus a vertical `ClipDrawable` level; reworked `PlayerSurfacePolicy` so a single `presentFeedback` applies placement and every presentation field with full reset; added `PlayerGestureFeedbackState` pure rules; pinch bar always visible with a completed state on commit; gesture-feedback hide runnable is now null-safe and cancelled in `onDestroyView`. Existing volume-overlay review fixes preserved.
- 2026-08-18: `assembleDebug`, `test` (289 green), `lintDebug` pass; APK zipaligned, debug-signed, newer than all sources. Environment note: builds required provisioning Temurin 21 into `~/.gradle/jdks` and running Gradle with that JVM plus `ANDROID_HOME` set; no repo build-file changes were made.

## Decisions

- Decision: Keep the profile **Watch live** action and remove the search-result CTA.
  Reason: The profile is the stable canonical playback entry point, while the entire search card already opens that profile; this removes duplication without removing access to playback.
  Alternatives considered: keeping direct playback only in search would make profile behavior less discoverable; keeping both contradicts the feedback.
- Decision: Treat the requested seek/speed swap as a behavior change, not copy-only.
  Reason: The feedback explicitly prefers seek at the bottom and playback speed at the top, and the guide must describe actual gesture behavior.
  Alternatives considered: changing only the guide would create a functional mismatch.
- Decision: Limit the vertical icon-only edge pill to large/wide surfaces and retain compact feedback on smaller surfaces.
  Reason: The supplied issue is from the wide player, and a narrow phone player needs a different spatial treatment.
  Alternatives considered: applying the vertical pill universally risks clipping and system-gesture conflicts on compact surfaces.
- Decision: Normalize the layout contract and reset all presentation state before styling individual feedback kinds.
  Reason: Parent-type differences and stateful shared views make piecemeal visual fixes unreliable.
  Alternatives considered: adding more one-off property mutations in `PlayerGestureListener` and `PlayerFragment` would preserve the underlying leakage.
- Decision: Keep one shared overlay host but define distinct edge and pinch presentations.
  Reason: It avoids overlapping feedback surfaces while allowing the indicator geometry to match each gesture.
  Alternatives considered: separate simultaneous overlay roots add lifecycle and z-order complexity without user benefit.

## Final PR summary draft

Summary: Remove the duplicate search Watch Live CTA, swap seekable speed/seek gesture zones, replace wide brightness/volume cards with vertical icon-level pills, and make Fit/Fill pinch feedback deterministic and consistently visible.
Tests: `assembleDebug`, `test` (289 green, new `PlayerGestureZonePolicyTest` and `PlayerGestureFeedbackStateTest`, updated `PlayerSurfacePolicyTest`/`PlayerGestureEducationTest`, trimmed obsolete `canWatchLive` tests), `lintDebug` pass; APK zipaligned and debug-signed.
Human QA: Required for search/profile navigation, live/VoD gestures, tablet/RTL/compact layouts, pinch feedback, player lifecycle, controls, and floating chat; not yet completed.
Risks: Gesture muscle-memory change and shared player-overlay state require careful device validation; playback-session behavior is intentionally unchanged.
