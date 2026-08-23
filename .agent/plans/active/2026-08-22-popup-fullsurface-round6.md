# Player popup round 6: full-surface sheets, truncation fix, stable positioning

## Goal

Address device feedback on the shared player popup host:

1. In short surfaces (portrait phones), Quality and Speed should expand into a
   full-player-surface sheet instead of a small floating card that clips content
   and needs awkward whole-panel scrolling.
2. Fix quality chip label truncation ("1080p60" rendered as "1080p6").
3. Keep More bounded and scrollable but visually tighter/smaller.
4. Fix the stale/misplaced popup position that required multiple presses after
   switching popups or reopening controls.

## Non-goals

- Do not change quality/speed/volume/More semantics or callbacks.
- Do not change player lifecycle, popup ownership, or dismissal rules.
- Do not reintroduce DialogFragment/bottom-sheet ownership.
- Do not touch Stats work from the pending follow-up branch state.

## Root-cause analysis

- Truncation: quality chips use a fixed 4-column grid with `maxLines=1` and no
  ellipsize; at 336dp panel width each chip is ~73dp, too narrow for bold
  "1080p60" at 14sp, so text silently clips to "1080p6".
- Positioning: `showPlayerPopup` seeds only container width, inheriting
  margins/height from the previous popup; the container layout listener only
  repositions on size changes; nothing repositions when the trigger view was
  GONE (0x0 rect -> null trigger -> fallback edge) and becomes visible later.
  Together these produce wrong first placement that only corrects itself after
  extra open/close presses.
- Portrait clipping feel: anchored cards are clamped to the safe surface and
  scroll internally, which reads as "cut off" in a tiny video strip even though
  bounds are respected.

## Design decisions

1. Full-surface expansion is opt-in per popup type: Quality and Speed set
   `allowFullSurface = true`; Volume and More stay trigger-anchored. Expansion
   happens only when the natural panel height exceeds the safe surface height,
   so landscape/large surfaces keep today's compact anchored cards.
2. Full-surface mode fills the entire safe player area. When the natural height
   fits, the card stretches to fill it (MATCH_PARENT inside the scroll viewport);
   when it overflows, the card keeps natural height and the existing viewport
   scrolls. Either way the sheet visually covers the player with no gaps and no
   clipped floating edges.
3. Column count for quality chips is derived from measured bold label widths so
   labels never truncate by construction; ellipsize END remains as a final
   safety net for extreme locales.
4. Placement application becomes idempotent (write params only on change), the
   container listener repositions on any geometry delta, and a trigger layout
   listener repositions when the trigger's window rect changes while open.
5. More gets smaller type/margins and 10dp row padding; 48dp touch targets are
   preserved by the binder's minimumHeight.

## Files

- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerPopupPolicy.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerFragment.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerQualityPopupBinder.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerSpeedPopupBinder.kt`
- `app/src/main/res/layout/layout_player_more_popup.xml`
- `app/src/main/res/layout/player_settings.xml`
- `app/src/test/java/com/github/andreyasadchy/xtra/ui/player/PlayerPopupPolicyTest.kt`
- `docs/PLAYER.md`

## Risks

- MATCH_PARENT children inside NestedScrollView must not break scrolling when
  content overflows; mitigated by keeping WRAP_CONTENT whenever natural height
  exceeds the viewport.
- Reposition listeners could relayout-loop; mitigated by idempotent writes and
  generation guards.
- Measured column selection could reduce density for long locale strings;
  acceptable because rows wrap rather than truncate.

Risk level: medium

## Verification

Automated checks:

- [x] Focused popup policy tests
- [x] `./gradlew assembleDebug`
- [x] `./gradlew test`
- [x] `./gradlew lintDebug`

Human QA required:

- [ ] Quality and Speed in portrait cover the player surface, show all common
      options, and "1080p60" renders untruncated (mixed-codec lists too).
- [ ] Quality and Speed in landscape/wide remain compact trigger-anchored cards.
- [ ] Open Quality -> dismiss -> open Speed immediately: first placement is
      correct, no stale position, no double press needed.
- [ ] Open a popup while controls were hidden: it appears correctly once
      controls become visible.
- [ ] More stays scrollable, slightly denser, all actions reachable.
- [ ] Standard player regression: live/VoD, switching, minimize/restore, PiP,
      gestures, floating chat, volume popup.

Human QA completed: none.

## Progress log

- 2026-08-22: Created plan from round-5 device feedback screenshots.
- 2026-08-22: Implemented policy expansion (`shouldExpandToSurface`, `expandToSurface`,
  `Placement.fullSurface`), fragment full-surface wiring for Quality/Speed, idempotent
  geometry writes, container/trigger layout listeners, measured quality chip columns
  with ellipsize safety, and denser More rows/title. Added 4 policy tests; updated
  `docs/PLAYER.md`.
- 2026-08-22: Verification passed: focused popup policy tests, full `test` +
  `assembleDebug`, and `lintDebug` (0 errors). No device attached; human QA open.
