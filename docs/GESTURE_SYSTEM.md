# Gesture System Architecture

The ThystTV gesture system provides intuitive touch controls for video playback, including volume, brightness, seeking, and playback speed adjustments.

## Core Components

### 1. `PlayerGestureListener`
*   **Location:** `ui/player/PlayerGestureListener.kt`
*   **Role:** The central state machine. It extends `GestureDetector.SimpleOnGestureListener` to handle raw touch events from the Android `GestureDetector`.
*   **Responsibilities:**
    *   Detects gesture types based on screen zones (Left/Right for Volume/Brightness, Top/Bottom for Speed/Seek).
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

*   `isVolume`, `isBrightness`, `isSeek`, `isSpeed`: Mutually exclusive flags. Once set, the gesture is "locked" to that mode until `ACTION_UP`. The direction is measured from touch-down (`PlayerScrubPolicy.classify`): vertical locks as soon as it dominates, while seek and speed need 24dp of sideways travel within about 27° of horizontal. A drag that has not reached either stays undecided and claims nothing.
*   `isScrolling`: General flag indicating a scroll is active. Used to prevent single-tap actions (toggle controls) from firing immediately after a scroll.
*   `hasNotifiedGestureStart`: Ensures `onSwipeGestureStarted()` callback is fired only once per gesture.

### Zone Logic
*   **Vertical Swipes:**
    *   Left 50%: Brightness
    *   Right 50%: Volume
*   **Horizontal Swipes (seekable media only):**
    *   Top X%: Playback Speed (Configurable via `zoneSplit`)
    *   Bottom Y%: Seek
    *   Zone selection is pure logic in `PlayerGestureZonePolicy`; a start position exactly on the split line belongs to the lower (seek) zone.
    *   Both measure travel from a 10dp start detent at the lock point: dragging back into it returns exactly to the starting position or speed. Seek issues a player seek only when its target changes. See "Side gesture feedback" and "Horizontal scrub gestures" in `docs/PLAYER.md` for the indicators and the haptic vocabulary.

### Pinch Display Modes
*   Pinch feedback uses the same indicator as seek and speed: the top slider in the Sliders style, the centered number in the Number in the center style. For Fit/Fill the start dot sits in the middle and the handle follows `PlayerGestureFeedbackState.pinchLevel`: toward the right end while arming or elastically pushing Fill, toward the left end for Fit. Both useful and dead-direction motion therefore remain visible on the slider. Its value is the mode the pinch points to.
*   Pinch ownership begins after a 2% cumulative relative-scale deadzone. There is no absolute pixel-span gate, so recognition does not vary with the initial distance between fingers. Two stationary fingers still claim nothing.
*   Fit/Fill previews stay in the committed renderer's coordinate space (`PlayerDisplayModePreviewer`): Fit scales from 1 to the Fill-to-Fit ratio; Fill scales from 1 to its inverse. Controller progress is logarithmic over reciprocal arm thresholds (1.15 outward / about 0.87 inward), and view scale is interpolated geometrically, so multiplicative finger motion produces perceptually even zoom. This avoids changing `AspectRatioFrameLayout.resizeMode` while fingers are down. An armed commit retains the equivalent preview scale until the target renderer's next layout and then normalizes to unit scale. Neutral releases use a 240ms emphasized deceleration to unit scale in the unchanged renderer. Stretch and pre-N devices step only when armed.
*   Dead-direction pinches emit `PinchDisplayModeController.Event.Elastic` with a normalized 0..1 deformation. A quadratic ease-out resistance curve is responsive near neutral and reaches zero velocity at 12% span deviation; it renders as a restrained maximum 5% view-scale deformation relative to the committed renderer and settles back on release. The controller's `update()` takes cumulative span scale relative to pinch start (1.0 = neutral), never an incremental detector factor.
*   Establishment rule: once a gesture has emitted Preview or Elastic, crossing neutral emits zero-deformation Elastic instead of `NoPreview`, so no event finalizes the surface between manipulation start and release/cancel; `NoPreview` (and its canonical finalize) only occurs before a direction is established.
*   Free zoom (`PinchZoomController`): on API 24+, from Fit or Fill, when Fill is at least about 1.12x Fit (`PinchZoomController.supports`), pinch scales the committed renderer continuously about its center instead of arming a mode. Zoom is relative to Fit (start zoom times cumulative span scale, so the picture follows the fingers). Within 5% of either end it snaps to Fit or Fill with a haptic tick on entry; past an end it uses the same eased 5% elastic resistance. Releasing between the ends keeps that zoom as mode `fit` plus `C.PLAYER_DISPLAY_ZOOM_LANDSCAPE`, so builds without free zoom read plain Fit. The indicator shows Fit, Fill or the zoom percent ("125%"; the accessibility text says "Zoom 125%"); the slider places the handle between Fit at the left end and Fill at the right, with the start dot where the pinch began. Its size never depends on the label, so it never changes width mid-pinch. Reaching Fill flashes a thin white outline around the player area (`pinchFillEdge`), skipped when animations are off. On release the surface settles to the kept geometry in the gesture's renderer, then `finalizePinchSurface()` swaps renderer behind that equivalent scale if Fit and Fill traded places. Stretch, pre-N devices and shapes close to the video's keep the toggle below.
*   The kept zoom crops the same share of the picture on any screen and is clamped to the current Fill, so a foldable can show a partial zoom on its inner screen and Fill on its outer screen. It is re-applied when the player area or video aspect changes; portrait, the mini-player, PiP and shapes without a free band draw plain Fit. Selecting a mode in More clears it.
*   The pinch ends when the first of the two tracked fingers lifts. Restore/commit starts immediately, while the remaining finger stays suppressed until the final pointer release so it cannot resume an old one-finger gesture.
*   Canonical surface geometry is owned by `PlayerFragment.finalizePinchSurface()`, which cancels any settle and applies the committed mode's resize mode and scale (unit, or the kept zoom with Fit). When the resize mode changes it keeps the current, equivalent scale until the new renderer lays out. New pinch starts, `selectDisplayMode`, minimize/restore visual state, and `onDestroyView` all route through it so a partially transformed surface cannot survive.

