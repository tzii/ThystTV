# ThystTV 1.2 Polish Execution Plan

Date: 2026-05-05
Branch: `release/1.2-prep`
Worktree: `worktrees/release-1.2-prep`

This plan covers the 1.2 polish workstreams:

- Changelog and updater settings feature
- New website
- New README with screenshots and banner
- Launcher icon background fix
- Selective sync from crackededed/Xtra
- New playback quality menu

## Current State

The 1.2 branch already contains several preparation commits:

- Updater release notes parsing and download progress work is present.
- Markwon dependencies are already available and now used for update/changelog markdown rendering.
- Changelog/updater settings, the launcher icon background retune, themed playback speed dialog, and the latest Xtra selective-sync ledger pass are implemented.
- A previous custom playback quality dialog attempt was reverted because portrait behavior was not reliable enough; the next implementation should reuse the now-verified themed speed dialog approach.
- `docs/RELEASE_1_2_UPSTREAM_COMMITS.md` tracks the 2026-05-05 upstream Xtra pass through `377bfac1`; a refresh after the update-sheet work confirmed upstream is still at the same head.
- README screenshot references and local docs screenshot assets exist in the working tree, but final screenshots are still waiting on the user's album.

Important working tree note after the 2026-05-05 pass:

- The earlier changelog/updater prototype has been completed into the final settings flow.
- README and screenshot-preview dirtiness should remain separate until the user provides the final screenshot album.

## Implementation Status

| Workstream | Status | Notes |
| --- | --- | --- |
| 1. Changelog and updater settings | Done | Settings now has updater/changelog/about/new-version flows, Markwon markdown rendering, GitHub releases loading, bundled fallback changelog, and a polished update prompt sheet. |
| 2. New website | Waiting | Blocked on screenshot album and final website direction. |
| 3. New README with screenshots and banner | Waiting | Blocked on screenshot album and final banner. |
| 4. Launcher icon background fix | Done | Background retuned to a muted lavender/purple palette that keeps foreground contrast without feeling detached from the logo. |
| 5. Selective Xtra sync | Done for current upstream | Upstream `377bfac1` refreshed again; no new commits since the last pass and no new code accepted beyond already-ported updater progress and unraid message-id work. |
| 6. Playback quality menu | Done | Custom themed quality menu uses speed-dialog-style chips and separates video qualities from audio/chat-only modes. |
| Playback speed menu theming | Done | Speed panel, slider, handle, buttons, and presets now follow the active light/dark app theme. |

## Research Summary

### Local ThystTV Findings

Relevant files:

- `app/src/main/java/com/github/andreyasadchy/xtra/util/UpdateUtils.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/settings/SettingsActivity.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/settings/SettingsViewModel.kt`
- `app/src/main/res/xml/root_preferences.xml`
- `app/src/main/res/navigation/settings_nav_graph.xml`
- `app/src/test/java/com/github/andreyasadchy/xtra/util/UpdateUtilsTest.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerFragment.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerSpeedDialog.kt`
- `app/src/main/res/layout/dialog_player_speed.xml`
- `app/src/main/res/drawable/ic_launcher_background_p4.xml`
- `docs/index.html`
- `README.md`

Useful existing behavior:

- ThystTV already has an updater utility layer and unit tests.
- The updater is already pointed at the ThystTV GitHub releases API.
- Release-note text is currently normalized for display, but that is not enough for the requested feature because markdown headings, lists, links, and formatting need to render properly.
- Playback quality labels are already built in `PlayerFragment.getQualityMap()`. The new quality menu should reuse that source of truth.
- The custom playback speed dialog is the best local reference for dialog sizing, theming, and player-overlay behavior.

### MetroList Updater Research

MetroList was checked from GitHub on 2026-05-05.

Remote refs observed:

- `MetrolistGroup/Metrolist` main: `a951c9eb71ca7e7b8d0324d3ab29fcabe8072552`
- `v13.4.2`: `884c90675e3a9c7088215ec93d9fe70fb68f0174`

Relevant MetroList files:

- `app/src/main/kotlin/com/metrolist/music/utils/Updater.kt`
- `app/src/main/kotlin/com/metrolist/music/ui/screens/settings/SettingsScreen.kt`
- `app/src/main/kotlin/com/metrolist/music/ui/screens/settings/UpdaterSettings.kt`
- `app/src/main/kotlin/com/metrolist/music/ui/screens/settings/ChangelogScreen.kt`
- `app/src/main/kotlin/com/metrolist/music/ui/component/ReleaseNotesCard.kt`
- `changelog.md`

