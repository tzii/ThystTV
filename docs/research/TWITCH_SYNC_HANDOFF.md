# ThystTV: official Twitch history and watch-streak interoperability

Research date: 8 September 2026.

## Scope and verification boundary

This is a research and source-code review, not a shipped implementation. No Twitch credentials were accessed, no account activity was submitted, no APK was built, and no two-device playback test was performed.

The Windows connector could not reach the local computer. The worktree document at `C:/Dev/ThystTV/.private/worktrees/vod-seek-preview/docs/research/TWITCH_WATCH_SYNC.md` was therefore not read or modified. The code observations below refer to GitHub `tzii/ThystTV`, `master` at `e78afcdb5c94220f8244c2a34278c2b2b12fc326`, not unpushed seek-preview changes.

## Decision

Proceed with an opt-in compatibility experiment. There is substantive evidence for both official-account VOD progress and live watch credit, but they use different mechanisms and need separate acceptance tests.

- VOD resume and a recent Continue Watching list: captured official-site GraphQL requests in Xtra issue 329, independently echoed by SmartTwitchTV PR 367.
- Live credit: ThystTV already sends `minute-watched`. SmartTwitchTV PR 368 reports a real TV test and subsequently reports maintained Twitch streaks.
- Server-confirmed streak milestones: current Twitch EventSub documentation provides structured watch-streak notification data; IRC provides viewer-milestone tags.
- Full historical watch-session sync, querying every existing streak at startup, and VOD-based streak recovery in ThystTV: not demonstrated by the inspected material.

Do not replace local storage or upload the old resume table wholesale. Do not turn local days watched into a Twitch broadcast streak.

## Evidence register

