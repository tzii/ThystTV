# Player lifecycle races and the audio-only retry tap (#71)

## Goal

Close the four 1.5.2 follow-ups from the 1.5.1 RC5 reviews (issue #71) without a
second start, a lost start or a stuck player:

1. A PiP "Audio only" tap made while this screen's start is still unconfirmed is
   kept and applied once the start is accepted, not silently dropped.
2. A screen recreated during a process-death resume (before its fresh start has
   sent a command) does not load twice.
3. A restored screen whose earlier source is still playing in the service (Don't
   keep activities) recognises it as its own, so the first exit honours the
   configured pause/audio-only behaviour, offline or not.
4. The lifecycle test whose comment misdescribes its scenario says what it tests,
   and a test covers the scenario the comment claimed.

## Non-goals

- No change to `PlaybackService` commands or the source-request protocol.
- No change to the gesture stack (#75/#76/#77), the updater, release or version.
- No change to the documented RC4 gap: a start unanswered when the screen stops
  and then lost to process death still replays at its original position.
- Legacy `ExoPlayerFragment`/`MediaPlayerFragment` behaviour stays the same; they
  only follow a signature change if item 1 needs one.

## Current context

Base: `master` 7bfdbc694 (1.5.1 release, PR #70). Branch `fix/71-lifecycle-races`.

Ownership model in `Media3Fragment`:

- Each START_* carries a fresh `SOURCE_REQUEST` UUID. The service records it only
  on accept and answers START immediately; `GET_SOURCE_REQUEST` returns it.
- `acceptedStart` gates saving positions, stop-time background behaviour and
  `startAudioOnly()`. It is not saved: after process death the service is new.
- `lastSourceCommand` is saved only while unaccepted; `sourceRequest` is always saved.
- `refreshSourceOwnership()` (controller connect) asks the service which request it
  holds. Match: accepted. Mismatch: `started = false` and `startOrReplay()`.
  RC4 added an early return when `lastSourceCommand == null && !viewModel.started`
  (restored accepted screen on a fresh model) because the query raced that
  screen's own fresh start into a second start.
- `startOrReplay()` replays an unaccepted command, else calls `startPlayer()`,
  which sets `viewModel.started = true` and resolves the source asynchronously
  (saved position, access token, playlist). The command is sent by the
  `videoResult`/`streamResult`/... collectors, which run only while STARTED.
- `onStop()` cancels the pending start result, then applies background behaviour
  only if `acceptedStart`.
- `MainActivity`'s PiP receiver calls `startAudioOnly()` then `moveTaskToBack()`.
  `startAudioOnly()` returns at once if `!acceptedStart`.
- `MainActivity` handles `orientation|screenSize|smallestScreenSize|screenLayout`
  itself, so rotation does not recreate the player. Night mode, locale, font or
  display size changes do recreate it, keeping the `PlayerViewModel`.

Root causes, found by reading the code and reproduced on the emulator (see the
progress log):

1. Audio-only tap. The tap is dropped whenever the start is unconfirmed: for
   milliseconds after any START, and for seconds while any fresh start is
   resolving (opening a player, or a screen restored after process death or
   Don't keep activities). The task still moves back and `onStop` skips
   background behaviour, so the tap does nothing. Worse, the start then resolves
   while the screen is stopped and is lost to race 5: nothing plays in the
   background, and the player is black on return.
2. Double load. Process death, reopen: the restored screen has the dead process's
   `sourceRequest` R0 and starts fresh (`started = true`, resolving). A recreating
   configuration change before the START is sent produces two starts:
   - The stopping fragment's controller connect listener runs when
     `releaseController()` cancels the still-pending connect future. The model
     is not started yet (the controller never connected), so it calls
     `startOrReplay()` from a fragment that is being destroyed and has no
     controller, and the retained model starts loading.
   - The new fragment has the retained, started model. The RC4 guard no longer
     applies (`started` is true), so it queries; the new service answers null and
     the mismatch branch resets `started` and starts again.
   The reviewers called this "rotation"; on this manifest rotation does not
   recreate, but any recreating change reaches it. The same mismatch also
   double-starts a retained screen whose service dropped its media, because the
   connect listener already restarted it (`loaded && currentMediaItem == null`).
3. Don't keep activities + offline. Leaving destroys the activity; the service
   keeps the screen's accepted source R0 (paused or playing audio-only).
   Returning restores a screen with R0 and a fresh model. The RC4 guard skips the
   ownership query, so the screen never learns that R0 is its own. Offline, its
   fresh start cannot finish, so `acceptedStart` stays false while R0 is shown
   and can be played from the controls. The first exit skips pause/audio-only.
   Once online, the fresh start replaces R0 and the next exit behaves.
4. Test comment. `returning screen recovers its own service acceptance after a
   delayed result` says "A returning (not recreated) screen keeps its started
   model", but its fixture has no `lastSourceCommand`. A returning screen whose
   result was cancelled keeps its command; a missing command plus a started model
   is a screen recreated with a retained model after acceptance.

5. Start resolved while stopped (found during review of the code, confirmed on
   the emulator, in scope). The start collectors resume in `onStart` before the
   new controller connects. If a fresh start resolves while the screen is
   stopped (leave during loading, return later), the collector calls
   `startVideo`/`startStream` with no controller, the command is dropped, and
   the result is cleared. `started` stays true and `loaded` is false, so nothing
   restarts: a black player with no media session until the user reopens it.

## Files likely involved

- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/Media3Fragment.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/main/MainActivity.kt` (item 1)
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerFragment.kt`,
  `ExoPlayerFragment.kt`, `MediaPlayerFragment.kt` (only for an item 1 return value)
- `app/src/test/java/com/github/andreyasadchy/xtra/ui/player/CastCloseSessionTest.kt`
- `docs/PLAYER.md`, `docs/MANUAL_QA.md`

## Risks

- A second start, or no start, after recreation, process death or reconnect.
- A restored screen claiming media that is not its own. Mitigation: ownership
  is only set from the service's answer for this screen's own request ID.
- A deferred audio-only firing after the user has returned to the player.
- Interaction with the Cast local-only rejection and "Stop casting and play here".

Risk level: high (player lifecycle).

## Human approval

Required before implementation: yes

Reason: broad player-lifecycle behaviour (AGENTS.md and PLANS.md), and it
replaces a reviewed 1.5.1 guard.

## Implementation steps

1. Emulator baseline on master 7bfdbc694: reproduce items 2 and 3 and the suspected
   fifth race (process death via `am kill`; recreation via `cmd uimode night`;
   Don't keep activities; offline via `svc wifi`/`svc data`). Record results
   here. Drop or rescope any item that does not reproduce.
2. Pending fresh start (items 2 and 3). Add `freshStartPending`, saved with the
   source state. `startOrReplay()` sets it before `startPlayer()`;
   `sendSourceCommand()` clears it. In `refreshSourceOwnership()`, remove the RC4
   early return. A match sets `acceptedStart` (the restored screen now owns its
   retained source). On a mismatch, restart only if an unanswered command exists
   (`lastSourceCommand != null`) or the model is started with no fresh start
   pending. Otherwise the screen's own start, pending or about to begin from
   the connect listener or `initialize()`, stays the only one. The restart a
   mismatch can still trigger keeps today's behaviour, so a screen whose service
   lost its source is never left without a start.
3. Audio-only tap (item 1). If `startAudioOnly()` runs before acceptance while
   this screen's start is in flight (started, not rejected), record the intent
   and keep the PiP window instead of moving the task back. On acceptance (the
   result callback or an ownership match), if still in PiP, apply it once and
   move the task back. Clear it on `onStop`, on leaving PiP and on rejection, so
   it can never fire after the user returns. `MainActivity` moves back only when
   the tap was applied immediately. Prefer a Boolean result over a new callback.
4. Item 4. Re-describe the existing fixture as a recreated screen with a retained
   model, and add a real returning-screen test: send, cancel as `onStop` does,
   query, answer with the sent ID, accepted.
5. If step 1 confirms the fifth race, keep the resolved start until the controller
   is connected (do not consume the result while `player == null`; deliver it
   from the connect listener). Otherwise record it as not reproduced.
6. Unit tests in `CastCloseSessionTest` (Robolectric, mock fragment):
   - restored fresh-model screen: one start; a null or foreign answer does not
     start again;
   - recreated retained model with a pending fresh start: a null answer neither
     starts nor sends;
   - retained model, nothing pending, service lost the source: exactly one restart;
   - restored screen whose request the service still holds: accepted, and the next
     command makes it unowned until its own result;
   - deferred audio-only applies once on acceptance in PiP, is dropped on stop,
     PiP exit and rejection, and never touches unowned media.
7. Docs: replace the "skips the ownership query" sentence in `docs/PLAYER.md`;
   add the audio-only rule; add Manual QA lines.
8. Gates: `assembleDebug test lintDebug`, plus `assembleRelease` (release-risk).
9. Independent reviews per AGENTS.md (GPT-6.1-sol/high and OpenCode GLM-5.3/max)
   on the frozen diff; verify every finding.
10. Emulator re-run of step 1 on the fix, then hand off human QA.

## Verification

Automated checks:

- [ ] `./gradlew assembleDebug`
- [ ] `./gradlew test`
- [ ] `./gradlew lintDebug`
- [ ] `./gradlew assembleRelease`

Human QA required (issue #71 matrix):

- [ ] Live and VoD: open, rotate, minimize/restore, close/reopen, stream switch.
- [ ] Process death (`am kill` from background), reopen a VoD, change night mode or
  font size within 2 s: one load, resumes at the saved position.
- [ ] Don't keep activities, online and offline: leave and return; first exit
  pauses or switches to audio-only per Settings; behaviour after reconnect.
- [ ] Offline to online restore of a live stream and a VoD.
- [ ] Ordinary VoD resume, and opening a VoD at a chat timestamp.
- [ ] PiP: Audio only right after opening and right after restart; one tap is
  enough; expanding PiP before the start finishes does not send the app back.
- [ ] Audio-only transitions with both background-audio settings; speed/quality.
- [ ] Gestures and floating chat still work after each restore.
- [ ] Cast: a local-only VoD while casting still offers "Stop casting and play here".
- [ ] Upgrade over the released 1.5.1 with a player open.

Human QA completed:

- [ ] None yet.

## Progress log

- 2026-10-30: Created plan from a code read of master 7bfdbc694.
- 2026-10-30: Maintainer approved the plan, including removing the RC4 query skip.
- 2026-10-30: Emulator baseline (Medium_Phone_API_36.1, a master debug build with
  temporary `T71` logs and a 3 s delay in `PlayerViewModel.loadVideo` to widen
  the pending window; neither committed). VoD 2914038727.
  - Item 2 reproduced: `am kill` from the background, reopen, `cmd uimode night`
    0.7 s later gave two usher loads and two STARTs. Stack traces showed the
    two causes above (cancelled connect future, then the null-answer mismatch).
    Plain recreation without process death did not reload in three runs.
  - Item 3 reproduced with Don't keep activities and background audio off:
    first exit online paused; offline return then exit kept playing (position
    advancing); after reconnecting, the fresh start replaced R0 and the next exit
    paused.
  - Item 1 reproduced with PiP on: Home 0.1 s after `startPlayer`, tap the PiP
    window, Audio only at +1.9 s. The task moved back, no START was ever sent,
    media session NONE. Control: the same tap after acceptance switched to audio
    only in the background.
  - Race 5 reproduced twice (leave during loading, and after the item 1 tap):
    return connects with `started=true loaded=false`, no START, black player.
- 2026-10-30: Implemented in `Media3Fragment` (plus the `startAudioOnly()` Boolean
  in `PlayerFragment`, the legacy fragments and `MainActivity`):
  - The connect listener returns when its future was cancelled or replaced.
  - `freshStartPending` (saved) is set before `startPlayer()` and cleared when a
    command is sent. The RC4 query skip is gone; a match calls
    `acceptSourceStart()`, a mismatch restarts only without a pending fresh start
    and with a command to replay or a started model.
  - Start methods called with no controller keep the start in
    `pendingSourceStart`; the connect listener sends it instead of resetting or
    starting again. Sending, closing or a rejection drop it. If the screen is
    destroyed with one kept, `onDestroy` clears `started` so a recreated screen
    resolves again.
  - `startAudioOnly()` before acceptance keeps the PiP window for the first tap
    on a started, unrejected screen; `acceptSourceStart()` applies it once if
    still in PiP. `onStop`, leaving PiP, a rejection and close clear it; a second
    tap leaves as before.
  - Tests: 11 new or rewritten cases in `CastCloseSessionTest`; 49 runs pass on
    SDK 23 and 28. Mutation check: dropping the pending-start gate fails the
    restore and recreation tests. The cancelled-listener guard and the connect
    listener's use of `pendingSourceStart` sit inside `onStart` and are covered
    by the emulator re-run, not unit tests.
  - Docs: `docs/PLAYER.md` core invariants and the Cast restore paragraph;
    `docs/MANUAL_QA.md` issue #71 section.

## Decisions

- Decision: gate the ownership query's restart on a saved pending-fresh-start flag
  instead of skipping the query for restored screens.
  Reason: skipping the query is why a restored screen never owns its retained
  source (item 3), and the RC4 condition (`started`) stops guarding once the
  model is retained (item 2). The flag marks exactly the window in which the
  screen's own start is resolving.
  Alternatives considered: never restart on a mismatch without a command (can
  leave a screen whose service lost its source without a start, the stuck case
  issue #71 warns about); clear `sourceRequest` on a fresh start (breaks
  conditional close of a source retained across Don't keep activities); a new
  service-side background command (more protocol surface).
- Decision: keep a start resolved without a controller in the fragment and send
  it on connect, rather than leaving the result in the view model.
  Reason: all four start paths (including synchronous ones such as a custom
  proxy or skip-access-token VoD) funnel through the `start*` overrides; the
  shared collectors in `PlayerFragment` stay untouched for the legacy backends.
  The rare recreation before connect falls back to resolving again
  (`onDestroy` clears `started`), never to a second command.
- Decision: a second Audio only tap while one is pending moves the task back as
  before.
  Reason: `started` stays true after a failed load (offline), so acceptance may
  never come; the user must still be able to leave PiP from the action.

## Final PR summary draft

Summary: Fix the issue #71 player lifecycle races. A screen restored after
process death or Don't keep activities owns its retained source and starts once.
A PiP Audio only tap during a pending start is applied when the start is accepted.
Tests: new ownership and audio-only cases in `CastCloseSessionTest`; full gates.
Human QA: the #71 matrix above.
Risks: player lifecycle; reviewed independently.