Useful ideas to adapt:

- Settings has separate entries for Updater, Changelog, and About.
- Settings also shows a visible "New version available" row when cached update data says a newer version exists.
- Updater logic models release info, assets, release date, and asset metadata instead of only a raw URL.
- Latest release data is cached so the settings screen can show update status without blocking the UI every time.
- Changelog is a dedicated screen rather than a tiny inline text area.
- Release notes are grouped by release tag and date.
- The updater can select the correct asset for the current app variant and device architecture.

What not to copy directly:

- MetroList is Compose-based. ThystTV settings are XML/preference/navigation based, so the implementation needs to fit ThystTV's existing architecture.
- MetroList's changelog markdown rendering is custom and partial. ThystTV already has Markwon dependencies, so the final implementation should use Markwon instead of writing a custom markdown renderer.
- MetroList's inline updater release notes render as plain text. The user specifically wants markdown to render properly.

### Xtra Upstream Research

The latest observed upstream Xtra remote head on 2026-05-05:

- `crackededed/Xtra` master: `377bfac17ff67f49f58593f79f17850693277aa0`

Current local sync notes are in:

- `docs/RELEASE_1_2_UPSTREAM_COMMITS.md`

Current conclusion:

- Do not merge upstream wholesale.
- Continue selective sync only.
- Keep the ledger updated with each reviewed upstream commit and the reason for accepting, adapting, or deferring it.

## Execution Principles

- Keep app feature work separate from website and README asset work.
- Make selective, reviewable commits rather than one large release-polish commit.
- Prefer existing ThystTV patterns over MetroList's Compose implementation details.
- Keep update checking resilient when GitHub is unavailable or rate limited.
- Render GitHub release markdown with a real markdown renderer.
- Avoid changing playback behavior while redesigning playback quality UI.
- Wait for the user's screenshot album before final website and README screenshot work.

## Workstream 1: Changelog And Updater Settings

### Goal

Add a proper updater and changelog section in settings, similar in spirit to the provided MetroList screenshots:

- Updater row
- Changelog row
- About row remains available
- New version available row when an update is found
- Dedicated release notes/changelog view
- Markdown rendered properly

### Desired UX

Settings should include a clear system/about area with these entries:

- `Open supported links`
- `Updater`
- `Changelog`
- `About`
- `New version available`, shown only when a newer release is known

Updater screen should show:

- Current installed version
- Latest available version, when known
- Manual "Check for updates" action
- Loading state while checking
- Last checked time, if available
- Update available state with release tag, date, and APK size when available
- Download/install action using the existing updater flow
- Browser fallback action if configured or if installation cannot continue
- Error state for offline/API failure
- "View on GitHub" action

Changelog screen should show:

- Current app release notes from bundled fallback markdown when network is unavailable
- GitHub releases when network is available
- Release tag and release date
- Markdown-rendered release body
- Clickable links
- Reasonable loading, empty, and error states

### Implementation Plan

1. Review the partial changelog/updater files currently in the worktree.
2. Decide whether to keep, refactor, or replace:
   - `fragment_changelog_settings.xml`
   - `thysttv_changelog.md`
   - settings navigation changes
   - `SettingsActivity.kt` and `SettingsViewModel.kt` changes
   - `UpdateUtils.kt` changes
3. Define a stable release model:
   - `ReleaseInfo`
   - `ReleaseAsset`
   - release tag
   - semantic version
   - release body markdown
   - release URL
   - published date
   - downloadable assets
4. Extend `UpdateUtils` or add a small updater repository layer to support:
   - latest release lookup
   - all releases lookup for changelog
   - current-version comparison
   - APK asset selection
   - cache of latest update state
   - safe fallback to bundled changelog markdown
5. Use Markwon for release markdown rendering.
6. Add settings screens/fragments using ThystTV's existing settings/navigation style.
7. Add preference keys and strings.
8. Keep startup update checking behavior intact.
9. Add unit tests for parsing, version comparison, asset selection, and fallback behavior.
10. Manually test settings navigation and updater states on an emulator/device.

### Markdown Requirements

The final implementation must not display raw markdown like:

```text
# Major changes
```

It should render as an actual heading/list/link.

Minimum supported markdown:

- Headings
- Paragraphs
- Bullet lists
- Numbered lists
- Bold and italic text
- Inline code
- Links
- GitHub usernames as clickable or at least styled links if practical

### Acceptance Criteria

