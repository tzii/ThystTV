# Player UX round 5: cohesive fullscreen popup polish

## Goal

Redesign the existing fullscreen Quality, Stream volume, Playback speed, and More
popups into one coherent, compact player-owned visual system while preserving the four
dedicated control buttons and each popup's existing content model. Quality and Speed
keep their pill controls; Volume keeps slider/mute controls; More keeps grouped rows.

## Non-goals

- Do not combine Quality, Volume, Speed, and More behind one trigger.
- Do not replace Quality/Speed pills with radio rows.
- Do not remove the existing speed slider, step controls, presets, or quality modes.
- Do not change quality, volume, speed, display-mode, or More-action semantics.
- Do not add a bottom sheet, drag behavior, decorative grab handle, heavy scrim, blur,
  Compose migration, playback backend work, or new More actions.
- Do not change pinch behavior in this round; round 4.1 is a separate preceding repair.

## Current context

- Branch: `codex/player-ux-tablet-live-discovery`; round 4 is committed at `a91f7995`.
  Round-4.2 repair is currently uncommitted and has green `assembleDebug`, 310 unit
  tests, and `lintDebug`; the user accepted its gesture behavior on device.
- Quality is `PlayerQualityDialog`, a full-window `DialogFragment` containing a width-
  constrained card and dynamically built pill rows.
- Speed is `PlayerSpeedDialog`, another full-window `DialogFragment` containing the
  slider, step buttons, and dynamic preset pills.
- Stream volume is already embedded in `fragment_player.xml`, but separately positioned
  and auto-dismissed by `PlayerFragment`.
- More is `PlayerSettingsDialog`, a `BottomSheetDialogFragment` with grouped rows and
  player callbacks. It is the only surface that actually uses sheet infrastructure.
- Quality/Speed/Volume share `PlayerPanelTheme`; Quality/Speed/More use
  `PlayerDialogSizing`, but ownership, placement, dismissal, motion, and lifecycle differ.
- Device screenshots show codec names appended directly to quality labels, producing an
  inconsistent mix of one- and two-line pills (`480p H.264` vs `720p60` + `H.264`). They
  also show the Audio/Chat section below the video-quality scroll fold, so Audio-only is
  not available without scrolling.
- All modal player fragments currently share the tag `closeOnPip`; stacking/replacement
  behavior is implicit rather than a dedicated popup contract.

## Files likely involved

- `app/src/main/java/.../ui/player/PlayerFragment.kt`
- `app/src/main/java/.../ui/player/PlayerPopupPolicy.kt` (new pure policy)
- `app/src/main/java/.../ui/player/PlayerPopupType.kt` (new, if useful)
- `app/src/main/java/.../ui/player/PlayerPanelTheme.kt`
- `app/src/main/res/layout/fragment_player.xml`
- `app/src/main/res/layout/layout_player_popup_host.xml` (new)
- existing Quality, Speed, Volume, and More layouts/classes during migration
- `app/src/main/res/values/dimens.xml` and player strings if needed
- matching unit tests and `docs/PLAYER.md`

## Risks

- Migrating dialog-owned callbacks into the player view can regress quality/speed state,
  More action visibility, or Fragment lifecycle cleanup.
- The popup host must consume touches without leaking them into `dragView`, but blank-area
  taps must dismiss it and restore normal player gestures.
- Player controls must remain visible while a popup is open without permanently disabling
  auto-hide after dismissal, rotation, minimize, PiP, or teardown.
- Compact landscape heights need scrolling without shrinking touch targets; wide/tablet
  layouts need bounded widths without drifting to screen center.
- More currently owns many conditional actions; migration must preserve every visibility,
  label, and callback branch exactly.

Risk level: high

## Human approval

Required before implementation: yes

Reason: This is a broad player-overlay migration. Approval is supplied by the user's
explicit request to proceed to round 5 after the gesture repair and by the recovered
round-5 design specifying the dedicated controls and preserved pill UI.

## Decisions (six contracts fixed before implementation)

1. **Embedded vs dialogs:** use one embedded, player-owned popup host for all four
   surfaces. Do not retain a mixed DialogFragment/BottomSheet/embedded architecture.
   This is required for player-relative anchoring, shared motion, one-popup ownership,
   control-auto-hide coordination, and teardown with the player view.
2. **Dedicated controls:** Quality, Volume, Speed, and More buttons remain independent.
   They select content in the shared host; opening one replaces any currently visible
   popup without stacking or routing through More.
3. **Placement:** anchor toward the triggering control when its bounds are valid; use a
   deterministic player-relative edge fallback otherwise. Width is bounded (roughly
   320–336dp compact and 352–384dp wide) and all placement respects player bounds,
   system bars, cutouts, and resized windows.
4. **Interaction ownership:** the host consumes touches over its panel; blank host area
   dismisses. While visible, player controls stay visible and auto-hide is suspended.
   Dismissal restores the normal timer and focus to the trigger. Underlying gestures and
   buttons cannot activate through the host.
5. **Semantics:** preserve current apply/dismiss behavior for this presentation round.
   Quality/preset-speed selection continues to apply through the existing paths and may
   close as it does today; slider changes remain live and save on release. Volume keeps
   stream-volume state distinct from device volume. More ordering/actions remain intact.
