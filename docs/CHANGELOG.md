 # Amethytw Changelog
 
 ## Changes in `feature/code-quality-improvements` Branch
 
 ### Project Renaming (Xtra → Amethytw)
 
 - **settings.gradle.kts**: Changed `rootProject.name` from "Xtra" to "Amethytw"
 - **strings.xml**: Updated `app_name` and notification channel IDs to use "Amethytw"
 - **App Icons**: Replaced all mipmap icons with new Amethytw branding (purple/blue play button)
 - **Class Renames**:
   - `XtraApp.kt` → `AmethytwApp.kt`
   - `XtraModule.kt` → `AmethytwModule.kt`
   - `XtraGlideModule.kt` → Deleted (Glide removed)
 - **AndroidManifest.xml**: Updated application class reference
 - **User-Agent strings**: Changed from "Xtra/" to "Amethytw/" across all API calls
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
 | `AmethytwGlideModule.kt` | Deleted |
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
 
 1. `fd7b9aca` - Rename project from Xtra to Amethytw
 2. `69ee08cc` - feat: Add enhanced analytics, code quality improvements, and security fixes
 3. `3e1142b6` - Fix critical runBlocking issues in player services and download workers
 4. `e65664fe` - Refactor: Integrate EmoteCache and migrate Glide to Coil
 5. `40ab1cc5` - Update README with new icon and branding
 6. `92b59d59` - Cleanup and fixes
 7. `951dd358` - Fix syntax errors - build now passes
