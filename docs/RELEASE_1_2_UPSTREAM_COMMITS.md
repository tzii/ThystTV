# ThystTV 1.2 Upstream Commit Status

Last updated: 2026-05-01

Branch: `release/1.2-prep`

Upstream remote: `upstream` -> `https://github.com/crackededed/Xtra.git`

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

## Manually ported or partially ported

| Upstream commit | Status | ThystTV handling |
| --- | --- | --- |
| `628ba784` - show update download progress | Partially ported | The useful updater download-progress behavior was manually implemented as `c35c1876` while preserving ThystTV release-note/changelog behavior. Do not cherry-pick the upstream commit directly because it also carries broader network utility changes that conflict with our updater work. |

## Reviewed and deferred

| Upstream commit | Recommendation | Reason |
| --- | --- | --- |
| `a25b9310` - replace bundleOf | Defer / optional cleanup | Low user value for 1.2. Safe-looking cleanup, but not release-critical. |
| `b35c869b` - WIP player changes | Defer | WIP player work is high risk for the 1.2 player polish already on this branch. |
| `7df2806e` - query updates | Defer | Very broad generated/schema/player/network change set. Needs a dedicated upstream-sync pass. |
| `06fd811b` - downgrade exoplayer | Defer | Player dependency change. Only take with a focused playback regression pass. |
| `1be85689` - remove debug api setting | Maybe later | Probably useful, but touches data sources/settings/localized strings. Not urgent for 1.2. |
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
