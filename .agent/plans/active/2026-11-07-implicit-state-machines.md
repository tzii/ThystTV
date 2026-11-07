# Implicit state machines and deadline-based chat keepalives

## Goal

Map the app's implicit state machines, name the abstractions that would remove
whole classes of bugs, and ship the first, lowest-risk ones: chat socket
keepalives and chat countdowns expressed as deadlines instead of
`java.util.Timer` threads, and each raid/poll/prediction card held as one value
that the view model owns and the chat view renders.

User-visible outcome: chat keeps the same reconnect behavior while no longer
starting a thread for each Hermes/EventSub notification; poll and prediction
countdowns show the true time left after the device sleeps; the channel-points
"minute watched" report no longer bursts after deep sleep; cards survive a
rebuilt chat screen; the chat-mode banner no longer hides early.

## Non-goals

- No player lifecycle, Cast, sleep-timer, PiP or layout change in this PR. The
  player items in the catalogue are recommendations that need their own plans and
  approval. Seven open PRs (#87-#93) touch `PlayerFragment`, `Media3Fragment`,
  `PlaybackService` and `MainActivity`; this PR touches none of them.
- No change to chat reconnect policy, transports, IRC parsing or the
  `permessage-deflate` framing.
- No `ChatSession` refactor yet (see abstraction D); existing tests reflect on the
  current field names.

## Current context

The app encodes most multi-step behavior as clusters of booleans, nullable
fields, jobs and generation counters that together form a state machine nobody
declared. Bugs then come from combinations that should not exist, and fixes add
another flag. The 1.5.1 RC2-RC4 and #71 fixes ("reset source acceptance for each
new start command", "avoid a second start when restoring an accepted player",
"old cleanup must not close the new connection") are fixes of this kind.

### Catalogue

Each row lists the fields that carry the state, the states actually in use, and
what has gone wrong or can go wrong there.

Player (high risk; recommendations only):

