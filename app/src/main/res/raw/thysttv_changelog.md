# ThystTV 1.5.1

ThystTV 1.5.1 fixes live casting to Google TV and resuming VoDs at their saved
position, and adds a way to stop casting from a VoD.

## Player

- VoDs and downloads resume at their saved position again instead of restarting
  at 0:00. Explicit link timestamps still win.
- Changing quality on a live stream starts at the live edge, so the new playlist
  can't begin outside its own window.

## Experimental Cast

- Live casting now works on Chromecast with Google TV. The receiver gets a
  playlist it can load, matching the phone's chosen quality, or the best one on
  Auto. Streams with no receiver-compatible variant (for example HEVC or AV1
  only) keep playing on the phone with a message.
- VoDs play only on the phone, because Twitch's VoD servers refuse receivers.
  Opening a VoD while casting offers "Stop casting and play here", which ends
  the Cast session and plays the VoD on the phone. Downloads keep asking you to
  stop casting first.
- Quality can't be changed while casting.

Live casting, clips, live switching and disconnecting were checked on a
Chromecast with Google TV using development builds. The final receiver start
fixes and "Stop casting and play here" have not yet been checked on a receiver.

## Upgrading

Version 1.5.1 uses version code 16 and database version 36, unchanged from
1.5.0. Install the official signed APK over official 1.5.0 to retain local
data; debug builds use a separate application ID. New messages are in English
for now.

