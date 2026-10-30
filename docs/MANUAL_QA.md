# Manual QA Handoff Checklist

Use this checklist for PRs that affect player behavior, UI, layouts, release builds, or visual presentation.

Agents should mark **Required** when a human should test an area. Mark **Completed** only when a real human/device check was actually performed.

## Build Under Test

- Branch:
- Commit:
- APK/build:
- Device/Android version, if actually tested:
- Tester, if actually tested:
- Date, if actually tested:

## Checklist

### 1.5.1 release candidate

On October 27 the maintainer reported that upgrade, offline cold start and player
smoke tests pass on a pre-release build (not this signed candidate), and live
casting works on a Chromecast with Google TV. No Cast receiver is available for
this candidate. "Stop casting and play here" is covered by unit tests and two code
reviews only.

Required on the exact signed 1.5.1 candidate:

- [ ] Upgrade official 1.5.0 and preserve accounts, settings, Stats, bookmarks and
  downloads. Cold-start while signed in and offline, then restore networking and
  retry.
- [ ] Resume a partly watched VoD and a download: both start at the saved
  position. Open a VoD link with a timestamp. Change live quality: playback
  continues at the live edge.
- [ ] Live/VoD playback, stream switching without old audio, minimize/restore,
  close/reopen, PiP/background, speed/quality, gestures and floating chat.
- [ ] Updater discovery, download, install permission and installer handover.
- [ ] When a receiver is available: the Optional Google Cast checks below.
- [ ] Delay the initial live playlist, connect Cast and immediately close or clear
  playback. Complete the lookup: old receiver media must not start. While lookup
  is pending, an alternate backend must be refused until the service releases
  the source. Repeat receiver failure immediately followed by a newer live/clip
  start: the old fallback must not disconnect the newer source.
- [ ] Retain receiver playback after app exit, reopen and open a VoD. Close or
  replace its screen before URL resolution, while the source command is pending,
  and after rejection: unrelated receiver media must continue. Also close a
  successfully accepted source before its result reaches the UI: that source
  must stop, without affecting a later replacement.
- [ ] Pause receiver media, then start a different live stream with a delayed
  playlist: the previous item stays paused until the replacement starts. Repeat
  background/return and screen recreation before source acceptance arrives;
  the returning screen must regain controls and close only its own source.

### 1.5.0 release candidate

On October 23 the maintainer reported that mobile testing of the earlier 1.5
debug candidate works. This is general phone smoke evidence; the report did not
identify a device, certify every scenario below, or test the final signed APK.
Cast and physical TV hardware are unavailable. Cast is labelled experimental and
both Cast and Keep casting after closing the app remain off by default.

Required on the exact signed 1.5.0 candidate:

- [ ] Upgrade official 1.4.0 and preserve accounts, settings, Stats, bookmarks and
  downloads. Cold-start while signed in and offline: open Saved, Bookmarks and
  Downloads, then restore networking and retry.
- [ ] Live/VoD playback, stream switching without old audio, minimize/restore,
  close/reopen, PiP/background, speed/quality, gestures and floating chat.
- [ ] Brief network loss and recovery; background VoD/clip recovery gives up
  after its finite budget. Pause, source changes and foreground return cancel
  stale retries.
- [ ] Updater discovery, download, install permission and installer handover.
- [ ] Real Cast receiver: local/remote handoff, live/VoD, disconnect, app exit
  with keep-casting off/on, notification control and reopening/switching streams.
- [ ] Keep casting, remove the task, reopen with no player screen: a download
  explains the rejection and leaves receiver media unchanged. Switch to each
  alternate player backend and try live/VoD/clip/download; it must not create a
  second owner. Repeat after receiver disconnection returns playback locally.
- [ ] While casting valid online media, start a phone-local proxy URL (localhost,
  IPv4/IPv6 loopback or wildcard): reject it and preserve the old receiver media.
  Rapidly replace/close the pending screen; a late rejection must not close the
  new player. Repeat background/restore and rotation during command completion.
- [ ] Authenticated TV phone/QR sign-in, browser fallback and physical D-pad/codec
  behavior. The emulator and state tests do not certify hardware compatibility.

