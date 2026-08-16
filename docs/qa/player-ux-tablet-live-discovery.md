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

## Slice 1 — Tablet correctness

Changes:

- Mini-player rendering switched from `RESIZE_MODE_ZOOM` to `RESIZE_MODE_FIT` in `applyMinimizedPlayerVisualState()`; the complete source frame is preserved (16:9, 4:3, 21:9, portrait, odd-dimension, transcode variants) and empty space is preferred to cropping. The canonical landscape mode is untouched by minimize/maximize.
- New `PlayerSurfacePolicy` classifies the measured active player surface (`binding.playerLayout`) as COMPACT (<600dp) or LARGE (≥600dp) and derives gesture-feedback placement:
  - compact surfaces keep the existing top-centered pill, margins, and 24dp top padding (phone presentation materially unchanged);
  - LARGE surfaces place Brightness at the start edge and Device volume at the end edge, vertically centered, capped at 280dp; Seek/Playback speed stay top-centered, capped at 360dp;
  - edge margins add captured system-gesture insets so indicators never sit in protected zones;
  - LARGE feedback shows a concise label (`Brightness`, `Device volume`, `Seek`, `Playback speed`) before the value; compact feedback is unchanged.
- Gesture zone math and feedback placement now use player-surface dimensions (`playerWidth`/`playerHeight` on `PlayerGestureCallback`) instead of `resources.displayMetrics`, correcting split-screen and resizable-window behavior. Recalculation happens naturally per event after rotation, split-screen, chat-visibility, and freeform resize changes.
- Feedback hide now fades out over 150ms after the existing 800ms idle hold.
- `layout_player_gesture_feedback.xml` root converted to FrameLayout to support runtime gravity/margins; ids and pill styling unchanged.

Tablet audit (static, per design list; interactive matrix rows pending hardware):

- Main player: single adaptive layout; large-surface feedback compact and edge-localized (this slice). No duplicated tablet screens introduced.
- Mini-player: Fit, full frame preserved, background remains theme surface (this slice).
- Player with/without chat: `playerLayout` width already excludes sidebar chat; policy reads the measured surface, so feedback and zones follow chat visibility (this slice).
- Controls, Quality, Speed, Stream volume, More: addressed in Slice 4.
- Gesture guide, profile live state, search live results: Slices 5–6.

Automated evidence:

- `PlayerSurfacePolicyTest`: classification boundaries (599/600dp, invalid dims), compact placement for all kinds, large edge/center placement, inset awareness, compact inset-ignoring.
- Gate: `check assembleDebug assembleDebugAndroidTest` **BUILD SUCCESSFUL** (2m50s, includes unit tests + lint).

Manual matrix: pending hardware (no attached devices in this environment).

## Slice 2 — Gesture infrastructure

Changes:

- New `PlayerGestureArbiter` owns every touch sequence with owners Idle, Seek, PlaybackSpeed, Brightness, DeviceVolume, DoubleTapChat, PinchDisplayMode. It is fed pointer lifecycle events inside the dragView listener before the single-finger detector decides:
  - a second pointer creates a pinch candidate before an unclaimed single-finger gesture can win (claims by the detector are denied while a candidate exists);
  - merely placing two fingers claims nothing; pinch claims only after span travel ≥ 2× touch slop AND |scale−1| ≥ 0.02;
  - an owned single-finger gesture cannot be converted by a second finger;
  - once pinch owns, lifting one finger suppresses remaining single-finger events until the final release/cancel (no pointer handoff, no upAction routing);
  - a third finger never creates or restores a candidate;
  - when pinch claims, CANCEL is dispatched to the tap detector and (if controls were visible at gesture start) to the controls root so no view stays pressed; `isSwipeGestureInProgress` blocks controller show/minimize mid-pinch;
  - pinch is the deliberate `controlsVisibleAtGestureStart` exception: candidacy requires only non-portrait + maximized + gestures enabled.
- Pinch candidacy also honors the gestures master toggle and portrait/minimized ineligibility.
- Double-tap compensation: the detector can fire double-tap on the first finger of a fast pinch (tap-to-hide-then-pinch). The arbiter allows pinch to supersede an already-claimed DoubleTapChat, and `beginPinch` reverts the chat toggle exactly once so an intentional pinch never toggles chat.
- New `PinchDisplayModeController` implements the spec's reversal state table (arm 1.08/0.92, hysteresis 0.04, Stretch exits toward Fill/Fit by direction; only Fit or Fill can be committed; float-tolerant boundary comparisons). `PlayerDisplayMode` enum introduced (FIT/FILL/STRETCH with Media3 renderer mapping) — the store and renderer wiring arrive in Slice 3; Slice 2 shows centered pinch feedback (icon + Fit/Fill label + progress + haptic-on-arm using the existing haptic preference) and changes no display mode yet.
- The temporary aspect-ratio resize button remains available and unchanged; double-tap still invokes the existing chat-mode callback (now claim-gated).

