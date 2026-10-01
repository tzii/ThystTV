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

### Website presentation

- [ ] On a physical phone and laptop, accept the visual layout in both themes;
  verify readable text and no horizontal overflow, including enlarged text.
- [ ] Select Floating chat, Player controls, Local stats, and Room to watch.
  Open the full-size control previews; confirm original images stay readable.
- [ ] Use keyboard arrows/Home/End and check focus. Open/close the demo by its
  close button, Escape, and the backdrop; focus returns to its trigger.
- [ ] Play the demo with native video controls. Closing it or hiding the page
  pauses playback. Confirm touch and audible playback on a real browser/device.
- [ ] Pause decorative motion, reload, and check persistence. System reduced
  motion stops animation; theme choice persists. Check JavaScript/storage denied.
- [ ] Follow GitHub Releases and the APK verification guide. Check downloads on
  the actual device; website browser QA is not Android installation evidence.

These checks are separate from player/device QA. Website sample-data previews
and pre-1.3 footage are labeled and do not certify current app behavior.

### September 25 large-screen and upstream update

On September 27 the maintainer reported that the delivered debug build seemed
fine. No device/backend/scenario breakdown was supplied, so the detailed rows
below remain a regression reference rather than a claim that every case passed.

On September 30 the maintainer also reported that the latest debug APK with TV
support seemed fine and proposed preparing 1.3.1. The supplied artifact identifies
as `com.tzii.thysttv.debug`, version `1.3.0-DEBUG`, code 12. No additional device or
scenario breakdown was provided. Record this as general debug smoke evidence;
the exact signed 1.3.1 upgrade and release matrix remain pending.

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

### September 27 Android TV remote navigation

The maintainer has no TV available. Codex checked an isolated Android TV
emulator and native Android view/key tests. Physical TV/OEM remote and codec
behavior remain unverified; emulator results are recorded separately in
`TESTING.md`.

- [ ] Remote-only launch: visible focus, left/right across enabled bottom items,
  CENTER selects; UP enters content and Back at a root page returns to navigation.
- [ ] Popular, Following, Games, Saved and Stats; tabs, long feeds, empty pages,
  search, settings and dialogs; nested Back returns normally and repeated Back exits.
- [ ] Following/Saved tabs, Stats ranges and channel Videos/Chat/Clips tabs remain
  fully visible while focused, after selection and after scrolling the page.
- [ ] Channel entry shows visible focus; profile details collapse while toolbar
  and tabs remain available and leave room for video cards.
- [ ] Live/VoD controls: reveal with D-pad, play/pause, seek, quality, speed,
  volume, More, popup Back/focus restoration; keyboard slider changes persist.
- [ ] Minimize, focus/restore mini-player, close/reopen, stream switching,
  PiP/background and chat controls. No focus reaches covered browse content.
- [ ] Phone/tablet gestures and floating-chat interaction remain regression areas.

### Following view cleanup

- [ ] Open available Following tabs, leave and return repeatedly, then rotate or
  recreate the screen. Tabs and lists still load; first insertion keeps position
  and later prepends still scroll to the top.
- [ ] After leaving Following, inspect a fresh LeakCanary result from the rebuilt
  APK. The previous destroyed pager/RecyclerView retention groups must not recur.
  Keep heap dumps and private analysis out of public issues and commits.

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
