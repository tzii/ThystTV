# Upstream Compatibility Cycle 1 Design

## Objective

Deliver the smallest high-confidence compatibility intake from Xtra after the ThystTV 1.2.0 release while preserving ThystTV's Hilt architecture, player customizations, floating chat, quality UI, and release version. The cycle also makes the existing floating-chat demonstration visible directly in the GitHub README.

## Baseline and Isolation

- Base commit: ThystTV `origin/master` at `699335172a1ba6798865ac71d6016b32be0a2c53`.
- Working branch: `codex/upstream-compat-cycle-1`.
- Worktree: a global isolated worktree so the original checkout and its untracked diagnostic files remain untouched.
- Java: Microsoft OpenJDK 21 supplied to Gradle per process.
- Android SDK: supplied per process from `C:\Users\simon\AppData\Local\Android\Sdk`; no `local.properties` file is committed.
- Baseline verification: `./gradlew test` succeeds before any repository edit.

## Scope

### Existing PR #10 microports

Cherry-pick the patch from commit `02749715694ddcf86a64df8b7fc6c6da8ee77683` without expanding its scope. Its parent changes because the design commit precedes it on this branch, so the resulting commit SHA is expected to differ. The patch fixes the moved-download thumbnail assignment, formats channel-point reward costs with the active locale, and documents both fixes under `[Unreleased]`.

### Twitch GraphQL endpoint compatibility

Replace all ten repository-local copies of `https://gql.twitch.tv/gql/` with one shared endpoint value, `https://gql.twitch.tv/gql`, used by GraphQL and playback-token requests across OkHttp, Cronet, HttpEngine, and proxy request construction. The implementation adapts Xtra commit `345cff59a2236d87574c6caab8f49980d8c3858b` without importing Xtra's version bump or architectural changes. A structural guard must prove that production source contains no trailing-slash copy and that the canonical literal appears only in the shared endpoint declaration.

### USERNOTICE message identity

Preserve the IRC `msg-id` tag when `ChatUtils.parseChatMessage` parses a USERNOTICE without a user-authored message. This adapts Xtra commit `ac0afa3d` and ensures subscription, raid, and related notice types remain distinguishable downstream. The parser test is parameterized over `sub`, `resub`, `subgift`, and `raid` so the downstream product purpose is explicit.

### Invalid stream-result filtering

Reject stream models only when both broadcaster identifiers are absent: `channelId == null` and `channelLogin == null`. Blank strings are deliberately not treated as null. A stream remains valid if either identifier exists. The predicate is shared by game, search, and general stream paging data sources to avoid subtly different filtering behavior. This adapts Xtra commit `cfa61fc8`. Coverage must verify both the four predicate cases and application to all eight mapping paths across `GameStreamsDataSource`, `SearchStreamsDataSource`, and `StreamsDataSource`.

### VOD retention policy

Define a single retention policy used by bookmark display and expiry sorting. Resolve the effective type with `userType ?: userBroadcasterType`; fallback is null-only, so a blank primary value does not fall through to the secondary value. After that resolution:

- null or blank broadcaster type: 7 days;
- Affiliate: 14 days;
- any other nonblank type, including Partner, Prime, and Turbo: 60 days.

This combines Xtra commits `627d440f` and `15dd7d9e`. The helper accepts both `userType` and `userBroadcasterType` so the current precedence remains explicit and testable. In particular, `userType = null` with an Affiliate fallback yields 14 days, while `userType = ""` with an Affiliate fallback yields 7 days.

### README floating-chat demonstration

The existing MP4 is 2,450,831 bytes, below GitHub's 10 MB free-plan video-attachment limit. Upload that unchanged file through a GitHub Markdown attachment editor to obtain a URL under `https://github.com/user-attachments/assets/`, then replace the plain relative MP4 anchor with that exact generated URL as a standalone inline-video reference. Keep the existing PNG screenshot as a static preview immediately above the player and retain a direct link to the repository MP4 as a fallback for browsers or contexts that do not render the attachment. The repository MP4 remains unchanged.

The root-level documentation gate must also become path-independent. `docs/site.test.js` resolves site assets from `__dirname` and the README from the repository root rather than relying on the process working directory. After the branch is pushed, a manual acceptance check opens the branch README on github.com and verifies that GitHub renders the attachment as an inline playable video; static tests cannot prove GitHub's rendering behavior.

## Commit Boundaries

