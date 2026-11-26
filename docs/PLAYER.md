# Player Architecture And Regression Notes

## Why This Area Is High Risk

The player is ThystTV's most important UX surface. Small lifecycle changes can cause double audio, stale fragments, broken minimize/restore, PiP regressions, gesture conflicts, black surfaces during transitions, or unreadable overlay controls.

## Staleness Note

The paths below are a map, not a guarantee. If code has moved, search for the current file/function and update this doc when useful.

## Bounded live recovery

Live recovery in the default Media3 service is bounded: three retries at
1s/2.5s/5s for transient connection/timeouts, direct HTTP 408/429/5xx and
behind-live-window errors. Continuous buffering is checked after 15s. A retry
waits at most 60s for validated connectivity, without repeatedly fetching while
offline. Thirty seconds of continuous actual playback, an explicit controller
Prepare on a failed/exhausted player (manual retry) or a new source resets the
budget. Routine Prepare calls from quality callbacks and brief ready/error flaps
do not. After exhaustion, an observed offline-to-validated transition permits one
additional three-attempt episode; further network flaps cannot rearm it until
healthy playback or explicit user intent. The service observes connectivity even
without a foreground player fragment, and rejects paused/remote/stale/ended or
permanently failed sources on return. A retry after confirmed loss seeks live.

Recovery retains the configured source, selected tracks, volume and play intent.
It avoids clearing media/surface; last-frame retention still needs device QA.
Behind-live-window failures seek to live. Pause, stop, source replacement, close,
Cast and service destruction cancel callbacks. Live network restoration no longer
rebuilds a paused player. Permanent/auth/404 errors remain visible; the existing
one-way custom-proxy HTTP fallback runs before automatic retries and is guarded
against stale or paused players. Exhaustion is a separate state, not a blanket
handled-error flag; later 404/auth failures stay visible. Feedback promises a
retry only while one is actually scheduled. Debug-only recovery traces contain
timing/state/error/offset values and exclude URLs, headers and account metadata.
Exhaustion asks the viewer to retry or reopen. The optional legacy backend and
ordinary foreground VoD recovery are unchanged.

Background VoD/clip playback (the app is stopped and the service keeps playing)
re-prepares only after the same transient errors, at most five times at
2s/5s/10s/20s/30s, and only while the same local source is still idle in a
transient error with playback requested. Pausing, receiver handoff and a later
permanent/ineligible error cancel any queued retry.
Thirty seconds of continuous playback, a new source or returning to the
foreground restores the budget. Other errors stay visible instead of being
re-prepared in a loop. Live and Cast playback never use this path.

