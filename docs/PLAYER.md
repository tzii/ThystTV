# Player Architecture And Regression Notes

## Why This Area Is High Risk

The player is ThystTV's most important UX surface. Small lifecycle changes can cause double audio, stale fragments, broken minimize/restore, PiP regressions, gesture conflicts, black surfaces during transitions, or unreadable overlay controls.

## Staleness Note

The paths below are a map, not a guarantee. If code has moved, search for the current file/function and update this doc when useful.

## Core Invariants

- Only one active player session should exist.
- Starting a new player should close or safely replace the old player.
- Closing a player should release playback resources.
- Minimize/restore must not leave stale fragments.
- Sleep timer should close only the current player.
- PiP auto-enter should be enabled only while a player is open.
- Stream switching must not leave old audio playing.
- UI controls should stay responsive during live/VoD transitions.
- Overlay surfaces should remain readable over arbitrary video content.

## High-Risk Files

Update this list as the code evolves.

- `app/src/main/java/com/github/andreyasadchy/xtra/ui/main/MainActivity.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/chat/`
- `app/src/main/java/com/github/andreyasadchy/xtra/player/lowlatency/`
- player-related layouts in `app/src/main/res/layout/`
- player-related strings in `app/src/main/res/values/`
- player settings/preferences in `app/src/main/res/xml/`
- Media3 / ExoPlayer dependency declarations in Gradle catalogs/build files

## Recent Lessons From ThystTV Fixes

- For issue #5, logs did not show a clean app `FATAL EXCEPTION`; the safer fix was the narrow Media3/ExoPlayer rollback and HLS parser compatibility change, not broad WIP player sync.
- Quality menu labels can regress to HLS variant indices after chat-only -> auto transitions. Preserve readable labels derived from labels, format height/frame rate, or URL path.
- VoD and live menus may legitimately differ. Do not add chat-only to VoD unless the data/model supports it.
- Floating chat should use a dark translucent video-overlay palette in both app themes; background opacity remains user-controlled.
- Stats/player overlay controls need compact and landscape checks because label clipping and stacked graph labels have happened before.
- Any view adapter tied to fragments can leak destroyed views. Clear listeners/adapters in `onDestroyView()` when changing RecyclerView or fragment lifecycles.

## Required Checks For Player Work

Run:

```bash
./gradlew assembleDebug
./gradlew test
```

Also run when resources/UI are touched:

```bash
./gradlew lintDebug
```

For release-risk player work, also verify:

```bash
./gradlew assembleRelease
```

## Human QA Handoff

Agents should not claim physical-device QA unless it was actually performed. Instead, list the required human QA from `docs/MANUAL_QA.md`.

At minimum, player work should request human verification for:

- live stream opens
- VoD opens
- live -> live switching
- live -> VoD switching, if touched
- VoD -> live switching, if touched
- minimize / restore
- close / reopen
- orientation change
- PiP/background behavior where relevant
- playback speed menu
- quality menu
- gestures
- floating chat overlay

## Common Regression Smells

- old stream audio continues after opening a new stream
- player view is black after minimize/restore
- controls show stale speed/quality
- sleep timer closes the wrong player
- PiP remains enabled after player close
- orientation recreates UI with stale player state
- gestures conflict with chat or controls
- lifecycle callbacks apply to an old fragment
- quality menu shows raw numeric HLS variants
- floating chat is unreadable in light mode or on bright video

## PR Expectations For Player Changes

A player PR must include:

- changed files
- risk areas
- Gradle checks run
- human QA required
- human QA completed only if actually performed
- screenshots/video when UI changed
- known risks or follow-up issues
