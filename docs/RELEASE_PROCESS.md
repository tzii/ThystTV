# Release Process

## Release model

Public releases should be intentional.

Recommended flow:
1. finish release PRs
2. merge to `master`
3. update `CHANGELOG.md`
4. add `docs/release-notes/X.Y.Z.md`
5. add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (short, ≤500 chars — IzzyOnDroid/F-Droid read it from the tagged commit)
6. create tag `vX.Y.Z`
7. let the release workflow publish the APK, its `.sha256` checksum, and the GitHub Release

The workflow verifies the APK is signed with the official release certificate (pinned in `.github/workflows/release.yml`) and appends a verification footer to the release notes. See `docs/APK_VERIFICATION.md` and `docs/DISTRIBUTION.md`.

## Before release
- version bump committed
- release notes written
- screenshots updated if release is visual
- README updated if user-facing behavior changed
- build and tests pass
- critical manual checks completed

## Release note contents
Each release note should include:
- highlights
- player changes
- UI / visual changes
- fixes
- known issues

## After release
- verify the GitHub release page
- verify APK artifact name and that the `.sha256` checksum asset is attached
- add the new APK checksum to the table in `docs/APK_VERIFICATION.md`
- verify screenshots / site update if included
- delete merged release branches