Visible live chat cancels its current connection jobs on confirmed network loss;
validated return reconnects through the existing automatic-reconnect preference.
Manual disconnect remains respected. The activity also checks validation changes
after initial network availability, following Android's
[network callback ordering](https://developer.android.com/develop/connectivity/network-ops/reading-network-state).
Asynchronous chat cleanup captures the old connections and jobs before dispatch,
so a delayed close cannot cancel a newly created session. Background/PiP and
authenticated read/write/emote transports still need the manual device matrix.

Manual quality and audio-only MediaItem changes now use the current configured
source factory too, preserving its HLS parser, HTTP headers and load-error policy.
Replacing the source changes the delegate; offline/download playback clears it
and uses the normal default factory. This also covers local playback with Cast off.

## Core Invariants

- Only one active player session should exist.
- Starting a new player should close or safely replace the old player.
- Closing a player should release playback resources.
- Minimize/restore must not leave stale fragments.
- Sleep timer should close only the current player.
- PiP auto-enter should be enabled only while a player is open.
- Stream switching must not leave old audio playing.
- UI controls should stay responsive during live/VoD transitions.
- Overlay surfaces should remain readable over arbitrary video content.
- Pending Cast playlist resolution still owns the service session even while the
  receiver queue is empty. Explicit stop/clear and source close invalidate its
  delayed load and fallback snapshots while preserving the configured local
  source factory. Deferred fallback disconnects recheck the source request as
  well as the Cast route generation.
- Each source-start command carries a unique request ID. The service records it
  only after accepting that source. Screen close sends a conditional close for
  that ID; a pending/rejected/stale screen cannot stop another retained source,
  and a source accepted before its UI acknowledgement can still be closed. UI
  save/background changes require an accepted start. The request ID survives
  screen recreation; a returning controller can recover acceptance from the
  service after its earlier result was cancelled. Remote live autoplay waits
  until the resolved item is installed so an older paused item stays paused.
- A screen's own fresh start (including a saved-position resume) is marked
  pending, and the mark is saved with the screen, until its command is sent. The
  ownership query never starts it a second time, so a recreation or
  process-death restore loads once. A loaded screen whose service lost its media
  restarts on connect, but not again while that restart is still resolving (its
  saved position, playlist or clip, until the start takes the result); a load
  that failed is retried on the next connect. A restored screen
  whose earlier source the service still holds (Don't keep activities) owns that
  source, so its first exit pauses or switches to audio only as configured,
  online or offline.
- A start resolved while the screen was stopped is sent once the new controller
  connects. Releasing a controller that is still connecting never starts anything.
- A PiP Audio only tap made while this screen's started source is still on its
  way (not yet accepted, or accepted before its qualities loaded) keeps the PiP
  window and applies once both have happened; audio only needs the audio-only
  rendition. Leaving PiP, stopping or a rejection drops it; a second tap leaves
  as before.
- Quality, Speed, Stream volume, and More share one player-owned popup host; only one
  can be attached at a time, and teardown/minimize/PiP must remove it with the player view.
  The host's root carries `elevation="24dp"` so open menus draw above the floating chat
  host (10dp). `<include>` ignores `android:elevation`, so it must stay on the included
  root (`PlayerPopupContentTest` checks the order).
- VoD playback state changes open the controls only when the play button is
  needed (paused, ended, idle). A rebuffer during playback only renews controls
  that are already up (`PlayerControllerPolicy`), as Media3's PlayerView does;
  before 1.7.1 every stall brought them up and stepped the floating chat aside.

## Mini-player geometry

The mini-player uses a 16:9 viewport in portrait and landscape, independently of
the window's aspect ratio. Its base rectangle fits inside the sliding container
before uniform scaling, so an extra-wide phone cannot clip the video before the
mini-player transform. Placement uses the measured player bounds, including
after rotation or resizing. Non-16:9 sources keep normal Fit letterboxing.
Placement and free-movement bounds subtract the measured player child origin,
not assumed system insets: START gravity in RTL and parent padding can move that
origin. Reapply placement after layout so extra-wide RTL windows remain inside
the physical corner/bounds without changing the viewport, margin or scale.

`PlayerLayout.isMinimized` is synchronized by `updateFoldLayout`, including
minimize, maximize, view initialization and PiP. It is separate from portrait
measurement: fold splits and the maximized landscape player retain their own
geometry. Never shrink a full landscape window as the mini-player viewport or
use different X/Y scales; that preserves window-shaped empty bands and distorts
the video. `PlayerMiniPlayerLayoutTest` checks native view measurements across
phone, near-square and tablet windows and restoration of the full-player area.
`PlayerMiniPlayerPlacementTest` drives real fragment minimize/init and production
drag-release placement, asserting transformed Android bounds for RTL/LTR,
extra-wide windows, padding and both orientations.

## Pinch and double-tap chat ownership

A pinch can supersede a double tap when its first finger is interpreted as the
second tap. Capture the chat flags and saved-open preference before the accepted
double tap cycles chat. On pinch takeover, restore that snapshot, cancel the chat
fade, reparent the existing chat view and restore sidebar/video geometry and the
chat button icon. Another cycle cannot undo hidden/sidebar/floating's three-state
transition. Clear the snapshot at pointer-sequence boundaries and view destruction.

`PlayerPinchChatTest` exercises the real fragment transitions, listener, arbiter,
Android containers and preferences with fragment attachment/network chat mocked.
Device gesture timing and playback still need the manual matrix in `MANUAL_QA.md`.

## Swipe to show or hide chat

In full-screen landscape with the controls hidden, a sideways swipe shows or
hides chat (`PlayerChatSwipePolicy`, setting `player_gestures_swipe_chat`, on by
default). Live streams have no horizontal gesture, so the swipe may start
anywhere. Seekable media (VoDs, clips, downloads, live rewind) keep seek and
speed and accept it only from a 56dp strip just inside the chat-side edge
exclusion (the system gesture inset, or 30dp with three-button navigation).
The chat side is the end edge, so RTL mirrors the strip and the directions.

The swipe claims `Owner.CHAT_SWIPE` like any single-finger gesture and acts once,
after mostly sideways travel: max(48dp, 8% of the width) on live, 24dp from the
seekable strip. A strip swipe that hides chat moves toward the nearby edge, so
its travel is also capped at 80% of the room left to that edge. Moving away from the
chat side shows the sidebar; moving toward it hides sidebar or floating chat. It
never cycles modes, and does nothing when chat is already in that state or chat
is disabled. `PlayerChatSwipeTest` drives the real listener, arbiter and chat
transitions; edge-strip reachability next to the Back gesture needs devices.

## Extended zoom

Setting `player_extended_zoom` (off by default, hidden before API 24) lets a
pinch magnify past Fill. Below Fill nothing changes: `PinchZoomController`
keeps the centered Fit-to-Fill band and its detents. Fill's elastic resistance
becomes a wall the fingers can push through: past its saturation the drawn zoom
continues from the resisted size, up to `magnifyCap` (4x Fit, or twice Fill on
very wide shapes), which resists in turn. A release within 10% of Fill
(`MAGNIFY_DETENT`) returns to plain Fill. On shapes `supports` rejects, Fit and
Fill become neighbouring detents so magnifying still works.

While magnified, the pinch keeps the picture point under the fingers in place
(`PlayerMagnifyGeometry.anchoredTranslation`) and clamps the pan so the
picture's edges never enter the player. Below Fill the pan drops to zero.
The fragment keeps `magnification` (relative to Fill) and a pan stored as a
share of the picture, so side chat, folding and video shape changes keep the
same picture point centered. Canonical surface geometry therefore includes a
translation; `setSurfaceScale`, `applyCanonicalSurface` and
`animatePinchSurface` set or animate it with the scale.

While magnified, a one-finger drag that starts with the controls hidden pans
the picture instead of brightness, volume, seek, speed or the chat swipe
(`canPanMagnified`, owner `MAGNIFIED_PAN`). It moves from the drawn position
up to the same edges (`PlayerMagnifyGeometry.pan`), with no feedback pill,
haptic or fling. A second finger can turn the pan into a pinch, which starts
from the panned picture; lifting it before the pinch claims returns to the
pan. The detector never sees a finger land or lift, so the listener follows
the fingers' midpoint and re-anchors whenever their count changes.

The magnification is session-only and never saved. Choosing a display mode,
minimizing, rotating to portrait, entering PiP or turning the setting off
draws plain Fill. More → Display mode lists it as "Zoom N%". Double tap stays
the chat action. The view-owned preference observer immediately clears the
drawn scale and pan when Extended zoom is disabled, including on API 24 and
devices without PiP; re-enabling cannot revive the previous crop. A pinch still
in flight cannot recommit disabled magnification. `PinchZoomControllerTest`,
`PlayerMagnifyGeometryTest` and
`PlayerPinchZoomTest` cover the controller, geometry, fragment surface and
magnified pan; `PlayerGestureArbiterTest` covers the pan-to-pinch handoff.

## Half-folded layout

Half-opening a foldable does not change the configuration, so `PlayerFragment`
follows Jetpack Window Manager's `FoldingFeature` while the player view is
started and splits the maximized player at a `HALF_OPENED` hinge:

- Tabletop (hinge across the window): video above the hinge, chat below. Floating
  chat is docked below the hinge and the chat cycle skips floating, because a
  floating window would cover the video pane. Docked chat floats again once the
  posture ends, unless the viewer changed the chat mode meanwhile. Hidden chat
  leaves the lower pane empty rather than drawing video across the hinge.
- Book (hinge down the window): video and chat side by side only while chat is
  showing; hidden or floating chat keeps the whole screen for video.

`PlayerFoldLayoutPolicy` maps the hinge and the sliding layout's content area
(both in window pixels, so status-bar and cutout padding are excluded) to pane
sizes, and returns no split when the hinge does not cross the whole window, as in
split screen, or a pane would be under 160dp. With the keyboard open, a smaller
tabletop chat pane is allowed: chat reserves 120dp above the keyboard where the
window has room, even if that covers the bottom of the video. IME inset changes
reapply the split, including keyboard height changes without a window resize.
Minimize, PiP, an unfold, or the "Half-folded layout" setting being
turned off restore the normal portrait or landscape geometry. Every chat
transition, orientation change, minimize/maximize, PiP change and sliding layout
resize re-runs the split; geometry changes are idempotent, so repeated calls do
not relayout.

`PlayerFoldLayoutPolicyTest` covers the pane math and `PlayerFoldLayoutTest`
drives the real fragment geometry and chat transitions with a simulated hinge.
Hinge timing and real postures still need the device matrix in `MANUAL_QA.md`.

## Floating chat

`PlayerFragment` decides when floating chat is on (`isFloatingChatEnabled`) and
moves the one `ChatFragment` view between `chatFragmentContainer` and
`floating_chat_container` (`reparentChatView`), so the connection and history
stay. `FloatingChatController` owns how the card looks and moves. Keep these:

- One chat fragment, never recreated to change the look. `ChatFragment.setOverlayStyle`
  rebinds rows in place; `null` puts the ordinary sidebar look back.
- `FloatingChatHostLayout` is a full-screen sibling of `slidingLayout` that takes
  no touches itself. It lays its children out locally on the next frame, so a
  resize never re-measures the player.
- Per-touch work only writes `x`/`y`. Grip resize is coalesced to one size change
  per frame; release and tuck use `SpringAnimation`, and a keep-clear move glides
  over the controls' own 250 ms fade (`glide`). Do not add layout writes or new
  animators per `ACTION_MOVE`. A size change lays the card
  out at once, so a spring that starts in the same frame uses the new size.
- A drag follows one finger; if it lifts while another is down, that finger takes
  over from where the card is. A pinch takes over a drag or grip resize under its
  first finger: the cancel it sends them does not end the interaction or settle
  the card. Grip and pinch store a size only when it changed, so a tap on the
  grip never stores a height a short screen cut the card down to.
- `FloatingChatPlacement` is pure. The card rests anywhere: its place is a share
  of the free travel across and down (`xFraction`/`yFraction`, 0 = flush left or
  top, 1 = flush right or bottom) plus a size in dp, inside bounds that keep clear
  of cutouts (also while the video draws behind one), the status bar and the
  navigation bar or keyboard. The controls never shrink those bounds and never
  move the card. While they show and the keyboard is down, a card over a control
  group (`floatingChatControlBands`: top rows, bottom rows and seek bar,
  play/pause, ±10, each its own rectangle) fades to 60% so the controls show
  through (`refreshCovering`), and a touch that starts on one of those groups
  goes to the player (`FloatingChatCardView.passThroughAt`), except on the
  card's visible header or grip. When the controls cover the card's top 48dp
  but not its bottom, the header shows on the bottom edge (chat padded above
  it); the grip hides while it would sit on a control or under a bottom
  header. Mid-drag the header keeps its edge, and it only changes edge while the
  controls show. The side (for the grip, tuck and TalkBack) is the half the card
  is on. While a brightness, volume, seek or speed slider shows, the card fades to 25%
  (`setDucked`): the arbiter claim starts it, and the feedback's hide runnable,
  or the touch's end when no slider showed, brings it back. A short screen drops
  the card's oldest (top) lines first; above the keyboard the card may go below
  its usual minimum height, and a window narrower than the card narrows it. The card's
  `layout_gravity` is `top|left`, because `x` must mean the same in RTL.
- A release stays exactly where the card was let go: the release velocity never
  carries it further, an edge within 8dp pulls it flush, and it is stored as
  fractions of the clear screen. It tucks when 35% of the card is past a side,
  or on a fast (1800 dp/s) outward sideways fling from a card that started
  within 24dp of that edge; a card thrown from the middle lands flush instead.
  Stored layouts still write the old side and anchor next to `x`, and entries
  written before free placement read flush with their side and anchor.
- Layouts are stored per channel plus a last-used fallback in the single JSON
  preference `floating_chat_layouts` (`FloatingChatLayoutStore`, 50 channels).
  1.7.0's `floating_chat_x/y/w/h_<channel>` keys migrate once and are removed.
- The header (Aa, tuck, close, optional compose) and the resize grip show only
  with the player controls and are `INVISIBLE` otherwise, never just transparent,
  so they cannot take touches. While the viewer drags, resizes or pinches,
  `holdControls` stops the controls from auto-hiding. Once a header drag passes
  the touch slop, or the grip or a pinch changes the size, the controls fade out
  (`dismissControls`) while the header and grip stay under the finger
  (`chromeHeld`), so the card is placed on the clear picture and never jumps when
  the controls go. A tap does not hide them.
  `FloatingChatHeaderLayout` takes over a touch that moves past the touch slop,
  so a drag that starts on a header button moves the card; a tap still presses
  it. The grip hides while the message box is open. While the header shows, the
  chat container is padded by its height, so its buttons never sit on a chat
  line; the padding goes once the header has faded out.
- The Aa popover sits beside the card's resting bounds (clear of the controls), on the
  video side first and level with its top (`PlayerPopupPolicy.placeBeside`), so
  the card stays in view while it is styled. Where the panel fits on neither
  side, as in portrait, it anchors to the Aa button like the other menus.
- `FloatingChatAppearance` maps the existing opacity preference: under 20 is
  Clear (no surface; `ChatMessageTextView` draws a halo around the text), under 80
  Shade, otherwise Solid. Aa writes 0, 55 or 92; untouched installs read 55.
  Pass-through makes the card ignore touches only while the controls are hidden.
  Appearance changes from Aa or Settings apply live through the preference
  listener. A change within the same mode restyles rows with the
  `ChatAdapter.PAYLOAD_STYLE` payload, without rebuilding their text.
- Clear draws each row twice (soft shadow, then tight outline). `ChatHaloPass`
  tells spans which pass is running: emotes and badges draw only in the second,
  and painted names draw the outline under their paint. An image name paint is
  drawn through its glyphs (`saveLayer` + `DST_IN`), never as a filled box.
- Over video, readable name colours come from the dark card, also in the light
  app theme (`ChatAdapter` keeps separate overlay colour caches). The message
  box takes the card's dark surface with white text and icons in every theme;
  the emote menu keeps the app surface.
- The card hides in PiP, when minimized and in portrait; PiP exit and maximize
  show it again. The host shares `slidingLayout`'s size and centre pivot, so the
  player's transform carries the card: a swipe down moves it with the video
  (`followPlayer`, fading toward the minimize point) and a short swipe brings it
  back (`returnWithPlayer`, on every touch end). Minimize freezes it before the
  controls and status bar change (`holdForPlayer`), then it rides into the
  mini-player on the player's own 250 ms curve and has faded by 60% of the way
  (`hideWithPlayer`). Maximize grows it out of the video from the player's pose
  (`showWithPlayer`). Keep-clear moves wait while the host moves with the player.
  A minimize that starts after a quick maximize leaves the card alone. Tucking leaves an edge tab with an unread count. While the card
  is hidden or tucked, animated emotes and the message glide pause, including
  emotes on rows bound while paused. Fade old messages (`ChatAgeFader`) wakes
  when the next row reaches 10 seconds, not on a fixed tick.
- `hideFloatingChat` fades the card with its lines and reparents in the hide
  callback. The callback reads the current mode, so a show or hide in between
  wins and the chat view always ends where the mode says. Showing the sidebar,
  portrait and PiP end a running fade at once (`finishFloatingChatHide`), so the
  chat view is back in the sidebar before it shows. "Hide chat" from the More
  menu hides a floating chat the same way the chat button does.
- `onDestroyView` puts the chat back on its sidebar look and turns the reply bar
  off, so the chat's next view does not start in overlay style.
- New lines glide in (`ChatFollowGlide`) only over video, outside PiP and with
  system animations on.

`FloatingChatPlacementTest`, `FloatingChatLayoutStoreTest` and
`FloatingChatAppearanceTest` cover the pure rules; `FloatingChatControllerTest`
drives the real card, tab and springs. The chat transition suites
(`PlayerChatSwipeTest`, `PlayerPinchChatTest`, `PlayerFoldLayoutTest`) run with
the controller attached.

## Android TV remote input

TV login exposes “Sign in with your phone”, explaining that some account features
still need browser sign-in. The existing device grant now has a locally generated
QR, readable code/instructions, provider expiry and an explicit “Check sign-in”
action. Checks respect the provider interval and do not poll in the background.
The countdown suspends while hidden and uses elapsed time when resumed. New code,
expiry and teardown invalidate stale results. Browser sign-in retains its original
Helix/GQL routing. The controller does not persist codes/tokens or send them to
a QR service. Validation/storage and device scopes use the existing auth flow.
Denied, invalid-code, expired and token-validation failures have distinct feedback
and remove stale QR actions. If the optional device token fails validation after
Helix was already validated, sign-in can finish with that held account. Instructions
also describe activation from a phone browser or a second device accurately.

Television UI mode enables remote focus routing; phone/tablet input is unchanged.
The empty activity player container is not a focus target. On browsing roots,
Back returns to the selected bottom-navigation item; UP returns to the current
page, without scrolling to the end of a paged feed. Focus rings are separate
from the selected destination indicator. TV toolbars, tabs and Stats ranges stay
visible, including on TV images that also advertise touchscreen support. Profile
headers can collapse to their pinned toolbar/tab area so video lists retain space.
If that collapse covers the initially focused profile action, a one-shot layout
check moves focus to the selected visible tab or toolbar action. It preserves
focus that has already moved into page content or the player.

A first D-pad/Enter press reveals hidden player controls and focuses play/pause
when visible, otherwise the first available action such as Quality;
subsequent keys use native control handling. Remote input renews the hide timer.
Visible controls and player popups contain directional focus above the browse UI.
Back dismisses a popup, then hides controls, then uses existing minimize behavior.
A focused mini-player restores with CENTER/Enter; a TV-only Close action invokes
the existing player close path. Speed/volume slider changes persist from remote
input. Speed labels round the slider's float to two decimal places before compact
formatting, so a 1.05x setting cannot appear as 1.04x. Existing media-session
hardware keys keep their original handling.

See `TESTING.md` for emulator evidence and its limits. Real live/VoD decoding,
switching, minimize/restore, close/reopen, PiP/background, speed/quality, gestures
and floating-chat behavior remain the player regression checklist.

## High-Risk Files

Update this list as the code evolves.

- `app/src/main/java/com/github/andreyasadchy/xtra/ui/main/MainActivity.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/main/LaunchActivity.kt` and the
  launcher filters in `AndroidManifest.xml`: the home-screen icon reaches the player's
  activity through it, so check PiP and minimized-player return from the icon.
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/chat/`
- `app/src/main/java/com/github/andreyasadchy/xtra/player/lowlatency/`
- player-related layouts in `app/src/main/res/layout/`
- player-related strings in `app/src/main/res/values/`
- player settings/preferences in `app/src/main/res/xml/`
- Media3 / ExoPlayer dependency declarations in Gradle catalogs/build files

## Recent Lessons From ThystTV Fixes

- Media3 1.11's old `Callback.onConnect` default is a placeholder. The default
  session service negotiates through `PlaybackSessionCallback.onConnectAsync`
  with controller-aware defaults; copying the old default loses surface, tracks
  and playback permissions even while the service itself continues playing.
  Keep existing custom commands for trusted controllers and Media3's read access
  for untrusted controllers. A dependency update must test visible live/VoD video
  and usable quality controls, not merely an advancing playback position.
- For issue #5, logs did not show a clean app `FATAL EXCEPTION`; the safer fix was the narrow Media3/ExoPlayer rollback and HLS parser compatibility change, not broad WIP player sync.
- Quality menu labels can regress to HLS variant indices after chat-only -> auto transitions. Preserve readable labels derived from labels, format height/frame rate, or URL path.
- Mixed-codec Quality choices keep resolution as the primary label and show a standardized
  codec name as secondary metadata. Audio-only and Chat-only stay outside the scrolling
  video grid so they remain reachable in compact landscape players.
- Selected Quality/Speed chips render as solid primary pills with luminance-matched
  content color (`PlayerPanelColors.onSelected`); do not revert to low-contrast blends.
- Quality chip column counts are derived from measured bold label widths so labels such
  as "1080p60" never truncate; do not return to fixed 4-column grids with silent clipping.
- VoD and live menus may legitimately differ. Do not add chat-only to VoD unless the data/model supports it.
- Floating chat should use a dark video-overlay palette in both app themes; background opacity remains user-controlled, and Clear (no surface) must stay readable through the text halo.
- Stats/player overlay controls need compact and landscape checks because label clipping and stacked graph labels have happened before.
- Any view adapter tied to fragments can leak destroyed views. Clear listeners/adapters in `onDestroyView()` when changing RecyclerView or fragment lifecycles.

## Player System-UI Listener Ownership

The landscape decor-view system-UI listener is owned by `PlayerSystemUiListener`
and the player's **view** lifecycle. Detach it on portrait layout and explicitly
at the start of `onDestroyView`, as well as on the view lifecycle's destroy event.
Its per-decor registration replaces and invalidates the previous callback; old
player teardown must not clear a newer player's registration. Detach drops the
callback, lifecycle and decor references, including on replacement. Do not install
a raw fragment-capturing callback on the activity window: the decor can outlive
the player and retain a closed fragment after rotation/stream switching.

## Player Popup Ownership

Quality uses the same 48dp gear button on compact, landscape, tablet and resized
surfaces. Never replace it with the selected-quality text based on width or label
availability: rotation callbacks can still see the previous layout's width, and
replacing the trigger also changes its popup anchor. The menu retains the selected
quality; the gear's accessible description includes it when available.

`PlayerFragment` owns the full-player dismissal host and delegates each popup's controls
to a short-lived binder. Quality, Speed, Stream volume, and More use the same bounded,
inset-aware, trigger-relative placement and alpha/scale motion. The host consumes panel
and outside touches, keeps controls visible while open, closes on back/outside tap, and
restores focus plus the normal control auto-hide timer on dismissal.

Popup motion animates the bounded container in both directions (220ms eased reveal,
140ms exit), with its pivot clamped toward the cached trigger. Never animate the inner
card on entry and the viewport on exit: scrolling makes their geometry differ. The
scrim fades during dismissal, and replacement/teardown cancels pending motion. Platform
animator duration settings apply. Quality/Speed chips provide bounded ripple feedback.
Selected chip foregrounds use the higher-contrast black/white color for the accent.
Quality binds text colors immediately because inherited drawable state can leave the
first, unattached frame using the unselected text color. Speed uses a short visual
heading with a full playback-speed accessibility label, keeping 200% text readable.

Cards close via outside tap or back. Quality, Speed, and More also have persistent
header close buttons with 48dp touch targets; Stream volume keeps its auto-dismiss timer.
In portrait a light scrim dims video/chat and passes taps to the outside-dismiss handler.
Taps in the card itself do not dismiss it unless an option's action requests dismissal.

More keeps its existing grouped action order and preference gates. Its display-mode row
intentionally opens the existing single-choice alert above the embedded host; the four
top-level player popups themselves must not return to DialogFragment or bottom-sheet
ownership.

Placement rules: popups prefer the space above their trigger, fall back to below it, and
when the surface is too short for either they pin to the edge nearest the trigger
(bottom-bar buttons such as Stream volume stay bottom-anchored; top-bar triggers such as
Quality, Speed, and More stay top-anchored). Horizontally, left-side controls align the
panel's left edge and right-side controls align its right edge before bounds clamping, so
placement remains visibly tied to the button rather than drifting around the surface.
The host is a sibling of `slidingLayout`, above floating chat, **not a child of the
16:9 `PlayerLayout`**. Portrait may use the visible fragment space over chat; landscape
is bounded by the video's visible screen area. Surface and window rectangles use screen
coordinates consistently; trigger coordinates are relative to the host. Width classes
use the available video width, not the full window width including side chat.

`PlayerPopupContent` preserves the card and its first/header row, moving only options
into `playerPopupViewport`. More's old inner scroll view is unwrapped so only one scroll
view handles options. Natural content height is measured at final width, then the card
height is bounded and its weighted viewport receives the remaining space. Never wrap
the whole card in a scroll view: that hides the rounded outline, title, and close button.
Quality chips keep single-line Auto vertically centered beside codec pairs, and chip
heights grow when enlarged fonts need more room.
Popup content is bound before the host is shown, and `showPlayerPopup` places the
container (explicit measure, no layout pass needed) before the host becomes visible,
so the reveal animation's first frame is already at the anchored geometry. Placement
application is idempotent (geometry written only on change), host-bound changes trigger
repositioning, and the first valid trigger rect is cached as the popup's anchor
for its whole lifetime: while the cache is empty the trigger is re-read (a popup that
opened from fallback geometry corrects itself when the trigger gains bounds, for
example controls were GONE at open time), but once cached, later control-bar reflows —
quality-label or viewer-count text changes — must not drag a visible popup around.
Do not remove these guards; stale inherited margins, reveal-before-place frame races,
and untracked trigger moves caused jump/multi-press misplacement reports.

## Saved resume and headset fallback

`PlayerViewModel` resolves automatic saved positions for all player backends. A
position at or beyond a known positive duration restarts at zero when a network
VoD has final remote thumbnail metadata. A processing thumbnail, absent thumbnail
or locally cached bookmark image cannot establish completion: the broadcast may
still be live and growing, so its saved position is retained. Unknown/invalid
durations and unfinished positions are also retained. Network argument durations are
seconds converted to Long milliseconds; downloaded videos use their own duration,
not the source VoD offset. No eager database reset is needed; normal playback saves
the new position. Explicit timestamp requests bypass automatic resume, and existing
active-session restoration remains unchanged. Do not apply the completion rule to
quality changes or minimize/restore of an active player.

The start position travels with the source (`setSource(..., startPositionMs)`,
`setMediaItem(item, positionMs)`), never as a `seekTo` after `prepare()`. Until a
new item's timeline is known it is not seekable, and the MediaSession/Cast
forwarding players drop that seek, so resumed VoDs started at zero (issue #61).
Quality and audio-only source swaps use the same rule; live swaps start at the
live edge, since the old window offset can fall outside the new playlist window.

The optional legacy `ExoPlayerService` supplements Android media-session dispatch
only for unhandled previous/next key-down events. It checks seek command availability
and ignores cancelled/release events. The default Media3 path and platform
MediaPlayer path are unchanged; never introduce a second handler that repeats an
already-handled seek. Test actual headset and background dispatch on devices.

## Side gesture feedback

Gesture feedback follows Material 3 Expressive and always reads as a video
overlay. It uses the dark-scheme accent roles of the theme's primary color
(`PlayerPanelTheme.gestureFeedback`) on a dark translucent container, in light
and dark app themes alike.

Settings > Player > Gestures > Gesture indicator
(`C.PLAYER_GESTURE_FEEDBACK_STYLE`, `PlayerGestureFeedbackStyle`) picks one of
two styles. Both show the same readings; neither shows a label such as
"Brightness" or "Seek".

**Sliders** (the default) use Material 3 Expressive slider geometry: a 40dp
track split by a 4 × 44dp handle with 6dp gaps, 12dp outer and 2dp inner
corners, and a small dot marking a reference point. The accent part runs from
the start of the track to the handle; the dark part is the rest.

- Brightness and device volume (vertical swipes) show a vertical slider at the
  edge of the half being swiped (`PlayerGestureLevelView`), 176dp long and capped
  to 60% of the player. It has no number. The icon sits in the accent fill and
  moves above the handle when the fill is too short; a slider too short for either
  drops the icon. A stop dot marks the top until the handle reaches it. The icon
  follows the level (auto/low/half/high brightness; off/low/high volume).
- Both follow the finger directly (`PlayerLevelGesturePolicy`): a drag over the
  full player height covers the whole range, measured from where the swipe
  locks, so the first update shows the starting value. The handle moves
  between whole percents with no animation, and the shown value changes one
  percent at a time. Brightness applies whole percents from 5% up, and Auto
  below 5%. Device volume has only a few steps (often 15 or 25), so the device
  takes the nearest step while the shown level stays smooth, as the system
  volume slider does; only 0% mutes. The next volume swipe continues from the
  shown level while the device is still on the step the last swipe set.
- Seek, speed and pinch zoom (horizontal gestures) show a horizontal slider at
  the top of the video (`PlayerGestureSliderView`), 300dp wide, or 220dp on
  players shorter or narrower than 320dp, and narrower still in a tiny window.
  The icon sits at the start of the track and the value at its end, with
  tabular digits. Near the end of the track the value moves into the accent
  part beside the handle. A value too long for either part shrinks instead of
  clipping. The dot marks where the gesture started and hides under the
  handle, icon or value. The slider draws left to right in RTL too, because
  right is always later, faster or closer to Fill.
  - Seek shows the signed change (`+1:25`, `−0:30`), and the handle and the
    dot sit on the whole VoD, so the track shows where in the video the seek
    lands. Back at the start, the icon turns into undo and the value reads
    "Cancel". When the seek reaches the start or end of the video, the handle
    stops there. A small dark caption centered under the track shows the
    target time and the VoD's length (`24:00 / 3:12:45`); it stays in place
    while the handle moves, keeps showing the position at "Cancel", and
    shrinks to the track's width when it is longer. With spoiler-free VoDs the
    track is relative instead: the start sits in the middle and the handle
    follows the finger, reaching an end after half the player width, and the
    caption shows the target time only, so nothing reveals the position on
    the track or the length.
  - Speed shows the speed (`1.50x`, the speed button's format) on a
    logarithmic 0.25x–4x track with 1x in the middle.
  - Pinch shows the mode or zoom it points to (`Fit`, `Fill`, `125%`). To Fit
    or Fill the handle grows from the middle toward the mode it arms. Free zoom
    is placed between Fit (left) and Fill (right), with the dot where the pinch
    began.

**Number in the center** shows every gesture as one large number in the middle
of the video (`PlayerGestureValueView`), on a soft dark glow rather than a
container. The glow is 60% black behind the value, so white text stays
readable over white video. The number is 48sp (36sp on small players) and
shrinks to fit.

- Seek shows the change, with the target time and the VoD's length small
  under it (`24:00 / 3:12:45`). Spoiler-free VoDs show the target time only,
  never the length. Back at the start it shows the undo icon over "Cancel".
- Brightness and volume show their icon above the percentage.
- Speed and pinch show only the value.

Plain messages (the pinch hint, live-rewind caught up) use the fully rounded
top pill in both styles: an accent icon badge, a 6dp bar with a track gap and
end stop where a level applies, and a tabular-digit label.

Sliders draw only plain shapes, with no shadow: a blurred shadow under shapes
that change on every touch update costs offscreen passes each frame and made
them stutter. Their views include a 4dp pad on each side, so the entrance
spring's overshoot is never clipped, and placement measures margins and the
top gap to the drawn track.

Touch updates are cheap by design. A gesture writes the overlay's layout
params only when its placement changes, so an update redraws the indicator
without laying out the player window. Brightness touches the window and
volume calls the audio service only when the applied percent or step
changes.

A freshly shown indicator springs in with the "fast spatial" spring
(`PlayerSpringInterpolator`, damping 0.6, stiffness 800):

- the edge slider grows from its edge;
- the top slider and the pill drop in;
- the number grows from its center.

Updates while visible move the handle in place instead of replaying the
entrance. Animator duration scale 0 lands on the final state.

Brightness is always on the physical left and volume on the physical right,
matching the screen halves that drive them, including in RTL. Their margins
come from `measureEdges`, which reads the player's offset in the window and
the root display-cutout and back-gesture insets.

- When both player sides lie on the window edge, both sliders sit the same
  distance from their physical screen edge. The cutout padding of the
  landscape player no longer pushes one slider further in, and with "draw
  behind cutouts" the slider stays clear of the camera.
- When a side is inside the window, such as the side chat's edge, the sliders
  mirror around the player instead.

`PlayerGestureFeedbackRenderTest` renders previews under
`app/build/ui-previews` with native Android layout and checks:

- side-chat and cutout placement;
- the edge slider's icon placement and stop dot, and that it follows each
  update at once;
- that updates at the same placement request no layout;
- the top slider's handle, start dot, icon and value placement at every
  position, in compact and narrow sizes;
- the centered number's size, centering and text fitting in both sizes;
- entrances, short players and pill restoration;
- overlay contrast in every app theme.

Device gesture timing, haptics, playback and actual window insets still
require the human player matrix below.

## Horizontal scrub gestures

Seek and speed share one set of rules (`PlayerScrubPolicy`):

- **Lock.** A drag is classified from touch-down, not from its first move. Vertical
  wins as soon as it dominates, as before. Seek or speed needs 24dp of sideways
  travel within about 27° of horizontal (|dx| ≥ 2|dy|). Until then the drag stays
  undecided, so a tap that slides, a diagonal swipe, or the first finger of a pinch
  does not seek. A vertical swipe that starts with a sideways wobble still becomes
  brightness or volume, including on live streams and in the VoD chat-swipe strip:
  chat swipes wait for the same horizontal lock (capped at the strip's trigger near
  a narrow edge) before claiming the drag, while keeping their own trigger distance.
- **System gesture edges (`PlayerEdgeZonePolicy`).** Window-edge system gesture
  insets are mapped into `playerLayout` coordinates so cutout padding and an open
  side chat never stack a second deadzone inside the video. Top and bottom edges
  block all swipes (notification shade and home bar). Left and right edges still
  block horizontal swipes (chat swipe, seek, speed) and magnified pan for Android's
  Back gesture, while allowing clearly vertical brightness and volume swipes
  (`|dy| ≥ 2|dx|`) so camera-cutout sides do not create a deadzone when drawing
  behind cutouts.
- **Start detent.** Travel is measured from the near edge of a 10dp band at the
  lock point, so the value starts changing straight away. Dragging back into the
  band returns exactly to the starting value, so a seek can be undone by feel.
- **Seek distance.** `PlayerScrubPolicy.seekOffsetMs` maps finger travel to time
  on one accelerating curve, the same on every long video:
  `0.3 s × dp + 10 s × (dp / 100)³`, scaled by the gesture sensitivity setting.
  That is about 6 s at 20dp, 40 s at 100dp, 2:20 at 200dp, 6 min at 300dp and an
  hour at 700dp. Small corrections stay precise on a six-hour VoD, and a long
  swipe still crosses it. A drag across the whole player never covers more than
  the whole video, so a short video spreads over the full width instead.
- **Live seeking without repeats.** The player seeks only when the target changes.
  Holding still, staying in the detent, or pushing past either end of the video
  issues no repeated seeks.
- **Haptics**, when the gesture haptics setting is on, use one vocabulary for every
  gesture (`PlayerGestureHaptic`, `PlayerScrubHapticTracker`, `PlayerGestureHaptics`):
  - a start tick when a swipe claims its gesture;
  - step ticks: every volume step, every 10% brightness, every quarter speed, and
    seek intervals counted from the start. The seek step is the round interval
    (1 s up to 1 h) the swipe covers in about 32dp at its current pace, so ticks
    stay evenly spaced under the finger while the seek speeds up;
  - a detent when a scrub returns to or passes over its start, and when a pinch
    arms Fit or Fill or snaps free zoom to an end;
  - a limit at either end: auto or full brightness, mute or maximum volume,
    0.25x or 4x, and the start or end of the video.

  Step ticks closer than 30ms apart are dropped. On Android 14 and later these use
  the segment tick effects and on Android 11 and later the reject effect. Older
  releases fall back to clock tick, context click and virtual key.

## Live rewind controls

The live-rewind button is available for maximized live streams in portrait and
landscape. Portrait uses one bottom bar: compact uptime/viewer indicators share
space with the existing actions, while fullscreen and the other right-hand
controls stay pinned. Play/pause stays centered. A native HorizontalScrollView
provides horizontal overflow for optional buttons and enlarged text instead of
creating a second row, dropping controls or shrinking the user's text size.
The original views/listeners are reused; leaving portrait live playback restores
the original XML parents, parameters and stats styling. Native layout handles
later text changes without a deferred fragment callback. Minimized players,
recording replays and TV keep their normal layout. `PlayerLiveRewindControlsTest`
checks defaults, overflow/RTL/200% text, later updates and repeated restoration.

Unless the local rewind buffer below covers a request, every preset, including the last
3 minutes, plays the broadcast's recording. Twitch's
live media playlists reach back only about 30 seconds (measured November 2026), so
seeking within the live source cannot serve them, and larger Media3 buffers do not
change that. With neither a recording nor saved video, the player stays live and shows the
unavailable message.

A rewind never starts at exactly 0 ms (`LiveRewind.startPosition`). The recording of a
broadcast that is still going and the local buffer are growing EVENT playlists, which Media3
plays as live, and its `MaskingMediaSource` replaces a start position of 0 with the live
window's default position, the live edge. Before 1.7.1, "Watch from beginning" (and a preset
longer than the recording) therefore started about 30 seconds before the end. The clamp is
applied twice: on the handoff in `startPlayer()`, and in `playVideo()` for a rewind restarted
after process death or a lost source, which no longer has its handoff and may resume at 0.
Ordinary VoDs are not clamped.

## Local rewind buffer

Off by default (`C.PLAYER_LIVE_DVR`, `C.PLAYER_LIVE_DVR_SIZE`), and wired only to the
default Media3 backend. On the others the buffer stays empty and only recordings are used.

- `LiveDvrHlsDataSourceFactory` tees the player's own playlist and segment loads into
  `LiveDvrBuffer`. It adds no requests while live, and capture failures never reach the
  player. Only a segment read from its start through to its end is stored, under the URL
  its playlist lists rather than wherever a redirect sent it.
- `LiveDvrBuffer` joins bytes with playlist metadata, keeps one copy of each moment
  across variants, and evicts the oldest video to stay under the size setting. Init
  segments count toward that limit and go with the last video that used them.
- A rewind uses the buffer when it covers the request, otherwise a recording, then the
  buffer as a shorter fallback with a toast. "Watch from beginning" always tries the
  recording first. It reuses the single-player VoD handoff through `LiveRewind.LOCAL`, and
  needs no connection to start. A replay prepared just as the viewer minimizes or leaves is
  cancelled, so the buffer goes back to evicting.
- Pinning (`pin`) stops eviction and makes the playlist append-only, because a player
  holding an EVENT playlist needs the listed segments to stay in place.
- `LiveDvrRecorder` keeps a replay's playlist growing, since nothing else fetches new
  video then. Each saved segment is published at once. If the stream ends, nothing new
  arrives for 60 seconds, or the buffer reaches twice its size, it closes the playlist with
  ENDLIST and playback returns to live at the end. The 60 seconds also bounds a response
  that trickles in without finishing. A replay that ended during background audio returns to
  live once its screen is resumed and maximized. A viewer who reaches the end of the saved
  video during a shorter drop gets Exo's stuck-playlist error (3.5 target durations, 21s),
  which also returns to live. Losing the network doesn't stop a replay the way it stops a
  VoD, since the video is on disk.
- The replay playlist has no PROGRAM-DATE-TIME, which would switch on Exo's live speed
  control, and its target duration is at least 6 seconds.

`LiveDvr` owns one session per channel and outlives player fragments, because a rewind
and Go live each replace the fragment and source: closing a source starts a 30-second
grace period that the matching next start cancels. Anything else deletes the session.

The buffer lives in the app's cache. Android clears every app's cache when free space
falls below 150% of its low-storage threshold (at most 750 MB), which an emulator run hit
in practice, so capture evicts and stops below 1 GB free. If the cache is cleared anyway,
the buffer starts over; a replay in progress fails its next read and returns to live.

Known limits: ads are saved as they played; replay chat stays live; pausing or chat-only
leaves a gap; the rewind distance is measured from the newest saved segment, a few seconds
ahead of the viewer's position; a process death ends the replay and returns to live; a
replay that falls back to live while offline starts a new buffer if the player stays blank
past the 30-second grace period, since nothing starts until the network returns; changing
the player backend during a session means the next rewind can't play locally, since only the
default backend plays a local replay: live carries on with a message while the default player
still holds the stream, and otherwise it falls back to live. Go live from a replay already
running when the backend changed is refused with the same message, so the replay stays on
screen until the player is closed.
## Creating clips

More > Create clip, or the optional player button (`C.PLAYER_CLIP_BUTTON`, off by
default), asks for a title and a length of 5 to 60 seconds, then calls Twitch's Create
Clip API with the viewer's Helix token and the `clips:edit` scope. The request runs in
`PlayerViewModel` and never touches the player. `ClipCreation` maps supported refusals
to distinct messages, reopens the options when a title is rejected, treats 503 as
temporary unavailability, and stops waiting for Get Clips confirmation at 60 seconds.
Both requests use `CancellableHttp`, so cancelling stops the HTTP call and each has
an elapsed-time limit. The [endpoint reference](https://dev.twitch.tv/docs/api/reference/#create-clip)
says 60 seconds; the separate [Clips guide](https://dev.twitch.tv/docs/api/clips/)
still says 15 seconds. The dialog or message waits until the player is resumed,
maximized and outside PiP. A completed result remains available after lifecycle
dismissals until Close, Back/outside cancellation, Watch clip, Edit or Share; Copy
link copies without closing it. Watch clip goes through `MainActivity.openClip`, the
same load a clip link uses, so the new clip replaces the live stream in the single
player. Another tap during processing shows Creating clip instead of starting a
duplicate request.
Further trimming happens in Twitch's editor (`edit_url`). It clips the live broadcast
rather than the viewer's position, so recording and local replays do not offer it.
Create Clip From VOD is limited to broadcasters and editors. Tokens from before this
scope get a 401, which leads to a sign-in prompt. That sign-in preserves the current one
(`EXTRA_REAUTHORIZE`): cancelling changes nothing, the same account continues without a
restart and retries the pending clip, and only a different account restarts the app.
TV device-code sign-in has no Helix token, so the TV approves a separate grant from
another device (`EXTRA_CLIP_DEVICE_AUTH`), kept apart from `C.TOKEN` in
`ClipAuthorization` and renewed with its refresh token. That grant can belong to a
different account from the TV's sign-in until login is entered again.
A replaced token stays valid for what is already on screen, and is revoked at the next
app start or on logout. A grant refresh that finishes after the grant changed is
discarded and revoked, and a refusal only clears the grant that request used.

## VoD seek previews

`SeekPreview` draws a frame from a Twitch storyboard sprite sheet while the seek
bar is dragged; it never seeks the decoder during the drag. `VideoStoryboard`
derives the optional index from the existing animated-preview URL and validates
the grid before mapping positions to cells. Missing or processing storyboards
silently leave normal seeking available. Online finished VoDs with preview
metadata are supported; clips, downloads, live rewind and spoiler-free mode do
not request previews.

The preview belongs to the player view. Hiding controls, minimizing, stopping,
PiP and destroying the view cancel its sheet request and drop bitmap references.
Request generations reject late callbacks, including a second drag on the same
sheet. Drawing uses a source rectangle rather than creating a bitmap per frame;
Coil owns cached sheet bitmaps, so they must not be recycled by the view.

`VideoStoryboardTest` checks index parsing and sheet boundaries. `SeekPreviewTest`
checks real Android controls, source-cell rendering, placement, cancellation and
late callbacks. Native renders are under `app/build/ui-previews/seek-preview-*`.
They do not establish actual Twitch availability, touch timing or playback.

## Required Checks For Player Work

Run:

```bash
./gradlew assembleDebug
./gradlew test
```

Also run when resources/UI are touched:

```bash
./gradlew lintDebug
```

For release-risk player work, also verify:

```bash
./gradlew assembleRelease
```

## Spoiler Shield in the player

`protectPlayerTitle` replaces the visible title when `SpoilerShield.hidesTitle` matches
the channel or category, including after a live title or category update. A tap
reveals that title for that player through `PlayerViewModel.spoilerRevealedTitle`,
and a new live title stays hidden until revealed too.
`playbackTitle()` supplies the media-session title on every backend, so the notification
and lock screen follow the same rule. A rule changed while the player stays open updates
the visible title at once. The media session keeps its title until a source next starts:
nothing updates it during playback, so it never shows anything newer than what was already
there. A start uses the latest live title with the category it came with, so an old protected
title can't return after the stream moves to an unprotected category. Share intents still
carry the real title.

## Optional Google Cast (default Media3 backend)

Google Cast is experimental and off by default; receiver playback remains
unverified on hardware. Enable it in Player settings, then reopen playback. The route button is
inflated only when the service reports actual Cast capability. Setting changes apply at the next
explicit source start, including after closing/reopening; the MediaSession stays
stable while the old stopped route is released. Default-off playback does not
initialize the SDK. Disabling Cast ends an existing session on that next start.
Receiver volume/speed remain receiver-owned across source switches. Returning
locally honors the keep-screen-on-when-paused setting even while buffering.
Devices without Google Play services keep local
playback. Custom ExoPlayer and platform MediaPlayer backends are unchanged.

Media3 CastPlayer owns the local/receiver switch and stops the inactive player.
ThystTV retains only the current configured MediaSource for a return to local:
clips preserve receiver position, speed and pause; live returns to the live edge.
Downloads and VoDs remain local, including a service-side rejection while casting. Close
stops playback and ends the Cast session. Backgrounding the phone leaves receiver
playback alone. Finishing the app (back out or remove the task) also ends the
session unless **Keep casting after closing the app** is on: then, while media is
on the receiver, the fragment only releases its controller and `onTaskRemoved`
leaves the service running, so the media notification keeps control. Reopening
the app does not restore the player UI for that session; starting playback
replaces the receiver media through the same service. Phone-only audio processing,
quality/track controls, live rewind discovery and system PiP are unavailable remotely.

The service registers its current player independently of any activity view.
After reopening with a retained session, downloads remain blocked while media is
remote. Switching to another backend without an attached Media3 player is
rejected while the service owns media, including after receiver disconnection
returns it locally. Stop the session from its notification before switching.
An ordinary default-backend online start continues to reuse the same service.

Both route transfers and already-remote source switches apply the same URL
eligibility policy. Local-only URLs are rejected before retained source/factory
or receiver ownership changes. Service command rejection removes only the failed
screen and its controller, without saving unrelated media under the new source
or stopping the receiver. Stale results cannot discard a replacement player.
When the service rejects a local-only start (a VoD, or live without a receiver
variant) while casting, the screen offers "Stop casting and play here" instead.
`CastStopRestart` clears the receiver media, ends the session and replays the
rejected source command, which keeps its resolved URL and start position
(explicit timestamps included), once playback is local. Cancel discards the
screen as before; a 10 s timeout discards it with a playback error; leaving the
app replays the same command (and offer) on return; a minimized screen gets the
toast and is discarded instead. Until a new source command is sent, a rejected
screen never saves a position, runs stop-time teardown, or stops/disconnects
media on close: the controller's media belongs to someone else. Opening the VoD
from a visible casting player still closes that player first, which stops
receiver media, so Cancel keeps the session connected but idle. The rejected
command and its state are saved with the fragment, so activity recreation replays
the same resolved start; a start whose result was lost while stopped asks the
service for its accepted request on return and replays if it was not accepted.
An accepted start is not saved: after process death the recreated screen starts
normally and resumes from the saved position, not the original open position. Its
ownership query cannot start it a second time (see Core Invariants). A start
still unanswered when the screen stops is saved as unknown; if the
process then dies, it replays at its original position (a narrow known gap).
While receiver media exists, downloads are still blocked in `MainActivity` with a
toast before any screen opens. With a connected but idle route, a download
reaches the service and gets the same offer.

The receiver fetches the media URL directly. Phone HTTP headers/proxies are not
forwarded. Receivers are browsers, so HLS needs CORS (issue #60): Twitch's usher
multivariant playlists and the VoD CDNs allow only Twitch origins, while live
variant playlists and segments allow any origin. A live stream is therefore sent
as one H.264 variant (`CastReceiverMediaItemConverter` swaps `contentUrl`; the
local URI stays the session identity). Transfers pick it from the local player's
parsed playlist, matching a fixed local quality or using the best for Auto;
streams started while casting fetch the playlist off the main thread with the
source's own HTTP stack and playlist parser (portrait variants stay hidden).
A variant the receiver cannot reach, such as a phone-local proxy, counts as none,
as does a playlist without H.264 video. The receiver keeps the variant URL for the
whole session: unlike local playback, it is not re-resolved after a token expiry
or stall, and RemoteCastPlayer reports no receiver errors to the phone. If no variant can be found, the session ends with a
message and the stream continues on the phone. VoD HLS is local-only. Clips are
progressive MP4 and are sent unchanged. Quality is fixed while casting.
A live stream resolved while casting is loaded paused and played only after the
receiver's status reports that exact LOAD (`CastReceiverLoad` tags it in the
media's custom data); an empty receiver ignores an earlier PLAY. The LOAD is
staged paused even when it replaces playing receiver media. A play or pause sent
through the media session while the lookup or LOAD is pending replaces the play
state used on acknowledgement and on the phone fallback; internal handoff
commands do not. Without that status within 15 s the session ends and the stream
continues on the phone.
Receiver codec/network compatibility still needs physical Cast QA.

## Optional chat in system PiP

Player settings → Chat in picture-in-picture is off by default. A maximized local
live stream with visible, ready portrait chat requests a 9:16 system PiP window:
video above a read-only message list. VoDs/clips, landscape, minimized, floating
or hinge-split chat, TV and Cast keep the normal video-only behavior. No extra
player or overlay permission is involved.

The window keeps the shape requested before entry. Layout and preference changes
refresh the next request, including when Settings covers a running player. Chat
containers measure/draw only the message path during PiP, so async polls, status,
scroll buttons, emotes and composer cannot cover messages. Normal visibility and
draft text remain intact on return; new messages follow the newest item in PiP.
Video-only requests explicitly restore 16:9 in wide/side-chat/fold layouts and
use the video frame for the animation hint. PiP layout observation ends on view
detachment. Returning from PiP chat clears stale scroll-up/touch flags so normal
message following resumes.
The composite source hint replaces the video-only hint tracker, retains system
play/pause actions and disables seamless resize for non-video chat content.

## Human QA Handoff

Agents should not claim physical-device QA unless it was actually performed. Instead, list the required human QA from `docs/MANUAL_QA.md`.

At minimum, player work should request human verification for:

- live stream opens
- VoD opens
- live -> live switching
- live -> VoD switching, if touched
- VoD -> live switching, if touched
- minimize / restore
- close / reopen
- orientation change
- PiP/background behavior where relevant
- playback speed menu
- quality menu
- gestures
- floating chat overlay

## Common Regression Smells

- old stream audio continues after opening a new stream
- player view is black after minimize/restore
- controls show stale speed/quality
- more than one player popup is visible, or popup content survives minimize/PiP/rotation
- controls auto-hide while a popup is open or fail to resume auto-hide after dismissal
- controls appear by themselves during VoD playback (stall or rebuffer)
- sleep timer closes the wrong player
- PiP remains enabled after player close
- orientation recreates UI with stale player state
- gestures conflict with chat or controls
- lifecycle callbacks apply to an old fragment
- quality menu shows raw numeric HLS variants
- floating chat is unreadable in light mode or on bright video
- the floating chat header or grip catches touches while the controls are hidden
- floating chat stays on screen in PiP or after minimize, or comes back empty

## PR Expectations For Player Changes

A player PR must include:

- changed files
- risk areas
- Gradle checks run
- human QA required
- human QA completed only if actually performed
- screenshots/video when UI changed
- known risks or follow-up issues
