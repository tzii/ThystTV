# Twitch VoD resume compatibility experiment

This branch implements the first stage of the supplied [research handoff](TWITCH_SYNC_HANDOFF.md).
It does not enable automatic sync, migrate old positions, change local Stats, or change live
watch reporting. Originally built from `c9a055d0`, it was rebased on 2026-09-13 onto
published `v1.3.0` (`cceaa6f14`). The seek-preview experiment remains in a separate
worktree and is not included in this APK. The two additional player ports are recorded
in the [upstream ledger](../UPSTREAM_SYNC_LEDGER.md).

## Verified evidence and remaining boundary

The captured [Xtra request/response examples](https://github.com/crackededed/Xtra/issues/329#issuecomment-2943100913)
were read directly on 2026-09-08. They identify `queryUserViewedVideo`,
`updateUserViewedVideo` and `FollowedStreamsContinueWatching`, including successful writes
and seconds-based position values. Their persisted hashes remain historical compatibility
seeds, not an assertion that every current token works. The [SmartTwitchTV live-reporting PR](https://github.com/fgl27/SmartTwitchTV/pull/368)
also contains the author's report of earned points and preserved streaks; this is evidence
for a later experiment, not verification of ThystTV.

No authenticated Twitch request or official-app round trip was performed here. The probe
must verify those with the user's real app login. It never requests credentials in chat.

An anonymous read-only probe on 2026-09-08 confirmed that Twitch still recognizes both
captured read/list hashes: VoD `635475444` returned `self.viewingHistory: null`, and the
list returned `currentUser: null`. This verifies current operation recognition only.
It does not establish authenticated reads, write permission, or any personal history.

## How to run it

1. Enable **Settings -> Debug -> Enable Twitch resume test**.
2. Open an accessible online VoD and choose **More -> Twitch resume test**.
3. **Read Twitch position** validates the app's GQL token against Twitch's validation
   endpoint, checks the account and client ID, and reads that VoD. No local seek occurs.
   A null history by itself is not evidence of successful account interoperability; compare
   against a known saved position in the official app and independently check the list.
4. **Seek to the Twitch position** is an explicit local player action after a successful
   read. A missing position is distinct from zero; unavailable/out-of-range positions are
   not silently applied.
5. **Read Continue Watching** independently tests the recent-list operation and displays
   at most twenty entries. It does not import the shelf into local history.
6. **Send current playback position...** snapshots the actual current VoD position. The
   confirmation displays the account, VoD and timestamp. **Send and verify** validates the
   account, sends that snapshot in seconds, checks the mutation result's video identity,
   then reads it back. A backwards position and zero are allowed. No legacy row is uploaded.

The diagnostic uses the existing app GQL headers (including configured integrity headers)
and app OkHttp transport, regardless of the selected Cronet/HttpEngine preference. It does
not substitute a Helix token or change client IDs to force authorization. Validation success
alone is not proof that the private read/list/write operation is authorized.

## Required account experiment

- In the official Twitch app, watch a VoD to a distinctive point, pause and close its player.
- Open that VoD in ThystTV, read Twitch's position, and explicitly seek to it.
- Continue watching in ThystTV, confirm a captured position write, and inspect readback.
- Close the ThystTV player, then open the same plain VoD URL in the official app (no `t=`
  parameter). Confirm it resumes near the reported position.
- Repeat with an intentional backwards seek and a near-zero restart. The larger position
  must not automatically win. Test zero separately from missing history.

Keep other players closed during this first test. The captured protocol offers no verified
compare-and-swap revision, so simultaneous clients can overwrite each other's progress.
This experiment verifies resume compatibility, not complete watch history or streak credit.

## Failure behavior and diagnostic limits

- Requests are user-triggered, bounded to 512 KiB, cancellable, timeout-limited and not
  automatically retried. Redirects and cookies are disabled. Raw bodies/headers are not logged.
- HTTP failures, GraphQL errors, obsolete hashes, invalid data, account/client mismatches
  and readback disagreement have separate outcomes. HTTP 200 alone is never success.
- An accepted mutation whose readback fails is shown as accepted but unverified. A transport
  failure or interruption can leave the write outcome unknown; read again before retrying.
- Switching accounts/credentials invalidates the session. Backgrounding, rotation, PiP,
  minimize and closing dismiss/cancel the diagnostic; it does not replay an interrupted write.
- The dialog keeps only in-memory results and uses no outbox or database migration. Local
  playback retains its normal checkpoint behavior. Explicitly seeking to Twitch's position
  naturally changes the current playback position.

## Human regression QA

Rebase verification on 2026-09-13: `assembleDebug test lintDebug` passed on the 1.3
base with the two player ports. All 534 tests passed, including the 23 probe tests
and 14 new player-port tests. Lint reported zero errors and 345 warnings. The APK is
`1.3.0-DEBUG` (code 12). No device or authenticated Twitch test was performed.

Build verification on 2026-09-08: `assembleDebug test lintDebug` passed after the final
credential-header fix. All 373 tests passed, including 23 probe tests. Lint reported zero
errors and 334 warnings; the new warning concerns concatenating diagnostic text. No Android
device was connected and no authenticated Twitch experiment was run.

Required: live/VoD playback, stream switching without old audio, minimize/restore,
close/reopen, PiP/background/rotation, speed/quality controls, gestures and floating chat.
Check the diagnostic on compact/wide screens and large fonts, including long recent lists.
Also test logout/account switching, expiry/integrity failure, missing/private/deleted VoDs,
offline/timeout/cancellation after a write, and ordinary playback with the setting disabled.
Confirm local history and Stats are unaffected by diagnostic reads or remote writes.

## Next stages

After the account experiment passes, tackle playback-owned live credit reporting and
own-account milestone observations as a separate change. Account-scoped automatic VoD
sync and conservative legacy migration follow their own tests. Existing local rows lack
ownership and timestamps, so neither bulk upload nor maximum-position merging is safe.
