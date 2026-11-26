# Floating chat redesign

## Goal

Make the fullscreen floating (pop-up) chat beautiful, smooth and dependable:

- **Glass card (A):** a 16 dp card in the gesture-feedback colors (#121116
  plus 6% theme accent, 1 dp light outline, soft shadow). The header lives
  inside the card and appears with the player controls (Reply, Aa, Tuck,
  Close). A quiet 24 x 3 dp pill moves the card while controls are hidden.
  The top 24 dp fades out.
- **Background:** Clear (no surface, text with a tight double halo), Shade
  55% (default for anyone who never changed the opacity) and Solid 92%. Clear
  must look as good as the others; it is the user's top requirement.
- **Fade old messages (A + B):** optional; rows fade after 10 s and come back
  while controls are up or while scrolled back.
- **Motion:** a fling glides to the nearest side with corner magnets; the card
  keeps clear of the cutout, the controls' top and bottom bars and the
  keyboard; a fast sideways fling or dragging 30% past the edge tucks it into
  a tab with an unread count. New lines glide in. Live resize from the corner
  that faces the video; pinch to resize while controls are up.
- **Reply bar:** composer hidden by default; a Reply bar option shows a
  compact composer in the card, which then keeps clear of the keyboard.
- **Memory:** position and size per channel (side, vertical anchor, size in
  dp) with a last-used fallback, capped at 50 channels; old keys migrate.

## Non-goals

- No change to the sidebar chat's look or behavior (glide, fade, composer).
- No edge-panel (C) style, no pills (B) as a separate style.
- No change to chat networking, the message store or PiP chat.
- No player engine, Media3, quality, speed or gesture changes.

## Current context

1.7.0 (`57ba79913`). Research, prototypes and the agreed design:
https://claude.ai/artifact/DgeZPYKnH7bGrkJWnYqkeP (discussed over two rounds
with Gemini 4 Neon xhigh). User decisions on 2026-11-21: A Glass card plus the
fade option; Clear background must work great; Shade 55% default for untouched
installs; composer hidden with a Reply bar option shipping now; per-channel
memory with last-used fallback.

Verified 1.7.0 bugs fixed here: the invisible bottom bar still takes touches;
the contrast button recreates ChatFragment (reconnect) into the floating
container, where `initView` doesn't look for it; PiP never hides the floating
chat; resize ignores ACTION_CANCEL and can grow off-screen while writing layout
params per move; dragging doesn't hold the controls; `scheduleDrawable` passes
an absolute uptime to `postDelayed`; the composer shows when chat is
reparented from the sidebar; the card ignores the cutout and the controls.

## Files likely involved

- `ui/player/FloatingChatController.kt` (new): views, physics, touch, popover
- `ui/player/FloatingChatPlacement.kt` (new, pure): bounds, snap, tuck, size
- `ui/player/FloatingChatLayoutStore.kt` (new, pure): per-channel JSON, LRU
- `ui/player/FloatingChatAppearance.kt` (new, pure): background/text mapping
- `ui/player/FloatingChatHostLayout.kt`, `FloatingChatCardView.kt` (new)
- `ui/chat/ChatFollowGlide.kt`, `ui/chat/ChatAgeFader.kt` (new)
- `ui/view/ChatMessageTextView.kt` (new): double-halo text for Clear
- `ui/player/PlayerFragment.kt`, `ui/chat/ChatFragment.kt`,
  `ui/chat/ChatAdapter.kt`, `util/chat/ChatAdapterUtils.kt`
- `res/layout/fragment_player.xml`, `res/layout/chat_list_item.xml`,
  `res/layout/layout_floating_chat_popup.xml` (new), `res/xml/chat_preferences.xml`,
  `values/strings.xml`, `values/arrays.xml`, drawables
- `app/build.gradle.kts`, `gradle/libs.versions.toml` (androidx.dynamicanimation)
- Tests under `app/src/test/java/com/github/andreyasadchy/xtra/ui/player/`
- `docs/PLAYER.md`, `docs/MANUAL_QA.md`, `CHANGELOG.md`

## Risks

- Player lifecycle: floating chat show/hide across PiP, minimize/restore,
  fold postures, double-tap chat restore and portrait. Only one ChatFragment
  may exist; the view is reparented, never recreated.
