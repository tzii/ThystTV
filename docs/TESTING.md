# Testing Guide

## Minimum local checks before merge

```bash
./gradlew assembleDebug
./gradlew test
```

For release-related work also run:

```bash
./gradlew assembleRelease
```

For UI/resources/player work, also run `./gradlew lintDebug`.

Android resource/layout regression tests use Robolectric with Android resources enabled.
They run on API 28 with Conscrypt disabled (these tests do not perform networking; its
JNI provider is unavailable on the Windows test host). `PlayerPopupContentTest` checks
fixed headers, a single scroll owner, bottom-row reachability, host parentage, mixed
codec/Auto labels, and one accessible 48dp Quality gear through compact/wide/compact
layout measurements at enlarged font sizes. `StatsLayoutTest` checks compact card text,
range-button height, number/unit wrapping, and horizontal reachability at 100%/200%
font size. Native graphics renders generate sample-data previews under
`app/build/ui-previews/`, including More before/after scrolling and Quality/Stats at
both font sizes. Tests explicitly resolve layout direction for detached preview roots.
These check Android layout/rendering, not real player playback, device window placement,
or animation smoothness. Human checks below remain required.

## Manual regression checklist

### September 27 TV remote navigation

- `TvNavigationFocusTest` attaches the real activity layout and Material bottom
  navigation to an Android window (substituting the NavHost to avoid network/Hilt
  startup). It checks D-pad traversal/CENTER, selection versus focus, RTL and
  reordered/hidden items, clipped competing feed targets, stale page targets,
  empty lists, header fallback, native tab activation without collapsing focused
  headers, usable content beneath populated profile headers, and light/dark focus
  rendering. It does not exercise the activity's navigation back stack.
- `PlayerTvRemoteTest` uses actual player views and remote key events with player
  attachment/backend calls mocked. It checks first-press reveal, subsequent
  activation, hidden live play/pause fallback, timeout refresh, Back, TV gating,
  full close delegation, mini-player activation, popup focus boundaries and
  persistence after native Slider D-pad changes, including matching 1.05x popup
  and toolbar labels. It does not decode video.
- `StatsTvFocusTest` checks remote focus through static cards and long nested
  category/favorite rows, actual scrolling and unchanged phone focus behavior.
- `ChannelPagerHeaderTest` exercises the production tab-header transition with
  a populated Material channel layout. Native Videos/Chat/Clips selection keeps
  TV toolbar/tabs visible and reserves content space, while phone scroll flags
  and Chat collapse are preserved. It also checks initially obscured profile
  focus and a focus change into content before the deferred correction runs.
- Native previews are generated under `app/build/ui-previews/tv-*`.

Use an Android TV AVD with only D-pad/Center/Back for the integration pass: launch,
bottom destinations, current-page tabs/content, long lists, empty pages, nested
Back, search/settings/dialogs, playback controls/popups and mini-player restore.
Physical-TV remote implementations and hardware codecs are a separate validation
limit; the maintainer has no TV available for this update.

September 27 validation: `assembleDebug`, `test` and `lintDebug` passed, with **558 tests,
no failures/errors/skips, and lint 0 errors / 343 warnings**, including 38 new TV
and view-lifecycle test cases. Native previews were visually checked in both
themes. On the Windows host, builds and the emulator were run sequentially to
avoid memory pressure. A full-suite retry redirected Java temporary extraction
to a roomy local drive after Robolectric ran out of disk space copying fonts;
no test or app behavior was changed to bypass that environment failure.

An isolated Android TV API 36 / Android 16 x86_64 emulator at 1920x1080 was
operated with D-pad, CENTER and Back (ADB text input supplied the search query).
Following/Saved tabs remained fully visible after selection; Stats ranges and
scrolled category rows were reachable. Search results and nested search tabs
also remained reachable. The final APK opened a populated channel with visible
content focus; Videos/Chat/Clips selection retained full-height focused tabs and
compact header geometry, and nested Back returned to search. Anonymous live video/chat and a growing
VoD decoded; remote pause, Quality selection, Speed adjustment, More, popup
Back, minimize/restore and Close worked. Closing removed the Media3 fragment
from the activity. No AndroidRuntime fatal entries were recorded in those
passes. This headless emulator had audio disabled, so it cannot establish
audible overlap, OEM codec/remote behavior or physical-TV compatibility.
Authenticated flows, touch gestures/floating chat, rotation and PiP/background
remain separate regression checks; the maintainer's acceptance of the earlier
debug APK is not a scenario-by-scenario certification of this TV update.

### Following view cleanup

Retained views can survive a navigation change even when their fragment remains
legitimately on the back stack. Paging scroll observers and ViewPager/TabLayout
connections must be detached with the fragment's view, then recreated with its
next view. `PagedListFragmentLifecycleTest` checks initial/later insertions and
actual retained-fragment view recreation before and after the first insertion.
`FollowPagerFragmentLifecycleTest` checks real ViewPager2/TabLayoutMediator and
FragmentStateAdapter lifecycle observers over two view creations. These tests
verify the identified retaining paths; a fresh device heap is needed to verify
the reported leak groups no longer occur in the rebuilt app.