| # | Machine | Encoded as | Real states | Symptoms |
|---|---|---|---|---|
| 1 | Source-start handshake (`Media3Fragment` ↔ `PlaybackService`) | `sourceRequest`, `lastSourceCommand`, `rejectedStart`, `acceptedStart`, `freshStartPending`, `pendingSourceStart`, `audioOnlyPending`, plus VM `started`/`loaded`/`sourceLoading` and the service's `sourceRequest`; four of them saved in the Bundle | Idle → Resolving → AwaitingController → Sent(id) → Accepted(id) / Rejected(id) → OfferingStopCast → Restarting | `accepted && rejected` is representable; recreation needs `refreshSourceOwnership` and `lostRetainedSource` heuristics; #66-#71 fixes live here |
| 2 | Source resolution (`PlayerViewModel`) | five `MutableStateFlow<X?>` used as one-shot events and set to null after use, five jobs; `sourceLoading` ORs ten conditions back together | Idle / Resolving(kind) / Resolved(source) / Failed | "Is a start in flight?" is reconstructed, not stored |
| 3 | Quality selection | `quality`, `previousQuality`, `userHasChangedQuality`, `restoreQuality`, `updateQualities`, `applyingNetworkQuality`, audio-only/chat-only pseudo-qualities | Auto / Manual(q) / AudioOnly(prev) / ChatOnly(prev) | Labels regressed after chat-only → Auto (AGENTS.md lesson) |
| 4 | Proxy/ads | `useCustomProxy`, `playingAds`, `usingProxy`, `stopProxy`, service `proxyMediaPlaylist` | Direct / Proxied / ProxyFailedOver | One-way fallback guarded by flags in two processes' worth of objects |
| 5 | Live rewind | `liveRewindPending/Failed/Dropped`, two jobs, a dialog (36 uses in `PlayerFragment`) | Live / Discovering / Starting / Rewound / Failed / DroppedWhileHidden | Ordering rules spread across the fragment |
| 6 | Chat presentation | `isChatOpen` × `isFloatingChatEnabled` (3 legal of 4), `foldDockedFloatingChat`, `doubleTapChatSnapshot`, `pipChatRequested`, `KEY_CHAT_OPENED` | Hidden / Sidebar / Floating (+ fold-docked) | Pinch-supersedes-double-tap needs a snapshot of four values; icon updates are called by hand after each change |
| 7 | Controller visibility | `isVisible`, `controllerIsAnimating`, `controllerAnimation`, posted hide runnable | Hidden / FadingIn / Shown(autoHide) / FadingOut | `controllerIsAnimating` acts as a lock: a non-forced show during the 250 ms fade-out, or hide during fade-in, is dropped |
| 8 | Pinch/zoom | ~15 `pinch*`/`magnif*` fields, commit generation, settle animator | Idle / Pinching / Anchoring / Settling / Magnified(pan) | Partly extracted (`PinchZoomController`, `PlayerGestureArbiter`) |
| 9 | Mini-player drag/resize | pointer id, last X/Y, velocity tracker, `isTap`, edge/status-bar swipe flags, animator, dX/dY, resize fields | Idle / Tracking / Dragging / Flinging / Resizing | |
| 10 | Popup host | active type/trigger, four binders, two layout listeners, `popupGeneration` | None / Open(type) | Already "one host"; generation counter is a hand-made scope id |
| 11 | Window presentation | `isMaximized`, `isPortrait`, PiP, fold layout, IME | product of the above | Already rendered idempotently from state (fold layout) — the pattern to copy |
| 12 | Live recovery (`LivePlaybackRecovery`) | `attempts`, three runnables, `notified`, `offlineObserved`, `networkResumeUsed`, `needsLiveEdge`, `proxied` | Idle / Healthy(window) / Stalled / Retrying(n, waitingForNetwork) / Exhausted(resumeAvailable) | Well tested; still a hand-rolled machine |
| 13 | Background retry (`BackgroundPlaybackRetry`) | same shape as 12 | same | Second copy of "bounded retry budget with a health reset" |
| 14 | Cast handoff | `request` counter, `pending`/`rejected` snapshots, `cancelReceiverLoad` | Local / LookingUp(id) / Receiver / Rejected(snapshot) | |
| 15 | Sleep timer | `MainViewModel` `Timer` in the foreground, `PlaybackService` `Timer` in the background, handed over through `SET_SLEEP_TIMER` | Off / Armed(deadline) | Two owners for one deadline; `duration == -1` also means "foreground" and sets `background` |
| 16 | Backends | Media3 + `PlaybackService`, legacy ExoPlayer + `ExoPlayerService`, MediaPlayer + `MediaPlayerService` | — | ~3.3k lines of parallel lifecycle code; `startPlayer` guards "media still in use" only because of this |
| 17 | Activity player slot | `playerFragment`, fragment-manager lookup, `isPlayerOpened`, PiP auto-enter params | NoPlayer / Player(fragment) | Three sources of truth; `canReplacePlayer` checks two |

Chat and networking:

| # | Machine | Encoded as | Real states | Symptoms |
|---|---|---|---|---|
| 18 | Live chat session (`ChatViewModel`) | 11 nullable transports/jobs plus `started`; "connected" inferred from three nulls | Stopped / Connecting / Live / Lost | PLAYER.md: cleanup must capture old references before async dispatch so it cannot close the new session |
| 19 | Socket keepalive (×5) | IRC read/write ping→pong `Timer`s, EventSub/Hermes per-message `Timer`, unused base `sendPings` path | Idle / Probing / Expired | **A new thread per Hermes/EventSub notification**; timers could fire after their connection closed — fixed here |
| 20 | Socket connect loop (`WebSocket.start`) | `connectionAttempt`, `delayReconnect`, 20-attempt cap | Connecting / Open / Backoff(1s or 60s) / GaveUp | Not observable; UI infers from `chatReadJob.isActive` |
| 21 | Raid/poll/prediction cards | `usedXId`, `xClosed`, `xTimer`, `xTimeoutJob`, `xSecondsLeft`, `hideX`; fragment writes VM state at 18 sites; cards passed as one-shot events the view clears | Hidden / Open(deadline) / Result(hides at) / Dismissed | Countdown `Timer` thread per card; stale after sleep; 20 s hide captured the old view; cards lost when the view is rebuilt — fixed here with `ChatCardSlot` |
| 22 | Chat replay | `ChatReplayManager` and `ChatReplayManagerLocal` repeat the same fields | Idle / Loading / Playing(cursor) / Paused | Two copies of one machine |
| 23 | Device-code sign-in, updater | `generation`/`active`/`disposed`/`checking` + `Status`; `job`/`statusTimeout`/`install` | | Sign-in already uses an explicit status and generation; fine as is |

