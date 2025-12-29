# ThystTV

<p align="center">
  <img src="docs/images/icon.png" width="128" height="128" />
</p>

A fork of [Xtra](https://github.com/crackededed/Xtra) with additional features focused on viewer experience, data management, and accessibility.

> **Note:** This fork is experimental and mostly vibe coded. Features are tested manually but may have rough edges. Contributions and bug reports welcome!

## What's Different from Xtra?

| Feature | Xtra | ThystTV |
|---------|------|---------|
| Floating Chat Overlay | No | Yes |
| Screen Time & Watch Stats | No | Yes |
| Swipe Gesture Controls | No | Yes |
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

### 👆 Gesture Controls
*   **Intuitive Control**: Easily adjust settings without blocking the view.
*   **Volume**: Slide up/down on the **right** half of the screen.
*   **Brightness**: Slide up/down on the **left** half of the screen.
*   **Visual Feedback**: Real-time slider overlay during adjustment.

## Building

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run tests
./gradlew test
```

## License

ThystTV is licensed under the [GNU Affero General Public License v3.0](LICENSE), same as the upstream Xtra project.
