# Manual QA Handoff Checklist

Use this checklist for PRs that affect player behavior, UI, layouts, release builds, or visual presentation.

Agents should mark **Required** when a human should test an area. Mark **Completed** only when a real human/device check was actually performed.

## Build Under Test

- Branch:
- Commit:
- APK/build:
- Device/Android version, if actually tested:
- Tester, if actually tested:
- Date, if actually tested:

## Checklist

| Area | Required | Completed | Notes |
| --- | --- | --- | --- |
| App launches | [ ] | [ ] | |
| Navigation smoke test | [ ] | [ ] | |
| Live stream opens | [ ] | [ ] | |
| VoD opens | [ ] | [ ] | |
| Player closes/reopens | [ ] | [ ] | |
| Live -> live switching | [ ] | [ ] | |
| Old audio does not continue after switch | [ ] | [ ] | |
| Mini-player -> new stream | [ ] | [ ] | |
| Minimize / restore | [ ] | [ ] | |
| PiP/background behavior | [ ] | [ ] | |
| Playback speed display/control | [ ] | [ ] | |
| Quality menu/selection | [ ] | [ ] | |
| Chat-only/audio-only restore | [ ] | [ ] | |
| Gestures: brightness/volume/seek | [ ] | [ ] | |
| Floating chat: open/drag/resize | [ ] | [ ] | |
| Floating chat readability over video | [ ] | [ ] | |
| Portrait phone layout | [ ] | [ ] | |
| Landscape phone layout | [ ] | [ ] | |
| Wide/tablet layout | [ ] | [ ] | |
| Split-screen/resized window | [ ] | [ ] | |
| Stats screen | [ ] | [ ] | |
| Stats filters/charts | [ ] | [ ] | |
| Updater/changelog Markdown and actions | [ ] | [ ] | |
| README/site screenshots render | [ ] | [ ] | |
| Icon/banner assets look correct | [ ] | [ ] | |
| No private account data exposed | [ ] | [ ] | |

### Automatic Twitch sync

Follow the [automatic sync account/device matrix](research/TWITCH_AUTOMATIC_SYNC.md).
User-confirmed so far: sharing and custom intervals. First sync screenshot shows an
authentication error and two queued positions; the original empty-history label was
misleading. Retest the corrected status UI and report its specific authentication
message. Official-app resume/history and streak credit remain unverified. Cover all
playback engines and account changes; test fixtures are not evidence that Twitch
credited a real account.
The September 15 follow-up fixes incorrectly rejecting a successfully validated token
with zero/omitted expiry. Its debug build, 589 tests and lint passed (0 errors, 345
warnings); signing identity matches the previous APK. Use this September 15 build,
refresh with the current login before signing out, and distinguish
token validation rejection from a history request that fails after validation.

### 1.3 probe rebase and selective player ports

Required; not yet device-verified for this branch:

- Share from a live stream, VoD and clip; cancel the chooser and return to playback.
  Open the shared VoD link in official Twitch, including after a rewind and at zero.
  Verify the Share setting hides the action and offline playback offers no public link.
- Set different custom rewind/forward values (for example 7 and 23 seconds), reopen
  the player, and check button labels, button/gesture seeks and headset seek commands.
  Existing presets survive an upgrade. Empty, negative, decimal, zero and excessive
  input must not replace a saved value. Check phone/tablet, large fonts and rotation.
- Repeat live/VoD playback, switching, minimize/restore, close/reopen, PiP/background,
  speed/quality controls, gestures and floating chat; no stale player/audio or popup.
- Run the [Twitch probe round trip](research/TWITCH_RESUME_PROBE.md), including identity
  validation, read/list, explicit seek, confirmed write/readback and official-app resume.
  Automated request fixtures do not establish real Twitch authorization or streak credit.

## Evidence

Screenshots, videos, logs, or APK links:

-

## Known Issues / Deferred QA

-


## 1.3 release checks

- [ ] Landscape, maximized player, double-tap chat and floating chat enabled: from
  each of hidden, sidebar and floating chat, tap once and immediately begin a
  two-finger pinch (first finger lands within the double-tap interval). After the
  scale threshold is crossed, chat must return to its starting mode, in the same
  container, with the correct player width and chat icon. Verify it stays restored
  after animations settle and both fingers lift; repeat with a cancelled pinch.
- [ ] Repeat from hidden/sidebar with floating chat disabled. A normal double tap
  must still advance one mode; a normal pinch must leave chat unchanged. Reopen
  playback and confirm the saved chat-open choice was not changed by the pinch.

- During an updater download, cancel/retry and rotate/background/restore; one current
  attempt should remain and stale work must not launch an installer.
- Interrupt a transfer and verify retry/browser recovery. Deny/grant install permission;
  cancel/confirm the OS installer without an automatic reopening loop.
- Check Markdown, action reachability and scrolling at 200% text and in resized windows.
- Process death requires a fresh explicit attempt; do not expect download resumption.
- Test an exact signed 1.3 APK upgrading official 1.2.1, retaining data and migrating
  landscape display mode. Repeat the player, gestures, floating chat, Stats and launcher
  matrix on that same candidate. See RELEASE_1_3_REVIEW.md and RELEASE_PROCESS.md.