### Opus review corrections (required)

The [correction handoff](OPUS_REVIEW_FIXES.md) records automated checks and the
API 36.1 emulator's short/long outage and wide PiP observations. These do not mark
the physical-device checklist below completed.

- [ ] With a service already running, change Cast off/on, close/reopen playback,
  connect/disconnect and switch live/VoD sources. Confirm one playing owner,
  capability-based route UI and receiver volume/speed retained across switches.
- [ ] Return locally while buffering with keep-screen-on-when-paused enabled.
  Check video-only PiP shape from wide/sidebar/fold layouts, then portrait chat
  PiP from a scrolled-up chat and return to normal message following.
- [ ] Open/close the player repeatedly and check retained fragments with a heap
  dump; static observer tests do not measure retained heap on actual hardware.

### TV device-code sign-in (required)

- [ ] Use a disposable account: entering the existing login activity revokes its
  previous login tokens. On TV, reach phone sign-in by D-pad, read the limitation,
  cancel and continue. Browser sign-in must remain available.
- [ ] Scan the QR on a real phone and compare the displayed activation code.
  Authorize, choose Check sign-in, and verify account identity and features.
- [ ] Check before approval/twice rapidly, deny/revoke the code, interrupt
  networking, expire it while visible/hidden and request a new code. Old results
  must not sign in a new session. Close/reopen and rotate during each request.
- [ ] Test browser fallback, no installed browser, phone external-code flow,
  Helix/GQL preferences, enlarged fonts, compact/wide layouts and OEM remote
  focus. All text/actions must remain readable/reachable by scrolling.

### Bounded local live recovery (required)

- [ ] Interrupt/restore connectivity during live playback in foreground, PiP,
  background and audio-only. Verify quality, speed, volume and last-frame retention.
- [ ] Stay offline over 60s and simulate repeated transient errors/buffering.
  Recovery must stop and allow manual retry. One observed validated return can
  start a finite second episode; repeated flaps must not keep rearming it.
  Healthy playback over 30s should permit another bounded recovery sequence.
- [ ] Pause during the wait, then restore the network: no automatic playback.
  Repeat close/reopen, switching, live/VoD transitions and sleep-timer expiry.
  No old player may reopen or produce double audio.
- [ ] Confirm live chat reconnects with new messages after short/long outages,
  including signed-in read/write, notices and third-party emotes. Repeat rapid
  loss/return, PiP/background and manual chat disconnect; old cleanup must not
  close the new connection, and manual disconnect must stay disconnected.
- [ ] Test ended/404, unauthorized and custom-proxy failures, including the
  one-way fallback. Transfer to a Cast receiver during a pending wait.
- [ ] Repeat live/VoD, minimize/restore, PiP/background, speed/quality, gestures
  and floating chat. Check the optional legacy backend separately.

### Website presentation

- [ ] On a physical phone and laptop, accept the visual layout in both themes;
  verify readable text and no horizontal overflow, including enlarged text.
- [ ] Select Floating chat, Player controls, Local stats, and Room to watch.
  Open the full-size control previews; confirm original images stay readable.
- [ ] Use keyboard arrows/Home/End and check focus. Open/close the demo by its
  close button, Escape, and the backdrop; focus returns to its trigger.
- [ ] Play the demo with native video controls. Closing it or hiding the page
  pauses playback. Confirm touch and audible playback on a real browser/device.
- [ ] Pause decorative motion, reload, and check persistence. System reduced
  motion stops animation; theme choice persists. Check JavaScript/storage denied.
- [ ] Follow GitHub Releases and the APK verification guide. Check downloads on
  the actual device; website browser QA is not Android installation evidence.

These checks are separate from player/device QA. Website sample-data previews
and pre-1.3 footage are labeled and do not certify current app behavior.

### October 11 1.4.0 candidate

October 12 finalization: the maintainer tested the combined debug build containing
#45–#48 and reported that everything works. Supplied screenshots show the update
prompt, install-permission and preparation dialogs, and Changelog with rounded
surfaces. This is general debug/device smoke evidence, not verification of the
official signed upgrade. The updater screenshots use a temporary external test
feed; restore the ThystTV source for the exact signed candidate's final checks.

