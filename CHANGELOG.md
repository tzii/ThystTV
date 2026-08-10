# Changelog

All notable changes to ThystTV should be documented here.

## [Unreleased]

### Fixed
- Bookmark VOD expiry now uses Twitch's current 7-day regular, 14-day Affiliate, and 60-day Partner/Prime/Turbo retention policy (manual ports from upstream Xtra `627d440f` and `15dd7d9e`).
- Stream results with both broadcaster ID and login missing are now filtered before display (manual port from upstream Xtra `cfa61fc8`).
- USERNOTICE chat events without message text now preserve their `msg-id` for subscription, gift, and raid handling (manual port from upstream Xtra `ac0afa3d`).
- Twitch GraphQL and playback-token requests now use the canonical no-trailing-slash endpoint (manual port from upstream Xtra `345cff59`).
- Downloaded video thumbnails now update correctly when moving downloaded files (manual port from upstream Xtra `12a8fac5`).
- Channel point reward cost in chat now uses locale-aware number formatting (manual port from upstream Xtra `12a8fac5`).
- Keep locally saved bookmarks and the Downloads tab reachable when bookmark metadata cannot refresh offline.
- 7TV channel emotes now load from the referenced emote-set endpoint when the channel response omits or returns an empty embedded set (manual port from upstream Xtra `9c47305f`).

## [1.2.0] - 2026-05-08

Major milestone release with the new ThystTV updater/changelog flow, custom quality and speed dialogs, local stats range filters, refreshed README/GitHub Pages assets, release automation, and focused upstream Xtra fixes.

See `docs/release-notes/1.2.0.md`.

## [1.1.6] - 2026-04-19

See `docs/release-notes/1.1.6.md`.

## [1.1.5] - 2026-04-15

See `docs/release-notes/1.1.5.md`.

## [1.1.4] - 2026-04-13

See `docs/release-notes/1.1.4.md`.