- Touch: the card, pill, grip, tab and pass-through must not steal video
  gestures or the system back gesture.
- Performance: per-move work stays to translation; resize relayouts once per
  frame inside a host that does not relayout the player.
- Clear text legibility over bright and busy video.

Risk level: high (player surface and lifecycle touch points)

## Human approval

Required before implementation: no

Reason: the user chose the direction and every open default on 2026-11-21 and
asked for the build; work stays on an unpushed branch until they ask to commit.

## Implementation steps

1. Pure policies with tests: placement/snap/tuck/keep-clear, layout store with
   LRU and migration, appearance mapping.
2. Host layout, card view, header/pill/grip/tab layout; controller with
   SpringAnimation physics, keep-clear, storage and controls hold.
3. PlayerFragment integration: show/hide, PiP, minimize/restore, portrait,
   fold, insets, controller visibility; remove fragment recreation.
4. Chat: overlay text styles (Clear double halo, Shade, Solid, bold, size),
   top fade, following state with glide, age fade, unread callback, composer
   and Reply bar, emote pause, `scheduleDrawable` fix.
5. Aa popover through the player popup host; settings and strings.
6. Tuck tab, pinch, TalkBack actions.
7. Tests, docs, Gradle checks.

## Verification

Automated checks:

- [x] `./gradlew assembleDebug`
- [x] `./gradlew test` (only `testDebugUnitTest` exists): 1601 passed after the
      review fixes; see the progress log for the earlier combined-run OOM.
- [x] `./gradlew lintDebug`: no errors; no new warnings in changed lines.

Human QA required:

- [ ] Clear background on bright snow/white scenes and dark night scenes:
      every line readable, no box, names and emotes crisp.
- [ ] Shade (fresh install default) and Solid; Aa changes apply live with no
      reconnect or lost scroll position.
- [ ] Drag by pill and by header; fling to each side; corner magnets; release
      mid-screen stays put; tuck by fast sideways fling and by dragging 30% past
      the edge; tab shows unread count; tap tab restores; back gesture at the
      tab edge still works elsewhere.
- [ ] Resize from the inner corner (both sides), pinch with controls up; card
      never leaves the screen; size remembered per channel and for a new channel.
- [ ] Controls show/hide: card slides off the top and bottom bars; short phone
      drops oldest lines rather than covering the seek bar; cutout side.
- [ ] Fade old messages: rows fade after 10 s, reappear with controls and when
      scrolled back; new lines glide in; scroll back stops following.
- [ ] Pass touches to video: taps and gestures reach the player while controls
      are hidden; card is interactive again while controls are up.
- [ ] Reply bar on (logged in, live): compose, emote menu, send; keyboard
      pushes the card up; message-dialog Reply works with the bar off.
- [ ] Player safety: live and VoD playback, stream switching, minimize/restore,
      close/reopen, PiP (card hidden in PiP, back after), speed/quality,
      gestures, double-tap chat cycle, swipe chat, fold postures, portrait.
- [ ] TalkBack: actions to move, resize, tuck and restore.

Human QA completed:

- [ ] none yet

## Progress log

- 2026-11-21: Created plan after the user picked the direction and defaults.
- 2026-11-21: Steps 1-6 written: placement, store and appearance policies;
  host, card and controller; chat overlay styles, glide, age fade, composer and
  Reply bar; Aa popover; tuck tab, pinch and TalkBack actions; PlayerFragment
  integration (PiP, minimize, portrait, fold, insets, controls hold, live
  appearance through the preference listener). Removed the drag/resize/contrast
  views, `toggleHighVisibility` (fragment recreation) and pixel position keys.
- 2026-11-21: Step 7: new `FloatingChatPlacementTest`, `FloatingChatLayoutStoreTest`,
  `FloatingChatAppearanceTest`, `FloatingChatControllerTest`; the chat
  transition suites now attach the controller. PLAYER.md, MANUAL_QA.md and
  CHANGELOG updated.