Required on the exact signed final candidate: official 1.3.1 upgrade and preserved
accounts/settings/Stats/bookmarks/downloads; logged-in cold-start offline Saved,
Bookmarks and Downloads plus network-restoration retry; live/VoD playback, stream
switching, minimize/restore, close/reopen, PiP/background, speed/quality, gestures
and floating chat; Settings forward/back/search and all corner choices; updater
discovery, download, install permission and installer handover. Record a fresh
leak regression check separately; release APKs do not include LeakCanary.

On October 11 the maintainer installed the device-test debug build
(`com.tzii.thysttv.debug`, `1.3.1-DEBUG`, code 13, local merge `368426903` of
#30-#38) over the previous debug build on a Honor Magic V5 (Android 16). They
went through the supplied checklist (pinch pill and Fill outline, full-screen
rewind button and 1x catch-up, spoiler-free VoDs, free zoom across rotate, fold
and PiP, settings drag, gesture guide in PiP, quality, notifications, rewards)
and reported that everything seemed fine. No per-item breakdown was supplied.
LeakCanary caught a closed-player leak during that session; its fix (#39) was not
in that build. The exact signed 1.4.0 upgrade and release matrix remain pending.

### September 25 large-screen and upstream update

On September 27 the maintainer reported that the delivered debug build seemed
fine. No device/backend/scenario breakdown was supplied, so the detailed rows
below remain a regression reference rather than a claim that every case passed.

On September 30 the maintainer also reported that the latest debug APK with TV
support seemed fine and proposed preparing 1.3.1. The supplied artifact identifies
as `com.tzii.thysttv.debug`, version `1.3.0-DEBUG`, code 12. No additional device or
scenario breakdown was provided. Record this as general debug smoke evidence;
the exact signed 1.3.1 upgrade and release matrix remain pending.

- [ ] On tablet and landscape phone, brightness/volume side feedback is easy to
  see over bright/dark video, with larger icons and a thicker level track. Check
  both ends of each level; its pill must stay within the video and system insets.
- [ ] Resize to short split-screen and narrow side-chat video; return to full
  screen. Compact feedback, seek/speed/pinch indicators and chat touches remain
  correct. Repeat with large fonts and RTL.
- [ ] Live/VoD playback, stream switching without old audio, minimize/restore,
  close/reopen, PiP/background, speed/quality, all gestures, floating-chat
  open/drag/resize, and Stats load/rotate/range smoke.
- [ ] Refresh individual and multiple saved bookmarks, reopen Saved/relaunch and
  confirm updated title/duration/art persists without duplicate entries.
- [ ] Resume a VoD while its broadcast is still live, with a saved position past
  the listed duration. It must retain position. Repeat via cached bookmarks;
  completed VoDs with final metadata still restart, explicit timestamps win and
  downloaded segments retain their own duration behavior.
- [ ] Issue #61: reopen a partly watched VoD from Followed/channel VoDs, Saved
  and a download. Each resumes at its saved position, not 0:00. Mid-VoD,
  change quality and toggle audio-only/background audio: position is kept.
  Repeat with Cast enabled in settings but not connected.
- [ ] With no API tokens but a retained account ID, player/channel buttons reflect
  local follows. Repeat signed in through GQL and Helix fallback.
- [ ] Open chat profiles using moderator permissions and Helix fallback; follow
  dates belong to the selected viewer. Ordinary accounts without follower permission
  still show profiles. Check profiles in floating chat and ordinary followed lists.
- [ ] `/color` displays the current color through HttpEngine and legacy Cronet
  where supported; existing OkHttp/Cronet behavior remains intact.
- [ ] German/Japanese/Spanish/Russian UI: inspect changed settings, quoted labels,
  plural/count values, formatted percentages and Russian ThystTV login text.

### September 27 Android TV remote navigation

The maintainer has no TV available. Codex checked an isolated Android TV
emulator and native Android view/key tests. Physical TV/OEM remote and codec
behavior remain unverified; emulator results are recorded separately in
`TESTING.md`.

- [ ] Remote-only launch: visible focus, left/right across enabled bottom items,
  CENTER selects; UP enters content and Back at a root page returns to navigation.
- [ ] Popular, Following, Games, Saved and Stats; tabs, long feeds, empty pages,
  search, settings and dialogs; nested Back returns normally and repeated Back exits.
- [ ] Following/Saved tabs, Stats ranges and channel Videos/Chat/Clips tabs remain
  fully visible while focused, after selection and after scrolling the page.
- [ ] Channel entry shows visible focus; profile details collapse while toolbar
  and tabs remain available and leave room for video cards.
- [ ] Live/VoD controls: reveal with D-pad, play/pause, seek, quality, speed,
  volume, More, popup Back/focus restoration; keyboard slider changes persist.
- [ ] Minimize, focus/restore mini-player, close/reopen, stream switching,
  PiP/background and chat controls. No focus reaches covered browse content.
- [ ] Phone/tablet gestures and floating-chat interaction remain regression areas.

### Following view cleanup

- [ ] Open available Following tabs, leave and return repeatedly, then rotate or
  recreate the screen. Tabs and lists still load; first insertion keeps position
  and later prepends still scroll to the top.
- [ ] After leaving Following, inspect a fresh LeakCanary result from the rebuilt
  APK. The previous destroyed pager/RecyclerView retention groups must not recur.
  Keep heap dumps and private analysis out of public issues and commits.

### Swipe to show or hide chat

- [ ] Gesture navigation, landscape live stream, controls hidden: swipe right to
  left across the middle; side chat opens once. Swipe left to right; it closes.
  Neither swipe triggers Back or minimizes the player.
- [ ] VoD and clip: a horizontal swipe in the middle still seeks (lower half) or
  changes speed (upper half). A swipe that starts just inside the right edge
  (not in the Back zone) opens chat; with chat open, the same strip next to the
  chat closes it. Repeat with three-button navigation.
- [ ] Floating chat showing: swipe toward the chat side hides it; swipe the other
  way does nothing. On a VoD, start in the strip just inside the right edge and
  swipe right: it hides, also on a tablet. Double tap still cycles hidden, side
  and floating.
- [ ] Settings → Player settings → Gestures → Swipe to show or hide chat off, or
  chat disabled: horizontal live swipes do nothing and the gesture guide drops
  the row. With gestures off entirely, nothing changes either.
- [ ] Phone in landscape on a VoD: open Player gestures from the player menu.
  Every row, down to the swipe row, can be scrolled into view above Got it.
- [ ] Vertical swipes on the right half still change volume. A swipe with the
  controls showing still minimizes. Pinch and double tap work as before.
- [ ] Foldable inner screen, book and tabletop postures, and an RTL language
  (chat on the left, directions mirrored).

### Pinch free zoom

- [ ] Near-square foldable inner screen (or a tablet in landscape): pinch out
  partway, release, and confirm the picture stays centered at that zoom. The pill
  reads "Zoom N%" between Fit and Fill, with a haptic tick at each end when
  enabled. Open another stream or VoD: the zoom is kept.
- [ ] Landscape phone: a short pinch still reaches Fill and back to Fit; ends snap
  within about 5%. Pinching past either end resists and settles back.
- [ ] The pill keeps one width through Fit, Zoom N% and Fill, its bar moves
  smoothly, and reaching Fill flashes a white outline once. Brightness, volume,
  seek and speed pills keep their usual font and size.
- [ ] While zoomed, rotate, fold/unfold, open/close side chat, resize split screen,
  minimize/restore, enter/leave PiP and close/reopen. Portrait, the mini-player and
  PiP show plain Fit; full-screen landscape returns to the zoom, never past Fill.
- [ ] More → Display mode shows the zoom as the current choice; choosing Fit, Fill
  or Stretch clears it. Stretch and Android 6 keep the Fit/Fill toggle.
- [ ] Live, VoD and clips on all three backends; a double tap followed by a pinch
  still restores chat.

### Extended zoom

Turn on Player settings → Gestures → Extended zoom.

- [ ] Landscape phone and foldable inner screen: pinch out to Fill, keep pushing
  through the resistance and confirm magnifying continues smoothly from the
  resisted size, without a jump. The pill reads "Zoom N%". Magnifying stops at
  about 4× Fit with a short resistance.
- [ ] Magnify on a corner detail (a minimap or scoreboard): it stays under your
  fingers, and the picture never shows black past its edges. Content that Fill
  crops can be brought into view.
- [ ] Release just past Fill: it settles back to Fill. Release further out: the
  magnification and position stay. Pinch in again: it resumes from there, and
  reaching Fill gives a tick and the white outline and settles centered.
- [ ] While magnified: open/close side chat, fold/unfold and change stream quality.
  The same detail stays centered. Minimize, rotate to portrait, enter PiP or
  choose a mode in More → Display mode: playback returns to plain, centered Fill.
- [ ] While magnified, with the controls hidden, drag with one finger on live and
  on a VOD: the picture follows the finger in every direction and stops at the
  video's edges. Brightness, volume, seek, speed and the chat swipe do not
  trigger, and there is no pill or haptic. Taps still show the controls.
- [ ] Mid-drag, put a second finger down and pinch: zooming continues from the
  dragged position without a jump. Put a second finger down and lift it
  without pinching: the drag continues without a jump.
- [ ] More → Display mode lists the magnification as "Zoom N%". Double tap still
  toggles chat. At plain Fill, brightness, volume, seek, speed and the chat
  swipe work as before. The gesture guide lists "Drag while magnified" only
  with the setting on.
- [ ] With the setting off, pinching past Fill only resists and settles back,
  as before. A 16:10 tablet with the setting on: Fit and Fill still snap, and
  magnifying past Fill works.

### Half-folded layout

- [ ] Foldable inner screen, landscape, side chat open: half-fold like a laptop.
  Video fills the top half above the hinge, chat the bottom half; unfolding
  returns to the side chat without a flash of the wrong size.
- [ ] Tabletop with floating chat: chat docks below the hinge and floats again
  in its old place after unfolding. If the chat button is used while docked
  (it cycles hidden and docked only), unfolding keeps the sidebar.
- [ ] Tabletop with chat hidden: video stays above the hinge. Portrait tabletop
  behaves the same instead of the 16:9 stack.
- [ ] Tabletop keyboard: tap the chat input; the input and a few chat lines stay
  above the keyboard; switch to its emoji panel or change keyboard height and
  confirm the input stays reachable. Closing it restores the split. Repeat in
  a resized window; video stays above the hinge while typing.
- [ ] Book posture (hinge down the screen) with chat showing: video on one side,
  chat on the other, nothing across the hinge; with chat hidden or floating the
  video keeps the whole screen. Check an RTL language puts chat on the left.
- [ ] While half-folded: minimize/restore, PiP and back, rotate, close/reopen,
  switch streams, pinch zoom, double-tap chat, quality/speed/More popups.
- [ ] Settings → Player settings → Half-folded layout off: postures keep the
  normal layout. Phones without a hinge and Android TV are unchanged.

### Spoiler-free VODs

- [ ] Off (default): VoD seek bar, length, Past Broadcasts/Saved/Downloads
  durations and watched bars look as before.
- [ ] On: a VoD and a downloaded VoD hide the seek bar, position and length;
  swipe-to-seek shows only the new position; double-tap seek still works.
- [ ] On: Past Broadcasts, Saved and Downloads thumbnails hide the duration and
  watched bar. Live streams and clips play as before.
- [ ] On: the VoD download dialog shows no duration or end-time hint, and an end
  time past the VoD still shows the error; the player games list shows game
  names without positions or durations, and tapping a game still seeks.
- [ ] Known limit: the media notification and lock-screen controls can still show
  the length.

| Area | Required | Completed | Notes |
| --- | --- | --- | --- |
| App launches | [ ] | [ ] | |
| Navigation smoke test | [ ] | [ ] | |
| Live stream opens | [ ] | [ ] | |
| VoD opens | [ ] | [ ] | |
| Player closes/reopens | [ ] | [ ] | |
| Live -> live switching | [ ] | [ ] | |
| Old audio does not continue after switch | [ ] | [ ] | |
| Mini-player -> new stream | [ ] | [ ] | |
| Minimize / restore | [ ] | [ ] | |
| PiP/background behavior | [ ] | [ ] | |
| Playback speed display/control | [ ] | [ ] | |
| Quality menu/selection | [ ] | [ ] | |
| Chat-only/audio-only restore | [ ] | [ ] | |
| Gestures: brightness/volume/seek | [ ] | [ ] | |
| Floating chat: open/drag/resize | [ ] | [ ] | |
| Floating chat readability over video | [ ] | [ ] | |
| Portrait phone layout | [ ] | [ ] | |
| Landscape phone layout | [ ] | [ ] | |
| Wide/tablet layout | [ ] | [ ] | |
| Split-screen/resized window | [ ] | [ ] | |
| Stats screen | [ ] | [ ] | |
| Stats filters/charts | [ ] | [ ] | |
| Updater/changelog Markdown and actions | [ ] | [ ] | |
| Settings sections, moved toggles and search results | [ ] | [ ] | |
| Settings predictive back: hold, cancel and finish a back swipe on a sub-screen | [ ] | [ ] | |
| README/site screenshots render | [ ] | [ ] | |
| Icon/banner assets look correct | [ ] | [ ] | |
| No private account data exposed | [ ] | [ ] | |

### Startup profiling (performance stack)

- [ ] On physical hardware, compare cold startup with/without the saved profile
  using `baselineprofile/README.md`. Record raw timings/device/API/commit and test
  launch, navigation, first scroll and returning from background. Emulator profile
  generation alone does not establish a speedup.

### Media3 1.11.1 (performance stack)

- [ ] Default session service first: visible live/VoD video and usable quality,
  seek and speed controls. An advancing position alone cannot establish video.
- [ ] Stream switching, minimize/restore, close/reopen, PiP/background, rotation,
  gestures and floating chat: one current session, no stale frame/audio overlap.
- [ ] Repeat on the direct-bound custom ExoPlayer backend; check the platform
  MediaPlayer alternative still opens and switches correctly. Cover an older
  Android device as well as current Android, plus TV and foldable/window changes.

### Portrait live rewind (performance stack)

- [ ] Portrait live player: the bottom-left rewind action opens the existing
  recording options. Check 360dp width, 200% text, RTL, uptime/viewer count and
  optional restart, seek-live, volume, compressor, audio-only and keyboard buttons.
  Rotate, minimize/restore and return from recording replay; controls stay readable.

### VoD seek previews (performance stack)

- [ ] Finished online VoD: drag slowly, quickly and across sprite-sheet boundaries;
  preview time follows the thumb, stays within the player at both ends and vanishes
  on release/cancel. Playback seeks once on release as before.
- [ ] Check compact portrait, landscape, tablet, RTL, enlarged fonts and TV D-pad.
- [ ] Missing/processing storyboards and offline networking leave seeking usable.
  Spoiler-free VoDs, downloads, clips, live and live rewind show no preview.
- [ ] Minimize/restore, close/reopen, switch VoDs/live streams, rotate and enter/leave
  PiP/background during a drag. No old preview or image request survives.
- [ ] Live/VoD playback, speed/quality, gestures and floating chat remain usable.

### Optional chat in picture-in-picture

- [ ] Setting off: normal video-only PiP for live/VoD/clip on every backend.
- [ ] Enable the option over an already running portrait live player. Home shows
  video above readable, updating chat in a portrait PiP window. Resize, move,
  stash, play/pause and restore; no input, emote, poll/status or scroll overlays.
- [ ] Return with a typed draft/reply/emote panel open. Normal chat/input state
  returns without sending or clearing the draft. Repeat entry/exit and rotate.
- [ ] Landscape, minimized, disabled/hidden/unavailable chat, floating chat,
  hinge-split layouts, VoD, clips and TV retain video-only PiP. Cast cannot enter
  PiP. Test settings changes before entry and view recreation while in PiP.
- [ ] Repeat minimize/restore, stream switching, close/reopen, background/lock,
  speed/quality, gestures and floating chat. Check Android 8–11 manual entry and
  Android 12+ gesture auto-entry on physical devices.

### Optional Google Cast

- [ ] With Cast off, default live/VoD/clip/download playback and controls match the
  local player. With Cast enabled, test Google Play services absent/unavailable.
- [ ] On real hardware (Chromecast/Google TV) on the same network: discover,
  connect, play live and clip, pause, seek a clip and change speed. Live must
  keep playing on the TV (issue #60), not load and stop. Cast with Auto and with
  a fixed quality (for example 480p): the TV uses the best or the chosen variant.
  Also start casting from chat-only and enhanced-broadcasting (HEVC/AV1) channels.
- [ ] Cast from a VoD: the phone explains it plays only on this device and keeps
  playing locally. Switch live/live and open a VoD while casting: the phone
  offers "Stop casting and play here". Cancel closes the new screen and keeps the
  session connected (the TV is idle if the VoD was opened from the casting
  player). Stop casting ends the session and plays the VoD on the phone at its
  resume position, or at the link's timestamp with saved positions off. Leave
  the app while the offer is open and return: the offer appears again. Retain a
  session after closing the app, reopen, open a VoD and decline: the TV keeps
  playing and no VoD position changes. A download while the receiver has media
  still shows "Stop casting before opening a download"; with a connected idle
  receiver it gets the stop-casting offer.
- [ ] Connect to an idle receiver (nothing playing), then start a live stream:
  the TV starts playing on its own within a few seconds and the phone does not
  fall back. Repeat while the TV is playing another stream.
- [ ] Disconnect: one local player resumes the same paused/playing VoD position
  and speed, or the live edge, using normal quality and HTTP configuration.
- [ ] Background/lock/restore the phone, minimize/restore, rotate, lose/recover
  sender and receiver networks, close/reopen, close from the notification and
  remove the task. No duplicate audio, stale route dialog or orphaned receiver.
- [ ] Keep casting after closing the app: off (default), backing out of the app or
  removing the task stops the receiver. On, both leave receiver playback running
  with a working media notification (pause/play/stop); local-only playback and an
  empty receiver session still close normally. Reopen the app and start another
  stream: it replaces the receiver media without a second player.
- [ ] Repeat local speed/quality, gestures, floating chat and system PiP. Cast is
  currently supported only by the default Media3 session backend.

## Evidence

Screenshots, videos, logs, or APK links:

-

## Known Issues / Deferred QA

-


## 1.3 release checks

- [ ] Landscape, maximized player, double-tap chat and floating chat enabled: from
  each of hidden, sidebar and floating chat, tap once and immediately begin a
  two-finger pinch (first finger lands within the double-tap interval). After the
  scale threshold is crossed, chat must return to its starting mode, in the same
  container, with the correct player width and chat icon. Verify it stays restored
  after animations settle and both fingers lift; repeat with a cancelled pinch.
- [ ] Repeat from hidden/sidebar with floating chat disabled. A normal double tap
  must still advance one mode; a normal pinch must leave chat unchanged. Reopen
  playback and confirm the saved chat-open choice was not changed by the pinch.

- During an updater download, cancel/retry and rotate/background/restore; one current
  attempt should remain and stale work must not launch an installer.
- Interrupt a transfer and verify retry/browser recovery. Deny/grant install permission;
  cancel/confirm the OS installer without an automatic reopening loop.
- Check Markdown, action reachability and scrolling at 200% text and in resized windows.
- Process death requires a fresh explicit attempt; do not expect download resumption.
- Test an exact signed 1.3 APK upgrading official 1.2.1, retaining data and migrating
  landscape display mode. Repeat the player, gestures, floating chat, Stats and launcher
  matrix on that same candidate. See RELEASE_1_3_REVIEW.md and RELEASE_PROCESS.md.
