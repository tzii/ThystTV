# ExecPlans For ThystTV

Use an ExecPlan for complex work that spans multiple files, has regression risk, or may take multiple agent passes.

This file defines the required format. Plans are living documents, checked into the repository, and updated as work proceeds.

## When An ExecPlan Is Required

Create or update an ExecPlan for:

- player lifecycle changes
- stream handoff / audio bleed fixes
- minimized-player / PiP fixes
- player speed or quality dialog redesigns
- broad UI/resource changes
- release workflow/signing/version changes
- upstream Xtra sync batches
- website/landing-page redesign
- multi-file refactors
- work expected to require several implementation passes

Do not require an ExecPlan for:

- typo fixes
- one-link docs updates
- one screenshot replacement
- tiny localized string updates
- mechanical formatting only

## Approval Behavior

For interactive/coplay workflows:

- Produce the plan first.
- Wait for human approval before editing when the user asked for planning/review first, or when risk is high.

For autonomous/local agent workflows:

- After creating a plan, proceed only if all are true:
  - the plan is narrow and reversible
  - risk is low or medium
  - no secrets/private data are involved
  - no release signing/publishing action is involved
  - no broad upstream sync is involved

Pause for human approval when:

- risk is high
- release publishing/signing/tagging is involved
- broad player lifecycle behavior changes are involved
- upstream sync is broad or conflict-heavy
- private data, credentials, or real crash logs are involved
- the task scope is ambiguous

## Location

Store active plans in:

```text
.agent/plans/active/
```

Store completed plans in:

```text
.agent/plans/completed/
```

Use names like:

```text
.agent/plans/active/2026-05-07-player-quality-dialog.md
.agent/plans/active/2026-05-07-release-sweep-1.2.md
```

## Required ExecPlan Format

Each plan must be self-contained. A future agent should be able to resume from the plan without reading old chat.

```markdown
# <Plan title>

## Goal

What user-visible outcome should exist when this is done?

## Non-goals

What should this task explicitly not change?

## Current context

Relevant files, docs, current behavior, and known constraints.

## Files likely involved

- `path/to/file`
- `path/to/other-file`

## Risks

- regression risk
- UI risk
- release risk
- upstream conflict risk

Risk level: low / medium / high

## Human approval

Required before implementation: yes / no

Reason:

## Implementation steps

1. ...
2. ...
3. ...

## Verification

Automated checks:

- [ ] `./gradlew assembleDebug`
- [ ] `./gradlew test`
- [ ] `./gradlew lintDebug`, if relevant
- [ ] `./gradlew assembleRelease`, if release-risk

Human QA required:

- [ ] ...

Human QA completed:

- [ ] ...

## Progress log

- YYYY-MM-DD: Created plan.
- YYYY-MM-DD: ...

## Decisions

- Decision:
  Reason:
  Alternatives considered:

## Final PR summary draft

Summary:
Tests:
Human QA:
Risks:
```

## Rules For Agents

- Update the progress log after meaningful work.
- Record decisions that affect architecture, player behavior, release flow, or UX.
- Keep the plan honest. If something failed, write it down.
- Do not claim manual QA unless it was actually performed.
- Move completed plans to `.agent/plans/completed/`.

## Archive Policy

- Keep completed plans that document important architecture, player, release, or upstream decisions.
- For trivial completed plans, it is acceptable to delete instead of archiving once the PR is merged.
- If completed plans become noisy, revisit the archive policy in a separate cleanup PR.