- 2026-11-21: Review fixes: the chat's row animations unpause whenever it
  leaves floating mode (a card hidden while tucked had left them paused), and
  `onDestroyView` clears the floating flag before hiding the card so the
  surviving `ChatFragment` is never left paused. `FloatingChatControllerTest`
  resets DynamicAnimation's per-thread frame handler, which Robolectric keeps
  across tests and would otherwise freeze later springs.
- 2026-11-21: Gate: `testDebugUnitTest` 1593/1593 passed on its own. Two
  earlier runs that built, tested and linted in one invocation each failed one
  unrelated test (`UpdatePolicyTest`, a 256 KB allocation) with
  `OutOfMemoryError`. The test JVM's heap limit is the 7.4 GB JVM default, and
  the same tests pass alone, so the likely cause is Windows commit memory being
  exhausted while lint ran alongside, not a leak. No heap dump was produced
  because the failure did not recur.
- 2026-11-22: Rebased onto the v1.7.0 tag (d73e1db34) as 00efac3c4 with no
  conflicts; the gate passed there.
- 2026-11-22: Independent review by Gemini 4 Neon (Antigravity, thought level
  xhigh) on a snapshot of 00efac3c4. It reported 19 findings and 8
  improvements. Each was checked against the code before acting.
  Fixed (verified):
  - RTL: the card and tab used `TOP|START`, so in RTL a width change moved `x`.
    Both are now `top|left`.
  - Light app theme: the overlay used name colours made readable for a light
    background. The overlay now has separate colour caches.
  - Rows bound while the card was hidden or tucked started their animated
    emotes. `loadImages` now asks `startAnimations` first.
  - A hide fade could still be running when the sidebar, portrait or PiP
    showed, leaving an empty sidebar. Those paths now end the fade at once.
  - When a pinch started, its cancel ended the drag or resize under the first
    finger and settled the card. A tap on the grip stored the cut-down height.
  - A second finger made the drag jump; drag now tracks the pointer id.
  - At the smallest width the header buttons swallowed drags
    (`FloatingChatHeaderLayout`). The grip covered the open message box.
  - The More menu's chat-bar label read the sidebar preference while floating,
    and "Hide chat" left the floating card on screen (also in 1.7.0).
  - The fade did not reveal on focus. Closing the keyboard kept focus in the
    card. Copy opened no message box.
  - Cutouts were ignored while video drew behind them; the status bar was not
    kept clear; the tab was not re-placed while tucked.
  - On short phones the keyboard pushed the card under the controls'
    minimum-height floor. A window narrower than the card overflowed it, and
    pinch kept that floor.
  - Clear drew emotes twice, and an image name paint drew an opaque box.
  - Migrating legacy positions retried on every open.
  - The overlay style survived `onDestroyView`.
  - With no vertical room, the release chose its anchor by a tie. A fast
    sideways throw from the other half tucked the card.
  - The fade ticked every 500 ms (now event-driven), and the tab was re-placed
    on every message.
  - Aa chips were under 48dp and had no radio semantics.
  - The card jolted on settle after a resize; it now lays out synchronously.
  - The message box was light in the light theme; it is now dark over video.
  - Removed a dead field.
  Improvements taken: header drag, dark message box, payload-only restyle,
  event-driven fade, and halo layering for painted names.
  Skipped: collapsing the emote menu with the composer; capping alpha that
  compounds under the halo; tinting the edit-text underline (AppCompat owns that
  tint).
  Follow-ups: z-order over gesture feedback and the seek preview (covered by
  ducking since 2026-11-22; the seek preview never met the card in QA); TV D-pad
  focus inside the card; drag to untuck and keeping the tucked state across PiP
  and minimize; haptics and a custom opacity in Aa; `ChatFollowGlide` extra top
  space; 40dp header buttons.
  Tests added: keyboard and narrow-window placement, no-room anchor, the
  cross-half throw, header drag from a button, and a grip tap.
- 2026-11-22: Gate after the review fixes: `assembleDebug testDebugUnitTest`
  passed (1601 tests, 0 failures). `lintDebug` then passed on its own: no
  errors, no warnings on changed lines.