Automated evidence:

- `PlayerGestureArbiterTest` (18 tests): the full conflict matrix — candidate creation/dissolution, third finger, no-claim for stationary fingers/below slop/below deadzone, single-claim-per-sequence, conversion denial, post-pinch suppression, double-tap claim/denial/supersede, invalid claims, resets.
- `PinchDisplayModeControllerTest` (18 tests): every row of the reversal table from Fit/Fill/Stretch, hysteresis boundaries, re-arm, release-commit vs neutral-restore, cancel, single Armed event per target, committed-mode carryover.
- Gate: `check assembleDebug assembleDebugAndroidTest` **BUILD SUCCESSFUL** (4m09s). Two earlier runs failed for environment reasons recorded here: one flaky lint/UAST tooling crash (`RepeatOnLifecycleDetector`/FIR on the untouched `MainActivity.kt`) that did not reproduce, and three `UnsafeOptInUsageError`s on the new `PlayerDisplayMode` enum (fixed with the same `@OptIn(UnstableApi)` treatment the player fragments use).

Manual matrix: pending hardware; the conflict list from the design (swipe-then-second-finger, controls visible/hidden, portrait/minimized, pinch near edges, two fingers without movement, below deadzone, reversal, one-finger lift, third finger, pinch with controls visible, modal surface open, rapid gestures, double-tap before/after pinch) is covered deterministically by the arbiter/controller tests above and awaits interactive confirmation.

## Slice 3 — Display modes

Changes:

- New `PlayerDisplayModeStore` owns persistence and legacy migration for the non-portrait maximized player. Canonical key `playerDisplayModeLandscape` (strings fit/fill/stretch). On first read when absent, the legacy `aspectRatioLandscape` integer migrates: 0→Fit, 1/2/4→Fill, 3→Stretch, missing/corrupt/unknown→Fit, and the canonical value is persisted so upgrades are deterministic. The legacy key is never written and remains read-only migration input.
- One canonical runtime state: `PlayerFragment.displayMode` (loaded via the store) drives the renderer (`applyMaximizedPlayerVisualState`), pinch (`effectivePinchDisplayMode`/`PinchDisplayModeController.begin`), and the More menu. The temporary resize button cycles Fit→Fill→Stretch→Fit through `cycleDisplayMode()` and writes only the canonical key — it never writes raw five-mode renderer values.
- Pinch preview and commit: on API 24+ the renderer pins to Fit and a uniform view scale interpolates toward the target geometry (`PlayerDisplayModePreviewer`); Stretch previews anchor at the Fill scale in both directions since Stretch is not on the Fit-to-Fill continuum; below API 24 (unreliable SurfaceView transforms) the preview steps to the target renderer mode only when armed. Commit stores the canonical mode; neutral release and cancellation restore the committed mode and clear transient transforms; `applyMinimizedPlayerVisualState`/`applyMaximizedPlayerVisualState` also reset preview scale. When source and viewport aspects match, the ratio collapses to 1 and feedback is label-only.
- Video aspect ratio is now tracked via `PlayerFragment.updateVideoAspectRatio`, called by the Media3, ExoPlayer and MediaPlayer backends instead of touching the frame layout directly.
- More menu: the pref-gated raw `menuRatio` cycler is replaced by a permanent `Display mode` entry (non-portrait) showing the current mode and opening a Fit/Fill/Stretch single-choice picker that writes the canonical state. Portrait maximized playback and the mini-player remain forced Fit and never mutate the stored mode.

Automated evidence:

- `PlayerDisplayModeStoreTest` (8 tests): every legacy mapping including missing/corrupt/unknown, canonical-wins, corrupt-canonical fallback, save writes canonical only, renderer mapping for the three modes.
- `PlayerDisplayModePreviewerTest` (9 tests): matching-aspect collapse to 1, 4:3/9:16 ratios, invalid inputs, Fit↔Fill interpolation, Stretch anchoring in both directions, progress clamping.
- Gate: `check assembleDebug assembleDebugAndroidTest` **BUILD SUCCESSFUL** (3m18s).

