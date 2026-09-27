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

### September 25 large-screen and upstream update — required, not completed

- [ ] On tablet and landscape phone, brightness/volume side feedback is easy to
  see over bright/dark video, with larger icons and a thicker level track. Check
  both ends of each level; its pill must stay within the video and system insets.
- [ ] Resize to short split-screen and narrow side-chat video; return to full
  screen. Compact feedback, seek/speed/pinch indicators and chat touches remain
  correct. Repeat with large fonts and RTL.
- [ ] Live/VoD playback, stream switching without old audio, minimize/restore,
  close/reopen, PiP/background, speed/quality, all gestures, floating-chat
  open/drag/resize, and Stats load/rotate/range smoke.
- [ ] Refresh individual and multiple saved bookmarks, reopen Saved/relaunch and
  confirm updated title/duration/art persists without duplicate entries.
- [ ] Resume a VoD while its broadcast is still live, with a saved position past
  the listed duration. It must retain position. Repeat via cached bookmarks;
  completed VoDs with final metadata still restart, explicit timestamps win and
  downloaded segments retain their own duration behavior.
- [ ] With no API tokens but a retained account ID, player/channel buttons reflect
  local follows. Repeat signed in through GQL and Helix fallback.
- [ ] Open chat profiles using moderator permissions and Helix fallback; follow
  dates belong to the selected viewer. Ordinary accounts without follower permission
  still show profiles. Check profiles in floating chat and ordinary followed lists.
- [ ] `/color` displays the current color through HttpEngine and legacy Cronet
  where supported; existing OkHttp/Cronet behavior remains intact.
- [ ] German/Japanese/Spanish/Russian UI: inspect changed settings, quoted labels,
  plural/count values, formatted percentages and Russian ThystTV login text.

No device or platform-backend acceptance has yet been recorded for this build.

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
