# ThystTV Code Review Rubric

Use this checklist when reviewing a branch or PR.

## Scope

- [ ] Is the PR narrowly scoped?
- [ ] Does it avoid unrelated cleanup?
- [ ] Does it mix player/release/site/upstream work unnecessarily?
- [ ] Does the PR summary explain user-visible impact?

## Correctness

- [ ] Does the code match the stated task?
- [ ] Are edge cases handled?
- [ ] Are nullability/lifecycle states handled?
- [ ] Are failures visible or safely ignored?

## Project Conventions

- [ ] `CONTRIBUTING.md` was considered.
- [ ] Existing Kotlin/Android style is followed.
- [ ] Existing architecture patterns are reused instead of inventing a new local pattern.
- [ ] New abstractions are justified by repeated use or clear simplification.

## Android Lifecycle

For activity/fragment/player work:

- [ ] No stale fragment callbacks
- [ ] No multiple active player sessions
- [ ] No lifecycle work after close/destroy
- [ ] Orientation/minimize/restore considered
- [ ] PiP/background behavior considered

## UI/Resources

- [ ] Layout works in portrait and landscape
- [ ] Text is readable
- [ ] Strings are localized or intentionally not
- [ ] No hardcoded private/test values
- [ ] No broken accessibility worse than before

## Release/Build

- [ ] Version changes are intentional
- [ ] Release notes updated if user-facing
- [ ] CI/build workflows still make sense
- [ ] Secrets remain in GitHub Secrets, not code

## Upstream Sync

- [ ] Upstream commits were not blindly cherry-picked
- [ ] Accepted/deferred commits are documented
- [ ] Retest areas are listed
- [ ] ThystTV UX is preserved

## Tests

- [ ] `./gradlew assembleDebug`
- [ ] `./gradlew test`
- [ ] `./gradlew lintDebug`, if relevant
- [ ] `./gradlew assembleRelease`, if release-risk

## Human QA

- [ ] Required human QA is listed using `docs/MANUAL_QA.md`
- [ ] Completed manual QA is marked only if actually performed
- [ ] Unperformed QA is listed as required/deferred, not pretended

## Final Review Output

Return:

1. overall risk: low / medium / high
2. blocking issues
3. non-blocking issues
4. missing tests or QA
5. suggested PR summary
