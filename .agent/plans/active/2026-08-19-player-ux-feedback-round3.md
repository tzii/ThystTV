# Player UX feedback follow-up, round 3: pinch polish, pill stability, volume panel uniformity

## Goal

Apply three pieces of device feedback on top of the round-2 fixes:

1. Pinch Fit/Fill feedback and motion feel: make the pill bar directional
   (Fill grows it, Fit shrinks it) and remove the release "snap" of the video
   preview so the whole pinch interaction feels smooth.
2. Stop the gesture-feedback pill from visibly floating/teleporting across the
   screen when different gestures are performed quickly in succession.
3. Restyle the stream-volume popup (opened from the player volume button) to
   match the quality and speed panels so all three player popups look and
   behave like one family.

## Non-goals

- Do not change pinch arm/commit thresholds, haptics, mode persistence, or the
  Fit/Fill/Stretch semantics (`PinchDisplayModeController` logic stays).
- Do not change gesture mappings or feedback placement policy
  (compact top-centered, large edge pills, pinch centered).
- Do not redesign the volume button itself in the player control bar, only the
  popup it opens.
- Do not touch the playback backend, updater, release flow, or website.

## Current context

- Branch: `codex/player-ux-tablet-live-discovery` with rounds 1-2 uncommitted.
- Pinch bar: `PlayerGestureFeedbackState.pinchPresentation` maps
  progress-toward-arm onto 0-100 in both directions, so pinching toward Fit
  also grows the bar - direction is only visible via the label.
- Pinch video preview: `applyPinchPreview` interpolates a uniform view scale
  continuously during the gesture (API 24+). Commit is geometrically seamless,
  but Restore/Cancelled snap `scaleX/scaleY` back to 1 instantly, producing a
  visible jump when a pinch is released below the arm threshold.
- Pill switching: the shared feedback view keeps
  `android:animateLayoutChanges` on its container, and
  `PlayerSurfacePolicy.presentFeedback` repositions the still-visible root by
  swapping gravity/margins/orientation between gesture kinds. Rapid mixing
  (e.g. device volume at the right edge, then seek at top center) therefore
  shows LayoutTransition drift plus an instant mid-fade jump.
- Volume popup: `layout_player_volume_overlay.xml` is a black 24dp-radius card
  with 4dp elevation, anchored above the volume button via screen-coordinate
  translation math. The quality/speed panels are theme-colored (surface
  container), 18dp radius, no elevation, drag handle, centered 22sp bold
  title, `PlayerDialogSizing` width/gravity policy, and outside-tap dismissal.

## Files likely involved

- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerGestureFeedbackState.kt` (+ test)
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerFragment.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerSurfacePolicy.kt` (+ test)
- `app/src/main/res/layout/layout_player_gesture_feedback.xml`
- `app/src/main/res/layout/layout_player_volume_overlay.xml`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerQualityDialog.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerSpeedDialog.kt`
- possible new `PlayerPanelTheme.kt` (shared panel color resolution)

## Risks

- Animating the preview settle must not fight a new pinch that starts during
  the settle, or leave a half-scale surface behind on minimize/restore.
- Hiding the pill before repositioning must not introduce flicker on
  consecutive same-kind updates (the common case must stay zero-flicker).
- Volume popup repositioning replaces the anchored-at-button placement; the
  popup must remain reachable and dismissible on compact and wide layouts.
- Player overlay changes need device revalidation for gesture conflicts and
  floating chat.

Risk level: medium

## Human approval

Required before implementation: yes

Reason: The user asked for a plan first. The changes alter pinch feedback
semantics and player overlay motion, which are high-risk surfaces per
`docs/PLAYER.md`.

## Implementation steps

1. Directional pinch bar.
   - Add pure mapping in `PlayerGestureFeedbackState`: neutral level 50;
     toward FILL maps 50 -> 100 at the arm threshold; toward FIT maps
     50 -> 0. Pass the pinch target into the presentation.
   - Commit FILL briefly shows the full bar (existing completed state);
     commit FIT shows the empty bar with the Fit label during the linger.
   - Keep the bar visible from gesture start (round-1 rule unchanged).
2. Mode-aware pinch settle with centralized cleanup.
   - The settle target depends on the committed mode because the preview
     anchors at mode-dependent scales (`PlayerDisplayModePreviewer`):
     - Committed Fit: animate scale back to (1, 1) with Fit rendering, which
       is already canonical Fit.
     - Committed Fill: animate scale to (fillRatio, fillRatio) with Fit
       rendering, then switch to canonical Fill (resize mode Fill, scale 1);
       the switch is geometrically seamless by the previewer's invariant.
     - Committed Stretch (documented fallback): snap to canonical Stretch via
       the central cleanup. Stretch is not on the uniform Fit/Fill continuum,
       and independent X/Y settle geometry adds risk for a legacy mode.
     - API 23 and below (documented fallback): no scale animation existed
       (stepped previews only), so releases snap via the central cleanup.
   - Add one central `finalize` in `PlayerFragment` that cancels any settle
     animator and applies the committed display mode's canonical resize mode
     and scale (1, 1). Mere animator cancellation is not sufficient - a
     cancelled settle must leave a canonical surface. Invoke it from: new
     pinch start, `selectDisplayMode`, minimize/restore and orientation/PiP
     mode changes, and `onDestroyView`. `NoPreview` during a live gesture also
     routes through the central cleanup (seamless, since the preview already
     sits at the committed anchor geometry).
   - Commit stays instantaneous (scale at arm equals the target geometry, so
     applying the mode is seamless); only neutral releases animate.
   - Optional stretch (only if approved separately): YouTube-style edge glow -
     thin gradient overlays at the player edges whose alpha follows pinch
     progress toward Fill. Not included by default.
3. Kill pill teleport/floating.
   - Remove `animateLayoutChanges` from the feedback container.
   - Store the last applied placement on the feedback view itself (tag keyed
     by an id resource), never as global state in `PlayerSurfacePolicy`, so
     multiple player instances cannot leak placements to each other.
   - In `presentFeedback`, when the incoming placement differs from the last
     applied one while the pill is visible: cancel the fade animation, remove
     the pending hide callback, hide instantly (no fade), apply the new
     geometry, then show - the jump happens while invisible. Consecutive
     updates with an unchanged placement only refresh content in place.
   - Extract the reset decision as a small pure function with unit tests
     covering: same placement (no reset), placement changed while visible
     (reset), changed while hidden (no reset needed), and placements that
     differ only because insets changed (reset).
4. Uniform volume popup (embedded view, not a dialog).
   - Keep the volume popup embedded in the player layout so its auto-dismiss
     timer, player lifecycle, and rotation behavior stay as they are; only
     its appearance, geometry, and dismissal parity change.
   - Rebuild `layout_player_volume_overlay.xml` with the panel chrome used by
     quality/speed: 18dp radius, 0 elevation, 1dp theme-tinted stroke, drag
     handle, centered bold title ("Stream volume"), then a row with the mute
     round-control, slider, and percent.
   - Extract a shared `PlayerPanelTheme` resolver covering panel, onPanel,
     secondary text, stroke, handle, primary, control fill, selected fill,
     and inactive slider color - the full set the panels actually use - so
     the volume slider and controls match the speed panel exactly, and
     refactor the quality/speed dialogs onto it.
   - Placement parity within the player surface: width from
     `PlayerDialogSizing.panelWidthPx(binding.playerLayout.width, ...)`,
     gravity CENTER (large) / BOTTOM|CENTER_HORIZONTAL (compact) relative to
     the player layout, honoring bottom/start/end system gesture insets.
     Replaces the anchor-above-button screen-coordinate translation.
   - Behavior parity: keep auto-dismiss, mute, and remember-last-volume
     logic; toggling the player controls (video tap or control press)
     dismisses the popup, matching the dialogs' outside-tap dismissal.
5. Complete the pass.
   - Update `docs/GESTURE_SYSTEM.md` for the directional bar and the
     centralized settle/cleanup ownership in `PlayerFragment`.
   - Capture before/after screenshots (phone and wide profiles) of the
     gesture pills and volume panel where the emulator allows; otherwise
     request them as human-QA artifacts.
   - Checkpoint rounds 1-2 as their own commit before round-3 edits so
     regression attribution stays possible.
6. Rebuild, run checks, update plan and QA list.

## Verification

Automated checks:

- [ ] Unit test: Fill grows the pinch bar from 50 to 100 and Fit shrinks it
      from 50 to 0 across the arm range, including clamps, neutral states,
      and commit levels.
- [ ] Unit test: same-placement updates do not reset; placement changes while
      visible reset; changes while hidden do not; insets-only changes count
      as placement changes.
- [ ] Existing pinch/previewer/surface-policy/feedback-state tests stay green.
- [ ] `./gradlew assembleDebug`
- [ ] `./gradlew test`
- [ ] `./gradlew lintDebug`
- [ ] Debug APK alignment/signature verification

Human QA required:

- [ ] Pinch outward (Fill): bar starts half-full, grows with the pinch,
      reaches full at commit; video preview settles without a snap.
- [ ] Pinch inward (Fit): bar starts half-full, shrinks, empties at commit.
- [ ] Release below the arm threshold: Fit-origin eases back to (1, 1);
      Fill-origin eases to Fill scale then becomes canonical Fill; Stretch
      and API-23 fallbacks snap cleanly; no half-scaled video on quick
      re-pinch, minimize/restore, rotation, or PiP.
- [ ] Rapidly alternate volume (right edge), brightness (left edge), seek,
      speed, and pinch: each pill appears in place, no visible slide or
      teleport between positions, no flicker on consecutive same-kind updates,
      including switching mid-fade and after inset changes.
- [ ] Compact and wide: pinch pill stays top-centered; edge pills stay
      start/end aligned; RTL included.
- [ ] Volume popup: same look as quality/speed (handle, title, colors,
      radius), centered (wide) / bottom-centered (compact) within the player
      surface with insets honored, slider + mute + percent still work,
      persists while dragging, auto-dismisses, and dismisses on video tap or
      controls toggle.
- [ ] Quality/speed popups unchanged after the shared-theme extraction
      (light and dark themes); slider theming stays readable in both.
- [ ] Quality and speed interaction: opening them during/after a pinch
      settle behaves; selections apply normally.
- [ ] Orientation change, close/reopen, and live-to-live switching leave no
      stale pill, popup, or half-transformed video.
- [ ] Split-screen / resized-window layouts keep pills and panels placed
      correctly (large <-> compact transitions included).
- [ ] PiP/background behavior remains correct.
- [ ] Floating chat does not conflict with the new volume popup placement.
- [ ] Live/VoD playback spot-check.

Human QA completed:

- [ ] None yet.

## Progress log

- 2026-08-19: Created plan from round-3 device feedback; inspected the pinch
  presentation mapping, preview scale lifecycle, feedback view placement
  switching (LayoutTransition + mid-fade repositioning), and the volume
  overlay's layout/anchoring versus the dialog panels.

## Decisions

- Decision: represent the pinch bar as a bidirectional zoom amount (50 at
  neutral, 100 armed toward Fill, 0 armed toward Fit).
  Reason: the user's feedback asks the bar itself to express direction, and a
  center-anchored level reads as "how far into Fill / back to Fit" without
  needing the label.
  Alternatives considered: keeping progress-toward-threshold with a direction
  icon leaves the bar non-directional; a reversed bar for Fit only (100 -> 0)
  loses the shared neutral origin.
- Decision: fix teleporting by hiding-before-repositioning plus removing
  LayoutTransition, not by animating position; the last placement is stored
  per view (id-keyed tag) so `PlayerSurfacePolicy` stays stateless.
  Reason: the pill is a transient indicator; cross-surface position
  animation would look floaty and add latency, while an invisible swap reads
  as a new indicator appearing in place. Per-view storage prevents placement
  leakage between player instances.
  Alternatives considered: animating gravity changes is not supported
  directly and translate-based animation complicates the shared-view reset
  contract.
- Decision: animate only neutral-release settles, with mode-aware targets;
  commit stays instant, and a single central cleanup owns canonical geometry.
  Reason: commit is already geometrically seamless (scale at arm equals the
  target geometry), while neutral releases snap today. The settle target
  depends on the committed mode (Fit -> (1,1); Fill -> fillRatio then
  canonical switch), so cleanup must be centralized and must finalize a
  canonical resize mode/scale on cancellation to prevent partially
  transformed surfaces after quick re-pinches, mode changes,
  minimize/restore, rotation, PiP, or view destruction. Stretch and API 23
  snap via the central cleanup as documented fallbacks.
  Alternatives considered: animating to (1,1) unconditionally is wrong for
  Fill-origin previews; independent X/Y settle for Stretch adds risk for a
  legacy manual mode.
- Decision: keep the volume popup embedded in the player layout, with dialog
  parity in appearance, player-surface-relative geometry (width from the
  player layout width, insets honored), and dismissal.
  Reason: preserves the existing auto-dismiss and player-lifecycle behavior;
  true window-level parity would require a real dialog and change portrait
  behavior.
  Alternatives considered: converting to a DialogFragment matches placement
  exactly but re-opens lifecycle work for marginal gain.
- Decision: `PlayerPanelTheme` resolves the full panel palette (panel,
  onPanel, secondary text, stroke, handle, primary, control fill, selected
  fill, inactive slider).
  Reason: matching the speed panel's slider and controls requires the same
  inputs the dialogs use, not just surface colors; one shared resolver keeps
  all three surfaces identical and fixes latent light-theme contrast issues.
  Alternatives considered: copying the speed panel's hardcoded whites would
  keep broken light-theme rendering.

## Final PR summary draft

Summary: Directional pinch bar with a smooth release settle, teleport-free
gesture feedback pill, and a volume popup rebuilt to match the quality/speed
panel family.
Tests: Pending Gradle checks after implementation approval.
Human QA: Required for pinch direction/settle, rapid gesture switching, and
the volume popup's new look, placement, and dismissal.
Risks: Player overlay motion changes need device validation; pinch and
display-mode logic is intentionally untouched beyond presentation.
