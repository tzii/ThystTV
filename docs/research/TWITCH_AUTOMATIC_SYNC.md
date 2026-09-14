# Automatic Twitch viewing sync

Implemented on the 1.3-based `codex/twitch-sync-probe` branch. Sharing and custom seek
intervals passed the user's device check. Automatic resume, history and streak credit
still require an authenticated official-Twitch round trip; mocked tests cannot prove it.

## Account controls

Open **Settings → Twitch sync** or **Player → More → Twitch sync**.
New sync text currently uses the project's English fallback convention in other locales.

- **Automatically sync VoD progress** starts disabled for each account. Enable once;
  subsequent playback uploads need no per-position confirmation.
- **Count live playback toward Twitch streaks** initially follows the existing collect
  channel-points preference (default on). Changing it saves an account-specific override.
- The dialog shows pending uploads, errors, conflicts, the account's recent Continue
  Watching shelf (up to 20 VoDs), and last confirmed channel streak milestones.
- Refresh reads Twitch's shelf and retries paused sync. A shelf entry opens the same
  normal player/deep-link path with its timestamp. It is not a full history archive.

## VoD behavior

Before playback, a remote read has a three-second budget. An explicit timestamp link
or bookmark, including zero, retains priority. Completed-VoD restart behavior is retained.
A timeout falls back to account-owned pending progress or the ordinary local position;
a late response never seeks an already-running player.

Only a VoD that actually advances in this player becomes eligible for upload. Progress
is checkpointed about every five seconds and at an observed pause/close. The queue is
limited to 500 pending VoDs without evicting unsent progress. Clean records retain the
last 100 entries. An unavailable VoD pauses individually with retry/dismiss actions.
Uploads are coalesced and normally spaced at least thirty seconds apart. Local rewinds and zero are
valid positions. A durable, account-specific outbox survives offline playback and app
restarts. Playback or opening sync controls resumes work; no background service is added.
Android may stop the process, and up to the last checkpoint interval can be lost on a kill.

Every upload reads Twitch first, compares the last observed baseline, writes the desired
position, then reads it back. A retry can recognize an already-applied write. A newer
local checkpoint is never cleared by an older acknowledgement. If another client changes
Twitch's position, that VoD pauses with two choices: keep ThystTV, or keep Twitch on next
open. Choosing Twitch also suppresses uploads from the currently open instance of that
VoD; it does not unexpectedly seek the player. Reopen to resume the chosen position.

The private protocol has no verified compare-and-swap operation. Another client can
still change progress between preflight and mutation. Use one active player for the
first account test. Account/client validation and credential checks guard every request;
logout or credential changes cancel active uploads/reports and invalidate reads. Authentication/protocol failures pause
uploads until refresh. Transient failures retain progress and back off up to five minutes.
No old local positions are bulk-uploaded, and remote positions never become local Stats
watch time. Clips and downloads remain outside this sync path.

## Live reporting and channel streaks

A single player owner observes all three playback engines. The live clock counts wall
time only across consecutive advancing, playing samples. Pause, buffering, chat-only,
ads and long sampling gaps do not earn time. Account, broadcast and player switches reset
the partial minute. PiP/background audio can count while the player view still exists
and playback actually advances. Reporting stops when that owner is destroyed.

The old Hermes/chat timer is removed, avoiding duplicate or chat-only reporting. One
`minute-watched` event is attempted per observed minute, with no replay/backfill of missed
live time. Broadcast metadata must be recent (within six minutes); the normal five-minute
stream refresh supplies its identity. Twitch's public settings supply the reporting URL,
cached for one hour. Discovery is HTTPS/Twitch-host restricted and bounded to 2 MiB;
requests have timeouts, cancellation, no cookies/redirects/automatic retries or credentials
sent to the reporting endpoint. Existing claimable channel-point bonuses are unchanged.

HTTP acceptance means **sent**, not **earned**. Own-account, same-channel IRC
`viewermilestone/watch-streak` and EventSub `watch_streak` notices populate a separate
cache. Duplicates and older notices are ignored; a newer lower count can replace an old
streak. Keep chat connected to receive these confirmations. Cached milestones are not a
complete query of current streaks, and ThystTV's local viewing-day streak remains separate.

The live approach is supported by the [SmartTwitchTV author's implementation and device
report](https://github.com/fgl27/SmartTwitchTV/pull/368). Milestone parsing follows Twitch's
[IRC USERNOTICE documentation](https://dev.twitch.tv/docs/chat/irc/) and
[EventSub reference](https://dev.twitch.tv/docs/eventsub/eventsub-reference/).
VoD transport reuses the captured operations documented in the [manual probe](TWITCH_RESUME_PROBE.md).

## Required account and device QA

1. Enable VoD sync for the intended account. Watch an accessible VoD in ThystTV, pause,
   wait for pending count zero and matching readback, then close it. Check the official
   app's position and Continue Watching. Repeat official app → ThystTV, rewind and zero.
2. Verify explicit timestamp links and completed-VoD restart. Play offline, reconnect,
   and verify queued progress. Kill/reopen the app. Switch accounts and disable sync
   while a request is in flight; another account must never upload the old queue.
3. Change the same VoD on Twitch while ThystTV has queued progress. Exercise both
   conflict choices, including another Twitch change after choosing. Check unavailable
   VoDs, expired tokens, integrity failure and a write interrupted before readback.
4. Watch a live broadcast with only ThystTV open. Record existing official channel
   points/streak, observe report status after actual minutes, then compare official
   Twitch. A streak milestone may require another qualifying broadcast. Check another
   user's and shared-chat notices cannot become this account's milestone.
5. Pause, buffer, enter chat-only, switch broadcasts, reopen and disconnect chat; no
   duplicate or paused-time credit reports. Test both live and VoD with all player engines,
   minimize/restore, close/reopen, PiP/background, speed/quality, gestures and floating chat.
   Confirm only one audible player. Check dialog scrolling on phone/tablet and large fonts.

Automated checks cover policy, account storage, retries, conflicts, session isolation,
clock gating, trusted discovery/form encoding, malformed responses and milestone parsing.

## Validation on 2026-09-14

Debug assembly and all 575 unit tests passed (41 new tests, no failures/errors/skips).
Lint passed with zero errors and 345 warnings, matching the pre-change warning count.
The full suite used the bounded Windows test-process profile documented in
[TESTING](../TESTING.md). The APK remains `1.3.0-DEBUG`, version code 12,
package `com.tzii.thysttv.debug`. New text uses the existing English fallback convention.
No Android device was connected and no authenticated Twitch account test was performed.
