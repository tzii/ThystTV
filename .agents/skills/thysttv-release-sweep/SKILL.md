# ThystTV Release Sweep Skill

Use this skill before publishing or finalizing a ThystTV release.

## Read First

- `AGENTS.md`
- `docs/AGENT_MAP.md`
- `docs/ROADMAP.md`
- `docs/RELEASE_PROCESS.md`
- `docs/TESTING.md`
- `docs/MANUAL_QA.md`
- `CHANGELOG.md`
- `.github/workflows/ci.yml`
- `.github/workflows/debug-build.yml`
- `.github/workflows/release.yml`
- `app/build.gradle.kts`

## Required Checks

Verify:

- versionCode
- versionName
- changelog entry
- `docs/release-notes/<version>.md`
- README user-facing claims
- screenshots if visual release
- APK artifact naming
- CI workflow
- debug APK workflow
- release workflow
- signing uses GitHub Secrets only
- no keystore/secrets committed

## Approval Rule

Release publishing, signing, tagging, and final release upload require human approval. Do not perform those actions autonomously.

## Release Branch Rules

- Do not include broad cleanup.
- Do not include broad upstream sync.
- Do not include unrelated website/player/updater changes.
- Release PR should be boring and easy to audit.

## Required Commands

```bash
./gradlew test
./gradlew assembleDebug
./gradlew lintDebug
./gradlew assembleRelease
```

If a command cannot run locally, explain why and list the CI job that should cover it.

## Human QA Handoff

Use `docs/MANUAL_QA.md`.

At minimum for release, require human verification for:

- app launch
- live playback
- VoD playback
- stream switching
- minimize/restore
- speed/quality controls
- floating chat
- stats screen
- portrait/landscape
- release APK install check, if possible

## Final Output

Return:

1. release readiness: ready / not ready
2. blockers
3. non-blocking issues
4. files changed
5. checks run
6. human QA required
7. human QA completed only if actually performed
8. release notes status
9. recommended tag/version action
