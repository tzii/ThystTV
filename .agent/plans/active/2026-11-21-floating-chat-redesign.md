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
- [x] `./gradlew test` (only `testDebugUnitTest` exists): 1593 passed when run
      on its own; see the progress log for the earlier combined-run OOM.
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

## Final PR summary draft

Summary: Redesigns floating chat as a dark rounded card over video with Clear
(text with a halo, no surface), Shade (default for untouched installs) and
Solid looks, text size and bold options, all switched live from a new Aa
popover without reconnecting chat. The card moves by a pill or the header,
springs to the nearer side, snaps to the top or bottom, keeps clear of the
controls, cutout, navigation bar and keyboard, tucks to an edge tab with an
unread count, resizes from the inner corner or by pinch, and remembers its
place per channel with a last-used fallback (1.7.0 positions migrate). New
options: Fade old messages, Pass touches to video, Reply bar. Fixes the
invisible drag bar catching taps, the card covering PiP, and the contrast
toggle recreating chat.
Tests: new `FloatingChatPlacementTest`, `FloatingChatLayoutStoreTest`,
`FloatingChatAppearanceTest`, `FloatingChatControllerTest`; the chat
transition suites run with the controller attached. `assembleDebug`,
`testDebugUnitTest` (1593) and `lintDebug` pass.
Human QA: `docs/MANUAL_QA.md` → "Floating chat redesign", plus the player
safety rows (live, VoD, stream switching, minimize/restore, close/reopen, PiP,
speed/quality, gestures).
Risks: chat view reparenting now waits for the hide fade; keep-clear bands are
measured from the controls' rows and fall back to fixed sizes; keyboard
avoidance relies on window insets; Robolectric cannot show real spring or
fade feel, so motion needs device QA.
