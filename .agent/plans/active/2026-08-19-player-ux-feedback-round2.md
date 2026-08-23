# Player UX feedback follow-up, round 2

## Goal

Apply three pieces of device feedback on top of the 2026-08-18 player UX pass:

1. Center the vertical edge-pill content: the gesture icon must sit on the same
   vertical axis as the level bar inside the wide brightness/volume pill.
2. Make the quality and speed popups collapse when tapping outside the panel,
   instead of requiring a chip/preset press.
3. Merge the live indicator and the Watch-live action into a single
   highlighted pill, used coherently in channel search results and the channel
   profile.

## Non-goals

- Do not change gesture mappings, thresholds, pinch behavior, or the feedback
  presentation state machine from the previous round.
- Do not redesign the quality/speed panel contents, only their dismissal.
- Do not change stream discovery data, playback-session ownership, or the
  profile's offline "Open player" behavior.
- Do not touch the playback backend, updater, release signing, or website.

## Current context

- Branch: `codex/player-ux-tablet-live-discovery`, containing the uncommitted
  2026-08-18 UX pass (search CTA removal, zone swap, vertical edge pills,
  pinch bar, unified feedback presentation).
- The vertical pill's icon keeps `android:layout_marginEnd` from the shared
  layout XML while `PlayerSurfacePolicy` only calls `setMargins()`. Relative
  (`marginStart`/`marginEnd`) margins are re-applied over the absolute ones on
  layout resolution, so in vertical mode the intended zero end margin is
  overridden by the stale 12dp XML margin and the icon sits off the bar axis.
- `PlayerQualityDialog` and `PlayerSpeedDialog` use a plain `Dialog` with a
  transparent, full-width, non-dimmed window. Taps on the blank window area
  land inside the decor, so `setCanceledOnTouchOutside` alone can never fire
  there; the dialogs only close through chip/preset clicks.
- Search rows show a red "LIVE" text badge next to the channel name; the
  profile shows the same red badge above the stream title plus a plain 50dp
  "WATCH LIVE"/"OPEN PLAYER" text link. Two separate live signals per surface.

## Files likely involved

- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerSurfacePolicy.kt`
- `app/src/main/res/layout/layout_player_gesture_feedback.xml`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerQualityDialog.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerSpeedDialog.kt`
- `app/src/main/res/layout/fragment_search_channels_list_item.xml`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/search/channels/ChannelSearchAdapter.kt`
- `app/src/main/res/layout/fragment_channel.xml`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/channel/ChannelPagerFragment.kt`
- `app/src/main/res/values/styles.xml`, `res/drawable/` (pill style, dot)

## Risks

- Dialog dismissal must not swallow panel taps or break chip selection.
- The search pill reintroduces direct playback from search; tapping it must
  not also navigate to the profile (separate click targets in one card).
- The profile must keep a working player entry point when offline.
- Player overlay/dialog changes require device revalidation.

Risk level: medium

## Human approval

Required before implementation: no

Reason: The user supplied this feedback directly after device testing and
asked for these three specific changes; each is narrow, view-scoped, and
reversible. Per `.agent/PLANS.md` autonomous rules the plan is low/medium risk
with no release or lifecycle-behavior change.

## Implementation steps

1. Fix vertical pill icon centering.
   - Remove `android:layout_marginEnd` from `feedbackIcon` in
     `layout_player_gesture_feedback.xml` so margins are code-owned.
   - In `PlayerSurfacePolicy.presentFeedback`, set `marginStart`/`marginEnd`
     alongside `setMargins()` so no stale relative margins survive resolution.
2. Outside-tap dismissal for quality and speed popups.
   - Root click listener that dismisses when the transparent area outside the
     panel is tapped (panels consume their own touches).
   - Also enable `setCanceledOnTouchOutside(true)` for taps fully outside the
     window.
