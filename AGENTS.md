# AGENTS.md

## Project Identity

ThystTV is a third-party Android Twitch client forked from Xtra. The product priority is stable, polished viewing UX: playback, VoD scrubbing, floating chat, local stats, large-screen behavior, and clean open-source presentation.

Protect ThystTV-specific UX over blindly following upstream Xtra behavior.

## Start Here

Before non-trivial work, read:

- `docs/AGENT_MAP.md` for the repo map.
- `CONTRIBUTING.md` for project contribution/style expectations.
- `docs/ROADMAP.md` for current priorities.
- `docs/TESTING.md` for required checks.
- `docs/MANUAL_QA.md` for human QA handoff.
- `.agent/PLANS.md` for complex tasks.

Task-specific docs:

- Player/lifecycle changes: `docs/PLAYER.md`
- Release/version/workflow changes: `docs/RELEASE_PROCESS.md`
- Upstream Xtra sync: `docs/UPSTREAM_SYNC.md` and `docs/RELEASE_1_2_UPSTREAM_COMMITS.md`
- Visual/README/site work: `docs/VISUAL_IDENTITY.md`

## Build And Test Commands

Minimum checks for normal Android code changes:

```bash
./gradlew assembleDebug
./gradlew test
```

For UI/resources/player/release-risk changes:

```bash
./gradlew lintDebug
```

For release-related changes:

```bash
./gradlew assembleRelease
```

On Windows PowerShell, use:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat test
.\gradlew.bat lintDebug
.\gradlew.bat assembleRelease
```

## Work Rules

- Keep changes small and scoped.
- Do not mix player lifecycle, updater, website, screenshots, release, and upstream-sync work in one PR.
- For complex work, create or update an ExecPlan using `.agent/PLANS.md`.
- Do not blindly cherry-pick broad upstream Xtra commits.
- Prefer minimal, testable, user-visible changes.
- Update docs when behavior, process, QA expectations, or release flow changes.
- If mapped paths are stale, search the repo, use the current path, and update the map.
- Never commit secrets, keystores, tokens, private crash logs, private APKs, or personal local notes.

## Player Safety

Player changes are high risk. Always list required human QA for:

- live stream playback
- VoD playback
- stream switching
- minimize / restore
- close / reopen
- PiP or background behavior where relevant
- speed and quality controls
- gestures
- floating chat interaction

Do not introduce multiple active player sessions or stale player fragments.

## Recent Regression Lessons

- Player dependency or HLS parser changes need live/VoD retesting; issue #5 was handled with a narrow Media3 rollback, not broad player churn.
- Quality controls must preserve readable labels after chat-only/audio-only transitions.
- Floating chat overlays video, so it should use video-overlay readability rather than ordinary app light-theme surfaces.
- Stats filters and charts need compact and wide layout checks; labels can clip or stack when ranges change.
- Updater/changelog UI must render Markdown, avoid nested scrolling button rows, and keep actions Material-consistent.

## Release Safety

For release branches, avoid unrelated cleanup and broad upstream sync. Release work should be boring, documented, and easy to verify.

## Definition Of Done

A task is done only when:

- relevant Gradle checks pass, or failures are documented
- required human QA is listed
- docs are updated if behavior/process changed
- the diff is reviewed for regressions
- remaining risks are stated
