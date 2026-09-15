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
- History status follows the actual request: not loaded, loading, successful or failed.
  An empty local cache does not prove account verification. A failed refresh keeps
  cached entries marked as previously saved; cancelled or superseded requests cannot
  publish a successful result. VoD and live-report statuses have separate labels.
- Authentication errors distinguish missing GraphQL credentials, account mismatch,
  client-ID mismatch and Twitch rejection/expiry. An app username or Helix-only
  login does not prove the GraphQL credentials needed for this experiment are present.
  Only fixed error descriptions are displayed; tokens and server error bodies stay hidden.
- Token validation accepts a successful response with matching account/client even
  when `expires_in` is zero or omitted. Some legacy tokens have no scheduled expiry;
  zero must not be treated as proof of expiry. Each operation still validates again,
  and HTTP 401/403, identity mismatch or malformed expiry prevents the operation.
  Errors distinguish rejection during token validation from a later history request
  that could not authenticate after the token was accepted.

This follows Twitch's [validation endpoint contract](https://dev.twitch.tv/docs/authentication/validate-tokens/)
and [reported non-expiring legacy tokens](https://discuss.dev.twitch.com/t/not-understanding-token-expirations/46777).
Successful validation proves token identity, not permission for every private history request.

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

First device feedback showed a generic authentication error with two pending VoD
positions. The original dialog incorrectly displayed the verified-empty-history label
whenever its cache was empty, including after a failed request. The follow-up corrects
that label and exposes the transport's specific authentication reasons. The screenshot
does not establish which authentication check failed or any successful Twitch sync.
Retest by installing the updated debug APK, opening Twitch sync, and pressing Refresh.
Record the VoD/history status; only proceed with the round trip once authentication
succeeds. Pending positions must survive the update and failed refreshes. Also check
empty successful history, cached history while offline, reopening/cancelling refresh,
and account changes. Never include tokens in QA reports.

The September 14 debug assembly and all 583 unit tests passed (eight additional status regression
tests in this follow-up, no failures/errors/skips).
Lint passed with zero errors and 345 warnings, matching the pre-change warning count.
The full suite used the bounded Windows test-process profile documented in
[TESTING](../TESTING.md). The APK remains `1.3.0-DEBUG`, version code 12,
package `com.tzii.thysttv.debug`. New text uses the existing English fallback convention.
The first incremental KSP run failed internally; the complete checks passed with
`-Pksp.incremental=false`. The packaged APK's updated status labels were verified.
Development tools did not access a real Twitch account or install on a connected
device. User feedback above is the available device evidence; a successful account
round trip and streak credit remain unverified.

## Token validation follow-up on 2026-09-15

User feedback shows the combined authentication/integrity/expiry error while ordinary
Following is available. Inspection found that sync rejected `expires_in: 0`, unlike
the app login path. That is a reproducible code defect and a possible explanation of
the reported failure; no real token response was collected to confirm it for this account.
The fix accepts the legacy validation shape while retaining fresh validation and
account/client checks. A rejected validation token is now distinct from later history
authentication failure. Saved positions and credential selection are unchanged.

Six transport regressions were added for zero/omitted expiry, malformed values,
identity mismatch, history rejection after validation and later token revocation.
Existing zero-position write and manager error-mapping cases were extended.
Code review and `assembleDebug test lintDebug` passed. All 589 tests passed with no
failures, errors or skips, including all 29 transport tests. Lint reports zero errors
and 345 warnings, unchanged from the prior build. The full suite used the bounded
Windows worker profile in [TESTING](../TESTING.md); no tests were excluded.
The built APK contains the new error code and both updated authentication messages.
Its signing certificate matches the previous debug APK; package/version remain
`com.tzii.thysttv.debug`, `1.3.0-DEBUG`, code 12, supporting an ordinary app update.
Development tools did not use a real Twitch account; the user's token response and
successful official-app interoperability remain unverified.

Post-fix device screenshots on September 15 show **Twitch Continue Watching refreshed**
with a populated list of VoD titles and resume timestamps, including zero. Account
validation and authenticated Continue Watching reads are now device-verified. No raw
token response was collected, so the exact expiry value remains unknown.

The screenshots still show two pending VoD positions and no received channel-streak
milestone. Upload acknowledgement/readback, actual player resume in both directions,
and Twitch streak credit remain unverified. A populated shelf does not prove writes;
an empty milestone cache does not establish failure of live reporting. The VoD sync
and Live reports status lines are above the captured scroll position. Read those next,
then continue the account round-trip and live-report QA matrix. Do not infer real sync
success from fixture tests.