- 2026-11-22: The maintainer added #122 to 1.7.1. Emulator QA (Grok 4.7, report
  in `.private/qa/2026-11-22-171-emulator/`) found the card drawn above Quality,
  Speed, More and Aa, over the +10 button, and over an edge slider on its side.
  The menus: `android:elevation` on the popup host's `<include>` was ignored, so
  the host sat at Z 0 under the 10dp floating chat host (also true in 1.7.0); it
  now sits on the included root. By the maintainer's choice, the card steps aside
  from the seek button on its side while the controls show, and fades to 25%
  while a brightness, volume, seek or speed slider is up (the "ducking"
  follow-up). Its other findings were checked and are not bugs: the book-posture
  video is vertically centred in its pane, the unfolded inner screen is portrait
  (docked chat by design), and the chat-button "second tap" came 23 s later, after
  the controls had hidden, so it only showed them.
- 2026-11-23: Maintainer feedback on prerelease `debug-patch171-615b41abb`: a
  header drag at the top dropped the card below the controls' band and then
  jumped it up when they hid; side and edge snapping stopped the viewer from
  putting the card where they wanted; the card just faded on minimize while it
  also sprang from the controls and status bar changing. Root cause of the
  jump: holding the controls during a drag kept their ~70dp top band out of the
  bounds, so the card rested low until they hid. Discussed with Gemini 4 Neon
  (high) and agreed:
  - Free placement: the layout is `xFraction`/`yFraction` of the clear screen
    plus a size; release keeps the drop spot (0.1 s fling, 220dp cap, 16dp edge
    magnets); tuck at 35% overhang, or a 1800 dp/s outward fling only from
    within 24dp of that edge. The store still writes side and anchor for older
    builds and reads legacy entries flush with their edges.
  - A header drag (past slop), grip resize or pinch fades the controls out and
    keeps the header and grip (`chromeHeld`), so the card is placed on the clear
    picture. A tap leaves the controls.
  - The controls no longer shrink the bounds. A card that covers a control
    group moves the shortest way clear (one or two moves, 8dp gap; a too-tall
    card fits its column), gliding over the controls' 250 ms fade.
  - Minimize: the host shares `slidingLayout`'s geometry, so it follows the
    swipe down (translation only, fading to 55% at the threshold), returns on a
    short swipe, freezes before the controls and status bar change, rides the
    player's pose on its own 250 ms AccelerateDecelerate curve and fades out by
    60% of the way. Maximize grows it back from the player's pose and fades it
    in over the last 65%. Keep-clear moves wait while the host moves.
  Tests rewritten for the new API, plus chrome hold, drop-in-place, the swipe
  follow and return, the minimize ride, maximize mid-ride and a late minimize
  after a quick maximize. Robolectric runs a whole animation in one idle, so
  only the ends of the ride are tested; its feel needs device QA. Gate:
  `testDebugUnitTest` 1620/1620, `lintDebug` clean.

- 2026-11-26: Maintainer feedback on the 1.7.1 candidate (`a4a37be04`): the
  card still could not be placed freely, worst on small and very wide screens.
  From the code: the release glide (0.1 s of velocity, up to 220dp) carried a
  careful drop on by tens of dp, and `avoid` moved or shrank the card every
  time the controls appeared, which on a short screen is almost everywhere.
  Research page with a placement playground:
  https://claude.ai/artifact/AZ9ffmXFLmd3L7DKk7GmJD. The maintainer chose
  "stays where you drop it" with a slight fade where controls are under the
  card. Built: no glide (velocity only decides a tuck), 8dp magnets, `avoid`
  removed; a card over a control fades to 60%, touches on a covered control
  pass through to it, the header moves to the bottom edge when the top one is
  covered, and the grip hides over a control. The default size from the screen
  share was offered and not taken up yet.

## Decisions

- Decision: Keep `floating_chat_transparency` as the background opacity and
  default it to 55; Aa offers Clear 0 / Shade 55 / Solid 92.
  Reason: keeps anyone's explicit value; untouched installs get Shade.
  Alternatives considered: a new enum key (loses explicit values).
- Decision: Replace "High visibility" (bold text + offset shadow) with "Bold
  text" (new key, off) and a per-background text treatment.
  Reason: the offset bold shadow looked heavy and blurred; Clear now gets a
  dedicated halo.
  Alternatives considered: keep the old switch (two overlapping controls).