| Source | What it establishes | What it does not establish |
| --- | --- | --- |
| [Xtra issue 329, captured requests](https://github.com/crackededed/Xtra/issues/329#issuecomment-2943100913) | Official-site VOD read/write/list request and response shapes; example server timestamps are from June 2025. | Present-day compatibility with ThystTV's exact token/client combination. |
| [SmartTwitchTV PR 367](https://github.com/fgl27/SmartTwitchTV/pull/367) | An August 2026 proposed VOD implementation using the same fields. | A validated implementation. It is open/unmerged; its test checklist is unchecked and there are no PR comments reporting validation. |
| [SmartTwitchTV PR 368](https://github.com/fgl27/SmartTwitchTV/pull/368) | Author reports 20 points after 10 minutes on a TV, then maintained streaks in an August 20 comment. | Independent verification, ThystTV equivalence, or universal eligibility. |
| [Twitch EventSub reference](https://dev.twitch.tv/docs/eventsub/eventsub-reference/) | `watch_streak` chat notification with count and awarded points. | A state-query API or guaranteed notification for every increment. |
| [Twitch IRC reference](https://dev.twitch.tv/docs/chat/irc) | Structured shared viewer-milestone tags. | Historical backfill of missed milestones. |
| [Twitch Helix reference](https://dev.twitch.tv/docs/api/reference/) | No documented VOD resume/history or streak-setting endpoint found. | That private client operations are impossible. |
| [Archived Twitch-GQL schema](https://github.com/daylamtayari/Twitch-GQL/blob/master/schema.graphql) | Independent historical schema corroboration, including seconds-based positions. | A current schema contract. |
| [Xtra issue 890](https://github.com/crackededed/Xtra/issues/890) | Users reported an earnings regression; the maintainer subsequently marked it fixed. | That the same regression exists in the audited ThystTV snapshot. |

## 1. Separate the data domains

### VOD resume

A position in one video, associated with one Twitch account. It can decrease after a rewind or rewatch. It is not a count of seconds genuinely watched.

### Continue Watching

The captured `FollowedStreamsContinueWatching` response contains account-owned `viewedVideos` edges and progress. Treat this as a recent unfinished-video shelf, not an unlimited history export. Do not infer that disappearing from the shelf means the local record must be deleted. The trace's name is not evidence that only followed channels are returned.

### Local viewing statistics

ThystTV has a richer local session store used for category totals, viewing hours and loyalty. Importing a remote position must not create a synthetic watch session or add that position to watched time.

### Local versus Twitch streak

The inspected `model/stats/WatchStreak.kt` is one global row tracking consecutive days. Twitch's streak is per-channel and based on broadcasts. These values require separate storage and labels, not a conversion function.

Suggested labels: "ThystTV viewing days" and "Twitch channel streak". A cached notification should say "Last confirmed: N broadcasts" with a timestamp, rather than promise it is the current complete server state.

## 2. VOD protocol leads

The Xtra issue contains actual request/response examples for three operations:

| Operation | Purpose | Important variables / result |
| --- | --- | --- |
| `queryUserViewedVideo` | Read one VOD's resume position | Variable `videoId`; result `data.video.self.viewingHistory.position`. |
| `updateUserViewedVideo` | Write resume position | Input `userID`, `videoID`, `videoType: VOD`, `position`; result contains the video ID. |
| `FollowedStreamsContinueWatching` | Read recent unfinished VODs | Variables `includePreviewBlur: false`, `limit`; result `currentUser.viewedVideos.edges`, including `history.position` and `history.updatedAt`. |

The trace reports the official website writing about every 20 seconds during unpaused playback. This is a historical observation, not a guaranteed current Twitch interval.

### Persisted-query seeds

These are public identifiers from that captured trace, not secrets. They must be treated as historical test fixtures, not guaranteed current production constants.

```text
queryUserViewedVideo
86bf57f2a04e2f4705ce456d082bcffd80a372b6641a9c9120c409b10959a7a5

updateUserViewedVideo
bb58b1bd08a4ca0c61f2b8d323381a5f4cd39d763da8698f680ef1dfaea89ca1

FollowedStreamsContinueWatching
c689d0645defdd63aaab322166a570c785cefa97b6e97c1a1e7fb66ccdfcad82
```

A current authenticated capture should confirm each operation, variables, and required headers. A persisted-query lookup failure is a compatibility failure, not permission to invent another hash.

A minimal, derived raw GraphQL read candidate is:

```graphql
query ThystResumeProbe($videoId: ID!) {
  video(id: $videoId) {
    id
    self {
      viewingHistory {
        position
        updatedAt
      }
    }
  }
}
```

This was not executed. Raw-query acceptance must be tested. The historical persisted single-video request returns position but does not demonstrate `updatedAt`; the list response does. Do not implement timestamp-based conflict resolution on the assumption that every read shape supplies a timestamp.

### Authentication gate

ThystTV's `TwitchApiHelper.getGQLHeaders(..., true)` uses a separate GQL token/header path from its Helix `Bearer` token path. The helper also supports stored integrity-related headers. A working Helix login is therefore not proof that the private resume operations will be authorized.

The first probe must run through ThystTV's actual authenticated request stack. Validate the token identity using Twitch's documented validation mechanism, bind state to the resulting account, and test each capability separately. A successful read does not prove writes are allowed.

Reference: [Twitch token validation](https://dev.twitch.tv/docs/authentication/validate-tokens/). Private GQL is not the supported third-party Helix API; [Streamlink's maintainer explanation](https://github.com/streamlink/streamlink/discussions/5696) is a useful compatibility warning, not a promise about today's authorization behavior.

Do not collect passwords or export bearer tokens into diagnostics. Do not work around an integrity rejection by fabricating credentials. Surface unsupported authentication and preserve local playback.

### Proof sequence

1. Pick an accessible VOD on the user's account. Play it in the official app to a distinctive position, pause and close that player.
2. Read the same VOD through ThystTV's own GQL context. Record operation outcome and position, without logging authentication headers.
3. Play the VOD in ThystTV, save a genuinely reached position locally, and explicitly enable one remote write.
4. Check GraphQL errors even for HTTP 200. Read the remote value back and verify the VOD/account identity.
5. Reopen the official app and compare the resume value. This last step is necessary: browser success alone is not official-mobile-app verification.
6. Repeat with a backwards seek and with a near-zero position. Test a deliberate replay from the beginning separately from absence of history.

Only after both directions pass should normal automatic synchronization be enabled.

## 3. Do not copy PR 367 unchanged

PR head inspected: `5f47a316c4ed43ea0c5cca8399ff7f3bdb38beb8` in `Marfa/SmartTwitchTV`.

The proposed transport is a useful lead. Its synchronization policy has concrete defects:

- Its supposed 30-second limiter only blocks when the new position equals the previous position. Continuously advancing positions bypass that limiter.
- It records the last-pushed position before knowing the request succeeded. The push callback does not inspect failures or GraphQL errors.
- Zero positions are discarded or treated as absent, so restarting from the beginning cannot be represented reliably.
- A maximum-position tie-break and an update path restricted to greater offsets are unsafe for intentional backwards progress.
- Some request bookkeeping is keyed only by VOD rather than by account plus VOD.

Use the request contract as a candidate, write the Android state machine independently, and test failure behavior before enabling writes.

## 4. Live reporting in ThystTV

### Existing execution path

`util/chat/HermesWebSocket.kt` connects to Twitch's internal Hermes websocket. On welcome, with account identity, GQL token and automatic collection enabled, it starts a 60-second timer. That calls `ChatViewModel.PubSubListener.onMinuteWatched()`, which calls `PlayerRepository.sendMinuteWatched(...)` when `streamId` is nonblank.

The "PubSub" name in the UI/code does not mean this connection still uses retired public PubSub. Twitch decommissioned that legacy service on April 14, 2025; the audited implementation uses Hermes. [Official migration notice](https://dev.twitch.tv/docs/pubsub).

The sender discovers the analytics endpoint through the channel page and a settings script, then submits a base64 JSON `minute-watched` event. It already recognizes both `beacon_url` and `spade_url`.

### Concrete review findings

| Observation in the inspected code | Proposed change |
| --- | --- |
| Timer belongs to chat/Hermes and the collection setting. The timer and listener hook have no actual-player-state input. | Make a playback-owned reporter; keep notification transport and bonus claiming separate. Audit all lifecycle callers before claiming a specific pause/background defect is reproduced. |
| Each call retrieves the channel page and settings again. | Cache endpoint discovery with bounded expiry and invalidation; permit at most one discovery in flight. |
| The final OkHttp request returns an unclosed response in the inspected method. | Close/consume it and capture a structured outcome. |
| Exceptions disappear in the caller, and unsuccessful HTTP results are not a clear status. | Add redacted diagnostics, bounded backoff, and explicit failure states. |
| Settings extraction uses a restrictive regex and the endpoint capture is not decoded as a JSON string. | Parse/normalize safely and validate HTTPS hosts before following discovered URLs. Do not send account credentials to a discovered analytics endpoint. |
| Base64 is concatenated into a form field without a form encoder. | Use proper form encoding consistently. This is a hardening finding, not a demonstrated cause of the user's missing streak. |
| The payload is leaner than the recent TV PR's tested payload. | Compare with a current official-client capture. Do not assume absent `live` or `channel` fields alone explain a failure. |

### Proposed reporter behavior

Use account ID, channel ID, live broadcast ID and a playback-session generation as the reporter identity. Emit only for genuine active playback. A chat connection, an open channel page, a seek, or elapsed wall time while paused is not playback.

Use a monotonic active-playback clock. Stop on pause, buffering without progress, account changes, ended streams and errors. Handle PiP and audio-only intentionally according to observed supported behavior; do not accidentally stop a genuinely playing session merely because its chat view is hidden.

Do not replay accumulated offline heartbeats. A delayed network retry must not claim that the user is watching a different broadcast or account. Avoid duplicate timers after reconnect, fragment recreation, or a service handoff. Cadence and payload remain subject to current-client verification.

### Independent evidence

SmartTwitchTV PR 368 was opened August 19, 2026. The author reports a built-TV-APK test yielding 20 points after 10 minutes. On August 20 the author added that it also keeps watch streaks alive. Its code reports during active playback and discovers the current telemetry endpoint.

This raises confidence in the mechanism. It does not prove ThystTV currently credits every viewing mode. A points change is supporting evidence; an own-account Twitch-confirmed streak milestone is stronger evidence. A successful analytics HTTP response alone proves neither.

## 5. Reading server-confirmed streak information

Current Twitch EventSub documents `channel.chat.notification` with `notice_type: watch_streak`, a `streak_count`, and `channel_points_awarded`. The user-token route requires `user:read:chat`; app-token authorization has additional requirements. [Event reference](https://dev.twitch.tv/docs/eventsub/eventsub-reference/) and [subscription authorization](https://dev.twitch.tv/docs/eventsub/eventsub-subscription-types/#channelchatnotification).

IRC provides `USERNOTICE`, `msg-id=viewermilestone`, `msg-param-category=watch-streak`, and `msg-param-value`. These are structured fields, preferable to parsing localized chat text. [IRC documentation](https://dev.twitch.tv/docs/chat/irc).

ThystTV's inspected `EventSubUtils.parseUserNotice()` retains generic chat data but does not extract a typed streak count. Its no-custom-message branch also omits the notice type/message ID from the `ChatMessage` it constructs. Parse the typed event before that lossy projection.

Proposed behavior:

- Only update the account's own cached streak when the event's viewer ID matches the signed-in account. Another viewer's milestone must not overwrite it.
- Keep broadcaster/source identity correct for shared chat, and deduplicate notices across transports and reconnects.
- Store the reported count, source, event timestamp and observation timestamp. Do not increment a local counter in response to each heartbeat.
- Label a missed/stale observation as unknown or last confirmed, not zero.

There is still no verified on-demand query in this research that returns all of the account's existing current streaks. Notifications solve observation of received events, not complete backfill. A current official-client capture of the streak panel or recovery UI is the next targeted investigation for startup hydration. Do not invent a `GetWatchStreak` operation or infer an exact long streak from a capped points bonus.

## 6. VOD-based streak recovery is a separate experiment

Twitch documents recovery for eligible streaks of at least three broadcasts within 24 hours of the first missed stream ending. Eligible VOD playback requires at least five minutes; eligible clips/stories require five seconds. Recovery preserves the count rather than increasing it. [Twitch recovery rules](https://help.twitch.tv/s/article/recover-watch-streaks).

Writing a resume position is not evidence that recovery conditions have been met. Inspect actual eligible playback and its server confirmation before implementing this. Never bulk-submit old local history as recovery credit. Until validated, an explicit open-in-official-Twitch action is a reasonable fallback; a hidden duplicate player is not.

## 7. Preserve and migrate local VOD progress

### What the current schema really contains

`model/VideoPosition.kt` defines `video_positions` with only `id: Long` and `position: Long`, keyed by ID. `VideoPositionsDao.kt` loads and replaces these records without account ownership or update timestamps. `PlayerRepository` forwards saves and loads to that DAO.

Therefore the migration cannot determine which account originally watched a legacy row, nor whether it predates the current server resume value. The model itself does not declare units; confirm units at every save/read call site before writing a conversion migration. An Android millisecond-to-Twitch-second mismatch would be a serious corruption risk.

### Safe default

Keep legacy rows intact as an unassigned local archive. On enabling sync, first download remote state without pushing legacy entries. Scope all newly recorded progress to the signed-in Twitch account.

When the user opens a VOD with conflicting legacy and remote positions, offer both timestamps and make the origin clear. Choosing and actually continuing playback establishes a new account-owned event. That event can be synchronized after explicit opt-in; opening the list must not mark every item as watched now.

An optional bulk import should be a separate reviewed action with an account selection, preview, conflict summary and explicit confirmation. It is not the default switch-on behavior.

### Suggested additive model

The names below are a design proposal, not existing code:

```text
AccountVodProgress
  accountId + videoId                         primary identity
  localPositionMs                            nullable
  localPlayedAt                              nullable, never invented for legacy rows
  lastObservedRemotePositionSeconds           nullable
  lastObservedRemoteUpdatedAt                 nullable
  remoteStateAtLastAcknowledgedSync           nullable baseline
  localSequence                              monotonically increasing locally
  pendingIntent                              continue / rewind / restart / completion
  origin                                     legacy / local playback / remote
  syncStatus                                 local / pending / confirmed / conflict / unavailable

VodProgressOutbox
  accountId + videoId                         coalescing key
  playbackSessionGeneration
  localSequence
  desiredPositionSeconds
  baseRemoteObservation
  attemptState
```

Prefer an additive, transactional database migration. Preserve the legacy table until migration and rollback tests pass. Exporting a live SQLite database must account for its journal/WAL, not simply copy one file while writers are active.

### Conflict rules

Do not use `max(local, remote)`: a newer rewatch at 10 minutes must be able to replace an older stop at 80 minutes. Do not use raw device-time last-write-wins when an old row has no timestamp or clocks disagree.

Compare each side to a previously observed remote baseline. A remote-only change can be imported; a local-only change can be queued; changes on both sides deserve conflict treatment unless ordering is actually known. Keep both candidates recoverable. Zero is a valid position, while a missing history object is a different state.

The observed API does not demonstrate a compare-and-swap revision precondition. A read-before-write reduces stale overwrites but cannot prevent every race with an active official player. Avoid overlapping players in the initial proof and state this limitation in the implementation.

An acknowledgement must only clear the exact local sequence it acknowledges. A slow response for an old session must not clear a newer rewind, overwrite another account, or seek a video after the user has already started watching.

### Special cases

A missing remote VOD or absent Continue Watching entry must not erase a local record. Private/deleted/unavailable videos should keep their local history and show a reason for unavailable sync.

For downloaded partial VODs, confirm the original Twitch video ID and timeline offset. Local file position may not equal original-VOD position. Do not upload download-internal IDs.

Keep historical viewing sessions and the global local-day streak intact. Remote resume imports must not change screen-time totals, category statistics, watched-day streaks, or fabricate sessions watched in another app.

Clearing local history should remain local unless a separate, supported remote deletion operation has been verified and deliberately requested.

## 8. Implementation sequence and release gates

### A. Compatibility probe

Add opt-in debug actions for one-video read, recent-list read, a user-approved real-position write, and readback. Reuse the app's network/auth code. Track independent read/list/write capabilities. Preserve local fallback for unsupported operations.

Gate: an official-app -> ThystTV -> official-app round trip, using ThystTV's actual credentials, plus successful backwards-position testing.

### B. Reliable actual-watch reporting

Extract a `LiveWatchReporter` from the Hermes timer. Fix resource handling and discovery caching. Add structured own-account milestone parsing, leaving automatic bonus claiming separate.

Gate: no false events while paused/buffering/closed; one session per account/channel/broadcast; evidence of server-awarded credit across eligible broadcasts.

### C. Account-scoped VOD synchronization

Add a local-first progress repository, a bounded coalescing outbox and account/session isolation. Pull on foreground/open; checkpoint real playback periodically and flush on pause/exit through a durable mechanism rather than a best-effort view coroutine.

Gate: network failure never loses local progress; logout never writes another account's data; older acknowledgements never clear newer changes.

### D. Legacy migration and user-visible history

Enable conservative merge UI and a combined recent-VOD view. Keep local-only history, downloads and unavailable VODs. Make stale server-streak observations explicit.

Gate: seeded old databases retain every legacy row and stat, including under interruption and rollback.

### E. Additional protocol research

Investigate on-demand streak state, eligible VOD recovery and live-to-VOD resume mapping independently. A historical official-client log in [FFZ issue 1028](https://github.com/FrankerFaceZ/FrankerFaceZ/issues/1028) contains the resume mutation with `videoType: LIVE`, but it is not a current implementation contract. Verify broadcast IDs, offsets and mapping rather than equating a live stream ID with a VOD ID.

## 9. Acceptance matrix

| Scenario | Required observation |
| --- | --- |
| Official app progresses a VOD | ThystTV reads the same account/video's new progress. |
| ThystTV progresses a VOD | Remote readback and the official app reflect it. |
| Newer backwards seek/restart | Old larger position does not win automatically. |
| Pause/buffering | No fabricated active-watch time; local progress is preserved. |
| 2x VOD speed | Resume uses media time; viewed-time statistics use actual active time. |
| PiP, hidden chat, genuine audio playback | Defined player-based behavior independent of chat view lifetime. |
| Reconnect/rotation/service handoff | No duplicate live timers or stale callbacks. |
| HTTP 200 + GraphQL error | Failure shown; pending write not marked synced. |
| Authentication expires or integrity fails | Bounded failure, no retry storm, local playback remains usable. |
| Offline -> online with remote changes | Stale local outbox does not blindly overwrite newer remote activity. |
| Account A -> B | No cross-account response application, upload, or old-history auto-assignment. |
| Legacy rows without metadata | Rows remain intact; no fabricated ownership/timestamps. |
| Removed VOD / missing shelf item | Local history remains; disappearance is not treated as deletion. |
| Another viewer shares a streak | Their count never updates the signed-in user's streak. |
| Shared chat / repeated notification | Correct original channel and deduplication. |
| Milestone not observed | Last-confirmed/unknown display, not an invented count or zero. |
| Optional recovery experiment | Genuine eligible playback plus Twitch confirmation, independently of resume success. |

## 10. Diagnostics and research coverage

Record operation name, outcome category, retry count, elapsed time, active playback state, anonymous session generation, expected/returned position, and whether a server confirmation was observed. Keep identity-sensitive logs local or redact them before export. Never log tokens, cookies, complete integrity headers, or raw authenticated request dumps.

The research covered ThystTV source, Xtra issues, SmartTwitchTV code/PRs, Twire and Frosty issue searches, Twitch developer/help documentation, historical GraphQL schema/logs, and related watch-credit implementations. Search results that merely advertise local history, cloud backup, or point farming were not treated as proof of official-app synchronization. No verified ready-made drop-in implementation covering all requested behavior was found.

Google Drive/WebDAV could synchronize ThystTV-only records, but that would not update the official Twitch app. Twitch account-data exports are retrospective snapshots, not a continuous read/write bridge. Neither substitutes for the actual account protocol.

## Audited ThystTV files

All links are pinned to the inspected master commit.

- [VideoPosition.kt](https://github.com/tzii/ThystTV/blob/e78afcdb5c94220f8244c2a34278c2b2b12fc326/app/src/main/java/com/github/andreyasadchy/xtra/model/VideoPosition.kt)
- [VideoPositionsDao.kt](https://github.com/tzii/ThystTV/blob/e78afcdb5c94220f8244c2a34278c2b2b12fc326/app/src/main/java/com/github/andreyasadchy/xtra/db/VideoPositionsDao.kt)
- [WatchStreak.kt](https://github.com/tzii/ThystTV/blob/e78afcdb5c94220f8244c2a34278c2b2b12fc326/app/src/main/java/com/github/andreyasadchy/xtra/model/stats/WatchStreak.kt)
- [WatchSessionDao.kt](https://github.com/tzii/ThystTV/blob/e78afcdb5c94220f8244c2a34278c2b2b12fc326/app/src/main/java/com/github/andreyasadchy/xtra/db/WatchSessionDao.kt)
- [HermesWebSocket.kt](https://github.com/tzii/ThystTV/blob/e78afcdb5c94220f8244c2a34278c2b2b12fc326/app/src/main/java/com/github/andreyasadchy/xtra/util/chat/HermesWebSocket.kt)
- [ChatViewModel.kt, minute-watched hook](https://github.com/tzii/ThystTV/blob/e78afcdb5c94220f8244c2a34278c2b2b12fc326/app/src/main/java/com/github/andreyasadchy/xtra/ui/chat/ChatViewModel.kt#L1200)
- [PlayerRepository.kt, minute-watched transport](https://github.com/tzii/ThystTV/blob/e78afcdb5c94220f8244c2a34278c2b2b12fc326/app/src/main/java/com/github/andreyasadchy/xtra/repository/PlayerRepository.kt#L417)
- [TwitchApiHelper.kt](https://github.com/tzii/ThystTV/blob/e78afcdb5c94220f8244c2a34278c2b2b12fc326/app/src/main/java/com/github/andreyasadchy/xtra/util/TwitchApiHelper.kt)
- [EventSubUtils.kt](https://github.com/tzii/ThystTV/blob/e78afcdb5c94220f8244c2a34278c2b2b12fc326/app/src/main/java/com/github/andreyasadchy/xtra/util/chat/EventSubUtils.kt)

The next engineering milestone is a verified account round trip and truthful playback reporting, not a bulk history migration or a promise that every existing streak can already be fetched.