See the [APK verification guide](https://github.com/tzii/ThystTV/blob/master/docs/APK_VERIFICATION.md)
for checksum and official signing-certificate verification.

# ThystTV 1.5.0

ThystTV 1.5.0 adds VoD seek previews, optional chat in picture-in-picture,
bounded playback recovery and experimental Google Cast. It also improves busy
chat handling and enlarged-text navigation.

## Player

- Drag the seek bar on a finished online VoD to see storyboard thumbnails and
  timestamps when Twitch provides them.
- The live-rewind button is also available in the maximized portrait player.
- Live streams automatically retry short network drops or stalls, up to three
  attempts with at most a minute waiting for connectivity. Recovery eventually
  stops and asks for Play instead of retrying forever.
- Background VoDs and clips retry transient network failures at most five times
  with increasing delays, then stop. Permanent media errors stay visible.
- Chat in picture-in-picture is optional and off by default. Enter PiP from a
  portrait live stream with visible chat to keep read-only messages below video.
  Other layouts and VoDs retain video-only PiP.
- Media3 is updated to 1.11.1. VoD replay chat resumes with ready playback.

## Experimental Cast

Enable Google Cast in Player settings and reopen playback to send online video
to a receiver on the same network. Cast is off by default and requires Google
Play services. Downloads stay local; the receiver fetches video directly without
the phone's proxy or custom HTTP headers. Receiver compatibility and playback
have not been verified on physical hardware.

Keep casting after closing the app is a separate option, also off by default.
Normally app exit stops casting. With this option enabled and receiver media
loaded, backing out or removing the task leaves receiver playback and its media
notification active. Reopening the app does not restore that session's player
screen; starting another stream replaces the receiver media. Android service
retention and notification control still need a real-receiver check.

## Chat, sign-in and navigation

- Busy chats batch list changes and use direct emote and 7TV user lookups. Late
  emote results cannot replace a newer message in a reused row.
- Android TV offers phone sign-in using a QR or activation code; browser sign-in
  remains available. Authenticated sign-in and physical TV behavior remain
  unverified for this release.
- Saved and Search tabs and channel-profile actions remain readable at 200%
  text size. Closing players no longer races system-bar updates, and Channel
  Points uses the current balance query.

## Performance and upgrading

Release builds include generated startup/navigation profiles. The unused Glide
dependency and its image-library preference are removed. Real-device startup
gains have not been measured.

Version 1.5.0 uses version code 15 and database version 36, unchanged from 1.4.0.
Install the official signed APK over official 1.4.0 to retain local data; debug
builds use a separate application ID.

Updater downloads require a fresh attempt after Android kills the app process.
New settings and messages are in English for now.

See the [APK verification guide](https://github.com/tzii/ThystTV/blob/master/docs/APK_VERIFICATION.md)
for checksum and official signing-certificate verification.

# ThystTV 1.4.0

ThystTV 1.4.0 adds live rewind, free pinch zoom, Channel Points rewards, a
half-folded layout for foldables and an optional spoiler-free mode for VoDs. It
plays the exact quality you pick and makes live notifications more reliable.

## Player

- Rewind a live stream to the beginning or the last 30 seconds, minute or five
  minutes of its recording. Open More → Rewind live stream, or tap ⏪ next to
  volume in landscape full screen. Replay chat follows along, and Go live returns
  to the stream. Faster playback drops back to 1x once it catches up.
- Pinch in landscape to any zoom between Fit and Fill. The zoom is kept across
  streams and VoDs, More → Display mode shows it, and choosing Fit clears it.
- Qualities keep their exact resolution, frame rate, codec and bitrate. Two
  1080p60 encodes stay distinct, and a saved choice no longer drops to a lower
  resolution on a higher-bitrate channel. Portrait variants are hidden, and clips
  can offer HEVC/AV1 when the codec setting allows it.
- A separate default quality for metered Wi-Fi such as hotspots and data caps. It
  follows the mobile-data default until you change it.
- Spoiler-free VoDs (Settings → Player settings, off by default) hides the seek
  bar, length, game chapter times and watched progress of VoDs and downloads, so
  a VoD's length does not reveal how many games were played. Seeking still works.
- On a half-open foldable, the full-screen player keeps video off the hinge. Fold
  it like a laptop to watch above the hinge with chat below; floating chat docks
  there and floats again when you unfold, unless you changed the chat mode while
  folded. Fold it like a book to see video and
  chat side by side while chat is showing. Settings → Player settings →
  Half-folded layout turns it off.
- The gesture guide no longer opens inside picture-in-picture.

## Chat and notifications

- Redeem a channel's custom Channel Points rewards from chat after an explicit
  "Spend N points" confirmation. An uncertain result is shown as such and never
  retried automatically.
- Live notifications ask Twitch once per poll and keep respecting each channel's
  notification setting when the main request fails. They do not repeat alerts
  after failed or offline polls, and they retry soon after a failure.

## Fixes

- Settings drag lists keep the order shown on screen after a fast drag.
- Closed live players no longer stay in memory while signed in.
- Search releases its pager and tab connections when its view closes.
- Settings screens no longer remain in memory after predictive back transitions.
- Rounded corners, less rounded corners and square corners now follow the
  selected theme setting correctly.

## Settings and updates

- Settings are grouped into Appearance, Watching, Network & account and System &
  About, with icons and summaries. Search also finds settings inside categories.
- Settings transitions follow the predictive back gesture.
- Update dialogs and the Changelog use Material 3 layouts, readable Markdown
  release notes and actions that stay reachable at large text sizes.

## Upgrading and limitations

Install the official signed APK over the existing official app to retain accounts,
settings, Stats, bookmarks and downloads. This update migrates the database to
version 36, adding two download columns; installing an older version afterward is
not supported. Debug builds use a separate app ID and do not verify the official
upgrade path.

Live rewind plays Twitch's own recording of the broadcast. It needs a channel that
saves past broadcasts, and availability and delay depend on Twitch. With
spoiler-free VoDs on, the media notification and lock screen can still show a
VoD's length. The half-folded layout needs a device that reports its hinge to
apps, and split-screen windows keep the normal layout. Notifications still check
every 15 minutes, subject to Android battery limits. New settings and messages
are in English for now.

Updater downloads still require a fresh attempt after Android kills the app process.

See the [APK verification guide](https://github.com/tzii/ThystTV/blob/master/docs/APK_VERIFICATION.md)
for checksum and official signing-certificate verification.

# ThystTV 1.3.1

ThystTV 1.3.1 adds Android TV remote navigation, improves large-screen gesture
feedback, and fixes Following cleanup, bookmarks and chat metadata.

## Android TV and large screens

- Use the D-pad to move between navigation items, browse lists and tabs, and reach
  Stats controls with visible focus.
- Reveal and operate player controls with the remote, including quality, speed,
  volume and More. Closing a popup restores player focus.
- Focused tabs and channel headers stay visible. Channel profiles collapse to leave
  room for content while keeping the toolbar and tabs available.
- Brightness and volume gesture indicators are larger on wide video surfaces and
  adapt to short or resized windows.
- Playback-speed labels avoid long decimal values.

## Fixes and translations

- Following releases destroyed pager and list views, adapters and observers.
- Refreshing bookmarks preserves their saved identity and updates metadata and art.
- Growing VoDs keep saved positions beyond an outdated duration. Cached or missing
  artwork no longer causes an automatic restart; explicit timestamps still win.
- Local follow status works when an account ID remains without usable API tokens.
- Chat profiles use the correct viewer for follow-date lookup where permissions
  allow it. Failed permission checks still allow the profile to open.
- Chat-color responses are handled on HttpEngine and legacy Cronet.
- Updated compatible German, Japanese, Spanish and Russian translations.

## Upgrading and limitations

Install the official signed APK over the existing official app to retain accounts,
settings, Stats, bookmarks and downloads. This update adds no database migration.
Debug builds use a separate app ID and do not verify the official upgrade path.

TV support adds remote navigation to the existing interface. It is not a dedicated
television layout. Automated view tests and Android TV emulator checks cover focus
and navigation; physical TV/OEM remote and codec compatibility remain unverified.

Updater downloads still require a fresh attempt after Android kills the app process.

See the [APK verification guide](https://github.com/tzii/ThystTV/blob/master/docs/APK_VERIFICATION.md)
for checksum and official signing-certificate verification.

# ThystTV 1.3.0

## Player and viewing

- Consistent Quality, Speed, Stream volume and More overlays with fixed headers and reachable options.
- Fit/Fill/Stretch landscape display modes, pinch feedback, a gesture guide and improved tablet mini-player sizing.
- Stable Quality gear through rotation and clearer brightness, device-volume and stream-volume feedback.
- Closed players release their landscape system-bar listener.
- Completed saved videos restart at zero; unfinished positions and explicit timestamps stay respected.
- Legacy headset previous/next controls seek once per press.

## Discovery, chat and Stats

- Live status and stream entry points in channel search and profiles.
- Responsive high-speed VoD chat replay, including downloaded chat.
- Correct followed-channel paging and native Twitch emote autocomplete refresh.
- Destination-host labels for social links and compact Markdown heading support.
- Compact Stats cards, readable chart scales and smoother range changes.

## Updates and appearance

- Clear update metadata and release notes, fixed actions and live window resizing.
- Shared download and installer state survives screen pauses/recreation while the process remains alive.
- Cancel/retry/browser recovery, install-permission guidance and private session-specific installer callbacks.
- Refreshed adaptive/themed/legacy gem icon and restored localized text.

## Upgrade notes

Landscape display settings migrate automatically to Fit/Fill/Stretch; no database schema migration is added.
Updater downloads do not resume after process death. Cronet/HttpEngine may report only final progress.
New updater captions use English fallback where translations are unavailable.
For unfinished downloads originally started on 1.2.0, delete and restart those downloads.

# ThystTV 1.2.1

ThystTV 1.2.1 is a focused compatibility and reliability release.

## Compatibility fixes

- Bookmark expiry now follows Twitch's current retention policy: 7 days for regular accounts, 14 days for Affiliates, and 60 days for Partner/Prime/Turbo accounts.
- Stream results with missing broadcaster identity are filtered before display.
- Chat subscription, gift, and raid events without text keep their event metadata.
- Twitch GraphQL and playback-token requests use the canonical endpoint.
- Downloaded video thumbnails update correctly after moving files.

## Bookmarks and downloads

- Saved bookmarks and the Downloads tab stay reachable offline; metadata refreshes are deferred instead of blocking access.
- Bookmark expiry estimates for regular accounts now use 7-day retention (display and sorting only).
- Partial downloads include the HLS segment overlapping the requested start time.
- 7TV channel emotes load via the emote-set endpoint fallback.

## Release verification

- Signed releases are verified by APK checksum and official signing certificate before install (see the APK verification guide).

# ThystTV 1.2.0

ThystTV 1.2.0 is the first major milestone release for the fork. It brings the app closer to a complete ThystTV experience with player polish, local stats improvements, a ThystTV-owned updater, bundled changelog support, refreshed project docs, and release automation.

## Player and controls

- Added a custom fullscreen quality picker with clearer video quality, audio-only, and chat-only controls.
- Fixed the landscape audio-only/manual-quality edge case that could collapse the quality list to only Auto and utility modes.
- Improved fullscreen playback speed controls and gesture feedback.
- Made VoD scrubbing duration-aware so long videos are easier to navigate.
- Cleaned up minimized player presentation on wide aspect ratios.

## Updater and changelog

- Update checks now use ThystTV GitHub releases by default.
- Release notes render in the updater and changelog screens.
- APK downloads show progress before the install handoff.
- A bundled changelog is available when network release data cannot be loaded.

## Stats and layout

- Added stats filters for 7 days, 30 days, and all time.
- Polished stats range controls.
- Improved layout behavior for phones, tablets, and wider screens.

## Stability and upstream sync

- Included a focused Media3/ExoPlayer rollback from upstream Xtra as a fix candidate for the player crash regression reported in issue #5.
- Included or adapted upstream Xtra fixes for strings, stream download quality, ProGuard, unraid message handling, and updater download progress.
- Deferred broad upstream player rewrites, Hilt removal, generated query churn, network refactors, and repo-wide rename churn until after 1.2.

## Thanks

Thanks to @Bijman for opening issue #5 and providing LogFox captures for the random player crash regression. Those logs helped narrow the regression window and guided the focused Media3/ExoPlayer rollback in this release.

Thanks also to the user who reported the landscape quality-dialog regression before release.
