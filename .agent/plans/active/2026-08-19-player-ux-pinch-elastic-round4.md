# Player UX round 4: elastic pinch endpoints + settle-target fix

## Goal

Make the pinch Fit/Fill gesture respond continuously in **both** directions, per device
feedback: pinching in the "can't go further" direction (inward while committed to Fit,
outward while committed to Fill) gently deforms the video a few percent and springs back
on release — YouTube-style direct-manipulation feel — instead of today's zero visual
response. Also fix a round-3 defect: the neutral-release settle animates toward the Fill
scale even when Fit is committed (contradicting its own KDoc), so Fit-origin releases
grow toward Fill then snap back.

## Research summary (source-verified)

- **Next Player** (`feature/player/.../state/VideoZoomAndContentScaleState.kt`): discrete
  content-scale mode separate from a continuous multiplicative zoom
  (`zoom = (zoom * zoomChange).coerceIn(0.25f, 4f)`), hard-clamped, **no elastic**,
  persisted per media item.
- **YouTube**: pinch is continuous direct-manipulation zoom that persists after release.
- ThystTV already has the continuous core (committed mode + transient view scale via
  `PlayerDisplayModePreviewer`; mode committed only on release). Endpoint elasticity is
  a deliberate ThystTV addition in the standard overscroll pattern: ~5% deformation with
  saturation, animated return via the existing settle animator. No arbitrary zoom, no pan.

## Non-goals

- No arbitrary/persistent zoom levels or pan (stay two-state Fit/Fill).
- No changes to arm/commit thresholds, hysteresis, or commit semantics.
- No pill changes (directional bar stays for mode transitions; elastic keeps the neutral
  bar + committed label).
- No gesture-detector swap (`PlayerGestureArbiter` already owns arbitration).
- No popup-menu redesign — deferred to its own round (round 5) per feedback.
- No playback backend, updater, release, or website changes.

## Current context

- Branch `codex/player-ux-tablet-live-discovery`; rounds 1-2 committed (635c48d0),
  round 3 checkpointed as 436b1890 (green: assembleDebug / test 295 / lintDebug).
- Dead direction today: `PinchDisplayModeController.targetFor` returns null →
  `Event.NoPreview` → `PlayerFragment.applyPinchEvent` shows the neutral pill
  and calls `finalizePinchSurface()` — zero video response.
- Round-3 defect: `settlePinchPreview` animates unconditionally to the Fill `ratio`;
  committed Fit must settle to (1,1) per its KDoc and the round-3 plan decision.
- Continuity fact (verified against the code): from Fill, both the Fit-ward preview
  (progress 0 = anchor = ratio) and the planned elastic path (`ratio·(1+d)`) render in
  the same FIT-mode coordinate space anchored at ratio, so the whole gesture can be one
  continuous transform with zero mid-gesture renderer switches.
- Event/previewer consumers: PlayerFragment only (grep-verified) — safe to extend.
- Pre-N fallback (no view-scale previews) and Stretch (always has a live target) are out
  of elastic scope by construction.

## Files likely involved

- `app/src/main/java/.../ui/player/PinchDisplayModeController.kt` (+ test)
- `app/src/main/java/.../ui/player/PlayerDisplayModePreviewer.kt` (+ test)
- `app/src/main/java/.../ui/player/PlayerFragment.kt`
- `docs/GESTURE_SYSTEM.md`
- this plan

## Risks

- Elastic from Fill switches the renderer ZOOM→FIT+scale at gesture start; seamless by
  the previewer invariant (FIT at fillRatio ≡ Fill) — promoted to critical device QA,
  including immediately after an adaptive resolution/quality change.
- Mid-gesture neutral crossing must not flip the renderer (NoPreview/finalize): handled
  by the controller establishment rule; unit-tested.
- Settle animator vs quick re-pinch / minimize: covered by existing `cancelPinchSettle`
  + `finalizePinchSurface` paths. Ordering guarantee asserted in code review:
  `cancelPinchSettle()` always precedes any scale write, and the settle end action nulls
  the animator before re-entering finalize, so a cancelled settle cannot finalize over a
  newly written pinch scale.
- When `fillToFitRatio == 1` (aspect matches viewport) the mode-transition preview is
  label-only by design; elastic still applies (deformation feedback, not a mode preview).
- Player gesture/overlay changes are high-risk per `docs/PLAYER.md`; full human QA list
  required.

Risk level: medium

## Human approval

Required before implementation: yes — captured by approval of this plan.

## Implementation steps

1. **Checkpoint**: commit the uncommitted round-3 work as its own commit (source, tests,
   docs, round-3 plan; exclude `.commandcode/` and local APK artifacts). Note the
   settle-target defect in the round-3 plan's progress log. (Done: 436b1890.)