Check Following tabs, navigate away and back repeatedly, and confirm
list updates, scroll-to-top and the selected tab still work. Raw heap dumps and
private LeakCanary output must remain outside tracked files.

### September 25 large-screen and upstream update

- `PlayerGestureFeedbackRenderTest` measures actual inflated views on tablet,
  short/resized, compact and side-chat-sized surfaces, including transitions back
  to compact feedback. Native PNGs under `app/build/ui-previews/gesture-*` compare
  new feedback with a reproduction of the prior dimensions (not an old-app run).
- `BookmarkMetadataUpdateTest` exercises single/batch ViewModel refreshes and
  checks that repository updates retain persisted identity and old adapter snapshots.
- `PlayerResumeLookupTest` covers processing/missing/cached artwork and stale
  duration, plus final metadata, explicit offsets and downloaded segment policy.
- `LocalFollowFallbackTest` checks player/channel local follow resolution without
  tokens and preservation of authenticated Helix fallback.
- `HelixChatMetadataTest` covers actual intercepted follower/followed endpoint
  requests and response identity fields, plus shared chat-color parsing fixtures.
- `MessageClickedViewModelTest` covers moderator follow-date fallback, denied
  permission, absent/mismatched users, no token and cancellation.
- `LocalizedStringsTest` checks representative packaged formatting, Japanese
  plurals, Spanish quoting and Russian branding after the compatible locale intake.

Transport fixtures do not exercise platform HttpEngine/legacy Cronet or real
moderator permissions; native renders do not certify playback or device gestures.
The September 25 pass in `MANUAL_QA.md` remains required.

Final Windows validation on September 25 (reports rechecked September 27):
`assembleDebug test lintDebug` passed, with **520 tests, no failures/errors/skips,
and lint 0 errors / 342 existing warnings**. Native previews were visually checked
for tablet brightness/volume, compact, narrow and short player surfaces. Independent
regression review found no actionable issues. On September 27 the maintainer
accepted the delivered build; no exact device/backend/scenario breakdown was supplied.

`PlayerPinchChatTest` covers pinch takeover from hidden/sidebar/floating chat,
floating-chat disabled, unchanged ordinary pinches, normal double-tap transitions,
rejected duplicate claims and preservation of an unset chat preference. It invokes
the real listener and fragment chat/pinch methods with Android views and
preferences; player attachment and network chat are mocked. It does not simulate
the platform's double-tap timing or actual playback.

### Release resource encoding

- `LocalizedStringsTest` loads packaged resources in all 13 affected locales and
  checks representative accented/non-Latin strings. The 1.3 restoration was verified
  against every corresponding v1.2.1 resource value while preserving new Close labels.
- Use UTF-8 explicitly when scripts read/write source text on Windows. The older
  PowerShell/Python default Windows-1252 decoding can silently corrupt valid UTF-8.

### Launcher artwork

- `LauncherIconTest` checks packaged adaptive/round layers, cyan foreground color,
  opaque background, aligned themed silhouette/play counter and the adaptive safe
  circle. A native normal/light-themed/dark-themed preview is saved in
  `app/build/ui-previews/launcher-gem-native.png`. Legacy PNGs at all five densities
  and the drawable XML were checked against the supplied archive bytes.
- Device launcher masks, actual Android 13+ themed-icon selection, launcher cache
  refresh and pre-Oreo launcher display remain human QA; simulated themed rendering
  on API 28 does not exercise the Android 13 launcher implementation.

### Updater presentation and recovery

- `UpdateUiTest`: constrained portrait/landscape layouts at 100%/200% font size,
  fixed 48dp-or-larger actions, scrollable notes, missing metadata, Markdown heading
  deduplication, date validation, known/unknown download sizes, actual dialog action
  dispatch and short-window download-body scrolling. Native sample-data
  previews cover light/dark prompt and download layouts in `app/build/ui-previews/`.
- `UpdateAttemptTest`: successful preparation, network/installer errors, cancellation
  propagation and prevention of work in an already-cancelled job. These are policy/
  layout tests, not live downloads or OS installer tests.
- See the updater pass in `MANUAL_QA.md` for real permission, network, lifecycle and
  signed-upgrade checks. Local unsigned release assembly is not signed-RC approval.
### Selective upstream correctness ports (September 2026)

- `ChatReplayTimingTest`: positive sub-millisecond waits at high speeds still
  suspend for at least 1ms; normal timing, invalid speed fallback and cancellation.
  Both network and downloaded-chat replay use this policy.
- `FollowedChannelsDataSourceTest`: actual first/append paging loads using mocked
  repositories, returned account-follow rows, last-page termination, local/account
  merging and integrity failures.