3. Unified live pill.
   - Shared style: stadium `MaterialButton`, filled `liveStreamRed`, white
     bold caps text, small white dot icon (`ic_live_dot`).
   - Search: replace the red LIVE text badge with the pill labeled "Live",
     trailing and vertically centered; tapping the pill starts the stream
     directly; the rest of the card still opens the profile; offline rows show
     no pill.
   - Profile: replace the plain text link with two pill variants - red
     "Watch live" pill (with dot) when live, outlined neutral "Open player"
     pill otherwise - and remove the separate red LIVE badge TextView and its
     code.
4. Rebuild, run checks, update plan.

## Verification

Automated checks:

- [x] `./gradlew assembleDebug`
- [x] `./gradlew test`
- [x] `./gradlew lintDebug`
- [x] Debug APK alignment/signature verification (zipaligned, debug cert, newer than all sources)

Human QA required:

- [ ] Wide landscape: brightness/volume vertical pill - icon and level bar on
      one axis, even padding, RTL included.
- [ ] Quality popup: opens, chip selection still works, tapping outside the
      panel dismisses without changing quality; back button still dismisses.
- [ ] Speed popup: same dismissal checks; slider and presets unaffected.
- [ ] Search live row: single red pill with dot, tap starts the correct
      stream, card tap opens profile, pill tap does not also navigate.
- [ ] Search offline row: no pill, no red badge, spacing balanced.
- [ ] Profile while live: single red "Watch live" pill (dot), no separate LIVE
      badge, opens the correct stream.
- [ ] Profile offline/unknown: neutral "Open player" pill, opens the player.
- [ ] Regressions from the previous round still hold (zones, pinch bar,
      compact feedback, gesture switching, player lifecycle basics).

Human QA completed:

- [ ] None yet.

## Progress log

- 2026-08-19: Created plan from round-2 device feedback (3 annotated
  screenshots) and inspected the pill layout margin resolution, both player
  dialogs' window setup, and the search/profile live elements.
- 2026-08-19: Implemented all three fixes. Icon margins are now fully
  code-owned (relative margins written alongside absolute ones); both player
  dialogs dismiss on blank-area taps via a decor-level touch listener plus
  `setCanceledOnTouchOutside`; added shared `LiveWatchPill`/`OpenPlayerPill`
  styles with `ic_live_dot`, replaced the search LIVE text badge with a
  tappable red "Live" pill, and replaced the profile's text link with the red
  "Watch live" pill (live) or outlined "Open player" pill (offline), removing
  the profile's separate red LIVE badge.
- 2026-08-19: `assembleDebug`, `test`, `lintDebug` pass; APK zipaligned,
  debug-signed, newer than all sources.

## Decisions

- Decision: fix icon centering by making feedback-icon margins fully
  code-owned (absolute and relative).
  Reason: `setMargins()` does not clear `marginStart`/`marginEnd`, so XML
  relative margins re-resolve over code-set absolute ones.
  Alternatives considered: clearing via `setMarginEnd(0)` only in vertical
  mode still leaves dual ownership of the same margins.
- Decision: dismiss popups through a root click listener rather than only
  `setCanceledOnTouchOutside`.
  Reason: the popup windows are match-parent wide, so most "outside" taps are
  inside the window and outside-touch cancellation never fires there.
  Alternatives considered: shrinking the window would change panel sizing and
  placement behavior.
- Decision: one shared red stadium pill with a dot as the single live element;
  profile keeps a neutral outlined variant for offline "Open player".
  Reason: red + dot exclusively means live and playable; offline playback
  entry stays available without faking a live signal.
  Alternatives considered: restyling only the badge keeps two elements; making
  the offline button red would present offline channels as live.

## Final PR summary draft

Summary: Center the vertical gesture pill content, let the quality/speed
popups dismiss on outside taps, and merge the live badge and Watch-live
action into one red pill used in search results and the channel profile.
Tests: `assembleDebug`, `test`, `lintDebug` pass; APK zipaligned and
debug-signed.
Human QA: Required for pill alignment (incl. RTL), popup dismissal, and the
unified live pill on live/offline search rows and profiles.
Risks: View-level changes only; watch entry behavior needs device checks.
