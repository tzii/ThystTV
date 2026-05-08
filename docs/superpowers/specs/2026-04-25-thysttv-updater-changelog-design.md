# ThystTV Updater Changelog Design

Date: 2026-04-25
Branch: `release/1.2-prep`

## Goal

Prepare the 1.2 updater experience so ThystTV checks ThystTV releases, compares versions reliably, and presents release notes in-app before a user downloads an APK.

The 1.2 scope is a practical GitHub-release updater prompt with a richer changelog body. A dedicated MetroList-style changelog screen can come later after the release-critical updater path is solid.

## Current State

- Update defaults still point at `https://api.github.com/repos/crackededed/xtra/releases/tags/latest`.
- `MainViewModel` and `SettingsViewModel` each contain similar release JSON parsing and APK download logic.
- Update detection compares the APK asset `updated_at` timestamp to `UPDATE_LAST_CHECKED`, so it can miss version semantics and depends on when the user last checked.
- The emitted state is only a download URL, so activities can only show a generic update message.
- `downloadUpdate()` downloads the full APK into memory before handing it to `PackageInstaller`; true download progress would require a separate streaming pass.

## Product Behavior

When an update is found, ThystTV should show a native update prompt with:

- Title: update available.
- Version chip or subtitle using the GitHub release `tag_name`, such as `v1.2.0`.
- Release date from `published_at` when available.
- Release note summary from the GitHub release `body`.
- Actions: `Download`, `Later`, and `View on GitHub`.

The prompt should feel closer to the MetroList changelog reference than the current generic dialog: readable release text, clear version/date context, and an obvious GitHub escape hatch. It should still use existing Android/Material patterns rather than introducing a large custom visual surface before 1.2.

Manual checks from Settings should:

- Show the same rich prompt when an update exists.
- Show the existing "No updates found" toast when the latest release is not newer than the installed app.
- Avoid updating `UPDATE_LAST_CHECKED` when the user simply views the prompt and chooses `View on GitHub`; update the timestamp only after choosing `Later`, opening the browser/download action, or reaching the install handoff.

Automatic checks from startup should:

- Use the same release parsing and version comparison as manual checks.
- Respect `UPDATE_CHECK_ENABLED` and `UPDATE_CHECK_FREQUENCY`.
- Not show a prompt if the latest release is equal to or older than `BuildConfig.VERSION_NAME`.

## Technical Design

Add a small shared updater model/helper under a common package, for example `com.github.andreyasadchy.xtra.model.ui.UpdateInfo` and `com.github.andreyasadchy.xtra.util.UpdateUtils`.

`UpdateInfo` should contain:

- `versionName: String`
- `tagName: String`
- `publishedAt: String?`
- `releaseNotes: String?`
- `releaseUrl: String?`
- `downloadUrl: String`

`UpdateUtils` should provide:

- `defaultReleaseApiUrl`, pointing at `https://api.github.com/repos/tzii/ThystTV/releases/latest`.
- GitHub release parsing from `JsonObject` to `UpdateInfo?`.
- Version comparison that normalizes leading `v`, strips suffixes when safe, and compares numeric version segments.
- A helper that returns `UpdateInfo` only when the release version is newer than `BuildConfig.VERSION_NAME`.

Keep networking in the existing view models for now to minimize 1.2 risk, but change both `MainViewModel.checkUpdates()` and `SettingsViewModel.checkUpdates()` to emit `UpdateInfo?` instead of `String?`. If duplication becomes awkward, extract a shared suspend fetch function only after the smaller parser change is stable.

Update `MainActivity` and `SettingsActivity` collectors to render the richer prompt. A native `MaterialAlertDialogBuilder` is acceptable for 1.2 if it includes a scrollable release-notes message and the GitHub neutral action. A custom bottom sheet is explicitly out of scope for this pass.

## Out Of Scope

- A dedicated full-screen changelog page.
- Markdown-rich rendering of release notes.
- Streaming APK download progress.
- Broad upstream Xtra sync.
- Changing installer behavior beyond preserving the existing PackageInstaller flow.

## Testing

Add focused unit tests for the shared parsing and comparison logic:

- `v1.2.0` is newer than `1.1.6`.
- `1.1.6` is not newer than `1.1.6`.
- `v1.1.5` is not newer than `1.1.6`.
- A release without an APK asset returns no update.
- A release with a non-APK asset plus an APK asset selects the APK asset.
- Release body, date, tag, HTML URL, and download URL are preserved.

Run:

- `./gradlew.bat testDebugUnitTest`
- `./gradlew.bat assembleDebug`

Manual verification:

- Change the update URL to a test/latest endpoint with a newer release and verify the prompt shows version, date, release notes, Download, Later, and View on GitHub.
- Verify `View on GitHub` opens the release page when a browser is available.
- Verify Download still reaches the Android install handoff.
- Verify no prompt appears when the latest release is equal to the installed version.
