# Player popup round 7: dismissal fixes and visual refresh

## Goal

Round-6 device feedback: panels are readable, but (1) tapping "outside" no longer
collapses the menus and (2) the popup design looks dated.

## Root cause for dismissal

In full-surface sheets the panel covers nearly the whole player surface, so almost no
tap lands on the host's outside-dismiss area; taps on blank sheet areas are consumed by
the scroll viewport. Anchored cards keep working; full-surface sheets need their own
dismiss affordances.

## Changes

1. Full-surface sheets dismiss on blank-area taps: the shared viewport gets a click
   listener that closes the popup when in fullSurface mode. Children (chips, slider,
   rows) still consume their own touches; anchored cards keep consuming blank taps.
2. Add a scrim View behind full-surface sheets (host layout, behind panel) so modality
   is visible; it is not clickable, so taps pass through to the host's outside-dismiss.
3. Explicit close buttons: Quality, Speed, and More headers get a trailing X icon wired
   to the existing dismiss path. Back/outside behavior unchanged.
4. Visual refresh via `PlayerPanelTheme`: selected chips render as solid primary pills
   with luminance-matched text (`onSelected`), panels get 24dp corners and lighter
   elevation, section labels get letter spacing.

## Non-goals

- No changes to popup ownership, placement policy, or content models.
- No blur, drag handles, or bottom-sheet migration.
- Volume keeps auto-dismiss (no close button).

## Files

- `app/src/main/res/layout/layout_player_popup_host.xml`
- `app/src/main/res/layout/layout_player_quality_popup.xml`
- `app/src/main/res/layout/layout_player_speed_popup.xml`
- `app/src/main/res/layout/layout_player_more_popup.xml`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerFragment.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerPanelTheme.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerQualityPopupBinder.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerSpeedPopupBinder.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerMorePopupBinder.kt`
- `docs/PLAYER.md`

## Risks

- Viewport click-to-dismiss must not fire during scroll flings or steal child touches;
  Android click dispatch only fires when children don't consume and no scroll occurred.
- Scrim must never intercept touches or trap accessibility focus.
- Selected-state text color must stay readable in light theme and with any primary hue.

Risk level: low-medium

## Verification

Automated checks:

- [x] Focused popup policy tests
- [x] `./gradlew assembleDebug`
- [x] `./gradlew test`
- [x] `./gradlew lintDebug`

Human QA required:

- [ ] Full-surface sheet: tap blank area, tap scrim ring, back button, and X button all
      close the menu; selecting an option still applies and closes.
- [ ] Anchored cards (landscape): outside tap closes; blank card area does NOT close.
- [ ] Speed sheet: dragging the slider never triggers dismiss.
- [ ] Light + dark theme readability of selected/unselected chips.
- [ ] Standard player regression: live/VoD, switching, minimize/restore, PiP, gestures,
      floating chat, volume popup.

Human QA completed: none.

## Progress log

- 2026-08-22: Created from round-6 device feedback.
- 2026-08-22: Implemented scrim (non-clickable, full-surface only), viewport
  blank-tap dismissal for sheets, Quality/Speed/More header close buttons, solid
  primary selection pills with `onSelected` content color, and 24dp/4dp panel
  treatment across all four popups. Added `close` string to all locales.
- 2026-08-22: Verification passed: focused popup policy tests, full `test` +
  `assembleDebug`, and `lintDebug` (0 errors). No device attached; human QA open.