- Decision: Aa opens through the player popup host.
  Reason: TalkBack, back, controls hold and theming already work there.
  Alternatives considered: a panel inside the card (clipped on small cards).
- Decision: Hiding floating chat fades the card with its lines and moves the
  chat view back in the fade's end callback, which reads the current mode.
  Reason: Clear has no surface, so an immediate move would make the text vanish
  with nothing to fade. Toggling to the sidebar still moves the view at once.
  Alternatives considered: snapshot bitmap (fails on hardware emote bitmaps).
- Decision: Tuck by drag measures the 30% overhang against the keep-clear
  bounds, as the prototype did, not the raw screen edge.
- Decision: The player's chat-bar toggle switches the Reply bar while floating.
- Decision: A recreated player view starts with sidebar chat (floating state
  resets in `onDestroyView`), instead of a floating flag with no card.
- Decision: Clear marks its two draw passes in a global `ChatHaloPass` instead
  of reading the paint's shadow.
  Reason: `Paint.getShadowLayerRadius` needs API 29; minSdk is 23, and rows draw
  on the main thread one at a time.
- Decision (2026-11-23, replaces side snapping and anchors): the card rests
  wherever it is dropped; the controls fade while it is moved; a card over a
  control moves the shortest way clear; the card rides into the mini-player.
  Reason: the maintainer's feedback on the 1.7.1 prerelease, design agreed with
  Gemini 4 Neon. Alternatives considered: keeping anchors with a finer grid;
  shrinking the bounds by the controls' bands (the cause of the jump); scaling
  the card during the swipe (the video only translates there).
- Decision (2026-11-26, replaces stepping aside and `avoid`): the controls never
  move the card. A card over a control fades to 60%, a touch on the covered
  control goes to it, and the header and grip move off the controls. The
  release keeps the exact drop spot.
  Reason: the maintainer's choice of option A on the placement research page;
  automatic moves made free placement feel like it fought the viewer.
  Alternatives considered: side magnet (PiP style), a docked strip, moving only
  up or down for the controls.
- Superseded 2026-11-26: With the controls up, the card steps inward past the ±10 button on
  its side; during brightness, volume, seek and speed swipes it fades to 25%.
  (2026-11-23: the step aside is now the general control avoidance above.)
  Reason: the maintainer's choice after emulator QA (2026-11-22); every control
  and slider stays visible and reachable without moving controls.
  Alternatives considered: leaving both covered; moving the gesture feedback
  above the card (crosses `PlayerLayout`'s fold and mini-player geometry).
- Decision: Overlay name colours get their own caches in the light app theme.
  Trade-off: a user without a Twitch colour may get a different random colour
  in floating and sidebar chat.

## Final PR summary draft

Summary: Redesigns floating chat as a dark rounded card over video with Clear
(text with a halo, no surface), Shade (default for untouched installs) and
Solid looks, text size and bold options, all switched live from a new Aa
popover without reconnecting chat. The card moves by a pill or the header
(the controls fade while it moves), stays where it is dropped, slides just off
any control it covers, keeps clear of the cutout, navigation bar and keyboard,
rides into the mini-player with the video, tucks to an edge tab with an
unread count, resizes from the inner corner or by pinch, and remembers its
place per channel with a last-used fallback (1.7.0 positions migrate). New
options: Fade old messages, Pass touches to video, Reply bar. Fixes the
invisible drag bar catching taps, the card covering PiP, and the contrast
toggle recreating chat.
Tests: new `FloatingChatPlacementTest`, `FloatingChatLayoutStoreTest`,
`FloatingChatAppearanceTest`, `FloatingChatControllerTest`; the chat
transition suites run with the controller attached. `assembleDebug`,
`testDebugUnitTest` (1620) and `lintDebug` pass. An independent Gemini 4 Neon
review's verified findings are fixed (see the progress log).
Human QA: `docs/MANUAL_QA.md` → "Floating chat redesign", plus the player
safety rows (live, VoD, stream switching, minimize/restore, close/reopen, PiP,
speed/quality, gestures).
Risks: chat view reparenting now waits for the hide fade; control avoidance
uses the measured control groups (none before the controls first lay out); keyboard
avoidance relies on window insets; Robolectric cannot show real spring or
fade feel, so motion needs device QA.