- Settings contains visible Updater and Changelog entries.
- New update row appears only when a newer GitHub release exists.
- Release notes render markdown properly.
- Offline mode still shows useful bundled changelog content.
- Download progress continues to work.
- Existing update preferences continue to work.
- Unit tests cover parser and version comparison behavior.

### Completion Notes

Implemented on 2026-05-05:

- Added a `ReleaseInfo` model for GitHub release metadata.
- Added a changelog screen backed by GitHub releases and bundled markdown fallback.
- Switched update prompts and changelog content to Markwon rendering.
- Replaced the default update AlertDialog with a bottom update sheet that keeps actions fixed, constrains release-note scrolling, and adds the animated squiggle divider inspired by MetroList.
- Preserved existing download progress and update install/browser fallback behavior.
- Verified with `UpdateUtilsTest` and `assembleDebug`.

## Workstream 2: New Website

### Goal

Replace the current docs site with a more polished ThystTV website after the user provides the screenshot album.

### Dependency

Wait for:

- Screenshot album
- Any new website direction from the user
- Final banner direction if the website and GitHub banner should share one visual system

### Desired Website Structure

First viewport:

- ThystTV identity is immediately visible.
- Real app screenshots are visible in the first viewport or immediately below it.
- Primary actions:
  - Download latest release
  - View GitHub
  - Read changelog

Main sections:

- App preview/screenshots
- Features
- Playback and quality controls
- Update/changelog support
- Download/build section
- Privacy/open-source notes

### Implementation Plan

1. Wait for screenshot album.
2. Choose final image set and crop ratios.
3. Replace placeholder/generated phone imagery with real app screenshots.
4. Update `docs/index.html` and any site assets.
5. Keep the site static and GitHub Pages friendly.
6. Verify desktop and mobile layouts.
7. Confirm links to GitHub releases and README assets.

### Acceptance Criteria

- Site looks complete without relying on placeholder art.
- Screenshots are clear on mobile and desktop.
- The first viewport communicates ThystTV immediately.
- Download and GitHub links work.
- No text overlap or clipped buttons at common viewport widths.

## Workstream 3: New README With Screenshots And Banner

### Goal

Update README with a polished GitHub banner and real screenshots.

### Dependency

Wait for:

- Screenshot album
- Final banner asset or website-derived banner direction

### Desired README Structure

- Banner image
- Short ThystTV description
- Screenshot strip/grid
- Feature list
- Download link
- Build instructions
- Updater/changelog note
- Credits/upstream note

### Implementation Plan

1. Add final banner under `docs/images/` or a dedicated README asset folder.
2. Add selected screenshots under `docs/images/screenshots/`.
3. Update README image references to stable relative paths.
4. Keep README concise enough to scan on GitHub.
5. Make sure image dimensions do not make the README awkward on desktop.
6. Confirm all image references resolve.

### Acceptance Criteria

- README has a strong banner.
- Screenshots are real and readable.
- Screenshot paths work on GitHub.
- README does not duplicate the full website.
- Build/download instructions remain correct.

## Workstream 4: Launcher Icon Background Fix

### Goal

Fix the launcher icon background so the play icon and purple facets do not blend into the background, as shown in the user's screenshot.

### Relevant File

- `app/src/main/res/drawable/ic_launcher_background_p4.xml`

### Design Direction

The current background is too close to parts of the icon. Test two possible directions:

- Darker: deeper near-black/navy/purple background behind the icon
- Lighter: pale high-contrast background that keeps the purple icon silhouette readable

Recommended starting direction:

- Use a darker background with enough contrast against the purple facets and cyan play button.
- Keep the app identity, but separate foreground and background more clearly.

### Implementation Plan

1. Inspect all adaptive icon foreground/background resources.
2. Update the background resource.
3. Regenerate or update any affected launcher preview assets if the repo stores generated images.
4. Check small sizes:
   - 48 px
   - 72 px
   - 108 px
   - 512 px if applicable
5. Check light launcher backgrounds and dark launcher backgrounds.
6. Update README/site icon preview only after final icon is approved.

### Acceptance Criteria

- Icon foreground no longer blends into the background.
- Cyan play button remains prominent.
- Icon works at small launcher sizes.
- Icon preview in README/site matches the app asset.

### Completion Notes

Implemented on 2026-05-05:

- Retuned adaptive launcher background from a blending purple/dark field to a muted lavender/purple field.
- Kept the cyan play button and purple foreground readable.
- Rebuilt debug APK for device review.

## Workstream 5: Selective Xtra Sync

### Goal