2. **Controller** (`PinchDisplayModeController`):
   - Add `Event.Elastic(from: PlayerDisplayMode, deformation: Float)`; deformation is a
     **normalized 0..1 endpoint displacement** — not raw pinch scale, not absolute view
     scale — so the controller stays UI-agnostic.
   - Document the input contract on `update()`: `cumulativeScale` is the **cumulative**
     span scale relative to pinch start (1.0 = neutral), never the detector's incremental
     per-callback factor.
   - Dead direction (target null, outside DIRECTION_EPSILON): emit
     `Elastic(from, min(1, deviation / ELASTIC_SATURATION_SCALE))` where deviation is
     `1 - scale` (inward) or `scale - 1` (outward); `ELASTIC_SATURATION_SCALE = 0.20f`.
   - **Establishment rule**: track `manipulationEstablished`, set when the gesture first
     emits Preview or Elastic, reset in `begin()`. While established, the neutral epsilon
     zone emits `Elastic(from, 0f)` — never `NoPreview`. `NoPreview` is reserved for
     "gesture has not established a direction yet" (where finalize is genuinely
     harmless). Invariant made explicit: once a gesture has produced visual manipulation,
     no event triggers `finalizePinchSurface()` until Commit / Restore / Cancelled.
   - Elastic never arms, commits, or changes phase; release after elastic-only pinches
     is a plain `Restore(committed)`.
3. **Previewer** (`PlayerDisplayModePreviewer`):
   - Add explicit `anchorScale(mode, fillToFitRatio)`: FIT → 1, FILL → ratio,
     STRETCH → ratio (consistent with the previewer's documented Stretch-anchors-at-Fill
     invariant; unreachable from the settle path, which early-returns for Stretch).
     This is the **only** API `PlayerFragment` uses to choose a resting/settle scale —
     no Fragment-local target computation anywhere.
   - Add `elasticScale(from, deformation, fillToFitRatio)`: FIT →
     `1 - ELASTIC_MAX_SCALE_DELTA * d`; FILL → `ratio * (1 + ELASTIC_MAX_SCALE_DELTA * d)`;
     STRETCH → anchor (unreachable, safe). `ELASTIC_MAX_SCALE_DELTA = 0.05f`.
4. **PlayerFragment**:
   - `applyPinchEvent`: new Elastic branch — `showPinchFeedback(from, 0f)` (unchanged
     pill) + new `applyPinchElastic(from, deformation)`: gated to landscape+maximized and
     API ≥ 24; `cancelPinchSettle()` **first**; pin `resizeMode = FIT`; write
     `scaleX/scaleY` from `elasticScale`. Portrait and pre-N keep pill-only behavior.
   - **Fix `settlePinchPreview`**: animate to `anchorScale(committed, ratio)` instead of
     the unconditional `ratio`; short-circuit to `finalizePinchSurface()` when already
     at target (abs diff < 0.001). KDoc updated to match. `applyPinchElastic(·, 0f)`
     renders the committed anchor, so neutral crossing is seamless with no renderer
     switch.
   - NoPreview branch unchanged (pre-establishment only; canonical finalize harmless).
5. **Tests** (JUnit 4, backtick names, existing style):
   - Controller: dead directions emit Elastic with rising deformation and saturation
     clamp; deformation recedes when the pinch reverses; elastic never produces
     Armed/Commit; release after elastic-only gesture → Restore; epsilon **before**
     direction establishment still NoPreview; **after** establishment emits
     Elastic(from, 0f), not NoPreview (neutral-crossing sequences both origins:
     Fit inward → reverse through neutral → outward Fill preview; Fill mirror); Stretch
     never emits Elastic. Update the two existing dead-direction NoPreview assertions.
   - Previewer: `elasticScale` math for both origins incl. clamps and ratio = 1;
     `anchorScale` returns 1 / ratio / ratio for FIT / FILL / STRETCH.
6. **Docs**: `docs/GESTURE_SYSTEM.md` pinch section — elastic endpoints, establishment
   rule (no mid-gesture finalize), settle-target fix, pre-N/Stretch exclusions, the
   cumulative-scale and normalized-deformation contracts.
7. **Checks**: `./gradlew assembleDebug`, `./gradlew test`, `./gradlew lintDebug`;
   confirm the debug APK is fresh for device QA. Update this plan's progress log and
   verification boxes.

## Verification

Automated:
- [x] `./gradlew assembleDebug`
- [x] `./gradlew test` (307 green: 295 existing + 12 new elastic/anchor/
      neutral-crossing tests)
- [x] `./gradlew lintDebug`

Human QA required (device):
- [ ] **Critical**: Fill resting → two fingers → begin outward pinch extremely slowly —
      zero visible discontinuity at the ZOOM→FIT+scale switch; repeat shortly after an
      adaptive stream resolution/quality change (stale-ratio check).
- [ ] Fit committed: pinch inward → video gently compresses a few percent; release →
      springs back smoothly (no grow-then-snap).
- [ ] Fill committed: pinch outward → video gently expands past Fill; release → springs
      back to Fill.
- [ ] **One uninterrupted gesture**: Fit compress → reverse through neutral → continue
      outward to Fill preview/arm; and the Fill mirror. Completely continuous — no snap,
      resize jump, or surface reinit around neutral.
