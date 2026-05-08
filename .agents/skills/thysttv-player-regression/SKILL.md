# ThystTV Player Regression Skill

Use this skill when investigating or changing:

- player startup
- stream switching
- old audio bleed
- Media3/ExoPlayer behavior
- minimize / restore
- PiP/background behavior
- speed or quality controls
- gestures
- floating chat interaction with player
- black bars or player surface glitches

## Read First

- `AGENTS.md`
- `docs/AGENT_MAP.md`
- `docs/PLAYER.md`
- `docs/TESTING.md`
- `docs/MANUAL_QA.md`

Also inspect relevant files under the current player/main-activity paths. Use `docs/AGENT_MAP.md` as a starting point, but search if paths have moved.

## Required First Output Before Edits

Return a short diagnosis plan:

1. suspected files/functions
2. likely lifecycle path
3. smallest safe fix
4. risks and risk level
5. whether human approval is required
6. automated checks
7. human QA required

In interactive workflows, wait for approval before editing when risk is high. In autonomous workflows, follow `.agent/PLANS.md` approval behavior.

## Implementation Rules

- Preserve the single-active-player invariant.
- Avoid broad player rewrites on release branches.
- Prefer small, reversible fixes.
- Do not change unrelated updater, website, README, or upstream-sync files.
- Keep ThystTV-specific UX over upstream behavior.
- Do not claim physical-device QA unless it actually happened.

## Required Checks

Normal player change:

```bash
./gradlew assembleDebug
./gradlew test
```

If layouts/resources changed:

```bash
./gradlew lintDebug
```

If release-risk:

```bash
./gradlew assembleRelease
```

## Required Human QA Handoff

Use `docs/MANUAL_QA.md`, especially:

- live stream opens
- VoD opens
- stream switching
- old audio does not continue
- minimize / restore
- close / reopen
- PiP/background behavior
- speed/quality controls
- gestures
- floating chat

## Final Output

Return:

- summary of changes
- files changed
- tests run
- human QA required
- human QA completed only if actually performed
- risks/follow-ups
