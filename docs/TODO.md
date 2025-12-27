 # ThystTV - Pending Features and Fixes
 
 This document tracks improvements that were identified but not yet implemented.
 
 ---
 
 ## Completed
 
 ### Security Improvements (Completed)
 
 - [x] **3.1 Encrypted Token Storage** - Migrated to `EncryptedSharedPreferences` (commit `5dfd004b`)
 - [x] **3.2 Remove Debug Keystore** - Removed from repository (commit `5dfd004b`)
 
 ### Code Quality (Completed)
 
 - [x] **Detekt in CI** - Added CLI-based Detekt to GitHub Actions workflow (`.github/workflows/ci.yml`)
 - [x] **LeakCanary** - Added for debug builds (`debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")`)
 - [x] **Baseline Profiles** - Added `baseline-prof.txt` with startup optimization rules
 - [x] **Unit Tests Expanded** - Added `EmoteCacheTest.kt` (10 tests), `PlayerGestureHelperTest.kt` (9 tests)
 - [x] **PlayerGestureHelper** - Extracted gesture handling utilities from PlayerFragment for better testability
 
 ### UI/UX Improvements (Completed)
 
 - [x] **Popup Chat High Visibility** - Floating chat defaults to high contrast mode (commit `611a607b`)
 
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
 
 **Progress**: `PlayerGestureHelper.kt` created as a starting point for PlayerFragment refactoring.
 
 ---
 
 ## Medium Priority
 
 ### 3. Additional Unit Tests
 
 Test coverage should be expanded:
 
 - `ChatViewModelTest.kt` - Needs more coverage (mocking dependencies)
 - `GraphQLRepositoryTest.kt` - Needs implementation  
 - `PlayerViewModelTest.kt` - Needs implementation
 
 **Goal**: Achieve >60% code coverage on critical paths (chat, player, API)
 
 ---
 
 ## Low Priority
 
 ### 4. Analytics Dashboard
 
 Feature branch includes analytics infrastructure that's not wired up:
 
 - `AnalyticsDashboardFragment.kt`
 - `AnalyticsViewModel.kt`
 - Performance metrics collection
 
 ---
 
 ### 5. UiState Pattern
 
 Some ViewModels use UiState sealed classes but not consistently:
 
 - Standardize all ViewModels to use `UiState<T>` pattern
 - Replace multiple LiveData with single state flow
 
 ---
 
 ### 6. Remaining runBlocking Usage
 
 `StreamDownloadWorker.kt` still uses `runBlocking` in some places where it was difficult to refactor. These should be reviewed:
 
 - Lines 437, 537, 694, 844, 1260, 1273, 1310, 1323, 1361, 1374, 1413, 1426
 
 ---
 
 ## Future Enhancements
 
 ### 7. Picture-in-Picture Improvements
 
 - Better PiP controls
 - Maintain chat connection in PiP mode
 
 ### 8. Offline Mode
 
 - Cache followed channels list
 - Show offline indicator on streams
 
 ### 9. Accessibility
 
 - TalkBack support for chat messages
 - Better contrast options
 - Screen reader descriptions for emotes
 
 ---
 
 ## Notes
 
 - Build currently passes with JDK 21 toolchain
 - Coil 3 is the sole image loading library
 - EmoteCache is injected but verify all usages are correct
 - CI workflow includes: lint (Detekt), test (unit tests), build-debug
 - LeakCanary 2.14 automatically detects memory leaks in debug builds
 - Project renamed from Xtra → Amethytw → ThystTV
