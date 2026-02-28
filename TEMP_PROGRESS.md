# ThystTV Feature Progress Tracker

## Current Branch: `feature/gesture-redesign`
**Based on:** `feature/floating-chat-stats-redesign`  
**Target merge:** `refactor-huge-classes`

---

## Completed Work (from previous session)

### 1. Floating Chat Visibility & Theming

| File | Changes |
|------|---------|
| `ChatAdapter.kt` | Force white text + black shadow when High Visibility mode is active |
| `PlayerFragment.kt` | Use theme-aware surface color with adjustable alpha instead of hardcoded `#80000000` |
| `ChatFragment.kt` | Fixed race condition where High Visibility styling persisted in sidebar chat after quick mode switch |

**Key Fix:** Text was previously "unreadable grey" - now explicitly sets color when overlay mode is active.

### 2. Stats Screen Material 3 Redesign

| File | Changes |
|------|---------|
| `fragment_stats.xml` | Replaced hardcoded dark colors with `?attr/colorOnSurface`, added 24dp rounded corners, proper elevation, Material icons |
| `DailyBarChartView.kt` | Theme-aware colors, uses StatsDataHelper for calculations |
| `HourlyHeatmapView.kt` | Theme-aware heatmap colors, extracted logic to helper |
| `CategoryPieChartView.kt` | Theme-aware pie chart, refactored for testability |
| `StatsFragment.kt` | Minor updates for theme compatibility |

**New Drawables Added:**
- `baseline_access_time_24.xml`
- `baseline_emoji_events_24.xml`
- `baseline_local_fire_department_24.xml`
- `baseline_pie_chart_24.xml`
- `baseline_schedule_24.xml`
- `circle_dot.xml`

### 3. Refactoring & Testing

| File | Purpose |
|------|---------|
| `StatsDataHelper.kt` | Pure Kotlin helper with calculation logic (no Android deps) |
| `StatsDataHelperTest.kt` | 17 unit tests covering edge cases |

**StatsDataHelper Functions:**
- `normalizeHeatmapData()` - Normalizes hourly data to 0.0-1.0
- `calculateBarRatios()` - Bar height calculations with min scale
- `interpolateColor()` - ARGB color interpolation
- `calculateDailyAverage()` - Average from daily values
- `formatSecondsToHoursMinutes()` - Time formatting

---

## Completed Work (Current Session: Gesture Redesign)

### 1. Visual Redesign (HUD & Sliders)
- Created `layout_player_gesture_feedback.xml`: Minimalist top-center feedback bar (white icon + progress bar on gradient).
- Created `bg_gradient_top.xml`: Gradient background for visibility.
- Updated `fragment_player.xml`: Replaced old "pill" overlay with new top bar.

### 2. Playback Speed Menu Redesign
- Created `PlayerSpeedDialog.kt` & `dialog_player_speed.xml`:
  - Bottom sheet dialog matching target design.
  - Large current speed display.
  - Fine-tune slider with 0.05x steps.
  - +/- stepper buttons.
  - Preset chips (0.25x to 4.0x).
- Updated `PlayerFragment.kt` to launch new dialog.

### 3. Split-Zone Gestures & Logic
- Refactored `PlayerGestureListener.kt`:
  - **Horizontal Top 50%:** Seek (Rewind/Fast Forward).
  - **Horizontal Bottom 50%:** Playback Speed adjustment (0.05x increments).
  - **Vertical:** Maintained Volume/Brightness with new visual feedback.
- Updated `PlayerGestureCallback` interface to support new logic (`seek`, `setPlaybackSpeed`, `getDuration`).
- Implemented `getDuration()` in `PlayerFragment` and overrides in `ExoPlayerFragment`, `Media3Fragment`, `MediaPlayerFragment`.
- **Fix:** Renamed `getVideoType()` to `getPlayerVideoType()` in interface to avoid platform declaration clash with `PlayerFragment` property.

### 4. Conditional Logic
- **Livestream:** Disables horizontal gestures (Seek/Speed).
- **VoD/Clip/Offline:** Enables all gestures.

---

## Files Modified
```
app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerFragment.kt
app/src/main/java/com/github/andreyasadchy/xtra/ui/player/ExoPlayerFragment.kt
app/src/main/java/com/github/andreyasadchy/xtra/ui/player/Media3Fragment.kt
app/src/main/java/com/github/andreyasadchy/xtra/ui/player/MediaPlayerFragment.kt
app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerGestureListener.kt
app/src/main/java/com/github/andreyasadchy/xtra/ui/player/PlayerSpeedDialog.kt (NEW)
app/src/main/res/layout/fragment_player.xml
app/src/main/res/layout/layout_player_gesture_feedback.xml (NEW)
app/src/main/res/layout/dialog_player_speed.xml (NEW)
app/src/main/res/drawable/bg_gradient_top.xml (NEW)
app/src/main/res/drawable/baseline_add_black_24.xml (NEW)
app/src/main/res/drawable/baseline_remove_black_24.xml (NEW)
app/src/main/res/drawable/baseline_speed_black_24.xml (NEW)
app/src/main/res/values/strings.xml
```

---

## Build Status
- **Build:** `./gradlew assembleDebug` **SUCCESS** (Fixed compilation issues with interface methods and resources)
- **APK:** `app/build/outputs/apk/debug/app-debug.apk` created.

---

## Pending Work

### Manual Verification Needed
- [ ] Test gesture feedback visibility (gradient background, text contrast).
- [ ] Verify split-screen touch zones are accurate.
- [ ] Test speed adjustment smoothness (0.05x increments).
- [ ] Verify seek gesture correctly shows current position / duration.
- [ ] Check Livestream vs VoD gesture enabling/disabling.

---

*Last updated: Session end*
