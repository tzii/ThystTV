# Changelog

All notable changes to ThystTV should be documented here.

## [Unreleased]

### Added
- In full-screen landscape, swipe sideways to show or hide chat (#72). On live streams the swipe works anywhere on the video; on VoDs, clips and downloads start it at the edge of the video on the chat side, so seek and speed swipes keep the rest. Swiping away from the chat side opens the side chat, swiping toward it hides side or floating chat. Turn it off in Player settings → Gestures.
- Extended zoom (#73), off by default in Player settings → Gestures. In full-screen landscape, keep pinching past Fill's resistance to magnify up to 4× Fit, for example to read a minimap. The picture under your fingers stays under them, without moving past the edges of the video. Pinch back to Fill to recenter. Zoom below Fill is unchanged and stays centered. The magnification lasts until you leave full-screen landscape or choose a display mode.

## [1.5.1] - 2026-10-27

### Added
- Opening a VoD while casting offers "Stop casting and play here", which ends the Cast session and plays the VoD on the phone. Downloads still ask you to stop casting first.

### Fixed
- Live casting works on Chromecast with Google TV: the receiver gets a variant playlist it can load, matching the phone's quality (best on Auto). Streams without a receiver-compatible variant keep playing on the phone with a message. VoDs stay on the phone because Twitch's VoD servers refuse receivers, and quality can't be changed while casting.
- VoDs and downloads resume at their saved position again instead of restarting at 0:00.
- Live quality changes start at the live edge.

### Release metadata
- Version 1.5.1 uses version code 16 and retains database version 36. Scope and limitations: `docs/release-notes/1.5.1.md`.

## [1.5.0] - 2026-10-23

### Added
- Finished online VoDs can show storyboard thumbnails and timestamps while dragging the seek bar.
- The live-rewind button is available in the maximized portrait player too, with stream stats below the action row.
- Experimental Google Cast (Player settings, off by default) hands online video to a receiver on the same network. Downloads stay on the phone, and the receiver fetches video directly without the phone's proxy or custom headers. Physical receiver playback remains unverified.
- Optional Chat in picture-in-picture (off by default) keeps read-only live chat beneath the video when PiP starts from the portrait player.
- Live streams reconnect on their own after short network drops or stalls: up to three retries, waiting at most a minute for the connection to return, then a message asks you to press Play.
- Android TV can sign in from your phone: scan the on-screen QR code or enter the code at twitch.tv/activate, then choose Check sign-in. Browser sign-in stays available.
- Keep casting after closing the app (Player settings, off by default) leaves a Cast video playing on the receiver when you close ThystTV; control it from the media notification.

### Changed
- Update Media3 to 1.11.1, adapting session connection permissions and Twitch HLS metadata while preserving the default playback service.
- Release builds include generated startup and local-navigation optimization profiles, with repeatable capture and cold-start benchmarks for validation.
- Busy chats add new messages in short batches and look up emotes and 7TV users directly instead of scanning every emote, so chat scrolls more smoothly.

### Removed
- The unused Glide image library and its Chat → Image library setting. Emotes and badges already loaded with Coil, so the APK just drops Glide and its native WebP decoder.

### Fixed
- Hiding or showing the system bars no longer crashes a player that is closing.
- Channel Points use Twitch's current balance query.
- A late-loading emote can no longer show an older message in a reused chat row.
- Chat cleared while the app was in the background no longer leaves the list out of sync when you return.
- VoD replay chat resumes when playback becomes ready.
- Saved and Search tabs and channel-profile actions stay readable at 200% text size.
- A VoD or clip playing in the background no longer retries a broken video forever. It retries network errors up to five times over about a minute, then stops.
- Retained Cast playback is checked before opening downloads or switching player backends after app exit. Rejected starts leave receiver media intact, and phone-local URLs cannot replace a playing receiver source.
- A rejected Cast attempt cannot restore obsolete media after a newer source or transfer takes ownership. Numeric receiver URLs are validated without DNS lookups on the UI thread.

### Release metadata
- Version 1.5.0 uses version code 15 and retains database version 36. Scope and limitations: `docs/release-notes/1.5.0.md`.

## [1.4.0] - 2026-10-13

### Added
- Recording-backed live rewind from More or a full-screen ⏪ button, with replay chat, Go live and a 1x reset when faster playback catches up.
- Free pinch zoom between Fit and Fill in landscape, kept across streams and shown in More → Display mode.
- Channel Points custom reward redemption from chat with an explicit cost confirmation.
- Optional spoiler-free VoDs setting that hides the seek bar, length and watched progress of VoDs and downloads.
- Separate default quality for metered Wi-Fi.
- Half-folded layout for foldables: video above the hinge with chat below in tabletop posture, or video and chat side by side in book posture (Settings → Player settings, on by default).

### Changed
- Qualities keep their exact resolution, frame rate, codec and bitrate in the player, quality menu and downloads. Portrait variants are hidden, and clips can offer HEVC/AV1.
- Live notifications use one live-follows request per poll, cache each channel's notification setting, keep durable deduplication and retry failed polls sooner.
- Group Settings into sections with icons and summaries, index nested settings in search, and follow predictive back gestures.
- Refresh update dialogs and Changelog with Material 3 layouts, Markdown notes and reachable actions at large text sizes.

### Fixed
- Settings drag lists keep the on-screen order after a fast drag.
- The gesture guide no longer opens in picture-in-picture.
- Closed live players are no longer kept in memory by the chat keyboard listener.
- Release Search pager views and tab connections when their view is destroyed.
- Stop closed Settings screens being retained by seekable transitions.
- Apply all three rounded-corner setting choices correctly.

### Release metadata
- Version 1.4.0 uses version code 14 and migrates the database to version 36. Scope and limitations: `docs/release-notes/1.4.0.md`.

## [1.3.1] - 2026-09-30

### Added
- Android TV D-pad navigation and visible focus for browsing, tabs, Stats and player controls.

### Changed
- Larger brightness and volume gesture feedback on wide video surfaces, with compact sizing in short windows.
- Updated compatible German, Japanese, Spanish and Russian translations.

### Fixed
- Keep focused TV tabs and channel headers visible, restore focus after profile collapse, and round playback-speed labels.
- Release Following pager/list views and observers when their views are destroyed.
- Preserve bookmark identity and refreshed metadata, including thumbnail updates.
- Preserve resume positions for growing VoDs and bookmarks whose artwork cannot establish completion.
- Use local follows when an account ID remains without usable API tokens.
- Correct chat profile follow-date fallback and chat-color responses on HttpEngine and legacy Cronet.

### Release metadata
- Version 1.3.1 uses version code 13. Scope and limitations: `docs/release-notes/1.3.1.md`.

## [1.3.0] - 2026-09-11

### Added
- Consistent Quality, Speed, Stream volume and More player overlays with fixed headers and reachable options.
- Canonical Fit/Fill/Stretch landscape display modes, pinch feedback and a playback gesture guide.
- Live status and stream entry points in channel search and profiles.

### Changed
- Improved tablet mini-player sizing, gesture feedback and stable Quality gear behavior through rotation.
- Redesigned compact Stats cards and improved chart scales, range controls and animation interruption.
- Refreshed adaptive, themed and legacy gem launcher artwork.
- Refreshed the update prompt with clear version details, rendered release notes and fixed actions that reflow at large text sizes.
- Shared retained download/installer state across updater entry points, with pause/recreation recovery, cancel/retry and browser fallback.
- Updater dialogs follow host-window resizing and request install permission when needed.

### Fixed
- Restored correctly encoded translations across 13 locales after a source-edit encoding regression.
- Released the landscape system-UI listener when a player closes or its view is destroyed.
- Kept high-speed network/downloaded VoD chat replay responsive and preserved appended followed-channel pages.
- Restored native Twitch autocomplete suggestions and synchronized suggestion snapshots during refresh.
- Restarted completed saved videos at zero while preserving unfinished positions and explicit timestamps.
- Handled unclaimed legacy headset previous/next buttons once per press.
- Rendered compact Markdown headings and displayed destination hosts on channel social links.

### Security
- Replaced exported installer-intent forwarding with a private callback receiver, per-attempt session identity and validated system confirmation handoff.

### Release tooling
- Added native Windows verifier coverage and corrected SDK batch launcher handling.
- Version 1.3.0 uses version code 12. Full scope and upgrade notes: `docs/release-notes/1.3.0.md`.

## [1.2.1] - 2026-08-10

### Fixed
- Bookmark VOD expiry now uses Twitch's current 7-day regular, 14-day Affiliate, and 60-day Partner/Prime/Turbo retention policy (manual ports from upstream Xtra `627d440f` and `15dd7d9e`).
- Stream results with both broadcaster ID and login missing are now filtered before display (manual port from upstream Xtra `cfa61fc8`).
- USERNOTICE chat events without message text now preserve their `msg-id` for subscription, gift, and raid handling (manual port from upstream Xtra `ac0afa3d`).
- Twitch GraphQL and playback-token requests now use the canonical no-trailing-slash endpoint (manual port from upstream Xtra `345cff59`).
- Downloaded video thumbnails now update correctly when moving downloaded files (manual port from upstream Xtra `12a8fac5`).
- Channel point reward cost in chat now uses locale-aware number formatting (manual port from upstream Xtra `12a8fac5`).
- Saved bookmarks and the Downloads tab stay reachable offline while bookmark metadata refreshes are deferred instead of blocking access.
- 7TV channel emotes now load from the referenced `/v3/emote-sets/{setId}` endpoint when the channel response omits or returns an empty embedded set (manual port from upstream Xtra `9c47305f`).
- Partial video downloads now include the HLS segment that overlaps the requested start time (manual port from upstream Xtra `8a8b99c5`).

### Changed
- Bookmark expiry estimates for regular accounts now assume 7-day VOD retention instead of 14 days; this changes bookmark expiry display and sorting only and never deletes Twitch content.

### Release tooling
- Local release builds remain unsigned; signed release candidates are built in CI and promoted to a published release only after exact-byte verification against the release manifest.
- README and website download instructions now point to GitHub Releases with checksum and signing-certificate verification guidance (`docs/APK_VERIFICATION.md`).

## [1.2.0] - 2026-05-08

Major milestone release with the new ThystTV updater/changelog flow, custom quality and speed dialogs, local stats range filters, refreshed README/GitHub Pages assets, release automation, and focused upstream Xtra fixes.

See `docs/release-notes/1.2.0.md`.

## [1.1.6] - 2026-04-19

See `docs/release-notes/1.1.6.md`.

## [1.1.5] - 2026-04-15

See `docs/release-notes/1.1.5.md`.

## [1.1.4] - 2026-04-13

See `docs/release-notes/1.1.4.md`.
