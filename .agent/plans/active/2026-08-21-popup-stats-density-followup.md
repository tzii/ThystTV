# Popup placement and stats density follow-up

## Goal

Make the player Quality and Speed popups fit compact portrait playback surfaces more
comfortably, keep every player popup visibly aligned with the control that opened it,
center codec-less quality labels such as Auto, and make the Stats dashboard denser with
a cleaner range selector.

## Non-goals

- Do not change playback, quality-selection, speed, volume, or More-action semantics.
- Do not change player lifecycle or introduce another popup owner.
- Do not redesign stats data, filtering semantics, charts, or navigation.
- Do not claim device QA from automated checks.

## Current context

- Branch: `codex/player-ux-tablet-live-discovery`, HEAD `d5f379b5`.
- Round 5 is implemented and committed. The worktree already contains an uncommitted
  follow-up that gives the shared popup host one whole-panel scroll viewport and pins
  oversized panels to the vertical edge nearest their trigger.
- Device screenshots show the older portrait failure: Quality and Speed extend below the
  video surface, top and bottom controls do not produce an obvious placement relationship,
  and Auto sits on the first line when neighboring qualities have codec metadata.
- The Stats range selector is a manually nested card, row, dividers, and three buttons;
  cards use generous shared spacing and chart-height tokens on compact phones.

## Files likely involved

- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerPopupPolicy.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerQualityPopupBinder.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerSpeedPopupBinder.kt`
- `app/src/main/res/layout/layout_player_quality_popup.xml`
- `app/src/main/res/layout/layout_player_speed_popup.xml`
- `app/src/test/java/com/github/andreyasadchy/xtra/ui/player/PlayerPopupPolicyTest.kt`
- `app/src/main/res/layout/fragment_stats.xml`
- `app/src/main/res/values/styles.xml`
- `app/src/main/res/values/dimens.xml`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/stats/StatsFragment.kt`
- `docs/PLAYER.md`

## Risks

- Dense dynamic quality/speed grids can clip long labels or reduce touch targets.
- Popup placement must stay correct in RTL, split-screen, landscape, and with insets.
- Stats token changes affect every dashboard card and need compact/wide regression review.

Risk level: medium

## Human approval

Required before implementation: no

Reason: The requested changes are narrow, reversible presentation fixes with no playback
or data-model changes.

## Implementation steps

1. Retain the existing whole-panel portrait clamp and vertical trigger-edge policy.
2. Align popup horizontal edges with the trigger side and add policy tests.
3. Use denser dynamic Quality and Speed grids while preserving 48dp-class targets and
   center labels that do not have codec metadata.
4. Replace the Stats range-selector shell with a single-selection Material segmented
   group and tighten shared compact dashboard spacing/chart dimensions.
5. Run popup policy tests, all unit tests, `assembleDebug`, and `lintDebug`; review the
   final diff and hand off device QA.

## Verification

Automated checks:

- [x] Targeted popup policy tests
- [x] `./gradlew assembleDebug`
- [x] `./gradlew test`
- [x] `./gradlew lintDebug`

Human QA required:

- [ ] Quality and Speed in portrait, landscape, split-screen, and wide/tablet layouts.
- [ ] Quality Auto/manual labels with mixed codecs; Audio-only and Chat-only remain
      reachable and selectable.
- [ ] Quality, Speed, Volume, and More placement matches each trigger and remains in bounds.
- [ ] Live/VoD playback, switching, minimize/restore, close/reopen, PiP/background,
      controls, gestures, and floating chat regressions.
- [ ] Stats range switching, compact and wide spacing, chart labels, scrolling, and rotation.

Human QA completed: none.

## Progress log

- 2026-08-21: Created the follow-up plan from device screenshots and the existing
  uncommitted portrait-clamping repair.
- 2026-08-21: Added trigger-side horizontal placement, denser Quality/Speed grids,
  centered codec-less quality labels, a native Stats segmented range group, and tighter
  shared dashboard spacing/chart dimensions.
- 2026-08-21: Final verification passed: focused popup policy tests, `assembleDebug`,
  all 319 unit tests, and `lintDebug` (0 errors; existing warning baseline remains).
  No Android device was attached, so the required visual/player manual QA remains open.

## Decisions

- Decision: Align the panel's near horizontal edge to the trigger's side of the player.
  Reason: This produces a stable visible relationship: left controls open from the left,
  right controls open from the right, while bounds and RTL conversion remain centralized.
  Alternatives considered: centering every popup on the player; continuing to center the
  panel on each trigger and relying on incidental edge clamping.
- Decision: Keep one whole-panel scroll fallback after compacting content.
  Reason: Dynamic quality lists and More actions can still exceed unusually short surfaces.
  Alternatives considered: clipping content or restoring separate nested scroll regions.

## Final PR summary draft

Summary: Clamp oversized player popups inside portrait playback, align them to their
trigger side, compact Quality/Speed content, center codec-less labels, and tighten Stats
with a native segmented range selector and denser dashboard spacing.
Tests: Focused popup policy tests, `assembleDebug`, 319 unit tests, and `lintDebug` pass.
Human QA: Required for all four player popups, player lifecycle/gestures/floating chat,
and Stats filters/charts on compact and wide layouts; none completed because no device
was attached.
Risks: Dynamic quality lists and extreme resized surfaces still rely on whole-panel
scrolling; final visual density and trigger alignment need device confirmation.