- `TwitchEmoteSuggestionsTest`: native Twitch suggestions without third-party
  emotes, image metadata/current-channel priority, duplicate/blank names, snapshot
  replacement, and preserved third-party/chatter entries. Cached/fresh/emote-set
  ViewModel paths use the same publication helper; device account/reload QA remains.
- `AutoCompleteSnapshotTest`: real adapter filtering copies its source while holding
  the same lock as emote/chatter producers. Guarded lists catch unlocked reads
  deterministically, and subsequent filters see replaced Twitch suggestions.
- `SocialLinkLabelTest`: parsed destination host, www/subdomains, user-info, and
  absent/relative/opaque URLs. Labels do not establish destination trustworthiness.

### Controls / resume / headings follow-up

- `MediaSeekFallbackTest`: unhandled previous/next dispatch, already-handled events,
  key-up/cancelled/unrelated/malformed inputs and unavailable seek commands.
- `VideoResumePositionTest` / `PlayerResumeLookupTest`: exact completion boundary,
  unknown durations, Long millisecond conversion, explicit offsets bypassing the
  repository and downloaded-segment duration independent of its source offset.
- `HeadingFixPluginTest`: actual CommonMark parsing of compact/ordinary/setext
  headings, Unicode, nested containers, links, code and non-heading hashes.

### Player basics

- `PlayerSystemUiListenerTest`: real View callback dispatch, view-lifecycle destroy,
  idempotent portrait-style detach, repeated landscape registration, stale callback
  invalidation, decor replacement and old/new player ownership. These deterministic
  cleanup checks do not replace repeated rotation/stream-close LeakCanary device QA.
- live stream opens
- VoD opens
- stream switching works
- minimize / restore works
- no obvious visual glitches during transition

### Speed control
- current speed is shown correctly
- speed changes update immediately
- state survives orientation and minimize/restore if relevant

### Gestures
- brightness still works
- volume still works
- VoD seek is responsive
- large drags allow fast movement through long VoDs
- gesture conflicts remain acceptable

### Floating chat
- overlay opens correctly
- drag / resize persistence works
- no broken empty-sidebar/floating-chat state

### Layouts
- portrait phone
- landscape phone
- at least one wide/tablet profile
- split-screen if layout code changed
- open Quality, Speed and More on a portrait live stream and VoD: card can extend over
  chat, title/close/rounded edges stay fixed while only options scroll
- repeat with large fonts, RTL, top/bottom controls, side chat, and floating chat; every
  option must be reachable and no popup may inherit the previous popup's position
- close via header/back/outside; rotate, minimize/restore, close/reopen, and enter/leave
  PiP/background while open; no orphaned overlay or blocked chat touches after dismissal

### Stats
- stats screen opens
- rotation while already on stats screen behaves correctly
- no obviously broken spacing in compact / wide layouts
- switch ranges rapidly: bar motion should settle without whole-card blinking
- leave/reopen Stats during animation and verify the chart shows final values
- repeat with system animator duration scale disabled
- check the compact summary metrics and streak row with long values and 200% text
- range selector stays compact at normal text size; enlarged/localized labels remain
  reachable by horizontal scrolling rather than clipping
- empty, minute-long, and multi-hour data use readable scales; compare bar values with
  totals, and check labels in portrait, landscape, and tablet layouts


## Updater and release checks

```bash
python3 scripts/check-updater-contracts.py
bash scripts/check-updater-core.sh
node --test docs/site.test.js .github/workflows/release.test.mjs scripts/release/*.test.mjs
./gradlew test lintDebug assembleDebug assembleRelease
```

The standalone Kotlin runner uses kotlinc and a compatible coroutines JVM jar;
set KOTLINC_BIN and COROUTINES_JAR when needed. The Android Gradle suite runs
these same production-core cases with the Android adapter and resource tests.

On Windows, use Git Bash with Java 21 and the Android SDK available. Set
THYSTTV_GIT_BASH for a nonstandard installation. All native Windows verifier
cases must run on Windows; a shell script named .bat is not native validation.

`publish-release.test.mjs` exercises the maintainer publication command with isolated
files and command-boundary fixtures: read-only operation, exact-byte publication, existing
release verification, redacted policy data, tampered artifacts and failed/partial releases.
These fixtures do not contact GitHub or sign an APK. When changing this command, also
run its read-only mode against an existing release whose signed-RC artifact is retained:
`node scripts/release/publish-release.mjs <tag>`. This checks actual GitHub policy data,
the Android SDK verifier and downloaded public bytes without creating a release. See
`RELEASE_PROCESS.md` for prerequisites and evidence handling.

Use the existing developer debug key for upgrades on a development device. An
unsigned release assembly is not an official candidate. See
[the 1.3 review guide](RELEASE_1_3_REVIEW.md) for validation and remaining checks,
and [RELEASE_PROCESS.md](RELEASE_PROCESS.md) for signed-candidate requirements.
