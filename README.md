# Xtra

Xtra is a Twitch player and browser for Android.

## New Features (v2.0)

### 1. Network-Aware Data Saver
*   **Smart Quality Control**: Automatically detects when you are on a metered connection (Mobile Data) and caps the video quality to 480p to save your data plan.
*   **User Override**: If you manually select a higher quality while on data, the app respects your choice for the rest of the session.
*   **Settings Toggle**: Configurable via Player Settings > "Data saver".

### 2. Stream Title Tooltip
*   **Full Visibility**: Long stream titles that are truncated by the UI can now be viewed in full by simply tapping the title text in the player controls.

### 3. Gesture Controls
*   **Intuitive Control**: Easily adjust volume and brightness without leaving the immersive full-screen player.
*   **Volume**: Slide up/down on the **right** half of the screen.
*   **Brightness**: Slide up/down on the **left** half of the screen.
*   **Visual Feedback**: Displays a real-time slider overlay during adjustment.

## Fixes
*   Fixed missing string resources causing build failures.
*   Fixed  logic to correctly identify mobile networks.

## Architecture
*   **MVVM**: Clean separation of concerns with ViewModels handling UI logic.
*   **Media3/ExoPlayer**: Robust video playback engine.
*   **Kotlin Coroutines**: Efficient background processing.

## Building
1.  Ensure you have Android SDK configured in `local.properties`.
2.  Run `./gradlew assembleDebug`.