Review latest crackededed/Xtra commits and port only changes worth carrying into ThystTV 1.2.

### Current Known Upstream Head

- `377bfac17ff67f49f58593f79f17850693277aa0`

### Existing Ledger

- `docs/RELEASE_1_2_UPSTREAM_COMMITS.md`

### Selection Criteria

Accept or adapt commits when they:

- Fix clear bugs
- Improve compatibility with Twitch/API/player behavior
- Reduce crashes
- Improve build stability
- Are low risk for ThystTV branding and release timing

Defer commits when they:

- Are broad rewrites
- Change architecture without a clear 1.2 benefit
- Conflict with ThystTV branding or updater work
- Touch sensitive playback behavior without enough validation time
- Require unrelated migrations

### Implementation Plan

1. Fetch or inspect upstream Xtra again before making decisions.
2. Compare upstream master against current ThystTV branch.
3. Update the ledger with every reviewed commit.
4. For each useful commit:
   - cherry-pick only if it applies cleanly and matches ThystTV's current code
   - otherwise manually port the minimal useful change
5. Commit each accepted upstream item separately or in small related groups.
6. Run targeted tests after each risky port.
7. Keep deferred commits documented with a reason.

### Likely Current Decision Areas

- Keep the already-ported updater download progress behavior.
- Keep the already-ported unraid message-id fix.
- Consider small Kotlin cleanup commits only if they are low risk.
- Defer broad player/query/exoplayer changes unless a specific bug requires them.
- Defer repo-wide file renames for 1.2.

### Acceptance Criteria

- Ledger is current to upstream `377bfac1` or newer if upstream changes again.
- Every accepted upstream change is traceable.
- No broad upstream merge is used.
- App still builds and relevant unit tests pass.

### Completion Notes

The 2026-05-05 pass confirmed upstream head `377bfac1`. No additional upstream commits were accepted for 1.2 because the remaining candidates are broad rewrites, player-risky dependency/query changes, or low-value cleanup. The ledger documents the decision.

Refresh after update-sheet testing:

- `git fetch upstream` completed successfully.
- `upstream/master` is still `377bfac17ff67f49f58593f79f17850693277aa0`.
- `git cherry -v release/1.2-prep upstream/master` is unchanged from the earlier 2026-05-05 pass.
- No additional Xtra code is recommended for 1.2 at this point.

## Workstream 6: New Playback Quality Menu

### Goal

Replace the current generic radio-button quality dialog with a cleaner custom menu that matches the custom playback speed dialog style while preserving existing quality behavior.

### Relevant Files

- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerFragment.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerSpeedDialog.kt`
- `app/src/main/res/layout/dialog_player_speed.xml`

### UX Direction

The menu should feel related to the playback speed menu:

- Dark/light background depending on app/player theme
- Material You style surfaces
- Cleaner button/list design
- Clear selected state
- No cramped text
- Works in portrait and landscape
- Works while player controls are visible

Suggested menu content:

- `Auto` or source/default option at top
- Available quality rows grouped by resolution/quality
- Optional codec/source suffixes if currently shown by `getQualityMap()`
- Selected option highlighted
- Compact enough for small screens
- Scrollable if many qualities are available

### Implementation Plan

1. Study `PlayerSpeedDialog` sizing and window positioning.
2. Reuse the themed runtime color approach from `PlayerSpeedDialog`.
3. Create a dedicated `PlayerQualityDialog`.
4. Reuse `getQualityMap()` output so labels and selected values remain consistent.
5. Visually separate video qualities from `audio_only` and `chat_only`.
6. Ensure selection calls the exact same playback quality change path as the current dialog.
7. Test portrait, landscape, fullscreen, chat-only, audio-only, and low-height cases.
8. Keep implementation scoped to the dialog and `showQualityDialog()` call path.

### Acceptance Criteria

- Quality menu is custom and visually consistent with speed menu.
- Current quality selection is highlighted.
- Selecting a quality updates playback exactly as before.
- Dialog does not overflow or render off-screen in portrait.
- Dialog does not regress playback speed menu behavior.

### Completion Notes

Implemented after the speed menu theming pass:

- Added a dedicated `PlayerQualityDialog` with themed surfaces, chip rows, and selection state.
- Replaced the generic radio quality picker in `PlayerFragment.showQualityDialog()`.
- Kept the existing quality change path by routing selection through `changeQuality`, `changePlayerMode`, and `setQualityText`.
- Split video quality chips from audio/chat-only chips so utility modes are not mixed into the resolution list.
- Confirmed the dialog is shared by livestream and VOD/player flows because both open the same `PlayerFragment.showQualityDialog()` surface and then delegate selection to the active player engine's `changeQuality` implementation.
- Normalized VOD-provided utility variants such as `Audio Only` so they do not appear as video quality chips or duplicate the canonical audio/chat-only section.
- Kept VOD chat-only out of scope. ThystTV supports VOD chat replay, but `chat_only` remains stream-only because adding it for VOD would be a playback behavior change rather than a menu-only fix.
- Verified with `assembleDebug`.

## Recommended Commit Sequence

1. `docs: add 1.2 polish execution plan`
2. `fix: clean up updater changelog prototype`
3. `feat: add updater and changelog settings screens`
4. `test: cover updater release parsing and asset selection`
5. `fix: improve launcher icon background contrast`
6. `docs: update xtra upstream sync ledger`
7. `fix/refactor: port selected xtra upstream changes`
8. `feat: add custom playback quality menu`
9. `site: refresh thysttv website`
10. `docs: refresh readme banner and screenshots`

Website and README commits should wait until the screenshot album is available.

## Remaining Release Checklist

### Blocked On User Assets

- Website refresh: waiting for screenshot album and final website direction.
- README refresh: waiting for screenshot album and final banner or website-derived banner.

### Release Inputs Needed

- Final 1.2 version name and version code.
- Final 1.2 release date.
- Final release notes text, or approval to derive release notes from the current plan and commit history.

### Final QA Before Release

- Updater prompt in light and dark themes.
- Updater download/install flow.
- Changelog markdown rendering with headings, lists, links, and long release notes.
- Playback quality on livestream and VOD.
- Playback speed menu in light and dark themes.
- Launcher icon in launcher, app info, and small icon contexts.
- Final `./gradlew.bat testDebugUnitTest`.
- Final `./gradlew.bat assembleDebug`.

### Already Completed

- Changelog/updater settings feature.
- Polished update prompt sheet.
- Launcher icon background fix.
- Current selective Xtra sync review.
- Playback speed theming.
- Playback quality chip menu.

## Test Matrix

### Automated

Run before merging app code:

```powershell
./gradlew.bat testDebugUnitTest
./gradlew.bat assembleDebug
```

Run targeted tests while iterating:

```powershell
./gradlew.bat testDebugUnitTest --tests "*UpdateUtilsTest"
```

### Manual Android QA

Updater/changelog:

- Fresh install
- Upgrade install
- No network
- GitHub API success
- GitHub API failure
- No update available
- Update available
- Release with no matching APK asset
- Browser fallback enabled
- In-app download enabled
- Markdown headings, lists, links, and inline formatting

Icon:

- Launcher preview on light wallpaper
- Launcher preview on dark wallpaper
- Small icon size
- App info icon
- Recents/task icon if applicable

Playback quality:

- Portrait video
- Landscape video
- Fullscreen controls
- Minimized player
- Audio-only stream
- Multiple quality options
- Single quality option
- Quality selection persists or resets according to existing behavior

Website/README:

- Desktop width
- Mobile width
- GitHub README rendering
- Image links
- Download links

## Risks And Mitigations

Risk: MetroList code does not map directly to ThystTV.

Mitigation: Copy the product pattern, not the Compose implementation.

Risk: Markdown rendering regresses into raw markdown.

Mitigation: Use Markwon in the changelog UI and add manual QA with real GitHub release notes.

Risk: GitHub API rate limits or offline state make settings feel broken.

Mitigation: Cache the latest known result and ship bundled fallback changelog markdown.

Risk: Playback quality popup regresses portrait behavior again.

Mitigation: Treat speed dialog as reference, test low-height screens, and keep implementation scoped.

Risk: Upstream sync pulls in unrelated changes.

Mitigation: Continue ledger-based selective sync only.

Risk: Website/README asset work blocks app release.

Mitigation: Keep app feature commits independent and wait for the screenshot album before final docs/site asset commits.

## Open Inputs Needed

- Screenshot album for README and website.
- Preferred icon background direction if the darker proposal is not desired.
- Final website/banner style direction if it differs from the app's current visual identity.
- Final target 1.2 version name/code and release date.
- Whether the playback quality menu must ship in 1.2 or can land immediately after 1.2.

## Suggested Next Step

Start with the changelog/updater feature because it affects app architecture and settings navigation. After that, fix the icon background and complete the selective Xtra sync ledger. Playback quality can then be built and tested independently. Website and README should wait for the screenshot album.