## Adding New Gestures

1.  Add a new state flag in `PlayerGestureListener`.
2.  Define the detection logic in `onScroll` (e.g., a new zone or direction).
3.  Add necessary methods to `PlayerGestureCallback` if the gesture requires new player interactions.
4.  Implement the feedback visualization in `layout_player_gesture_feedback.xml` if needed.

## Testing

*   **`PlayerGestureHelperTest`**: Unit tests for the math and logic (pure functions). Mocks `Context` for `AudioManager`.
*   **`PlayerGestureZonePolicyTest`**: Unit tests for horizontal zone selection, including the split-line boundary.
*   **`PlayerGestureFeedbackStateTest` / `PlayerSurfacePolicyTest`**: Unit tests for feedback presentation and placement (both indicator styles: edge sliders for brightness and volume on every surface, the top slider for seek, speed and pinch, the centered number for every gesture, their compact sizes and shadow room, the top pill for messages, cutout-symmetric margins on physical sides, level-following icons, pinch start and handle positions).
*   **`PlayerScrubPolicyTest`**: Horizontal lock distance and angle, the start detent, the spoiler-free relative seek track, the handle pinned at the ends of the video, logarithmic speed track positions, the signed change label, seek tick steps and the haptic tracker (steps, start detent, limits).
*   **`PlayerGestureFeedbackRenderTest` / `PlayerSpringInterpolatorTest`**: Native renders of the Material 3 Expressive vertical and top sliders, the centered number and the pill, side-chat and cutout placement, entrance springs and overlay contrast.
*   **`PinchDisplayModeControllerTest` / `PlayerDisplayModePreviewerTest`**: Unit tests for the pinch state machine (arm/hysteresis, responsive elastic deformation, neutral-crossing continuity) and renderer-relative preview/elastic scales.
*   **`PinchZoomControllerTest` / `PlayerPinchZoomTest`**: Free-zoom detents, elastic ends, kept and clamped zoom, cancel; and the real fragment path for saving, renderer swaps, re-clamping on resize and clearing from More.
*   **Integration**: Remaining `PlayerGestureListener` wiring is verified via manual testing due to `MotionEvent` mocking complexities in unit tests.