### Abstractions that remove problems, by leverage

A. **The service owns one session value; the UI projects it.** `PlaybackService`
publishes a sealed `PlaybackSession` (Empty, Resolving(id), Playing(id, source,
route), Rejected(id, reason), Exhausted) through session extras; screens render
it and send intents. This removes the request-ID acceptance protocol, Bundle
persistence of handshake flags, ownership recovery after recreation,
`PlaybackSessionOwner`'s weak global, the backend-in-use guard, the activity's
three "player open" truths and the sleep-timer handover (rows 1, 2, 15, 17).
Most 1.5.1/1.5.2 lifecycle fixes sit in this seam. Highest value, highest risk.

B. **A typed mode plus one render function for presentation.**
`ChatMode { HIDDEN, SIDEBAR, FLOATING }` and a controller *target* animated with
interruptible animators. The illegal fourth chat state disappears, the
double-tap snapshot becomes one value, the button icon is derived, and taps are
never dropped during fades (rows 6, 7). Fold layout already follows this pattern.

C. **Deadlines, not timers.** Store when something should happen and derive the
rest; one coroutine wakes at the next deadline. This removes cancel/re-arm races,
thread-per-timer, stale countdowns after sleep, callbacks outliving their owner,
and fixed-rate catch-up bursts (rows 15, 19, 21, and the retry windows in
12-13). **Implemented for chat in this PR.**

D. **Session objects with structured lifetimes.** A `ChatSession` that owns its
transports and closes by cancelling one scope; the same idea per source in the
service. Removes "capture before async cleanup", null-checks used as state, and
the hand-made generation counters (`CastPlaybackHandoff.request`,
`PlayerSourceCommands.generation`, `popupGeneration`, `pinchCommitGeneration`),
each of which is a scope identity (rows 10, 14, 18).

E. **One retry-budget policy.** `RetryBudget(delays, healthyWindow,
eligible)` for live recovery, background retry and socket reconnect, testable as
a pure function of events (rows 12, 13, 20).

F. **One player backend.** Retiring or freezing the legacy ExoPlayer and
MediaPlayer stacks deletes ~3.3k lines and every "which backend owns the media"
check (row 16). A product decision.

G. **Resolution as a value.** `StateFlow<SourceResolution>` instead of five
nullable event flows and the `sourceLoading` OR-chain (row 2). A prerequisite
for A that is safe to do on its own once #88/#93 merge.

Suggested order: C (this PR) → D for chat (no open PR conflicts) → B after the
open player PRs merge → G → A with its own ExecPlan and approval. F needs a
product call.

## Files likely involved

