# Upstream Sync Ledger

This is the living ledger for ThystTV's selective sync against upstream
[`crackededed/Xtra`](https://github.com/crackededed/Xtra). The policy that governs these
decisions lives in [`UPSTREAM_SYNC.md`](UPSTREAM_SYNC.md). The 1.2-cycle ledger
([`RELEASE_1_2_UPSTREAM_COMMITS.md`](RELEASE_1_2_UPSTREAM_COMMITS.md)) is frozen history;
new audits go here.

## Latest audit

- **Date checked:** 2026-06-12
- **Branch audited:** `master` (post-1.2.0, `69933517`)
- **Upstream remote:** `upstream` -> `https://github.com/crackededed/Xtra.git`
- **Latest upstream head checked:** `f43e3bf0d6f71afb8dcb2b37de7b38b624fe7fc2` (`load vod title when starting from clip`, 2026-06-11)
- **Merge base with upstream:** `b063872f` (`quality model`, 2026-03-19)
- **Commands used:**

```bash
git fetch upstream master
git cherry -v master upstream/master
git show --stat <commit>   # per-commit review
```

`git cherry` legend: `-` = patch already present (directly or equivalent), `+` = not present
exactly (may still be partially/manually ported — see notes).

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

## New upstream commits since last ledger check (`71d1222c`, 2026-05-08)

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
