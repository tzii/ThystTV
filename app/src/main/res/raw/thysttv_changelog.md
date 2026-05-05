# ThystTV 1.2

## Player and controls

- Improved fullscreen gesture tuning for seeking, brightness, volume, and playback speed.
- Kept the safer stock quality picker after the custom quality redesign caused portrait-mode regressions.
- Preserved the player hotfixes from 1.1.6 for stream switching and Media3 handoff behavior.

## Updater

- Update checks now default to ThystTV GitHub releases instead of upstream Xtra.
- Release version comparison now uses the GitHub release tag instead of only asset timestamps.
- Release notes render as markdown in Settings and update prompts.
- Update downloads show progress before the APK install handoff.

## Repo presentation

- README screenshot slots are prepared for final app screenshots.
- Release process notes and upstream sync notes are tracked for maintainers.

## Upstream sync

- Already included: upstream strings, stream download quality fix, ProGuard update, query updates already ported where safe, and the unraid message-id fix.
- Deferred for after 1.2: upstream WIP player rewrite, broad query/schema churn, ExoPlayer downgrade, large network refactors, and rename-only churn.
