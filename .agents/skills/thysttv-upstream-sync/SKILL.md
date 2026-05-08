# ThystTV Upstream Sync Skill

Use this skill when comparing, cherry-picking, porting, or documenting upstream Xtra changes.

## Read First

- `AGENTS.md`
- `docs/AGENT_MAP.md`
- `docs/UPSTREAM_SYNC.md`
- `docs/RELEASE_1_2_UPSTREAM_COMMITS.md`
- `docs/TESTING.md`
- `docs/MANUAL_QA.md`

## Policy

ThystTV uses selective upstream sync.

Usually acceptable:

- crash fixes
- compatibility fixes
- isolated correctness fixes
- networking fixes
- low-risk maintenance

Usually deferred:

- WIP player rewrites
- broad query/schema/generated churn
- UI changes conflicting with ThystTV UX
- architecture churn
- dependency changes not tied to a bug
- release-branch cleanup with low user value

## Required First Output Before Edits

Return:

1. upstream commits considered
2. recommendation per commit: accept / manual port / defer
3. reason for each decision
4. expected conflicts
5. retest areas
6. risk level
7. whether this belongs in current release or post-release
8. whether human approval is required

Do not cherry-pick before producing this plan unless explicitly instructed.

## Implementation Rules

- Prefer manual minimal ports over broad cherry-picks.
- Keep app id/version and ThystTV-specific behavior.
- Do not mix upstream sync with unrelated release/site/player polish.
- Update `docs/RELEASE_1_2_UPSTREAM_COMMITS.md` or a newer release ledger.
- Document accepted and deferred commits.

## Required Checks

At minimum:

```bash
./gradlew assembleDebug
./gradlew test
```

For UI/resources/player changes:

```bash
./gradlew lintDebug
```

For release-risk dependency changes:

```bash
./gradlew assembleRelease
```

## Required Final Output

Return:

- upstream head inspected
- commits accepted
- commits manually ported
- commits deferred
- files changed
- conflicts or manual resolutions
- checks run
- human QA required
