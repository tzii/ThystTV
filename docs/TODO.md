 # Amethytw - Pending Features and Fixes
 
 This document tracks improvements that were identified but not yet implemented.
 
 ---
 
 ## High Priority
 
 ### 1. ChatAdapter Message Caching
 
 **Problem**: Heavy processing in `ChatAdapter.onBindViewHolder` (lines 116-132) happens on every scroll, causing scroll jank.
 
 **Proposed Fix**: Implement a caching mechanism for prepared `SpannableStringBuilder` messages.
 
 ```kotlin
 // Example approach
 private val messageCache = LruCache<String, SpannableStringBuilder>(100)
 
 override fun onBindViewHolder(holder: ViewHolder, position: Int) {
     val message = getItem(position)
     val cached = messageCache.get(message.id)
     if (cached != null) {
         holder.bind(cached)
     } else {
         val prepared = prepareMessage(message)
         messageCache.put(message.id, prepared)
         holder.bind(prepared)
     }
 }
 ```
 
 **Files**: `app/src/main/java/com/github/andreyasadchy/xtra/ui/chat/ChatAdapter.kt`
 
 ---
 
 ### 2. Split Monolithic Classes
 
 Large classes that should be refactored for maintainability:
 
 | Class | Lines | Suggested Split |
 |-------|-------|-----------------|
 | `PlayerFragment.kt` | ~2952 | `PlayerGestureHandler`, `FloatingChatController`, `PlayerUIController`, `StreamQualityManager` |
 | `ChatViewModel.kt` | ~2713 | `EmoteManager`, `BadgeManager`, `ChatConnectionManager`, `MessageParser` |
 | `GraphQLRepository.kt` | ~1681 | Split by domain: `StreamRepository`, `UserRepository`, `GameRepository`, `ClipRepository` |
 
 ---
 
 ### 3. Security Improvements
 
 #### 3.1 Encrypted Token Storage
 
 **Problem**: User tokens are stored in plain SharedPreferences.
 
 **Proposed Fix**: Migrate to `EncryptedSharedPreferences` from AndroidX Security.
 
 ```kotlin
 val masterKey = MasterKey.Builder(context)
     .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
     .build()
 
 val securePrefs = EncryptedSharedPreferences.create(
     context,
     "secure_prefs",
     masterKey,
     EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
     EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
 )
 ```
 
 **Files**: Token storage locations throughout the app
 
 #### 3.2 Remove Debug Keystore
 
 **Problem**: `debug-keystore.jks` with password "123456" is committed to the repository.
 
 **Action**: Remove from git history or at minimum from tracked files.
 
 ```bash
 git rm --cached debug-keystore.jks
 echo "debug-keystore.jks" >> .gitignore
 ```
 
 ---
 
 ## Medium Priority
 
 ### 4. Baseline Profiles
 
 The feature branch includes Baseline Profile infrastructure but it's not fully integrated:
 
 - `BaselineProfileGenerator.kt` exists but needs proper test setup
 - Should be run on CI to generate profiles for release builds
 
 **Files**: `app/src/androidTest/java/.../BaselineProfileGenerator.kt`
 
 ---
 
 ### 5. Unit Tests
 
 Test files exist but need expansion:
 
 - `EmoteCacheTest.kt` - Basic tests for EmoteCache
 - `ChatViewModelTest.kt` - Needs more coverage
 - `GraphQLRepositoryTest.kt` - Needs implementation
 
 **Goal**: Achieve >60% code coverage on critical paths (chat, player, API)
 
 ---
 
 ### 6. Detekt Integration
 
 `detekt.yml` configuration exists but Detekt is not running in CI.
 
 **Action**: Add Detekt to GitHub Actions workflow for static analysis.
 
 ---
 
 ### 7. LeakCanary
 
 LeakCanary dependency may be added for debug builds to detect memory leaks.
 
 ```kotlin
 debugImplementation("com.squareup.leakcanary:leakcanary-android:2.12")
 ```
 
 ---
 
 ## Low Priority
 
 ### 8. Analytics Dashboard
 
 Feature branch includes analytics infrastructure that's not wired up:
 
 - `AnalyticsDashboardFragment.kt`
 - `AnalyticsViewModel.kt`
 - Performance metrics collection
 
 ---
 
 ### 9. UiState Pattern
 
 Some ViewModels use UiState sealed classes but not consistently:
 
 - Standardize all ViewModels to use `UiState<T>` pattern
 - Replace multiple LiveData with single state flow
 
 ---
 
 ### 10. Remaining runBlocking Usage
 
 `StreamDownloadWorker.kt` still uses `runBlocking` in some places where it was difficult to refactor. These should be reviewed:
 
 - Lines 437, 537, 694, 844, 1260, 1273, 1310, 1323, 1361, 1374, 1413, 1426
 
 ---
 
 ## Notes
 
 - Build currently passes with JDK 21 toolchain
 - Coil 3 is the sole image loading library
 - EmoteCache is injected but verify all usages are correct