6. **Lifecycle/accessibility:** close the host on minimize, fullscreen exit, PiP,
   configuration/view teardown, and stream replacement; never restore stale popup content.
   Keep approximately 48dp touch targets, selected semantics, D-pad focus order, visible
   focus/ripple states, no decorative handle semantics, and predictable trigger focus
   restoration.

## Implementation steps

1. Add a pure `PlayerPopupPolicy` for content type, bounded width, edge/anchor placement,
   compact-height limits, and one-popup transition rules. Unit-test boundaries, RTL,
   insets, and resized surfaces.
2. Add one host overlay to `fragment_player.xml`: transparent touch-owning dismissal area
   plus a dark elevated Material card. Remove the decorative gray handles and centralize
   corner radius, stroke/elevation, header typography, padding, scroll bounds, and short
   alpha/scale motion.
3. Add `PlayerFragment` popup ownership: current type, trigger reference, show/replace/
   hide, focus restoration, controls auto-hide suspension/restart, and lifecycle cleanup.
4. Migrate Speed first while preserving slider, step buttons, presets, selected state,
   preference save timing, and player updates. Reuse one shared pill factory/style.
5. Migrate Quality onto the same pill family while preserving filtering, Audio-only/
   Chat-only canonicalization, dynamic wrapping/scrolling, labels, and selection callback.
   Standardize codec presentation as secondary metadata with one stable chip geometry;
   never concatenate it inconsistently into the primary resolution line. Keep the
   Audio/Chat section outside the video-quality scroll region as an always-visible footer.
6. Migrate Stream volume into the shared host while preserving mute restore state, slider
   value, stream-vs-device distinction, and current auto-dismiss semantics where useful.
7. Migrate More last. Preserve every existing preference gate, group header rule, dynamic
   label/value, and callback. Display-mode selection may use an embedded nested selection
   view or the existing alert only if the ownership transition is documented and tested.
8. Remove obsolete dialog/sheet-only sizing, handle resources, classes, bindings, and
   tests only after all call sites are migrated and grep confirms no consumers.
9. Update `docs/PLAYER.md`, this plan, and the manual QA handoff. Run required checks and
   produce a fresh debug APK.

## Verification

Automated checks:

- [x] `./gradlew assembleDebug`
- [x] `./gradlew test`
- [x] `./gradlew lintDebug`

Human QA required:

- [ ] Quality: dedicated trigger, pill wrapping/selection, Auto/manual and Audio/Chat
      modes, standardized codec metadata, always-visible Audio/Chat footer, outside
      tap/back, compact landscape and tablet/wide placement.
- [ ] Speed: dedicated trigger, preset pills, slider drag/save, +/- steps, current value,
      outside tap/back, VoD playback continues.
- [ ] Volume: dedicated trigger, stream slider/mute restore, device-volume gestures remain
      separate, timeout/outside dismissal, compact and wide placement.
- [ ] More: every enabled action and group remains present and ordered; dynamic quality,
      speed, bookmark, subtitles, chat, and display-mode values/actions are correct.
- [ ] One popup at a time; rapidly switch Quality → Volume → Speed → More without stacking,
      stale content, teleports, or click-through.
- [ ] Controls remain visible while a popup is open and resume normal auto-hide afterward.
- [ ] Live and VoD playback, stream switching, minimize/restore, close/reopen, orientation,
      split-screen/resizing, PiP/background, gestures, and floating chat.
- [ ] D-pad/keyboard and TalkBack: focus order, selected state, dismiss/focus restoration,
      touch targets, and no fake drag-handle semantics.

Human QA completed: none.

## Progress log

- 2026-08-20: Recovered the approved round-5 direction from the earlier “Polish Playback
  Gestures” task and inventoried current ownership. Formalized the six architecture/
  interaction contracts above; selected one embedded player-owned host while preserving
  the four dedicated buttons and Quality/Speed pill UI.
- 2026-08-20: Added screenshot-derived Quality requirements: codec metadata must use a
  consistent secondary treatment and Audio/Chat actions must remain visible without
  scrolling the video-quality grid.
- 2026-08-21: Implemented one embedded, player-owned host and migrated Quality, Speed,
  Stream volume, and More. Added trigger-relative/inset-aware placement, shared motion,
  focus restoration, controls-auto-hide coordination, and lifecycle cleanup. Quality now
  uses stable two-line mixed-codec chips and a fixed Audio/Chat footer; More preserves the
  existing display-mode alert as the documented nested chooser. Removed the obsolete
  popup DialogFragments, More bottom sheet, and dialog-only sizing policy after confirming
  there were no remaining consumers. Final `assembleDebug`, all 316 unit tests, and
  `lintDebug` pass; human QA is still pending.

## Final PR summary draft

Summary: Replace the player's mixed Quality/Speed dialogs, embedded Volume overlay, and
More bottom sheet with one cohesive player-owned popup host. Preserve all dedicated
buttons and existing content semantics while unifying visual treatment, anchoring,
dismissal, motion, lifecycle, accessibility, and controls-auto-hide behavior.
Tests: `assembleDebug`, 316 unit tests, and `lintDebug` pass.
Human QA: required for all four surfaces on compact/wide layouts plus player lifecycle,
gesture, floating-chat, accessibility, and one-popup-at-a-time regression coverage.
Risks: broad player-overlay migration, especially More's conditional actions and
auto-hide/gesture ownership.