- [ ] Fit-origin neutral release (pinch outward below arm) settles back to Fit without
      first growing toward Fill (round-3 defect regression check).
- [ ] Useful directions unchanged: previews track fingers, haptic at arm, seamless commit.
- [ ] Quick re-pinch during settle, minimize/restore, rotation, close/reopen, PiP:
      no half-scaled or stuck surface (settle-cancel ordering assertion verified in code
      review: cancel always precedes scale writes).
- [ ] Pointer-count transitions: one-finger volume/brightness → add second finger →
      pinch → lift one finger; the remaining finger must not resume the old vertical
      gesture.
- [ ] Portrait player: pill-only feedback, no scale deformation, behavior unchanged.
- [ ] Live and VoD playback, stream switching, floating chat coexist with pinch.
- [ ] Pre-N device/emulator if available (else documented fallback).

Human QA completed: none yet.

## Progress log

- 2026-08-20: Plan approved; round 3 checkpointed as 436b1890 (with the settle-target
  defect noted in the round-3 plan's progress log).
- 2026-08-20: Implemented round 4: `Event.Elastic` with normalized saturation
  mapping and the establishment rule in the controller (plus the cumulative-scale
  KDoc contract); `anchorScale`/`elasticScale` in the previewer with `previewScale`
  refactored onto anchors; the Fragment Elastic branch with `applyPinchElastic`
  (cancel-settle-first ordering) and the settle-target fix with an at-anchor
  short-circuit (replacing the `ratio <= 1f` early-out so collapsed-ratio elastic
  releases still settle); 12 new unit tests (one initial expectation arithmetic
  error corrected: Fill elastic at d=0.5 is anchor*1.025, not anchor+0.025).
  `assembleDebug`, `test` (307 green), `lintDebug` pass. Settle-cancel ordering
  guarantee verified in review: every scale write follows `cancelPinchSettle()`,
  and the settle end action nulls the animator before re-entering finalize.
  Human QA remains open.

## Decisions

- Decision: elastic deformation capped at 5% view scale, saturating at 20% span
  deviation, **linear** mapping. Reason: restrained, easy to reason about, unit-test,
  and tune. A non-linear resistance curve can be perceptible via its derivative
  (motion-per-finger-movement) — if device feel is mechanical, a round 4.1 swaps one
  mapping function without touching gesture semantics. Alternatives: larger deformation
  (becomes the effect itself).
- Decision: established gestures emit `Elastic(from, 0f)` at neutral instead of
  NoPreview/finalize. Reason: keeps the entire gesture in one continuous FIT-rendering
  transform and eliminates mid-gesture renderer churn (the Fill-origin neutral crossing
  previously flipped ZOOM→FIT→ZOOM); makes "no finalize between manipulation start and
  gesture end" an explicit, testable invariant. Alternatives: a dedicated `Neutral`
  event (more types for no gain); Fragment-side gating (controller owns the state).
- Decision: explicit `anchorScale(mode, ratio)` as the single source of resting scale.
  Reason: the round-3 settle bug existed precisely because the committed anchor had no
  named representation; a dedicated API makes the corrected invariant hard to violate
  again and grep-able. `STRETCH -> ratio` stays consistent with the previewer's
  documented Stretch anchoring; unreachable in settle anyway.
- Decision: pill stays neutral-bar + committed label during elastic. Reason: the
  directional bar encodes mode-transition progress; riding it during elastic would read
  as false progress toward the other mode.
- Decision: new `Event.Elastic` instead of a payload on `NoPreview`. Reason: distinct
  semantics (dead-direction feedback vs undetermined direction).
- Decision: elastic applies even when `fillToFitRatio == 1`. Reason: it is deformation
  feedback, not a mode preview; the "no artificial crop" invariant targets transitions.
- Decision: exclude pre-N and Stretch. Reason: pre-N SurfaceView transforms are
  unreliable (existing stepped fallback); Stretch always has a live target so elastic is
  unreachable there.

## Final PR summary draft

Summary: Elastic endpoint feedback for the pinch Fit/Fill gesture (gentle ~5% saturated
deformation in the dead directions, springing back via the existing settle animator),
a fix for the round-3 neutral-release settle target (animated to the Fill scale
regardless of committed mode), and a mid-gesture continuity invariant (no surface
finalization between manipulation start and gesture end).
Tests: `assembleDebug`, `test` (all green; new controller/previewer coverage for
elastic mapping, anchors, and neutral-crossing continuity), `lintDebug`; round 3
checkpointed as its own commit first.
Human QA: required for the slow-pinch ZOOM→FIT switch (incl. post-resolution-change),
dead-direction pinch from both modes, neutral-crossing continuity in one gesture, the
Fit-origin settle fix, quick re-pinch/minimize/restore/rotation/PiP, pointer-count
transitions, portrait pill-only behavior, and live/VoD + floating chat spot-checks.
Risks: Fill-origin elastic starts with a renderer ZOOM→FIT+scale switch (seamless by
the previewer invariant, device-verified); player gesture surfaces are high-risk per
docs/PLAYER.md.
