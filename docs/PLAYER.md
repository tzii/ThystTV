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
- Quality, Speed, Stream volume, and More share one player-owned popup host; only one
  can be attached at a time, and teardown/minimize/PiP must remove it with the player view.

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

The magnification is session-only and never saved. Choosing a display mode,
minimizing, rotating to portrait, entering PiP or turning the setting off
draws plain Fill. More → Display mode lists it as "Zoom N%". Double tap stays
the chat action. `PinchZoomControllerTest`, `PlayerMagnifyGeometryTest` and
`PlayerPinchZoomTest` cover the controller, geometry and fragment surface.

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
- Floating chat should use a dark translucent video-overlay palette in both app themes; background opacity remains user-controlled.
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

Brightness and device-volume feedback use larger 64 × 216dp edge pills on player
surfaces at least 600dp wide, with 32dp icons and 8dp level tracks. Icons use
explicit AppCompat image views with white tint for readability over video. Surface classification
uses the measured video/player width, so side chat and resized windows can return
to the unchanged compact horizontal treatment. Edge pill height is capped to 45%
of the measured player height; padding, icon and spacing shrink together below
96dp pill height to keep a visible level track in short windows. Returning to a
compact or non-side gesture resets icon size, spacing and pill corners.

`PlayerGestureFeedbackRenderTest` checks native Android layout, edge placement,
progress/accessibility updates, very short players and compact restoration, and
renders phone/tablet feedback previews. Device gesture timing, playback and actual
window insets still require the human player matrix below.

## Live rewind controls

The live-rewind button is available for maximized live streams in portrait and
landscape. Portrait uses a separate stats row below the actions so uptime/viewer
count do not overlap optional volume/audio controls. The center play/pause shifts
up only as needed to clear that row. Rotation restores the original landscape
rules; minimized players and recording replays do not get a live-rewind button.
`PlayerLiveRewindControlsTest` renders compact/RTL/200% text and landscape controls.

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
normally and resumes from the saved position, not the original open position. It
skips the ownership query, which would otherwise race that start into a second
one. A start still unanswered when the screen stops is saved as unknown; if the
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
- sleep timer closes the wrong player
- PiP remains enabled after player close
- orientation recreates UI with stale player state
- gestures conflict with chat or controls
- lifecycle callbacks apply to an old fragment
- quality menu shows raw numeric HLS variants
- floating chat is unreadable in light mode or on bright video

## PR Expectations For Player Changes

A player PR must include:

- changed files
- risk areas
- Gradle checks run
- human QA required
- human QA completed only if actually performed
- screenshots/video when UI changed
- known risks or follow-up issues
