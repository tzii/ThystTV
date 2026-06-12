<p align="center">
  <img src="docs/images/readme/app-icon.svg" width="112" height="112" alt="ThystTV app icon">
</p>

<h1 align="center">ThystTV</h1>

<p align="center">
  A better Twitch client for Android, focused on player polish, floating chat, local viewing stats, and large-screen comfort.
</p>

<p align="center">
  <a href="https://github.com/tzii/ThystTV/releases/latest"><img alt="latest release" src="https://img.shields.io/github/v/release/tzii/ThystTV?style=for-the-badge&color=9850ee"></a>
  <a href="https://github.com/tzii/ThystTV/actions/workflows/ci.yml"><img alt="CI" src="https://img.shields.io/github/actions/workflow/status/tzii/ThystTV/ci.yml?branch=master&style=for-the-badge&label=CI"></a>
  <a href="https://github.com/tzii/ThystTV/blob/master/LICENSE"><img alt="license AGPL-3.0" src="https://img.shields.io/github/license/tzii/ThystTV?style=for-the-badge"></a>
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-93.9%25-7f52ff?style=for-the-badge&logo=kotlin&logoColor=white">
  <img alt="Stars" src="https://img.shields.io/github/stars/tzii/ThystTV?style=for-the-badge">
</p>

<p align="center">
  <a href="#download--install">Download</a>
  ·
  <a href="#screenshots">Screenshots</a>
  ·
  <a href="#what-thysttv-adds">Features</a>
  ·
  <a href="#build-from-source">Build</a>
  ·
  <a href="#credit">Credit</a>
</p>

---

## What is ThystTV?