## Slice 4 — Player-control polish

Changes:

- New shared `PlayerDialogSizing` policy: Quality, Speed and More surfaces derive their width and window gravity from the dialog's own window metrics (correct in split-screen/freeform). Compact surfaces keep the current bottom-anchored phone presentation; large/windowed surfaces (≥600dp) become centered surfaces capped at 420dp. The More bottom sheet is width-capped and horizontally centered on large surfaces.
- Quality exposes its current state as text (e.g. `1080p60`) on large/windowed surfaces — a new `qualityValue` control replaces the settings icon there and re-runs when the surface is first laid out or changes orientation; compact surfaces keep the icon. Speed continues to show its current value.
- Stream volume becomes a transient player overlay anchored near the speaker control: `Stream volume [speaker] [slider] 65%`. It stays open while touched, dismisses on an outside tap (the next tap on the player surface only dismisses, it does not start a gesture) or 1500ms after the last adjustment, and keeps mute, slider, percentage, current backend volume (`changeVolume`), and persisted `PLAYER_VOLUME` synchronized across Media3, custom ExoPlayer and MediaPlayer backends. The old `PlayerVolumeDialog` bottom sheet and its layout were removed. The More entry is labeled `Stream volume` to be unmistakable from the right-side vertical `Device volume` gesture. Stream volume is not moved into More as its primary interaction; the speaker button remains primary.
- More hierarchy regrouped dynamically: Stream (Viewer list, Chapters, Download, Bookmark, Sleep timer, Restart player), Chat (chat bar, chat visibility, Translate all, Reload emotes, Connect/Disconnect), Playback (Stream volume, Subtitles, Display mode, debug playlist tags). Existing feature preferences continue gating their rows; empty groups hide their headers. Quality and Speed remain leading entries with value text. Display mode is the permanent canonical picker from Slice 3.

Automated evidence:

- `PlayerDialogSizingTest` (6 tests): large/compact classification, 420dp capping, compact phone-sheet sizing, window gravity mapping.
- Gate: `check assembleDebug assembleDebugAndroidTest` **BUILD SUCCESSFUL** (3m49s). One earlier run hit the known-flaky lint/UAST tooling crash (on `ExoPlayerFragment.kt`, one line changed); it did not reproduce on the clean re-run.

Manual matrix: pending hardware (surfaces reposition/center after rotation and window resize need interactive confirmation).

## Slice 5 — Gesture education

Changes:

- New `PlayerGestureGuideDialog`: one compact screen, no demonstrations, `Got it` button; plain-text rows keep it keyboard/TalkBack navigable and it never auto-dismisses on a timer. Copy lives entirely in string resources and never teaches double-tap seeking; the double-tap row says `Toggle chat`.
- Context-aware rows: seekable media shows Left vertical → Brightness, Right vertical → Device volume, Upper horizontal → Seek, Lower horizontal → Playback speed, Pinch → Fit / Fill, Double tap → Toggle chat; live streams omit both horizontal rows (live playback supports neither gesture); Settings without a playback context keeps them with qualified copy (`Seek on VODs and seekable videos`, `Playback speed on supported non-live media`).
- One-time display: appears on the first eligible non-portrait maximized playback (initial layout or first rotation into landscape), never above an existing modal surface (`closeOnPip`). A versioned preference (`player_gesture_guide_version`, current 1) records dismissal so a materially revised guide can be shown once in a later release. Reopenable from `More > Help > Player gestures` and `Settings > Player > Player gestures`.
- Contextual pinch hint: after the guide is dismissed, a later eligible playback may show `Pinch to fit or fill video` when controls first hide (non-forced hide only). Separate preferences record hint-shown and pinch-used; a successful pinch commit sets pinch-used and permanently suppresses the hint; an unrecognized two-finger touch counts as nothing. The hint never appears above a modal surface.

Automated evidence:

- `PlayerGestureEducationTest` (5 tests): seekable/settings row sets, live row omission, guide versioning, full pinch-hint eligibility matrix (guide current, unshown, unused, later session).
- Gate: `check assembleDebug assembleDebugAndroidTest` **BUILD SUCCESSFUL** (2m40s; one prior run hit the known-flaky lint/UAST crash and passed on re-run).

## Slice 6 — Live discovery

Changes:

