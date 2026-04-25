# ThystTV Updater Changelog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the 1.2 updater pass so ThystTV checks ThystTV releases, compares release versions, and shows release notes in-app.

**Architecture:** Add a pure Kotlin `UpdateInfo` model and `UpdateUtils` parser/comparator, then wire the existing `MainViewModel` and `SettingsViewModel` update flows to emit `UpdateInfo` instead of a raw APK URL. Activities keep the existing native dialog pattern but render version/date/release notes and a GitHub action.

**Tech Stack:** Android Kotlin, kotlinx.serialization `JsonObject`, JUnit4 unit tests, existing Material alert dialogs and PackageInstaller flow.

---

### Task 1: Shared Update Parser

**Files:**
- Create: `app/src/main/java/com/github/andreyasadchy/xtra/model/ui/UpdateInfo.kt`
- Create: `app/src/main/java/com/github/andreyasadchy/xtra/util/UpdateUtils.kt`
- Test: `app/src/test/java/com/github/andreyasadchy/xtra/util/UpdateUtilsTest.kt`

- [ ] Write failing tests for version comparison and GitHub release parsing.
- [ ] Run `./gradlew.bat testDebugUnitTest --tests com.github.andreyasadchy.xtra.util.UpdateUtilsTest` and confirm unresolved `UpdateUtils`/`UpdateInfo` failures.
- [ ] Implement `UpdateInfo` and `UpdateUtils`.
- [ ] Re-run the focused test and confirm it passes.

### Task 2: ViewModel UpdateInfo Wiring

**Files:**
- Modify: `app/src/main/java/com/github/andreyasadchy/xtra/ui/main/MainViewModel.kt`
- Modify: `app/src/main/java/com/github/andreyasadchy/xtra/ui/settings/SettingsViewModel.kt`

- [ ] Change `updateUrl` flows to carry `UpdateInfo?`.
- [ ] Replace inline asset timestamp parsing with `UpdateUtils.getAvailableUpdate(response, BuildConfig.VERSION_NAME)`.
- [ ] Keep existing download methods accepting a `String` download URL.
- [ ] Run the focused update tests again, then compile with `./gradlew.bat assembleDebug` once activity call sites are wired.

### Task 3: Activity Dialogs And Defaults

**Files:**
- Modify: `app/src/main/java/com/github/andreyasadchy/xtra/ui/main/MainActivity.kt`
- Modify: `app/src/main/java/com/github/andreyasadchy/xtra/ui/settings/SettingsActivity.kt`
- Modify: `app/src/main/res/xml/update_preferences.xml`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] Replace hardcoded Xtra release API URLs with `UpdateUtils.DEFAULT_RELEASE_API_URL`.
- [ ] Render update dialogs with version/date/release notes plus Download, Later, and View on GitHub.
- [ ] Pass `updateInfo.downloadUrl` to existing download methods.
- [ ] Use `updateInfo.releaseUrl` for the GitHub action and keep existing browser error handling.
- [ ] Add only the strings needed for the richer prompt.

### Task 4: Verification

**Files:**
- No new files expected.

- [ ] Run `./gradlew.bat testDebugUnitTest --tests com.github.andreyasadchy.xtra.util.UpdateUtilsTest`.
- [ ] Run `./gradlew.bat testDebugUnitTest`.
- [ ] Run `./gradlew.bat assembleDebug`.
- [ ] Review `git diff` for accidental broad refactors or companion artifacts.
