# Keep sign-in open when the viewer leaves for another app

## Goal

A viewer who leaves sign-in to read a code from an authenticator, SMS or email app
comes back to the same sign-in page, whether they return from Recents or the
ThystTV home-screen icon.

## Non-goals

- No change to the sign-in flow, token handling, revocation or reauthorization.
- No change to `MainActivity`'s `singleTask` launch mode, links or notifications.
- No WebView state saving across process death (the page reloads; see Decisions).

## Current context

- `MainActivity` was the `MAIN`/`LAUNCHER`/`LEANBACK_LAUNCHER` activity with
  `launchMode="singleTask"`. A launcher intent sent to a singleTask activity that
  already exists closes every activity above it in the task, so returning by the
  icon closed `LoginActivity` (and Settings). Recents kept it.
- `LoginActivity` handled only `orientation|screenSize`. Folding, split-screen
  resizing, dark mode and keyboard changes rebuilt it, and `onCreate` cleared all
  cookies and loaded the first sign-in page again.
- `MainActivity.handleIntent` has no branch for `ACTION_MAIN`; the launcher intent
  only ever reached it as a no-op `onNewIntent`.

## Files likely involved

- `app/src/main/java/com/github/andreyasadchy/xtra/ui/main/LaunchActivity.kt` (new)
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/login/LoginActivity.kt`
- `app/src/test/java/com/github/andreyasadchy/xtra/ui/main/LaunchActivityTest.kt`
- `docs/MANUAL_QA.md`, `docs/AGENT_MAP.md`, `docs/PLAYER.md`, `CHANGELOG.md`

## Risks

- App entry: the launcher icon now opens `LaunchActivity`. Launchers that pinned
  the old component must re-link the icon on update (Launcher3 does).
- PiP and the minimized player: the icon used to deliver `onNewIntent` to the
  existing `MainActivity`. It now adds and closes a trampoline on top of the task.
  PiP expansion and minimized-player return need device checks.
- Startup: one extra activity start before `MainActivity` (no layout, finishes in
  `onCreate`); the system splash carries over.
- Behavior change: the icon now returns to Settings if it was open, instead of
  closing it. This is the usual Android behavior.

Risk level: medium

## Human approval

Required before implementation: no

Reason: narrow and reversible (revert the manifest filter move). Player lifecycle
code is untouched; the PiP/minimize return path needs human QA before release.

## Implementation steps

1. Add `LaunchActivity`: if it is the task root, start `MainActivity` with a copy
   of the launcher intent (flags cleared); always finish.
2. Move the launcher filters from `MainActivity` to `LaunchActivity`.
3. Widen `LoginActivity`'s `configChanges` to `smallestScreenSize|screenLayout|uiMode|keyboard|keyboardHidden|navigation`.
4. Keep cookies when `LoginActivity` is recreated (`savedInstanceState != null`).
5. Tests and docs.

## Verification

Automated checks:

- [x] `LaunchActivityTest` (launcher filters, launch modes, config changes, first
  launch and launch into a running task)
- [x] `./gradlew assembleDebug`
- [x] `./gradlew test` (1,457 tests, 0 failures or errors)
- [x] `./gradlew lintDebug`

Human QA required:

- [ ] `docs/MANUAL_QA.md` > Leaving sign-in for another app, on a phone and TV.
- [ ] PiP and minimized player returning from the icon; notification and link entry.

Human QA completed:

- [x] Emulator, Android 16 phone AVD, launcher intent sent with Launcher3's flags
  (`0x10200000`): before the fix, sign-in → Home → icon left only `MainActivity`. With
  the fix, the same `LoginActivity` stays on top after Home, another app, two dark-mode
  switches and the icon, with typed text still in the field. Settings → Home → icon
  returns to Settings. Cold start opens one `MainActivity`. PiP → icon expands the
  player. A channel link while in PiP opens in the same `MainActivity` with the player
  kept minimized.
- [ ] Physical phone with a real authenticator app, and Android TV.

## Progress log

- 2026-11-15: Created plan. Cause confirmed in the manifest; trampoline and
  sign-in config changes implemented with Robolectric tests.
- 2026-11-15: Full gate passed. Bug reproduced and fix checked on the emulator.

## Decisions

- Decision: a trampoline launcher activity instead of changing `MainActivity`'s
  launch mode.
  Reason: `singleTask` keeps one `MainActivity` (one player) when links arrive from
  other apps. `singleTop` would let a link create a second instance in the caller's task.
  Alternatives considered: login in its own task (activity results are lost across
  tasks and the icon would still show the main screen); relaunching sign-in from
  `MainActivity.onNewIntent` (the half-typed Twitch page would still be lost).
- Decision: keep cookies on recreation but do not restore the WebView or the
  collected tokens.
  Reason: a reloaded Twitch page loses a half-finished code step anyway, and saving
  tokens in the instance state would hand them to the system process. Keeping the
  cookies lets a Twitch web session that already finished continue without the password.

## Final PR summary draft

Summary: launcher trampoline so the icon no longer closes sign-in; sign-in survives
resizing and theme changes and keeps its cookies when rebuilt.
Tests: `LaunchActivityTest`; full gate.
Human QA: MANUAL_QA "Leaving sign-in for another app".
Risks: app entry, PiP/minimize return from the icon, launcher icon re-linking.
