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
