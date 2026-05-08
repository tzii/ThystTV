# ThystTV 1.2 Upstream Commit Status

Last updated: 2026-05-08

Branch: `release/1.2-prep`

Upstream remote: `upstream` -> `https://github.com/crackededed/Xtra.git`

Latest verified upstream head: `71d1222c3602d3e5977ac32d050c9bd21fa8900b` (`WIP player changes`)

Most recent refresh: 2026-05-08 during the final 1.2 release sweep. `git fetch upstream` completed successfully and `upstream/master` had moved from `377bfac1` to `71d1222c`.

This file tracks how the current 1.2 release branch relates to recent upstream Xtra commits. ThystTV is not blindly merging upstream because several changes overlap with ThystTV-specific player, updater, and release-note work.

## How to refresh this view

```powershell
git fetch upstream
git cherry -v release/1.2-prep upstream/master
```

In `git cherry` output:

- `-` means the patch is already present, either directly or as an equivalent change.
- `+` means the patch is not present exactly. It may still have been partially or manually ported.

## Already present

| Upstream commit | Status | Notes |
| --- | --- | --- |
| `e3bcad57` - Update strings.xml (#908) | Present | Patch-equivalent on `release/1.2-prep`. |
| `9d630ce4` - fix stream download quality | Present | Patch-equivalent on `release/1.2-prep`. |
| `579f87ac` - update proguard | Present | Patch-equivalent on `release/1.2-prep`. |
| `582f58ef` - update unraid message id | Ported | Applied on this branch as `373203e1`. |
| `06fd811b` - downgrade exoplayer | Ported | Accepted on 2026-05-06 as a targeted fix candidate for ThystTV issue #5. ThystTV kept its own app id/version and took only the Media3 `1.10.0` -> `1.9.3` downgrade plus the HLS parser compatibility rollback. |
| `88bc97b4` - update unraid message type | Adapted | Accepted on 2026-05-08 as a narrow chat compatibility fix. ThystTV kept its current notice parser and only added the new hide-raid behavior for parsed messages with `msgId == "unraid"`. |

## Manually ported or partially ported

| Upstream commit | Status | ThystTV handling |
| --- | --- | --- |
| `628ba784` - show update download progress | Partially ported | The useful updater download-progress behavior was manually implemented as `c35c1876` while preserving ThystTV release-note/changelog behavior. Do not cherry-pick the upstream commit directly because it also carries broader network utility changes that conflict with our updater work. |

## 2026-05-08 release sweep

`git cherry -v release/1.2-prep upstream/master` now shows four newer upstream commits after the previously reviewed `377bfac1` head:

```text
+ 08e29b1c German translation, further translation (#936)
+ 88bc97b4 update unraid message type
+ bd5656c5 remove hilt
+ 71d1222c WIP player changes
```

Decision for 1.2:

- `08e29b1c` can be deferred. It is a German translation update and has low release risk, but it is not required for the 1.2 player/updater/site release gate.
- `88bc97b4` was manually adapted for 1.2 as a small chat compatibility change.
- `bd5656c5` is deferred. It removes Hilt and touches dependency injection, repositories, view models, player services, and download workers. This is far too broad for the release branch.
- `71d1222c` is deferred. It is another broad WIP player/service change and conflicts with the 1.2 focus on stabilizing the existing player polish.

No broad upstream merge should happen before `v1.2.0`.

## 2026-05-05 selective sync pass

At the time, `git cherry -v release/1.2-prep upstream/master` still showed the same remaining upstream candidates:

```text
+ a25b9310 replace bundleOf
+ b35c869b WIP player changes
+ 7df2806e query updates
+ 06fd811b downgrade exoplayer
+ 1be85689 remove debug api setting
+ 90035c15 integrity SharedFlow
+ 628ba784 show update download progress
+ 8eb4a669 okhttp executeAsync
+ 377bfac1 rename files
```

No additional upstream code was accepted in this pass. The only user-facing upstream item needed for 1.2, updater download progress, is already manually covered by ThystTV's `c35c1876` implementation. The remaining commits are either cleanup-only, broad architecture churn, player-risky, or already documented as post-1.2 work.

## 2026-05-06 issue #5 focused sync

ThystTV issue #5 reports random player crashes in `v1.1.6` that did not happen in `v1.1.3`. The attached LogFox files were downloaded and checked locally under `C:\Users\tizzi\AppData\Local\Temp\thysttv-issue-5`.

Findings:

- The logs do not show a clean ThystTV `FATAL EXCEPTION` stack.
- The strongest repeated signal is Android 15/Nubia framework `AudioTrack` binder warnings: `Unable to retrieve AudioTrack pointer for setVolume()`.
- Those warnings occur while Media3 player fragments/codecs are being torn down and recreated.
- `v1.1.3` used Media3 `1.9.2`; `v1.1.6` moved to Media3 `1.10.0`; upstream later committed `06fd811b` to downgrade ExoPlayer/Media3 to `1.9.3`.

Decision: accept the narrow upstream dependency rollback now. Do not accept `b35c869b` WIP player changes or the broad `7df2806e` query/update churn for this issue.

Verification:

```powershell
.\gradlew.bat assembleDebug
```

Result: passed on 2026-05-06.

## 2026-05-05 refresh after update-sheet polish

`git ls-remote https://github.com/crackededed/Xtra.git refs/heads/master refs/heads/main` returned:

```text
377bfac17ff67f49f58593f79f17850693277aa0 refs/heads/master
```

`git cherry -v release/1.2-prep upstream/master` remained unchanged:

```text
+ a25b9310 replace bundleOf
- e3bcad57 Update strings.xml (#908)
+ b35c869b WIP player changes
- 9d630ce4 fix stream download quality
- 579f87ac update proguard
+ 7df2806e query updates
+ 06fd811b downgrade exoplayer
+ 1be85689 remove debug api setting
+ 90035c15 integrity SharedFlow
+ 628ba784 show update download progress
+ 8eb4a669 okhttp executeAsync
- 582f58ef update unraid message id
+ 377bfac1 rename files
```

Decision: no new upstream commits landed since the previous pass, so there is nothing new to accept for 1.2. Keep the current selective-sync decision unchanged.

## Reviewed and deferred

| Upstream commit | Recommendation | Reason |
| --- | --- | --- |
| `a25b9310` - replace bundleOf | Defer / optional cleanup | Low user value for 1.2. Safe-looking cleanup, but not release-critical. |
| `b35c869b` - WIP player changes | Defer | WIP player work is high risk for the 1.2 player polish already on this branch. |
| `7df2806e` - query updates | Defer | Very broad generated/schema/player/network change set. Needs a dedicated upstream-sync pass. |
| `06fd811b` - downgrade exoplayer | Accepted | Ported as the focused issue #5 fix candidate after log review and a successful debug build. |
| `1be85689` - remove debug api setting | Defer | Removes the debug API selector but also renames data-source concepts and touches many localized strings. Not worth mixing into the 1.2 updater/icon branch. |
| `90035c15` - integrity SharedFlow | Defer | Broad UI/view-model refactor. Not a safe release-branch cherry-pick. |
| `8eb4a669` - okhttp executeAsync | Defer for 1.2 | Useful direction, but not safe to cherry-pick. It depends on upstream network utility structure that ThystTV does not currently have, touches auth/GQL/Helix/player/download/updater paths, includes unrelated build/version changes, and has at least one response-close concern in the upstream patch. If taken, do a manual minimal port in a separate commit. |
| `377bfac1` - rename files | Defer | Broad rename churn. Avoid on the release branch unless needed for a later upstream sync. |

## Recommended rule for 1.2

For the 1.2 release branch, only take upstream commits that are either:

- already patch-equivalent,
- small isolated fixes,
- or manually portable without disturbing ThystTV's player and updater behavior.

Everything else should move to a separate post-1.2 upstream-sync branch.

## If we later port `8eb4a669`

Do not cherry-pick it directly. The safer plan is:

1. Add a small ThystTV-owned `Call.executeAsync()` helper.
2. Convert only suspended OkHttp calls where the response is already closed with `.use { ... }`, or where closing can be added safely.
3. Skip upstream app version and Android Gradle Plugin changes.
4. Keep updater/download streaming code unchanged until manually tested.
5. Retest login/auth, browse/search, live playback, VOD playback, downloads, and updater download progress.