After this design commit, implementation is divided into six independently reviewable commits:

1. Cherry-pick PR #10's microport patch without changing its file-level scope.
2. Add the tested shared GraphQL endpoint, migrate both repositories, add structural source coverage, and document the fix under `[Unreleased]`.
3. Add a failing parameterized USERNOTICE parser test, preserve `msg-id` in the no-message branch, and document the fix under `[Unreleased]`.
4. Add broadcaster-identity predicate and mapping-application tests, apply the predicate to all eight affected paging paths, and document the fix under `[Unreleased]`.
5. Add VOD-retention policy tests including null-only fallback precedence, use the policy in bookmark display and sorting, and document the fix under `[Unreleased]`.
6. Make `docs/site.test.js` path-independent, add README-rendering coverage, and replace the plain MP4 link with an inline GitHub attachment plus static and downloadable fallbacks.

No commit combines unrelated cleanup, dependency updates, upstream version changes, or player refactoring.

## Testing Strategy

Newly adapted behavioral changes in commits 2–6 follow red-green-refactor. PR #10's pre-reviewed microports are imported patch-equivalent and retain their existing CI/manual-QA boundary rather than claiming a retroactive red-green cycle.

- GraphQL endpoint: a unit test asserts the shared endpoint is canonical and has no trailing slash. A structural test scans production Kotlin source, rejects `gql.twitch.tv/gql/` anywhere, and verifies the canonical literal appears only in the endpoint declaration.
- USERNOTICE: parameterized raw IRC USERNOTICE fixtures for `sub`, `resub`, `subgift`, and `raid` must fail before the fix because `msgId` is null, then pass with each exact parsed tag.
- Stream filtering: unit tests cover neither identifier, ID only, login only, both identifiers, blank identifier values, and structural application to all eight intended mapping paths.
- VOD retention: unit tests cover null, blank, Affiliate with mixed case, Partner, Prime, and Turbo values, including `null + Affiliate fallback -> 14` and `blank + Affiliate fallback -> 7`.
- README: the repository documentation test verifies an inline GitHub attachment reference, the static preview image, and the downloadable MP4 fallback while rejecting the old link-only presentation. The test also proves `docs/site.test.js` works from the repository root.

Each commit runs its narrow test first. The completed cycle runs:

```powershell
.\gradlew.bat test --no-daemon --console=plain
.\gradlew.bat lintDebug assembleDebug --no-daemon --console=plain
node --test docs/site.test.js
```

The Java 21 and Android SDK environment values are set only for these commands.

## Error Handling and Compatibility

- Network behavior changes only the GraphQL URL string; headers, request bodies, proxy behavior, retries, and backend selection remain unchanged.
- USERNOTICE parsing preserves all current fields and adds only the missing identifier.
- Stream filtering does not require both identifiers; it accepts partial broadcaster data to avoid discarding usable results.
- VOD calculations preserve the existing date parsing and display mechanisms and centralize only the retention-day decision.
- README playback retains static and downloadable fallbacks, so failure to render GitHub's inline player does not hide the feature.
- Changelog entries are added to `[Unreleased]` in commits 2–5 without changing the application version.

## Non-Goals

This cycle does not include:

- a ThystTV version bump or 1.2.1 release decision;
- Xtra's 7TV emote-set fallback;
- the video-download segment-boundary fix;
- proxy, Cronet, HttpEngine, target SDK, IRC parser, Hilt, player-service, or quality-model modernization;
- PR #9's distribution and release-workflow changes;
- the broader website redesign.

Those changes require separate specifications and adversarial review boundaries.

## Acceptance Criteria

- The branch contains six implementation commits matching the boundaries above.
- Every newly adapted compatibility change in commits 2–6 has a regression test observed failing before its implementation change and passing afterward. PR #10's pre-reviewed microports retain their existing CI/manual-QA boundary.
- Existing unit tests, `lintDebug`, and `assembleDebug` pass under Java 21.
- `node --test docs/site.test.js` passes when invoked from the repository root on Windows and CI-compatible path handling is used.
- The README contains a GitHub `user-attachments` inline-video reference and preserves the static preview and MP4 fallback.
- After the branch is pushed, the branch README is manually verified on github.com to render an inline playable video.
- No dependency, SDK, application version, DI, player, gesture, floating-chat, or quality-selection behavior changes outside the listed scope.
- The original checkout remains unchanged apart from refreshed remote-tracking references.
