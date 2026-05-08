# ThystTV 1.2 Prep Status

Last updated: 2026-05-08

Branch: `release/1.2-prep`

## Current findings

- Player polish is implemented: the fullscreen speed button shows the current speed, fullscreen VoD scrubbing is duration-aware, minimized-player visuals were cleaned up, and the custom speed dialog remains in place.
- The playback quality picker has been reintroduced as a custom ThystTV dialog and should be covered by manual portrait, landscape, audio-only, chat-only, and low-height checks before tagging.
- Updater and changelog polish is implemented: default release checks point at ThystTV GitHub releases, update comparison uses release versions, release notes render with Markwon, and updater download progress is surfaced.
- Repo presentation work is in place: README, GitHub Pages, screenshots/media, community files, CI/debug/release workflows, roadmap, testing notes, release process docs, and upstream-sync policy.
- Stats range filters are implemented, but the stats ViewModel unit tests must stay green because this area has had recent churn.
- Issue #5 player crash investigation is tied to the focused Media3/ExoPlayer rollback to `1.9.3`; keep the Android 15/Nubia soak test in the final manual gate.

## Latest Xtra commits reviewed

- `08e29b1c` - German translation update: low-risk localization only; optional for 1.2.
- `88bc97b4` - update unraid message type: manually adapted for 1.2 by hiding the raid banner when parsed chat/usernotice messages carry `msgId == "unraid"`.
- `bd5656c5` - remove Hilt: broad architecture migration; defer.
- `71d1222c` - WIP player changes: broad player/service churn; defer for 1.2.
- Earlier deferred items remain deferred unless there is a specific user-facing bug: query updates, debug API removal, integrity SharedFlow, okhttp executeAsync, and repo-wide renames.

## Remaining release blockers

1. Keep `testDebugUnitTest` green after the stats test fix.
2. Run `assembleDebug` and `assembleRelease` after the version bump.
3. Manually QA player, quality menu, updater/changelog, stats filters, floating chat, and README/site rendering.
4. Tag `v1.2.0` only after the manual QA gate is complete.

## Current release gate

Before merging 1.2 into `master` or tagging `v1.2.0`, verify:

- `.\gradlew.bat testDebugUnitTest`
- `.\gradlew.bat assembleDebug`
- `.\gradlew.bat assembleRelease`
- live stream playback
- VoD playback and fullscreen scrubbing on short, medium, and very long VoDs
- playback speed button and speed dialog in landscape and portrait
- quality picker in portrait, landscape, audio-only, chat-only, and low-height cases
- minimized-player layout on phone and tablet aspect ratios
- floating chat open, drag, resize, opacity, and high-visibility behavior
- stats range filters and chart/card updates
- updater and changelog screens, including offline fallback markdown
- README/site screenshots render correctly on GitHub Pages and GitHub README
- issue #5 soak test: run live playback and VoD playback for at least 30-60 minutes on the latest debug APK, then check LogFox/logcat for `AudioTrack` binder spam, app process death, or `AndroidRuntime` fatal exceptions