- `app/src/main/java/com/github/andreyasadchy/xtra/util/KeepaliveWatchdog.kt` (new)
- `app/src/main/java/com/github/andreyasadchy/xtra/util/chat/RecentIds.kt` (new)
- `app/src/main/java/com/github/andreyasadchy/xtra/util/WebSocket.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/util/chat/ChatReadWebSocket.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/util/chat/ChatWriteWebSocket.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/util/chat/EventSubWebSocket.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/util/chat/HermesWebSocket.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/chat/ChatViewModel.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/chat/ChatCard.kt` (new)
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/chat/ChatFragment.kt`
- Tests: `KeepaliveWatchdogTest`, `RecentIdsTest`, `WebSocketMaskTest`, `ChatCardSlotTest`

## Risks

- Chat reconnect regression if a keepalive never expires or expires early.
  Mitigated by keeping the same intervals and triggers, and by virtual-clock tests.
- The keepalive uses uptime, as the main dispatcher does: deep sleep no longer
  counts as idle time, so a waking device reads buffered traffic before the
  deadline is judged. The old `Timer` mixed wall-clock checks with monotonic waits.
- The minute-watched report no longer catches up after deep sleep (the old
  fixed-rate `Timer` fired the missed minutes in a burst on wake).
- Upstream conflict risk in the five socket files; upstream has not changed their
  timers since February 2026. `ChatFragment`'s card block is rewritten, so an
  upstream change there needs a manual port.
- A lock or result now hides 20 s after it arrives, not 20 s after the chat view
  first draws it; returning from the background after that, the result is gone.
- Card rules moved from the view to `ChatCardSlot`; a mistake there shows or
  hides a card wrongly. The reopen and dismissal rules are copied one for one and
  covered by `ChatCardSlotTest`.

Risk level: low-medium (chat transport, no player code)

## Human approval

Required before implementation: no

Reason: narrow, reversible, no player lifecycle, release, signing or private data.
Abstractions A, B, F and G need approval before implementation.

## Implementation steps

1. Add `KeepaliveWatchdog`: a deadline that `feed()` moves later, one coroutine per
   connection, optional probe (IRC PING) with its own timeout, expiry launched
   separately so a reconnect cannot cut a disconnect short. State changes only under
   its lock and only for the current watch (a generation number), so a replaced watch
   cannot write its successor's deadline.
2. IRC read/write: start on connect, feed on PONG, stop on RECONNECT/disconnect.
3. EventSub/Hermes: a welcome restarts the watch with the server's keepalive timeout;
   a keepalive or notification feeds it, or starts one if none is running. Hermes
   minute-watched becomes a job.
4. Replace the O(n) message-id list with `RecentIds`.
5. `WebSocket`: remove the never-enabled `sendPings` timers, mask frames in one
   pass, end `Inflater`/`Deflater` instances explicitly.
6. `ChatViewModel`: poll/prediction countdowns become an elapsed-realtime end
   stored with the card (superseded the first version, a view-model ticker).
7. `ChatCardSlot`: one value per raid/poll/prediction card. The view model applies
   every update, close, 20 s result hide and disconnect; `ChatFragment` renders the
   card, counts down from its end while started, and only calls `close()`.
8. `ChatFragment`: the chat-mode banner uses one hide runnable, so a newer banner
   restarts the five seconds instead of being hidden by an older one.

## Verification

Automated checks:

- [x] Targeted: `KeepaliveWatchdogTest` (9), `RecentIdsTest` (1), `WebSocketMaskTest` (1),
  `WebSocketDisconnectTest` (1),
  `ChatCardSlotTest` (6), `ChatNetworkRestorationTest` (4), `ChatViewModelTest` (15)
- [x] `./gradlew assembleDebug` (JDK 21)
- [x] `./gradlew test`: 1275 tests, 0 failures, 0 skipped
- [x] `./gradlew lintDebug`: 0 errors, 332 warnings as before; none new in a changed file

Independent review:

- [x] OpenCode `zai-coding-plan/glm-5.3`, variant `max` (verified from
  `opencode session export`, `info.model`), crew run
  `20261107-154333-review-glm-1b8a0d`, session `ses_e4988f48dffeHj7lQfrSJPwyut`,
  on a detached snapshot of base `7c6ded4ab` plus this change. Verdict: nothing
  blocks; five low findings, none changed the code:
  1. Feed racing the probe transition in `KeepaliveWatchdog`: rejected as
     unreachable. Only a PONG feeds the IRC watchdog, and a PONG only answers our
     PING, which is never outstanding when the idle deadline passes; it would
     self-heal with the next PING.
  2. EventSub/Hermes `keepAlive()` re-arming during an expiry's 5 s close:
     plausible, self-healing (the reconnect's welcome feeds it), same as the old
     per-message `Timer`. No change.
  3. A same-id OPEN after RESULT keeps the 20 s hide: confirmed parity with base
     (`startPollTimeout` was cancelled only by a new id). Kept.
  4. Countdown rounds up instead of truncating: intentional.
  5. Minute-watched ticker outlives a Hermes that gave up reconnecting: confirmed
     parity with base (the `Timer` was cancelled only in `disconnect`). Kept.
- [x] Codex `gpt-6.1-sol`, reasoning `high` (echoed by the T3 delegated task), on a
  detached snapshot of head `7597bb646` with the GLM record removed from the plan.
  Verdict: blocks, two medium findings, both fixed:
  1. A replaced watch could overwrite its successor's deadline: confirmed. The loop
     runs in the connection's scope (main thread for chat) and wrote `deadline`/
     `probing` without the lock while `start()` ran on the socket's IO thread, so a
     reconnect landing as the old watch woke could leave the new connection with a
     10 s probe deadline and no probe. Fixed with a per-watch generation checked under
     the lock before every state change; `feed()` also takes the lock, which closes
     GLM finding 1 as well. Regression test `a restart while the old watch is waking
     keeps the new deadline` fails on `7597bb646` and passes now.
  2. `feed(timeout)` with a shorter announced timeout left the loop sleeping until the
     old deadline: plausible (needs a reconnect's welcome to announce less than the
     previous session). Fixed by restarting the watch on every welcome, as the base
     code did, and dropping the timeout parameter from `feed()`, so feeding only moves
     the deadline later. Test `a new session with a shorter timeout does not wait out
     the old one`.
- [x] Codex `gpt-6.1-sol`, reasoning `high`, re-check of `fb872cdab`: both round 1
  findings resolved. Verdict: blocks, one new medium finding, fixed:
  1. An expiry that had already chosen to fire could disconnect the connection made
     by a reconnect, since `WebSocket` reuses one object and `disconnect()` closes
     whatever socket it holds when it runs: confirmed, and present in base (the
     `pongTimer` task launched `webSocket?.disconnect()` the same way). The window is
     wider than reported: the socket field changes at `createSocket`, before the
     handshake and before `onConnect` restarts the watch. Effect: one extra reconnect.
     Fixed two ways: an expiry that has not begun is dropped if a newer watch started
     (`isCurrent`), and each expiry calls `WebSocket.disconnect(connection)` with the
     connection it watched. Regression test `a restart after the old watch decided to
     expire drops the old expiry` fails without the check.
- [x] Codex `gpt-6.1-sol`, reasoning `high`, round 3 on `1c972bfd9`: round 2 finding
  partly resolved. Verdict: blocks, one medium finding, fixed:
  1. `disconnect(connection)` checked `socket === connection` and then called the
     generic `disconnect()`, which read `socket`/`outputStream` again, so a reconnect
     between the check and the reads could still get the close: confirmed as a
     check-then-use race (it needs the IO thread to stall past the 1 s reconnect delay
     and a handshake). Fixed: every close goes through `closeGracefully(target)`,
     which writes the close frame to that socket's own output stream and closes that
     socket; `disconnect(connection)` never looks at the current socket, and
     `disconnect()` targets the current one once. `WebSocketDisconnectTest` installs a
     replacement before the expiry runs; it fails on `1c972bfd9` and passes now.

Human QA required:

- [ ] Live chat signed out: leave a quiet channel open 15+ minutes (IRC PING at
  4.5 min idle); messages keep arriving with no reconnect notices.
- [ ] Signed in: send after 15 minutes idle; the message posts.
- [ ] Airplane mode 30 s and back: chat reconnects once; manual disconnect stays
  disconnected.
- [ ] Channel points: watch signed in for 15+ minutes; points accrue and the claim
  appears.
- [ ] Poll and prediction cards count down; turn the screen off for 30 s on a
  non-playing channel and back on: the countdown shows the real time left.
- [ ] Debug > EventSub chat on a busy channel: messages flow; no stall after a
  quiet minute.
- [ ] Floating chat, PiP/background with chat: unchanged.
- [ ] Poll closed while voting stays closed, then its result shows and hides after
  20 s; a closed prediction reappears when locked but not for the final outcome.
- [ ] Raid card: tap opens the target, close hides it, auto-switch follows the raid,
  a cancelled raid hides it.
- [ ] Dark-mode switch with a card showing keeps the card and the right countdown.
- [ ] Two chat-mode changes within five seconds keep the banner up five seconds
  after the second.

Human QA completed:

- [ ] None yet.

## Progress log

- 2026-11-07: Surveyed player, chat, service and activity state; wrote the catalogue.
- 2026-11-07: Implemented steps 1-6 on `agent/state-machine-audit` from
  `origin/master` 7c6ded4ab. Targeted tests pass.
- 2026-11-07: Countdown made deadline-derived after self-review (a fixed delay
  showed stale values after sleep). `assembleDebug test lintDebug` pass on the
  final sources. Not committed; human QA pending.
- 2026-11-07: Added `ChatCardSlot` (steps 7-8) on request. Replaced
  `ChatCountdownTest` with `ChatCardSlotTest`. `assembleDebug test lintDebug`
  pass. Not committed; human QA pending.
- 2026-11-07: For one-APK testing, applied this diff onto the combined test build
  `4f7fc2bbf` (#87-#93 merged). Only `CHANGELOG.md` conflicted, resolved by keeping both
  sides. None of those PRs touch chat. `StreamDownloadWorker` connects chat once per
  download inside `try` and disconnects in `finally`, so the watchdog stops on every
  exit, the same lifetime as the old ping timer. `assembleDebug test lintDebug` pass
  (1427 tests, lint 0 errors). This branch still targets master on its own.
- 2026-11-07: Committed `7597bb646`, opened #94. GPT-6.1-sol high review found two
  keepalive defects (see Verification); fixed with a per-watch generation under the
  lock and a restart on every welcome. The race test fails on `7597bb646`.
  `assembleDebug test lintDebug` pass (1273 tests, lint 0 errors / 332 warnings).
- 2026-11-07: Pushed `fb872cdab`. GPT re-check confirmed both fixes and found a stale
  expiry could close a reconnected socket (base had the same race). Expiries now
  check they are current and close only the connection they watched.
  `assembleDebug test lintDebug` pass (1274 tests, lint 0 errors / 332 warnings).
- 2026-11-07: Pushed `1c972bfd9`. GPT round 3 found the connection check could still
  race the close; every close now writes to and closes only its target socket.
  `assembleDebug test lintDebug` pass (1275 tests, lint 0 errors / 332 warnings).

## Decisions

- Decision: start with chat timers rather than the player.
  Reason: largest measurable waste (a thread per notification) in code no open PR
  touches; player items need approval and would conflict with #87-#93.
  Alternatives considered: `ChatMode` enum in `PlayerFragment` (conflicts with
  #88/#89/#90/#93); `ChatSession` (rewrites reflection-based tests).
- Decision: delete `WebSocket(sendPings = true)` support instead of porting it.
  Reason: no caller enables it; porting would add untested code.
- Decision: keepalive on uptime, countdowns on elapsed realtime.
  Reason: idleness should exclude sleep; a poll deadline is real time.
- Decision: a welcome restarts the keepalive watch; `feed()` cannot change the idle
  period.
  Reason: with a fixed idle period a feed only moves the deadline later, so the
  sleeping loop never oversleeps. A welcome starts a new session anyway.
  Alternative considered: waking the loop when a feed shortens the deadline (more
  machinery for a case only a welcome produces).
- Decision: the view counts down from the card's end time; the view model keeps
  no per-second state.
  Reason: no ticking while chat is stopped, the right value after sleep or a
  rebuilt view, and nothing for the view to write back.
  Alternative considered: a view-model ticker publishing seconds left (the first
  version in this branch).
- Decision: keep the raid auto-switch in the view and close the card after it.
  Reason: it depends on the parent fragment; this matches the old one-shot event.

## Final PR summary draft

Summary: Chat socket keepalives and poll/prediction countdowns use deadlines
instead of `java.util.Timer` threads. Hermes and EventSub no longer start a thread
per notification; countdowns stay correct after sleep; frame masking and zlib
cleanup avoid needless allocation and native memory retention. Each raid, poll
and prediction card is one value owned by the view model, replacing five fields
per card and 18 view-side writes; the chat-mode banner no longer hides early.
Tests: new watchdog/card/id/mask tests; Gradle checks listed above.
Human QA: chat matrix above.
Risks: chat reconnect timing; minute-watched no longer bursts after deep sleep;
results hide 20 s after arrival.
