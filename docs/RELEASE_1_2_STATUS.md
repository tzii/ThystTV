# ThystTV 1.2 Prep Status

Last updated: 2026-05-06

Branch: `release/1.2-prep`

## Current findings

- Player polish is the strongest completed part of the branch: the fullscreen speed button now shows the current speed, fullscreen VoD scrubbing is duration-aware and much faster, and minimized-player black bars/aspect mismatch were addressed.
- The playback speed popup is in a better state than the original oversized dialog. The custom quality popup was intentionally reverted because portrait mode remained unreliable and it was becoming too risky for the 1.2 scope.
- Repo presentation work is in place: README structure, community files, CI/debug/release workflows, roadmap, testing notes, release process docs, and upstream-sync policy.
- Screenshots are still missing from the README and site. This is now one of the clearest remaining visual gaps before 1.2 feels polished.
- The app already has an update-checking path, but it still needs ThystTV-specific polish: the default update source should point to ThystTV releases, update detection should compare release versions instead of relying only on asset timestamps, and the changelog/release notes should be visible in-app.
- The latest Xtra upstream commits were reviewed at a high level and should not be merged blindly. Several are broad network/player/data-source refactors that could easily regress the player work already done on this branch.
- The detailed upstream commit ledger for this branch is in `docs/RELEASE_1_2_UPSTREAM_COMMITS.md`.
- Issue #5 player crash investigation is now tied to a specific fix: the reporter's LogFox files were downloaded and reviewed from `C:\Users\tizzi\AppData\Local\Temp\thysttv-issue-5`. They did not contain a clear ThystTV `FATAL EXCEPTION`; the concrete repeated signal was Android 15/Nubia `AudioTrack` `setVolume()` binder warnings while Media3 player fragments were being recreated. The targeted 1.2 fix is to port upstream Xtra `06fd811b`'s Media3/ExoPlayer downgrade from `1.10.0` to `1.9.3`, with the matching HLS parser compatibility rollback.

## Latest Xtra commits reviewed

- `8eb4a669` - okhttp `executeAsync`: useful direction, but touches network calls across repositories and view models.
- `628ba784` - show update download progress: relevant to the planned updater work, but bundled with large network utility changes.
- `90035c15` - integrity SharedFlow: broad UI/view-model refactor, not a quick safe cherry-pick.
- `1be85689` - remove debug API setting: probably useful, but still touches many data sources and localized settings strings.
- `06fd811b` - downgrade ExoPlayer: accepted on 2026-05-06 as the focused fix candidate for issue #5 after reviewing the attached logs.
- `7df2806e` - query updates: very large generated/schema/player/network change set; defer unless we intentionally do an upstream-sync pass.

## Recommended next steps

1. Add real app screenshots to `README.md` and `docs/index.html`, using consistent framing for browse, fullscreen player, floating chat, and stats.
2. Implement the ThystTV updater pass: point defaults at ThystTV GitHub releases, compare versions, show release notes/changelog in-app, and add download progress if safe.
3. Check MetroList's updater/changelog UX before implementing, then copy the pattern conceptually rather than adding a large dependency.
4. Do a selective Xtra sync in a separate commit or PR, starting with the smallest updater/network pieces only after local build and player smoke testing.
5. Finish 1.2 release prep: update release notes, bump version when ready, rebuild debug APK, test portrait/landscape player flows, then tag `v1.2.0`.

## Current release gate

Before merging 1.2 into `master`, verify:

- `./gradlew.bat assembleDebug`
- `./gradlew.bat testDebugUnitTest`
- live stream playback
- VoD playback and fullscreen scrubbing on short, medium, and very long VoDs
- playback speed button and speed dialog in landscape and portrait
- minimized-player layout on phone and tablet aspect ratios
- README/site screenshots render correctly on GitHub
- update checker points to ThystTV releases, not upstream Xtra
- issue #5 soak test: run live playback and VoD playback for at least 30-60 minutes on the latest debug APK, then check LogFox/logcat for `AudioTrack` binder spam, app process death, or `AndroidRuntime` fatal exceptions
