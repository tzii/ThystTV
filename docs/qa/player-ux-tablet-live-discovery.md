# QA Ledger — Player UX, Tablet Polish, and Live Discovery

Branch: `codex/player-ux-tablet-live-discovery`
Design: approved in conversation (player UX / tablet polish / live discovery spec).
PR: local only for now (draft PR creation deferred until the branch is pushed).

## Slice 0 — Baseline

- **Base commit:** `e78afcdb5c94220f8244c2a34278c2b2b12fc326` (`origin/master`, "Merge pull request #20 from tzii/codex/release-1.2.1-final-review").
- **Worktree:** sibling `ThystTV-player-ux`, created clean from `origin/master`. Untracked files in the unrelated `release/1.2-prep` worktree are not used or committed.
- **Toolchain notes:** worktree needs its own `local.properties` (gitignored; `sdk.dir` → local Android SDK) and the untracked `app/debug-keystore.jks` signing keystore, both copied from the developer's main checkout. Gradle runs with `JAVA_HOME` pointed at the local Microsoft OpenJDK 21 build; system default is JDK 25, which does not satisfy `jvmToolchain(21)`.

### Authoritative gesture/control inventory (verified against base)

| Interaction or control | Actual action on base | Evidence (base) | Final treatment |
| --- | --- | --- | --- |
| Left vertical swipe | Player-window brightness | `PlayerGestureListener.onScroll` left-half vertical branch | Preserve |
| Right vertical swipe | Android media/device volume via `AudioManager.setStreamVolume(STREAM_MUSIC)` | `PlayerGestureListener.onScroll` right-half vertical branch | Preserve; label `Device volume` |
| Upper-zone horizontal swipe | Seek (non-live media only: gated on `videoType != STREAM`) | `PlayerGestureListener.onScroll` top-zone horizontal branch | Preserve; do not advertise for live |
| Lower-zone horizontal swipe | Playback speed on non-live media | `PlayerGestureListener.onScroll` bottom-zone horizontal branch | Preserve; cover in arbitration/education/regression |
| Double-tap | Toggle chat mode (hidden → sidebar → floating → hidden; portrait-gated) | `PlayerGestureListener.onDoubleTap` → `cycleChatMode()` | Preserve exactly |
| Speaker button | ThystTV stream volume via `PlayerVolumeDialog` slider; applied through `changeVolume` per backend; persisted as `PLAYER_VOLUME` int 0–100 | `PlayerFragment` volume button wiring; `PlayerVolumeDialog`; `Media3Fragment`/`ExoPlayerFragment`/`MediaPlayerFragment` volume application | Preserve across backends; polish as anchored overlay |
| Full-player aspect control | `setResizeMode()` cycles raw Media3 ints 0→4 and persists `aspectRatioLandscape` | `PlayerFragment.setResizeMode()` | Replace with canonical Fit/Fill/Stretch |
| Mini-player rendering | `RESIZE_MODE_ZOOM` (crops) | `applyMinimizedPlayerVisualState()` | Render Fit; never mutate canonical mode |

Single-finger eligibility verified on base: master gesture toggle, non-portrait, maximized, not started in a protected edge zone (`isInEdgeZone`, gesture insets + 30dp fallback, landscape only), and controls hidden at touch-down (`controlsVisibleAtGestureStart`).

### Orientation/display-mode semantics (reconfirmed on base)

- `aspectRatioLandscape` (int) is the only persisted resize preference; read once at `onViewCreated`; `setResizeMode()` cycles and persists it.
- Portrait maximized playback is forced to Fit in `applyMaximizedPlayerVisualState()`; the stored landscape value is not overwritten.
- Orientation changes are handled in-place (`MainActivity` declares `configChanges`; `PlayerFragment.onConfigurationChanged` → `initLayout()`), and returning to landscape reapplies the stored landscape mode via `applyMaximizedPlayerVisualState()`.
- Mini-player (minimize) applies ZOOM + surface background; maximize restores portrait-forced Fit or the stored landscape mode.

### Available test targets

- Attached devices/emulators at baseline: **none** (`adb devices` empty). No compatible local target for `connectedDebugAndroidTest`; the connected-test gate is recorded as *not satisfied locally* and is not waived.
- Phones/tablets available to the developer: not recorded in this environment; manual matrix rows that need real hardware are marked pending.

### Source-aspect-ratio test assets

- No offline aspect-ratio test assets ship in the repo. Verification will use Twitch content: standard 16:9 streams/VODs, portrait mobile streams (1080×1920 class), and odd-dimension transcodes when reproducible; a source matching the viewport aspect effectively (16:9 on 16:9 screens) covers the Fit≈Fill case.

### Baseline gate

Command (per slice): `./gradlew.bat check assembleDebug assembleDebugAndroidTest` with JDK 21 and `ANDROID_HOME` set (no `local.properties`, so lint's `PropertyEscape` check has no property file to flag).

- Result at base `e78afcdb`: **BUILD SUCCESSFUL** — `check` (unit tests + lint) green, `app-debug.apk` and `app-debug-androidTest.apk` produced. No pre-existing failures.
- Environment notes recorded for reproducibility: the first lint run in this worktree failed only on an untracked `local.properties` formatting issue plus a stale lint-cache entry; resolved by switching to `ANDROID_HOME` and clearing `app/build/intermediates/*lint*`. These are worktree-environment issues, not code failures.

### Existing unrelated failures

- None recorded yet; to be appended per slice if `check` surfaces failures unrelated to this feature.
