 # ThystTV Changelog
 
 ## Changes in `feature/pending-improvements` Branch
 
 ### New Asset Images
 
 Added branded images for app store listings and documentation:
 
 | File | Description |
 |------|-------------|
 | `docs/images/appstore.png` | App Store download badge |
 | `docs/images/playstore.png` | Google Play Store download badge |
 | `docs/images/android/` | Android mipmap icons (hdpi, mdpi, xhdpi, xxhdpi, xxxhdpi) |
 | `docs/images/Assets.xcassets/` | iOS app icon assets (AppIcon.appiconset) |
 
 ### Development Configuration
 
 - **AGENTS.md**: Added AI agent instructions with coding guidelines, project layout, and development patterns
 - **.factory/**: Added Factory AI configuration directory with custom droids for code review, performance analysis, refactoring, security auditing, and test writing
 
 ### Code Quality & Testing
 
 #### CI/CD Pipeline Enhancements
 
 - **Detekt Integration**: Added CLI-based Detekt static analysis to `.github/workflows/ci.yml`
 - **Unit Test Job**: Added dedicated test job to CI pipeline with artifact upload
 - **Build Dependencies**: Build now requires lint and test jobs to pass first
 
 #### Unit Tests Added
 
 | Test File | Tests | Description |
 |-----------|-------|-------------|
 | `EmoteCacheTest.kt` | 10 | Tests for emote/badge cache operations, TTL validation, and clear functions |
 | `PlayerGestureHelperTest.kt` | 9 | Tests for gesture calculations, swipe detection, and duration formatting |
 | `StatsViewModelTest.kt` | 6 | Tests for statistics ViewModel (existing) |
 
 #### New Utility Classes
 
 - **PlayerGestureHelper.kt**: Extracted gesture handling utilities (volume, brightness, seek calculations) from PlayerFragment for better testability and reuse
 
 #### Debug Tools
 
 - **LeakCanary 2.14**: Added for automatic memory leak detection in debug builds
 - **Baseline Profile**: Added `baseline-prof.txt` with startup optimization rules for critical paths
 
 ### Security
 
 - **Encrypted Token Storage**: Migrated to `EncryptedSharedPreferences` for secure token storage
 - **Debug Keystore Removed**: Removed debug keystore from repository tracking
 
 ### UI/UX Improvements
 
 - **Popup Chat High Visibility**: Floating/popup chat now defaults to high contrast mode for better readability
 
 ---
 
 ## Changes in `feature/code-quality-improvements` Branch
 
 ### Project Renaming (Xtra → ThystTV)
 
 - **settings.gradle.kts**: Changed `rootProject.name` from "Xtra" to "ThystTV"
 - **strings.xml**: Updated `app_name` and notification channel IDs to use "ThystTV"
 - **App Icons**: Replaced all mipmap icons with new ThystTV branding (purple/blue play button)
 - **Class Renames**:
   - `XtraApp.kt` → `ThystTVApp.kt`
   - `XtraModule.kt` → `ThystTVModule.kt`
   - `XtraGlideModule.kt` → Deleted (Glide removed)
 - **AndroidManifest.xml**: Updated application class reference
 - **User-Agent strings**: Changed from "Xtra/" to "ThystTV/" across all API calls
 - **README.md**: Updated with new icon and branding
 
 ### Performance Fixes
 
 #### runBlocking Removal (Critical ANR Fix)
 
 Replaced blocking coroutine calls that were causing Application Not Responding (ANR) errors:
 
 | File | Change |
 |------|--------|
 | `PlaybackService.kt` | Added `serviceScope`, replaced `runBlocking` with `serviceScope.launch` |
 | `ExoPlayerService.kt` | Added `serviceScope`, replaced `runBlocking` with `serviceScope.launch`, cancel in `onDestroy` |
 | `VideoDownloadWorker.kt` | Replaced `runBlocking` with `coroutineScope` |
 | `StreamDownloadWorker.kt` | Fixed imports, retained `runBlocking` where necessary for network calls |
 
 #### Static Mutable State Refactoring
 
 - **ChatViewModel.kt**: Removed static companion object variables (`savedEmoteSets`, `savedUserEmotes`, `savedGlobalBadges`)
 - **EmoteCache.kt**: Created new singleton cache class injected via Hilt
 - Replaced direct companion object access with `EmoteCache` dependency injection
 
 ### Image Library Consolidation (Glide → Coil)
 
 Removed duplicate image loading library. Project now uses Coil 3 exclusively:
 
 | File | Change |
 |------|--------|
 | `build.gradle.kts` | Removed Glide dependencies |
 | `ThystTVGlideModule.kt` | Deleted |
 | `EmotesAdapter.kt` | Migrated to Coil |
 | `ImageClickedDialog.kt` | Migrated to Coil |
 | `DownloadsAdapter.kt` | Migrated to Coil |
 | `AutoCompleteAdapter.kt` | Migrated to Coil |
 | `ChatAdapterUtils.kt` | Migrated to Coil, removed `loadGlide` function |
 
 ### Syntax/Build Fixes
 
 - **ChatAdapterUtils.kt**: Fixed missing closing brace in `loadImages` function
 - **ChatViewModel.kt**: Fixed companion object closing brace
 - **DownloadsAdapter.kt**: Fixed string interpolation for channel name display
 
 ### Cleanup
 
 - Removed `TODO` folder
 - Removed `AppIcons.zip`, `appstore.png`, `playstore.png` from root
 - Removed duplicate `android/` and `Assets.xcassets/` folders from root
 
 ---
 
 ## Commits
 
 1. `fd7b9aca` - Rename project from Xtra to ThystTV
 2. `69ee08cc` - feat: Add enhanced analytics, code quality improvements, and security fixes
 3. `3e1142b6` - Fix critical runBlocking issues in player services and download workers
 4. `e65664fe` - Refactor: Integrate EmoteCache and migrate Glide to Coil
 5. `40ab1cc5` - Update README with new icon and branding
 6. `92b59d59` - Cleanup and fixes
 7. `951dd358` - Fix syntax errors - build now passes
 8. `5dfd004b` - Security: Add encrypted token storage and remove debug keystore from repo
 9. `611a607b` - Set popup chat high visibility mode as default
 10. `1899ab6b` - Rename project from Amethytw to ThystTV
 11. `cdf69f0b` - Add CI improvements, baseline profiles, and unit tests
 12. `dc97d70c` - Add app store images and update .gitignore
