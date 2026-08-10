# Distribution

How ThystTV is (and could be) distributed, and what each channel requires. Researched
against the current IzzyOnDroid inclusion policy and F-Droid practices (checked 2026-06-12).

## Current channel: GitHub Releases

The release workflow (`.github/workflows/release.yml`) runs on `v*` tags and:

1. runs unit tests,
2. builds and signs the release APK with the ThystTV release key,
3. renames it to `ThystTV-X.Y.Z.apk`,
4. verifies the APK is signed with the pinned official certificate (fails the release if not),
5. generates `ThystTV-X.Y.Z.apk.sha256`,
6. publishes the GitHub Release with the release notes from `docs/release-notes/X.Y.Z.md`
   plus a verification footer.

Users verify builds via [`APK_VERIFICATION.md`](APK_VERIFICATION.md). The in-app updater
checks GitHub Releases (opt-in).

## IzzyOnDroid

[IzzyOnDroid](https://izzyondroid.org/) distributes **developer-signed APKs** picked up
automatically from tagged GitHub releases — no extra signing or build infrastructure needed
on our side. This is the natural next channel for ThystTV.

### Readiness checklist (against their [inclusion policy](https://izzyondroid.org/docs/general/AppInclusionPolicy/))

| Requirement | Status |
|---|---|
| FLOSS license (SPDX) | ✅ AGPL-3.0-only |
| Public source repo | ✅ GitHub |
| Unique `packageName` + display name | ✅ `com.tzii.thysttv` / "ThystTV" |
| Fork rules: credit original, distinguishable name/icon | ✅ README/site credit Xtra; own name + icon |
| Developer-signed release APK on tagged GitHub releases | ✅ release workflow |
| No `android:debuggable` / `android:testOnly` flags | ✅ standard release build |
| Self-updater must be strictly opt-in | ✅ `update_check_enabled` defaults to `false` |
| Fastlane metadata in repo (short/full description, icon, screenshots) | ✅ as of this docs pass (en-US, ThystTV-specific) |
| Per-release changelogs `fastlane/.../changelogs/<versionCode>.txt` | ✅ added for versionCode 10; must be committed **before tagging** each release |
| **APK size ≤ ~30 MB (rule of thumb)** | ⚠️ `ThystTV-1.2.0.apk` is **34.7 MB** — see below |
| No trackers; non-free components tolerated only to a small degree | ⚠️ ships `play-services-cronet` + ML Kit (`language-id`, `translate`) — non-free, **not** trackers; would be flagged `NonFreeComp` |

### The size problem (the only real blocker-ish item)

IzzyOnDroid reserves ~30 MB per app (and keeps up to 3 versions within the hard limit).
34.7 MB may be accepted as an exception, but better options, roughly in order of impact:

1. **Per-ABI APK splits** (`splits { abi { ... } }`): an `arm64-v8a`-only APK drops all other
   native libs (ML Kit translate JNI is the heavy part). Izzy's `ApkMatch` config can then
   pick the arm64 asset from releases while the fat APK stays available for everyone else.
2. **Make the translation feature optional/removable** (build flavor without ML Kit): solves
   both the size and the `NonFreeComp` flag in one move, at the cost of a feature.
3. Ask Izzy whether the current size is acceptable as-is — exceptions exist but must be
   well-reasoned.

### How to submit

1. Make sure a tagged release with the APK attached exists and fastlane metadata is current.
2. File an issue with the IzzyOnDroid maintenance repo on Codeberg
   (https://codeberg.org/IzzyOnDroid/repo) requesting inclusion, linking the repo and the
   releases page.
3. Provide the `AllowedAPKSigningKeys` value (they pin our signing cert — APKs signed with
   any other key are refused):

   ```text
   7f8a843b92561e0fff49d77589f54d95169f6b739fcf235b52d4ca6b8ab71f4a
   ```

4. Mention the APK size up front and which mitigation we prefer (saves a round-trip).

After inclusion, their updater checks the releases page daily; new tagged releases with an
attached APK appear within ~24h. Keep `changelogs/<versionCode>.txt` committed before tagging
so the changelog ships with the same commit the APK is picked from.

## F-Droid (main repository)

Different model entirely: F-Droid **builds from source** on their servers and (by default)
signs with their own key. Precedent exists — upstream Xtra is in F-Droid main
(`com.github.andreyasadchy.xtra`).

What it would take for ThystTV:

- **No non-free dependencies in the built APK.** `play-services-cronet` and ML Kit
  (`language-id`, `translate`) are non-free and not buildable from source. We would need a
  build flavor (e.g. `foss`) that excludes them — Cronet has the `cronet-embedded`
  alternative; translation would have to be dropped or replaced in that flavor.
- A build recipe merge request to [`fdroiddata`](https://gitlab.com/fdroid/fdroiddata).
- Optional but recommended: **reproducible builds**, which let F-Droid publish our
  developer-signed APK (same signature as GitHub Releases — users can switch sources without
  reinstalling). Without reproducibility, F-Droid's build is signed by F-Droid, and users
  cannot cross-update between the GitHub APK and the F-Droid APK.

Verdict: feasible later, but real work (flavor split + recipe + ideally reproducibility).
IzzyOnDroid first; F-Droid main as a separate, deliberate project.

## Droid-ify / Neo Store and other F-Droid clients

Nothing extra to do: these clients consume F-Droid-compatible repos. Once ThystTV is on
IzzyOnDroid, Droid-ify users get it by enabling the IzzyOnDroid repo (bundled by default in
Droid-ify and Neo Store). Worth a line in the README install section once live.

## Release checklist additions (any channel)

- Commit `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` **before** creating
  the tag.
- Keep `docs/APK_VERIFICATION.md` checksum table updated per release.
- Never change the release signing key silently (see APK_VERIFICATION).
