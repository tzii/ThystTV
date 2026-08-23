# Player popup round 8: first-open jump and portrait readability

## Goal

Round-7 device feedback: the Quality menu jumps around when first pressed, and its
text is hard to read on portrait.

## Root-cause analysis

1. First-open jump had two contributing races in `showPlayerPopup`:
   - The host was made visible with the container still at its reset geometry
     (top-left, wrap height); placement only happened in `doOnLayout`, i.e. during
     the first layout pass, and the corrected margins applied on the *next*
     traversal while the 150 ms reveal was already fading in. The panel could be
     drawn starting at the wrong slot and snap to its anchor.
   - The trigger layout listener re-read the *live* trigger rect on every controls
     relayout. On the first press after opening a stream, `setQualityText()`
     (fires when `viewModel.loaded` lands) and periodic viewer-count updates
     change control-bar text widths, moving the Quality trigger while the popup
     was open; the popup followed and visibly jumped.
2. Portrait readability: round 7 tuned secondary text for atmosphere (alpha 150/178,
   11 sp codec sublabels). On the short full-surface portrait sheets that overlay
   bright video this is below comfortable contrast.

## Changes

1. All four popup callers now create and bind their binder *before*
   `showPlayerPopup`, and `showPlayerPopup` calls `positionPlayerPopup`
   synchronously (explicit measure specs; no layout pass needed) before the host
   becomes visible. The first drawn frame is already at the anchored geometry.
   The `doOnLayout` positioning stays as an idempotent safety net.
2. `positionPlayerPopup` caches the first valid trigger rect (`popupAnchorRect`).
   While the cache is empty the trigger is re-read (GONE-controls recovery keeps
   working); once cached, later control-bar reflows can no longer move a visible
   popup. The cache resets on every show/hide.
3. Contrast bumps for panel text that overlays video: `secondaryText` alpha
   150→176 (light) / 178→210 (dark); codec sublabels 11 sp→12 sp with alpha
   178→212 (unselected) and 216→235 (selected).

## Non-goals

- No placement-policy, ownership, dismissal, or lifecycle changes.
- No layout-structure redesign of the sheets.

## Files

- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerFragment.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerPanelTheme.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerQualityPopupBinder.kt`
- `docs/PLAYER.md`

## Risks

- Pre-reveal measurement must match laid-out measurement; both use the same
  explicit specs over fully bound content, and the doOnLayout pass is idempotent.
- A cached anchor can go stale if the controls bar relocates far while a popup
  stays open (e.g. landscape chat toggle); placement still clamps inside the
  surface, and reopening re-anchors. Traded deliberately for stability.
- Contrast bumps affect all four popups in both themes (visual only).

Risk level: medium (player UI, no playback/lifecycle changes)

## Verification

Automated checks:

- [x] `./gradlew assembleDebug`
- [x] `./gradlew test`
- [x] `./gradlew lintDebug`

Human QA required:

- [ ] Quality: press it right after opening a stream (while the player is still
      loading) and again later — the menu must appear in place with no shift as
      the quality label updates; leave it open through a viewer-count refresh.
- [ ] Quality in portrait: sheet covers the video strip, opens without jumping,
      section labels and codec sublabels (H.264/AV1/H.265) readable in light and
      dark themes.
- [ ] Quality/Speed in landscape stay compact anchored cards; Speed slider never
      dismisses; More and Volume placement unchanged.
- [ ] Dismissal matrix still works: blank sheet tap, scrim ring, back, X buttons.
- [ ] Standard player regression: live/VoD, stream switching, minimize/restore,
      close/reopen, PiP/background, gestures, floating chat.

Human QA completed: none.

## Progress log

- 2026-08-22: Created from round-7 device feedback (first-press jump, portrait
  readability).
- 2026-08-22: Implemented pre-reveal positioning, frozen popup anchor, and
  secondary-text contrast bumps. assembleDebug, all unit tests, and lintDebug pass
  (0 errors). Device QA open.