- New `ChannelSearchItem` model (channel identity/profile data plus optional live stream metadata) keeps search-only playback fields out of the shared `User` model. The paging pipeline (data source, view model, fragment, adapter) now carries it.
- Mapping per API without any per-row enrichment request: the GraphQL typed query now also requests stream id, title, game display name, creation time, preview image and viewer count; the persisted GraphQL query maps viewersCount (its response carries nothing else); Helix maps its existing title, game name and start time — viewer count stays absent for Helix because channel search does not provide it.
- Search rows: live results show a prominent red LIVE badge, stream title, category, viewer count when available, and a `Watch live` action that constructs the existing `Stream` model and calls `MainActivity.startStream` directly. Offline rows keep the channel and follower presentation. Missing optional metadata hides cleanly. The adapter's unconditional `areContentsTheSame = true` was replaced with real model equality (data class), so changing live state or metadata rebinds the row.
- Interaction targets are distinct: row, avatar and channel name open the channel profile; `Watch live` launches playback; if live status exists but playback identity is insufficient (missing channel id), `Watch live` is disabled while profile navigation remains available.
- Channel profile: the header stream layout now prominently shows a red LIVE badge above the title when live, alongside the existing title, category, viewer count, uptime and `Watch live` button (which already launches `startStream`). Offline profiles keep the existing open-player behavior without masquerading as a live CTA. No additional network request is made; a stale search result enters the existing player error path.

Automated evidence:

- `ChannelSearchMapperTest` (8 tests): persisted-GraphQL live/offline mapping, Helix title/game/start-time mapping, blank-metadata degradation, offline identity mapping, Watch-live eligibility matrix, content-equality rebinding.
- Gate: `check assembleDebug assembleDebugAndroidTest` **BUILD SUCCESSFUL** (4m03s).

## Slice 7 — Final cleanup

Changes:

- Removed the dedicated aspect-ratio resize control: the `aspectRatio` ImageButton is gone from the player controls layout, its landscape wiring and portrait hide are gone from `PlayerFragment`, and the obsolete `player_aspect` button preference and `player_menu_aspect` menu preference (and their constants) are removed from the settings screens. The now-unused temporary Fit→Fill→Stretch cycle helper was removed with it. Display mode remains permanently available through `More > Playback > Display mode` and the pinch gesture; no other user-configurable control was removed.
- The volume menu preference row label now reads `Stream volume` to match the overlay it opens.
- No new features; regression-only cleanup.

Gates:

- `check assembleDebug assembleDebugAndroidTest assembleRelease` **BUILD SUCCESSFUL** (7m25s). Release artifact: `app-release-unsigned.apk` (signing is a release-engineering step outside this branch).

## Final status against the Definition of Done

- Checkpoint commits: Slice 0 baseline docs, then Slices 1–7, each independently buildable and committed separately (`git log codex/player-ux-tablet-live-discovery`).
- Tablet mini-player never crops (Fit, Slice 1); large-surface feedback/menus are compact and edge/center-localized (Slices 1, 4).
- Gesture ownership: single arbiter, conflict matrix unit-tested (Slice 2); upper-zone seek and lower-zone playback-speed eligibility unchanged; double-tap still toggles chat (claim-gated, with pinch-supersede revert).
- Device volume (right vertical) and stream volume (speaker overlay) are separate and labeled (Slice 4).
- Pinch previews and commits Fit/Fill from every mode; Stretch remains available from the canonical More picker; one state source with deterministic migration (Slice 3).
- Gesture guide concise, context-aware, versioned, reopenable from More and Settings; pinch hint one-time and suppressed by successful use (Slice 5).
- Live profiles and search rows unmistakable (LIVE badge, metadata, Watch live); direct Search-to-player navigation without per-row requests; optional metadata degrades cleanly (Slice 6).
- Dedicated resize button absent from final primary controls; all three modes share one state source (Slices 3, 7).
- Automated gates green including release assembly.
- **Not satisfiable in this environment:** `connectedDebugAndroidTest` — no device/emulator is attached locally (recorded in Slice 0). The gate is *not* waived: it must run (locally or in CI) before the PR leaves draft. No androidTest source set existed at base; the instrumentation portion of the verification plan (view binding, contextual guide rows, display-mode selection, profile/search click targets) still needs authoring alongside that run.
- Manual matrix rows requiring hardware (phone/tablet portrait+landscape, split-screen, live resizing, chat modes, aspect-ratio coverage, backend-specific behavior) remain pending interactive QA; the deterministic cores (arbiter, pinch table, migration, sizing policy, mapping) are unit-tested.
- PR: branch is local-only by request; push and open the draft PR (targeting `master`) when ready, with this ledger attached.