**ThystTV** is a third-party Twitch client for Android. It is based on [Xtra](https://github.com/crackededed/Xtra), with ThystTV-specific work aimed at making the viewing experience cleaner, faster, and more comfortable on phones, tablets, and large displays.

The focus: player refinement, better floating chat behavior, local watch-history insights, large-screen comfort, and a trustworthy, well-documented release process.

> ThystTV is a fork of Xtra. A lot of credit goes to the Xtra project for the foundation this app builds on.

## Project status

| Area | Current detail |
|---|---|
| Repository | [`tzii/ThystTV`](https://github.com/tzii/ThystTV) |
| Active branch | [`master`](https://github.com/tzii/ThystTV) |
| Latest release | [`v1.2.0`](https://github.com/tzii/ThystTV/releases/latest) |
| Package name | `com.tzii.thysttv` ([how to verify builds](docs/APK_VERIFICATION.md)) |
| License | [GNU AGPL-3.0](LICENSE) |
| Primary language | Kotlin, with Java components |

## Screenshots

<table>
  <tr>
    <td width="33%" align="center">
      <strong>Popular streams</strong><br><br>
      <img src="docs/images/readme/popular.png" alt="Popular streams tab in ThystTV" width="260">
    </td>
    <td width="34%" align="center">
      <strong>Full-screen player</strong><br><br>
      <img src="docs/images/readme/player.png" alt="Full-screen player with floating chat overlay" width="430">
    </td>
    <td width="33%" align="center">
      <strong>Local stats</strong><br><br>
      <img src="docs/images/readme/stats.png" alt="Local stats dashboard in ThystTV" width="260">
    </td>
  </tr>
</table>

<table>
  <tr>
    <td width="33%" align="center">
      <strong>Playback speed</strong><br><br>
      <img src="docs/images/readme/playback-speed.png" alt="Playback speed popup showing active speed controls" width="300">
    </td>
    <td width="34%" align="center">
      <strong>Video quality</strong><br><br>
      <img src="docs/images/readme/video-quality.png" alt="Video quality popup with quality and chat-only controls" width="300">
    </td>
    <td width="33%" align="center">
      <strong>Updater changelog</strong><br><br>
      <img src="docs/images/readme/changelog-update.jpg" alt="Updater dialog showing release changelog and download action" width="260">
    </td>
  </tr>
</table>

## Floating chat

Floating chat is one of ThystTV's headline viewing upgrades. It keeps chat available during full-screen playback without forcing the player into a cramped split layout.

<p align="center">
  <img src="docs/images/readme/floating-chat.png" alt="Full-screen playback with floating chat overlay" width="760">
</p>

<p align="center">
  <a href="docs/images/readme/floating-chat.mp4">Watch the floating chat demo video</a>
</p>

## What ThystTV adds

### Player refinement

- Gesture-based playback controls for horizontal seek, playback speed, brightness, and volume.
- Clearer feedback while interacting with the player.
- Better visual handling for minimized player states.
- VoD scrubbing improvements that scale with video duration.

### Floating chat

- Chat overlay designed for full-screen viewing.
- A cleaner way to keep stream context visible while the video remains primary.
- Better fit for phones, tablets, and wide layouts.

### Local stats

- Watch-history and screen-time insights.
- Category breakdowns and viewing patterns.
- Favorite channel and session-focused views.
- Stats stay local on the device.

### Updater and changelog

- In-app update checks with release details.
- Changelog previews before downloading a new build.
- Direct links to the GitHub release when more context is needed.

### Large-screen comfort

- Layout work for tablets and wider Android screens.
- Player and browsing screens tuned to avoid cramped controls.
- More polished presentation for the 1.2 release cycle.

## Build from source

Recommended local setup:

- Android Studio, current stable release
- JDK 21
- Android SDK matching the project configuration

```bash
./gradlew assembleDebug
./gradlew test
./gradlew assembleRelease
```

On Windows:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat test
.\gradlew.bat assembleRelease
```

## Download & install

Official builds are published on **[GitHub Releases](https://github.com/tzii/ThystTV/releases)**.

1. Download `ThystTV-X.Y.Z.apk` from the [latest release](https://github.com/tzii/ThystTV/releases/latest).
2. Allow your browser/file manager to install unknown apps if Android asks.
3. Install. Future official releases install over it (same signing key), and the optional in-app updater can check GitHub Releases for you (off by default, enable in Settings → Updater).

ThystTV requires Android 6.0+ and installs alongside upstream Xtra (different package name).

### Verify your download

Every release is signed with the same ThystTV release key:

```text
Package:  com.tzii.thysttv
SHA-256:  7F:8A:84:3B:92:56:1E:0F:FF:49:D7:75:89:F5:4D:95:16:9F:6B:73:9F:CF:23:5B:52:D4:CA:6B:8A:B7:1F:4A
```

```bash
apksigner verify --print-certs ThystTV-X.Y.Z.apk
```

See **[docs/APK_VERIFICATION.md](docs/APK_VERIFICATION.md)** for keytool/AppVerifier instructions, per-release checksums, and signature-mismatch troubleshooting.

### Privacy & local data

ThystTV has no analytics and no tracking. Watch-history/stats data stays on your device; your Twitch login token is only used against Twitch's own APIs. The updater only contacts the GitHub Releases API, and only if you enable it.

### Troubleshooting installs

- **"App not installed" on update** — usually a corrupted download; re-download and check the checksum. If you previously installed a debug or self-built APK (different key), uninstall it first.
- **Update refused after switching download source** — only mixes of *differently-signed* builds conflict; all official GitHub releases share one key.
- More cases: [docs/APK_VERIFICATION.md](docs/APK_VERIFICATION.md#troubleshooting-app-not-installed--signature-mismatch).

## Documentation

- [Roadmap](docs/ROADMAP.md)
- [Testing guide](docs/TESTING.md)
- [Manual QA](docs/MANUAL_QA.md)
- [Player notes](docs/PLAYER.md)
- [Gesture system](docs/GESTURE_SYSTEM.md)
- [Release process](docs/RELEASE_PROCESS.md)
- [APK verification](docs/APK_VERIFICATION.md)
- [Distribution channels](docs/DISTRIBUTION.md)
- [Upstream sync policy](docs/UPSTREAM_SYNC.md)
- [Upstream sync ledger](docs/UPSTREAM_SYNC_LEDGER.md)
- [Visual identity](docs/VISUAL_IDENTITY.md)

## Contributing

Contributions should stay focused, reviewable, and easy to test. For UI work, include screenshots. For player or gesture changes, include manual test notes for live playback, VoDs, orientation changes, and minimize/restore behavior.

Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a non-trivial pull request.

## Credit

ThystTV is based on [Xtra](https://github.com/crackededed/Xtra). The upstream project deserves major credit for the base client, architecture, and years of work that made this fork possible.

## License

ThystTV is licensed under the [GNU Affero General Public License v3.0](LICENSE).
