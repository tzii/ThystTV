# Xtra - Enhanced Twitch Client

Xtra is a feature-rich, open-source Twitch player and browser for Android, designed for a superior viewing experience.

## Key Features

### 💬 Floating Chat (New!)
*   **Overlay Mode**: Keep up with the chat while watching in full-screen.
*   **Customizable**: Resize and move the chat window anywhere on the screen.
*   **Opacity Control**: Adjust transparency to balance visibility between the stream and the chat.
*   **High Contrast Mode**: Toggle for better readability over bright video content.

### ⏱️ Screen Time & Stats
*   **Daily Tracking**: Monitor your daily viewing time directly within the app.
*   **Top Channels**: See a leaderboard of your most-watched channels and streamers.
*   **Privacy First**: All stats are stored locally on your device and are never shared.
*   **Easy Access**: Access your stats via the new "Stats" tab in the bottom navigation.

### 📉 Network-Aware Data Saver
*   **Smart Quality Control**: Automatically detects when you are on a metered connection (Mobile Data) and caps the video quality to 480p to save your data plan.
*   **Helpful Tooltip**: A non-intrusive tooltip notifies you when Data Saver is active, with a "Don't show again" option.
*   **User Override**: If you manually select a higher quality while on data, the app respects your choice for the rest of the session.
*   **Settings Toggle**: Enable or disable this behavior via Player Settings > "Data saver".

### 👆 Gesture Controls
*   **Intuitive Control**: Easily adjust settings without blocking the view.
*   **Volume**: Slide up/down on the **right** half of the screen.
*   **Brightness**: Slide up/down on the **left** half of the screen.
*   **Visual Feedback**: Real-time slider overlay during adjustment.

### ℹ️ Stream Title Tooltip
*   **Full Context**: Long stream titles that are usually truncated are now fully accessible. Simply tap the title text in the player controls to see the full description.

## Architecture
*   **MVVM**: Clean separation of concerns with ViewModels handling UI logic.
*   **Media3/ExoPlayer**: Robust video playback engine.
*   **Kotlin Coroutines**: Efficient background processing.

## Building
1.  Ensure you have the Android SDK configured in `local.properties`.
2.  Run `./gradlew assembleDebug`.
