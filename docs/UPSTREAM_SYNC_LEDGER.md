# Upstream Sync Ledger

This is the living ledger for ThystTV's selective sync against upstream
[`crackededed/Xtra`](https://github.com/crackededed/Xtra). The policy that governs these
decisions lives in [`UPSTREAM_SYNC.md`](UPSTREAM_SYNC.md). The 1.2-cycle ledger
([`RELEASE_1_2_UPSTREAM_COMMITS.md`](RELEASE_1_2_UPSTREAM_COMMITS.md)) is frozen history;
new audits go here.

## November 22, 2026: 1.7.1 upstream and fork intake

Audited from the 1.7.0 source (`d73e1db34`).

**crackededed/Xtra** `96772030c..0ab697de0` (four commits):

| Commit | Disposition |
| --- | --- |
| `0444de816` fix helix timeout command duration | Adapted (`fix/171-timeout-duration`). ThystTV had the same bug: Helix bans permanently when `duration` is missing, and `/timeout user 10m` sent none. Upstream falls back to 10 minutes for plain-second arguments; ThystTV reads plain seconds and `s`/`m`/`h`/`d`/`w`, stays within Helix's 1 s to 2 weeks and stops with a notice on an unreadable value. Upstream's dependency and wrapper bumps are not taken. |
| `bce2c3746` German translation, further translation (#1077) | Taken (`l10n/171-german-upstream`): 261 of 291 changed strings whose English source matches ThystTV, plus the `followers` plural. Not taken: 25 keys ThystTV lacks, 5 with different English, and the Fastlane store texts. |
| `47c8d6db0` fix download progress update causing errors | N/A: ThystTV downloads use WorkManager workers and Room-observed status, not upstream's bound `VideoDownloadService` listener. |
| `0ab697de0` fix crash during stream downloads | N/A: ThystTV's `StreamDownloadWorker` already assigns `lastSegmentUrl` (no `+=` growth), so no database migration is needed. |

Upstream issues checked against ThystTV: #1082 (timeout became a ban) is the bug above;
#1097 (the live uptime keeps counting after a stream ends while paused in the background)
also applies to ThystTV's uptime `Chronometer` and is left as a cosmetic follow-up; #1095
(`startForeground` from `ExoPlayerService`) concerns upstream's default service, which
ThystTV keeps only behind `DEBUG_USE_CUSTOM_PLAYBACK_SERVICE`.

**ThystTV forks.** Ness945/ThystTV (`28c0b89b5`) adds proxy setting diagnostics and an
app-only scope notice; left as an optional settings feature. JhthnR/XtraForTwitch
(`97d68498f`) adds a swipe-to-chat gesture that ThystTV has shipped since 1.5.2.
feli19honk-pixel/ThystTV has no commits of its own.

**Xtra forks.** thiscallnet/Xtra (`81a9c8304`) merged about 60 PRs since the October
audit, mostly on its own chat v2, VAFT proxy, HUD editor and diagnostics stacks. Not
portable as-is; behavior worth a separate look later: VoD watch progress from Twitch (#617),
cheers rendering (#620), cached emotes offline (#619), a channel emotes tab (#611), and
network-change quality defaults (#568, player-risk). Its #610 (user history without replies
to that user) is a product choice left open: ThystTV's message dialog, like upstream Xtra's,
also lists replies to the selected user. ryankhart/Xtra (`c0c0e267b`) adds list space under the mini player and
swipe-down to close it; a broad list-decoration change, deferred. prime385/Xtra5
(`4c490be5f`) adds a VoD chapters dropdown and countdown; deferred as UI work.
razeNFR/XtraATV (`42d691f74`), Adr1GR/XtraTVReforged and danigalaxy98/XtraTV are TV
rewrites or TV-only layers; Mitchell0402/Xtra is a motion redesign; Kristijan1001/Xtra is
a service/chat rewrite. Nothing was taken from them.

## October 22, 2026: TV device-code sign-in adaptation

Audited razeNFR/XtraATV at `25b4a606f2768eb4ee2532f3570ce689bdd84d4c`, including
its `TvLoginScreen.kt` account-method presentation. Kept native views, existing
scopes, browser flow and storage instead of its Compose/account rewrite. Added a
TV entry, local QR, expiry/retry and guarded manual checks to the existing grant.
The canonical activation route and response timing follow
[Twitch's device-code grant documentation](https://dev.twitch.tv/docs/authentication/getting-tokens-oauth/#device-code-grant-flow).
QR encoding uses [ZXing core 3.5.4](https://github.com/zxing/zxing/releases/tag/zxing-3.5.4)
(Apache 2.0); no external QR service. Real account/device authorization remains
human QA, not an automated test claim.

## October 22, 2026: bounded live recovery adaptation

Audited thiscallnet/Xtra's background recovery and network/frame-preservation
work ([#469](https://github.com/thiscallnet/Xtra/pull/469), merge
`2d5302f0f3680b4982720be21a5acde0951b580d`; [#471](https://github.com/thiscallnet/Xtra/pull/471),
merge `07f7eebbc5936c25fff62d0d9ff0b01155ccf04a`; locally retained donor
`PlaybackService.kt`). Implemented a
small service-owned recovery listener rather than its broad service/proxy rewrite.
Retry count, offline wait and buffering are bounded. Stable playback resets the
budget; pause/switch/close/Cast cancel work. The implementation follows
[Media3 player event ordering and retry guidance](https://developer.android.com/media/media3/exoplayer/listening-to-player-events)
and its [error codes](https://developer.android.com/reference/androidx/media3/common/PlaybackException).
The optional legacy backend is outside this port.

## October 22, 2026: optional PiP chat adaptation

Adapted the interaction from [thiscallnet/Xtra #520](https://github.com/thiscallnet/Xtra/pull/520),
merge `daf8eab52a280a2d8aecc6117e5ec7c427940273`. Retained ThystTV's existing
RecyclerView chat, player surface, controls, fold geometry and draft state;
did not import the donor's canvas renderer or player rewrite. The option defaults
off. Portrait local live chat uses system PiP with guarded eligibility, a composite
source hint and read-only message presentation. [Android PiP guidance](https://developer.android.com/develop/ui/views/picture-in-picture)
informs the early request updates and non-video resize behavior.

## Latest audit

- **Date checked:** 2026-10-04; crackededed rechecked 2026-10-10, 2026-10-20 and
  2026-11-22 (`0ab697de0`, [1.7.1 intake](#november-22-2026-171-upstream-and-fork-intake))
- **Branches:** stacked `xtra-ports/1-notifications` → `2-rewards` → `3-quality` → `4-rewind`, based on `e8cbd77cb9f875ed2e4b777684065278bc766060`
- **crackededed/Xtra:** `9f8043e8bdf45e8a2b2bf4cecfa74dc1de8ae0a3`; rechecked at
  `a3cbb0325f3330573a6738f7d36a2c5b785f4d1c` ([October 10 recheck](#october-10-2026-crackededed-recheck))
  and `96772030c7712581ad150c9b8b619c77a3527e50` ([October 20 recheck](#october-20-2026-crackededed-recheck))
- **thiscallnet/Xtra:** `fbffe567381dfca9970860e2976ab0021c2602eb`
- **Disposition:** bounded manual feature ports, unreleased; the configured local
  Android build/test/lint gates passed. Hosted CI and device QA are separate gates. See [October intake](#october-4-2026-feature-intake-unreleased)
  and [validation status](FEATURE_PORTS_VALIDATION.md).

## September 25, 2026 audit

- **Date checked:** 2026-09-25
- **Branch audited:** `codex/large-screen-upstream-update`, based on public main `fc45301d4`
- **Upstream remote:** `upstream` -> `https://github.com/crackededed/Xtra.git`
- **Latest upstream head checked:** `cadfb8b7c38e29416b02b16cc0ba6c23e137bfcf` (`fix reply id`, September 22)
- **Merge base with upstream:** `b063872f` (`quality model`, 2026-03-19)
- **Commands used:**

```bash
git fetch origin
git fetch upstream
git log --oneline a8e239ef..upstream/master
git show --stat <commit>   # per-commit review
git show <commit> -- <path> # compare candidate hunks with the current ThystTV files
```

`git cherry` legend: `-` = patch already present (directly or equivalent), `+` = not present
exactly (may still be partially/manually ported — see notes).

PR #12 changed 22 files (`+1482/-91`). Post-Hilt upstream changes remain manual-port-only by
default (see the watershed note below).

## September 25 small-update intake

This update manually adapts selected hunks from **nine upstream commits**, together
with ThystTV's larger side-gesture feedback. It starts from public main, preserving
the experimental Twitch sync branch separately. No upstream merge, version,
dependency, service, DI, proxy, updater or release-workflow migration is included.

| Upstream commit | Adapted behavior |
| --- | --- |
| `3918379a14a1a9106961a3c20ddde0f3e5f74e41` | Preserve the Room primary key when refreshing single/batch bookmark metadata, and include thumbnail changes in adapter comparison. Keep immutable replacement objects so old adapter snapshots remain intact; no database schema migration. |
| `4567823a13ef93225b091e9e9b5b9ceb0884a520` | Preserve automatic resume past stale duration for processing/live VoDs. Central lookup checks artwork: processing/missing/local cached thumbnails cannot establish completion. Final remote thumbnails retain completed-VoD restart; explicit offsets and local-download duration policy remain intact. |
| `6513c0772d2816b7b0f9a5ca8fc5c7a022d3a6a5` | Player/channel follow status uses local follows when a retained account ID has no usable API tokens; keep authenticated GQL and Helix fallback. Omit broader notification/game/UI refactors. |
| `adc53a76b2c086ca7725999a56ccace297fbd0cc` | Parse successful chat-color responses on HttpEngine and legacy Cronet, sharing existing parsing across all backends. Omit the command/coroutine rewrite and moderation syntax changes. |
| `3e4bd045141e15b9a61c36e956d372e71af60598` | Decode follower `user_*` fields separately from followed-channel `broadcaster_*` fields; request authenticated GQL profile metadata and add guarded Helix follow-date fallback. Permission failures preserve profiles; cancellation propagates. Keep the existing `follows` package. |
| `08e29b1c072216770e6b9cc0bd8f493724d5ac6d` | Two compatible German translations. |
| `c303f618f2ef089c91d88ed6c9787b2352f6921b` | 196 Japanese strings and three plurals, preserving current feature meanings and units. |
| `4654de0a13c9f7ea8b1d182575c9f5800ec183d2` | Five compatible Spanish strings; Android quote escaping retained. |
| `cf95006c2f7e53b523b934289ec685c8b3850f9a` | Nineteen Russian strings, preserving ThystTV branding and compact-number meaning. |

The 225 localized entries retain existing keys/attributes/plural quantities and
match current English format arguments. Existing updater translations, fork-only
features and incompatible proxy formatting remain unchanged. German commits
`5e91e455`, `b2411e8b`, `c51adf90` and `51517e11b` were audited but add no compatible
missing changes: their keys target absent features, or introduce an incompatible
`proxy_error` placeholder. They are not counted as ports.

All fourteen new commits after the previously inspected `a8e239ef` were checked:

| New commit | Disposition |
| --- | --- |
| `51517e11b` German translations | No compatible missing keys; see above. |
| `bca10f761` setup-java bump | N/A to ThystTV's separately pinned workflows. |
| `cf95006c2` Russian translations | Adapted as above. |
| `fd63cd3fa`, `524d1828f` audio-only transitions/restoration | Deferred: target upstream's newer quality/service flow. ThystTV backends already disable video in several restoration paths; require a reproduced backend-specific failure before lifecycle adaptation. |
| `34e673144` codec proxy parameters | Deferred with proxy compatibility work. |
| `c54331819` proxy token-request timeout | Depends on the deferred timeout/failover series. |
| `3918379a1`, `4567823a1` bookmark update / live VoD resume | Adapted as above. |
| `8814b5bb2`, `e03010e23` highlight / other VoD chat timing | Fix regressions in upstream's unported `createdAt` timing migration. ThystTV keeps millisecond replay and its existing minimum-1ms wait. |
| `adc53a76b`, `3e4bd0451` chat commands / moderator follow dates | Selected hunks adapted as above. |
| `cadfb8b7c` reply ID | Already correct in ThystTV; fixes a regression from the unported command rewrite. |

Older candidates were also rechecked. Emote caching (`419898ad`) substantially
overlaps ThystTV's existing `EmoteCache`/`ChatAssetLoader`. Controls timeout
(`ea0c7b869`) and player link sharing (`8a0ea8609`) remain separate UX work.
Quality/bitrate identity, GIF/parser expansion, seek-value settings, proxy/network
migrations and broad service rewrites remain deferred for dedicated validation.

Earlier manual ports already shipped in public 1.3 are not counted again:
`17587ada` (minimum chat-replay wait), `9500c9c5` (follow pagination), `5e7f6a46`
(Twitch autocomplete), `f10e3abf` (social hosts), `f5f03a1a` (legacy headset seek),
`e4299f7d` (completed resume) and `a6dcee18` (compact headings). These supersede
the corresponding historical deferrals below; no patch-equivalence claim is made.

`assembleDebug test lintDebug` passed on the final source: 520 tests without
failures/errors/skips, lint 0 errors and 342 existing warnings. Independent source
review and native feedback preview inspection passed. Details and remaining device
checks are recorded in `TESTING.md` and `MANUAL_QA.md`; no physical-device QA is claimed.

## The Hilt watershed (read this before any cherry-pick)

Upstream removed Hilt in `bd5656c5` (deferred by ThystTV). **Every upstream commit after
`bd5656c5` is written against the no-Hilt codebase** — constructors, DI wiring, and service
plumbing differ from ThystTV. Direct cherry-picks of post-`bd5656c5` commits will conflict or
silently mismatch; anything we want from that range must be a **manual port**, reviewed hunk
by hunk. If upstream divergence keeps accelerating, a dedicated, fully-QA'd Hilt-removal
migration branch is the long-term option — not a release-branch task.

ThystTV's `PlayerFragment.kt` has also diverged heavily from the merge base (~1.4k diff
lines of gestures/floating chat/speed & quality dialogs), so upstream player commits are
conflict-prone by default.

## New upstream commits since the previous audit endpoint (`f43e3bf0`)

25 commits after `f43e3bf0` through `345cff59`, audited on 2026-08-10 against the PR #12
merge baseline:

| Commit | Subject | ThystTV 1.2.1 disposition |
| --- | --- | --- |
| `c303f618f` | Update Japanese translation (#966) | Defer; localization-only and not required for compatibility. |
| `8a1eab7bf` | remove old irc notice strings | Defer; IRC cleanup belongs with the parser series. |
| `8a0ea8609` | share video link from player menu | Defer; player UI scope. |
| `51a506836` | bump actions/checkout | N/A; ThystTV pins its own workflow actions by verified full SHA. |
| `ac0afa3d6` | fix user notice message id | Shipped by PR #12 as `cba774f47`. |
| `8a8b99c5b` | fix download start time | Included in 1.2.1 as a manual `VideoDownloadWorker` port. |
| `cfa61fc89` | hide streams with null broadcaster | Shipped by PR #12 as `4f6f216b4`. |
| `9886fbf96` | irc parser fixes | Defer; broad parser regression surface. |
| `ea0c7b869` | prevent player controls from hiding when pressing player buttons | Defer; player-control behavior is outside this release. |
| `e4299f7d1` | start video from 0 if saved position is at the end | Defer; player resume behavior needs dedicated QA. |
| `43b363ac7` | cronet proxy | Defer; network architecture change. |
| `2c582bb83` | handle qualities with same name and different bitrate | Defer; quality-model/player plumbing. |
| `a25de6fbd` | find unlisted vod | Defer; new product behavior, not a compatibility blocker. |
| `6513c0772` | fix following check without token | Defer; logged-out follow refactor. |
| `3e2fcf0e4` | recent messages api url setting | Defer; settings/API surface expansion. |
| `2a6e10009` | android 17 local network permission for local proxies | Defer with target-SDK/proxy work. |
| `35f31411a` | use cronet by default | Reject for 1.2.1; changes the default network backend. |
| `069a9877b` | seek button custom values | Defer; player feature. |
| `dfcdc98d1` | fix quality dialog | Defer; conflicts with ThystTV's custom quality UI. |
| `b2411e8b7` | German translation (#986) | Defer; localization-only. |
| `336f5b437` | check if HttpEngine proxy is supported before using it | Defer with proxy/network work. |
| `627d440fc` | update default VOD expiration delay to 7 days | Shipped by PR #12 in `1ec3d5452`. |
| `15dd7d9e0` | fix vod expiration date on channels without affiliate or partner | Shipped by PR #12 in `1ec3d5452`. |
| `9c47305f8` | get 7tv channel emotes from emote set api | Included in 1.2.1 as a Hilt-era manual port. |
| `345cff59a` | fix 404 errors | Shipped by PR #12 as `22d3912cc`. |

### 1.2.1 intake summary

1.2.1 newly ports only `8a8b99c5b` (download range selection) and `9c47305f8` (7TV emote-set
fallback). PR #12 already ships `ac0afa3d6`, `cfa61fc89`, `627d440fc`, `15dd7d9e0`, and
`345cff59a`. All other later commits remain deferred, rejected, or N/A as classified above.

## Commits reviewed on 2026-06-12 (through `f43e3bf0`)

22 new commits, reviewed individually on 2026-06-12:

| Upstream commit | Subject | Recommendation | Why / risk notes |
| --- | --- | --- | --- |
| `5c1bf9ea` | download service fixes | **Defer** | ~4.2k insertions across 36 files; broad download-service rework on the post-Hilt codebase. ThystTV downloads work today. |
| `b916acf6` | fix video offset on new playback service | **Defer** | Small, but targets the "new playback service" path which ThystTV does not enable; `MainActivity` has also diverged. |
| `0b48968e` | query updates | **Defer, watch** | GQL persisted-query/hash churn. Becomes relevant fast if Twitch retires old hashes — if browse/search/playback queries start failing, manually port only the query-hash parts. |
| `e08e2b3c` | target android 17 | **Defer** | targetSdk + dependency bump **plus** a ~490-line rewrite of the low-latency `HlsPlaylistParser`. Core player risk (issue #5 lesson). Revisit deliberately with full live/VoD QA, not as a drive-by. |
| `d9635c13` | restore millisecond accuracy to vod chat | **Manual port candidate** | Touches `PlayerFragment` + chat replay across 27 files. Port only if users report VoD chat sync drift; needs VoD chat replay QA. |
| `419898ad` | cache emote responses | **Manual port candidate** | Real perf win (emote fetch caching), but touches network/cache layers; needs Hilt-context adaptation and chat QA. |
| `5e91e455` | German translation (#956) | **Safe to take** | values-de strings only. Verify each string key exists in ThystTV before applying (our strings diverged slightly). Low priority. |
| `b2ec0aa4` | bump android.yml actions (#957) | **Reject (N/A)** | ThystTV does not have `android.yml`; we maintain our own ci/debug/release workflows. |
| `12a8fac5` | fixes | **Split — partly taken** | 4 independent hunks: ① `DownloadsViewModel` thumbnail-assignment bug fix — **taken** (ported on `fix/upstream-microports`); ② `ChatAdapterUtils` locale-aware reward-cost formatting — **taken** (same branch); ③/④ `ChatReplayManager(Local)` `createdAt` hunks — **defer**, they depend on the `d9635c13` chain. |
| `8bfb8bd8` | kotlin uuid | **Defer** | Cleanup; swaps `java.util.UUID` for experimental Kotlin uuid + version bump. Zero user value. |
| `06d2578e` | kotlin duration | **Defer** | Cleanup only. |
| `c5d65c67` | kotlin time | **Defer** | 31-file time-API migration; churn with no user value, conflict surface in chat/PubSub. |
| `eec90690` | chat message type | **Manual port candidate** | Chat parsing changes (PubSub/recent messages). Affects floating chat rendering; only with dedicated chat QA. |
| `8bb2b86e` | stop new playback service when left idle | **Defer** | New-playback-service only; not ThystTV's active path. |
| `f5f03a1a` | fix headset buttons | **Manual port candidate (top pick)** | Real user value (media-button handling). The `ExoPlayerService` hunk looks portable — ThystTV's copy is close to merge base (~72 diff lines). Skip the `MediaPlayerService` half. QA: headset play/pause, background playback, minimize/restore, notification controls. |
| `6e4aef3c` | show seekbar on notification for streams | **Defer** | Notification UX change entangled with the service refactor sequence; revisit together with `f5f03a1a` if ported. |
| `b17ab587` | show player controls less often | **Reject** | Directly conflicts with ThystTV's controls/gesture/feedback UX in `PlayerFragment`. Our behavior is intentional. |
| `18f901f2` | enable new playback service by default | **Reject for now** | ThystTV stays on its tested playback path; also carries dependency bumps. Re-evaluate only as part of a deliberate playback-service migration. |
| `577f76a3` | fixes | **Defer (mostly N/A)** | Most hunks service the message-translation feature (`translateAllMessages`) which ThystTV doesn't have; the `getMessageIdString(context)` refactor is harmless cleanup we can pick up whenever we next touch that file. |
| `97c8bc6e` | cancel cronet requests on coroutine cancel | **Defer** | ~2.9k insertions across 23 files; wholesale network-layer change (Cronet/coroutines). Needs its own sync pass + full network QA. |
| `e9474f76` | cronet timeouts | **Defer** | ~1k insertions, same area as `97c8bc6e`; take together or not at all. |
| `f43e3bf0` | load vod title when starting from clip | **Manual port candidate** | Nice UX, but implemented on `MediaPlayerService`; would need re-implementation against ThystTV's player path. |

## Carry-over items (decided in the 1.2 cycle, still showing as `+` in `git cherry`)

| Upstream commit | Subject | Standing decision |
| --- | --- | --- |
| `a25b9310` | replace bundleOf | Defer — cleanup, low value. |
| `b35c869b` | WIP player changes | Reject — superseded by later upstream player work; high conflict with our diverged `PlayerFragment`. |
| `7df2806e` | query updates | Defer — broad; same "watch" caveat as `0b48968e`. |
| `06fd811b` | downgrade exoplayer | **Handled** — Media3 `1.9.3` rollback taken 2026-05-06 for issue #5 (partial port, hence still `+`). |
| `1be85689` | remove debug api setting | Defer — renames data-source concepts, many localized strings. |
| `90035c15` | integrity SharedFlow | Defer — broad UI/view-model refactor. |
| `628ba784` | show update download progress | **Handled** — manually implemented as `c35c1876`, preserving ThystTV updater/changelog behavior. Never cherry-pick directly. |
| `8eb4a669` | okhttp executeAsync | Defer — manual-port plan documented in the 1.2 ledger if ever needed. |
| `377bfac1` | rename files | Defer — rename churn. |
| `08e29b1c` | German translation (#936) | Safe to take alongside `5e91e455` (translations only, verify keys). |
| `88bc97b4` | update unraid message type | **Handled** — adapted 2026-05-08 (hide-raid for `msgId == "unraid"`). |
| `bd5656c5` | remove hilt | Defer — the watershed (see above). |
| `71d1222c` | WIP player changes | Defer — broad WIP player/service churn. |

## What was actually taken in this audit

On branch `fix/upstream-microports` (manual ports, not cherry-picks):

1. From `12a8fac5`: `DownloadsViewModel` — moved-video thumbnail was never actually
   reassigned (`newVideoFileUri` was a no-op expression; now `thumbnail = newVideoFileUri`).
   Genuine bug fix, isolated, no behavioral risk beyond fixing the bug.
2. From `12a8fac5`: `ChatAdapterUtils` — channel-point reward cost is now formatted with
   `NumberFormat.getInstance()` (locale-aware separators) in both reward rendering paths.

Everything else: no upstream code merged in this pass.

## Protected areas (never regress for an upstream pick)

- player behavior (live, VoD, stream switching, quality/speed dialogs)
- floating chat
- minimize / restore
- gestures
- updater / changelog behavior
- stats screen

## Retest areas for the taken ports

- downloads: move downloaded videos to a new storage location → thumbnails survive
- chat: channel-point reward messages render correctly (cost shows locale separators),
  including in floating chat overlay
- regression sweep: live playback, VoD playback, floating chat, minimize/restore (CI build +
  smoke test; the two ports don't touch these paths)

## How to refresh this ledger

```bash
git fetch upstream master
git cherry -v master upstream/master
```

Then review each new `+` commit with `git show --stat <sha>` / `git show <sha>`, classify it
(safe to take / manual port / defer / reject), and append a dated section here. Keep the
"latest audit" header current.


## ThystTV 1.3 selected ports

The following upstream changes are manually adapted to ThystTV's Hilt and player
architecture. These entries supersede earlier deferrals only for the named scopes.

| Upstream commit | Included adaptation |
| --- | --- |
| `17587ada8efc562fcb3a36cad6b133e79c01e00f` | Minimum 1ms replay delay in network and downloaded chat loops, preserving millisecond timing. |
| `9500c9c532ef38cadccb0be835b386f461106899` | Retain fetched followed channels on appended pages while preserving enrichment and cursor behavior. |
| `5e7f6a466fbbdab7967f6a2f8533c88ea993ba67` | Populate/refresh native Twitch autocomplete, preserve third-party/chatter entries, and filter a synchronized snapshot. |
| `f10e3abfcd4c1496a90ee52283d5c228eb7577d6` | Show destination host beside social titles while preserving URL/open behavior; host text does not establish trust. |
| `f5f03a1aa0b4e4c0da6950084f513ea69cd0c1da` | Legacy ExoPlayer previous/next fallback: honor handled events and reject key-up/cancelled or unavailable seek actions. |
| `e4299f7d1e7e2bdce90f9ea1e6e3545539f37c34` | Restart completed automatic resumes, preserving unknown durations, explicit timestamps and active-session restoration; omit adapter/layout rewrites. |
| `a6dcee1856e2e85ada5304cb114bae31173e59e1` | Compact channel/team Markdown headings with corrected exclusive-end handling and standard/code/link/container preservation. |

Quality-identity/bitrate, proxy, dependency, DI and playback-service migrations are
not included. See RELEASE_1_3_REVIEW.md for the complete release review map.

## October 4, 2026 feature intake (unreleased)

Baseline: `e8cbd77cb9f875ed2e4b777684065278bc766060` (ThystTV 1.3.1 app).
Inspected donor snapshots: crackededed/Xtra
`9f8043e8bdf45e8a2b2bf4cecfa74dc1de8ae0a3`, thiscallnet/Xtra
`fbffe567381dfca9970860e2976ab0021c2602eb`.

| Source | Manual adaptation | Risk / exclusions |
| --- | --- | --- |
| crackededed `a70b0897`, `2c582bb83621881dd36c4c7f6632587d38e633cb`, `703437feee29435f9aa341dac17ad1358d059286` | Resolution-first fallback and full quality identity/bitrate through playback, labels and downloads | Preserve Thyst quality UI; no service or dependency migration. |
| thiscallnet `42ab1d2cb0a07842748c278dfd874e0fddb43208` (#433) | Metered-Wi-Fi quality policy/settings and capability-change handling | Android 6 preserved; explicit session choices remain authoritative. |
| thiscallnet `fbffe567` notification policy | Account-scoped cached notification preferences, Helix fallback and delivery-aware dedup | Retain WorkManager, Hilt and the single live-follows request per poll; no EventSub, foreground service or full follow-list sync. |
| thiscallnet `fbffe567` custom reward flow | Custom Channel Points catalog, input, availability and confirmation with guarded mutation | No automatic spend retry; no Drops, emote unlocks or poll/prediction expansion. |
| thiscallnet `fbffe567` LiveRewind.kt | Current-recording matching and failure rollback, adapted to existing live/VoD fragment replacement | High player/lifecycle risk. Existing VoD timeline/replay chat and Go Live; no local DVR or new playback service. |

The new upstream minSdk 24 and donor minSdk 26/GeckoView remain deferred. No broad
merge, Hilt removal, Media3 1.11.1 upgrade, Cronet rewrite, multiview, clipping,
captioning or release/version change is included. Earlier quality deferrals are
superseded only for the bounded scopes above.

See [feature behavior and QA matrix](FEATURE_PORTS_2026_10.md). These changes are
not a released APK. Local debug/release assembly, 743 configured tests and debug lint passed; see
[validation results](FEATURE_PORTS_VALIDATION.md). No physical-device QA is claimed.

## October 10, 2026 crackededed recheck

All ten crackededed commits after the September audit endpoint `cadfb8b7c`, through
`a3cbb0325f3330573a6738f7d36a2c5b785f4d1c`, were checked against the stacked branches.

| Commit | Disposition |
| --- | --- |
| `01f981ab0652cdf63a280b2d441cd2174ffe6128` hide portrait qualities | Adapted in `xtra-ports/3-quality`: portrait (`-portrait`, `FR_DUALFORMAT_INCOMPATIBLE`) variants are skipped by the low-latency parser, the shared playlist parser and the unavailable-quality expansion; clips use the widest asset. |
| `edd8ae2576177625c98c483e2fc725609b6cee90` clip supportedCodecs | Adapted in `xtra-ports/3-quality`: the clip access-token query sends codecs from the existing codec preference, with the upstream persisted-query hash. The existing player codec filter still applies. |
| `a3cbb0325f3330573a6738f7d36a2c5b785f4d1c` settings drag position | Same bug in ThystTV's drag lists; ported separately from this series. |
| `c1485c7adea094828f9a6798b2b85fe591f856ee` unavailable quality parsing | N/A: the quality layer already parses each variant as one object, so attributes cannot shift between variants. |
| `a70b0897e` quality sort | Already adapted (see October 4 intake). |
| `37a51c37f` experiment cookie | N/A: ThystTV never sent the cookie. |
| `9f8043e8b` minSdk 24 | Deferred: ThystTV keeps minSdk 23. |
| `38e4c9b5d` Play Services Cronet provider | Deferred: dependency and network-provider change. |
| `e3b8244e7` HTTP proxy fixes | Deferred with proxy compatibility work. |
| `40b701ede` player fixes | Deferred: ad proxy/video-swap timing in upstream's playback service. Delaying the un-hide until ad segments end may be revisited with live ad QA. |

## October 20, 2026 crackededed recheck

All three crackededed commits after `a3cbb0325`, through
`96772030c7712581ad150c9b8b619c77a3527e50`, were checked against master `ae3b1c6c7`.

| Commit | Disposition |
| --- | --- |
| `b24b45c7b85b01d85d284eb51dbb964a8d3eea5d` fix crash on player showStatusBar | Ported: `showStatusBar()`/`hideStatusBar()` skip the window call once the player fragment is detached. |
| `96772030c7712581ad150c9b8b619c77a3527e50` update channel point query | Ported: `ChannelPointsContext` uses the new persisted-query hash and sends `includeGoalTypes`. On 2026-10-20 Twitch still answered both hashes with the same balance shape, so this is preventive. The build-version hunk is N/A. |
| `0497f3d2ca9085abd72102b609d3c6ef838bfee6` fix unlisted video qualities | N/A: ThystTV already skips only `FR_CODEC_NOT_REQUESTED`/`FR_DUALFORMAT_INCOMPATIBLE` (`isSkippedUnavailableReason`) and keeps blank variant attributes null. |

Upstream moved Media3 to 1.11.x (`e08e2b3c2`, `61b165e99`, `8814b5bb2`). Its maintainer
reported in [crackededed/Xtra#1009](https://github.com/crackededed/Xtra/issues/1009)
that the update broke its old service. Commit
[`9a919bfa06a2efd32bb4c5d1b72e0ec96d2f9dea`](https://github.com/crackededed/Xtra/commit/9a919bfa06a2efd32bb4c5d1b72e0ec96d2f9dea)
removed `Media3Fragment`/`PlaybackService` and retained the direct-bound
`ExoPlayerService`. ThystTV's **default** is the affected Media3 session path;
`DEBUG_USE_CUSTOM_PLAYBACK_SERVICE=true` selects the direct-bound alternative.
An isolated upgrade must retest the default first. Do not import the broad service
removal or change the default based only on upstream's result.

## October 21, 2026 isolated Media3 adaptation

The performance stack evaluates 1.11.1 without importing upstream's service
removal. Default-service emulator testing reproduced a black VoD surface while
the service position advanced. Media3 1.11.1's old `Callback.onConnect` default
is a placeholder; ThystTV had copied its empty player permissions. The narrow
adaptation uses controller-aware `onConnectAsync` defaults and retains existing
custom commands for trusted controllers. The same public VoD and current live
video then rendered, with quality changes and minimize/restore working.

The custom HLS parser adapts the new model signatures, retains stable identities,
preserves initialization maps across updates, and treats missing/invalid JSON
frame rates as unknown. Content steering remains outside this port. Parser and
connection regressions have focused coverage; see [testing evidence](TESTING.md)
and [required device QA](MANUAL_QA.md). No broad service/lifecycle migration or
default-backend change is included.

## October 21, 2026 Cast adaptation

Inspected [thiscallnet/Xtra#481](https://github.com/thiscallnet/Xtra/pull/481),
merged as `c45058ebe80258b90f2dfdc424c19fe95fbba4d1`, and Spider19996/Xtra_Cast at
`f3d0edc2c2cdbfebe200369f83b9e60f1b0d293b`. Adapted the official Media3 CastPlayer
approach in the default session backend, with an optional route button, configured
source retention and local-only download handling. No donor notification/service
rewrite, ad behavior or retry machinery was imported. See PLAYER.md and
MANUAL_QA.md for receiver limits and the required hardware matrix.
