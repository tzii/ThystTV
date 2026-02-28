# Gesture System Architecture

The ThystTV gesture system provides intuitive touch controls for video playback, including volume, brightness, seeking, and playback speed adjustments.

## Core Components

### 1. `PlayerGestureListener`
*   **Location:** `ui/player/PlayerGestureListener.kt`
*   **Role:** The central state machine. It extends `GestureDetector.SimpleOnGestureListener` to handle raw touch events from the Android `GestureDetector`.
*   **Responsibilities:**
    *   Detects gesture types based on screen zones (Left/Right for Volume/Brightness, Top/Bottom for Seek/Speed).
    *   Manages the gesture lifecycle (Down -> Scroll -> Up/Cancel).
    *   Prevents conflicts with other gestures (e.g., tap controls, minimize gesture).
    *   Applies settings (sensitivity, zone split, haptics).
    *   Updates the UI via `PlayerGestureCallback`.

### 2. `PlayerGestureHelper`
*   **Location:** `ui/player/PlayerGestureHelper.kt`
*   **Role:** A stateless helper class for pure logic and calculations.
*   **Responsibilities:**
    *   Calculating new volume/brightness values.
    *   Formatting time durations strings.
    *   Mapping percentages to icon levels.
    *   Determining swipe directionality (Horizontal vs Vertical).
    *   Checking zone boundaries.

### 3. `PlayerGestureCallback`
*   **Location:** `ui/player/PlayerGestureListener.kt` (Interface)
*   **Role:** Interface implemented by `PlayerFragment` to expose player state and control methods to the listener.
*   **Key Methods:**
    *   `seek(position)`, `setPlaybackSpeed(speed)`
    *   `showController()`, `hideController()`
    *   `setWindowAttributes()` (for brightness)
    *   `getGestureFeedbackView()` (for visual feedback overlay)

### 4. Settings Integration
*   **Preferences:** Defined in `xml/player_preferences.xml`.
*   **Constants:** Keys in `util/C.kt`.
*   **Flow:** `PlayerFragment` reads `SharedPreferences` and passes configuration (`gesturesEnabled`, `sensitivity`, `zoneSplit`, `hapticEnabled`) to the `PlayerGestureListener` constructor.

## State Machine & Logic

The `PlayerGestureListener` uses a set of boolean flags to track the current gesture state during a scroll event sequence (`ACTION_DOWN` -> `ACTION_MOVE`... -> `ACTION_UP`):

*   `isVolume`, `isBrightness`, `isSeek`, `isSpeed`: Mutually exclusive flags set on the first significant scroll movement. Once set, the gesture is "locked" to that mode until `ACTION_UP`.
*   `isScrolling`: General flag indicating a scroll is active. Used to prevent single-tap actions (toggle controls) from firing immediately after a scroll.
*   `hasNotifiedGestureStart`: Ensures `onSwipeGestureStarted()` callback is fired only once per gesture.

### Zone Logic
*   **Vertical Swipes:**
    *   Left 50%: Brightness
    *   Right 50%: Volume
*   **Horizontal Swipes (VoD Only):**
    *   Top X%: Seek (Configurable via `zoneSplit`)
    *   Bottom Y%: Playback Speed

## Adding New Gestures

1.  Add a new state flag in `PlayerGestureListener`.
2.  Define the detection logic in `onScroll` (e.g., a new zone or direction).
3.  Add necessary methods to `PlayerGestureCallback` if the gesture requires new player interactions.
4.  Implement the feedback visualization in `layout_player_gesture_feedback.xml` if needed.

## Testing

*   **`PlayerGestureHelperTest`**: Unit tests for the math and logic (pure functions). Mocks `Context` for `AudioManager`.
*   **Integration**: Currently, `PlayerGestureListener` logic is verified via manual testing due to `MotionEvent` mocking complexities in unit tests.
