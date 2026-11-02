# Integrate the approved player changes for 1.5.2

## Goal

Combine the already reviewed/tested chat swipe, extended zoom/pan, lifecycle and
mini-player fixes without losing their behavior, and land the combined player
tree through protected master's normal CI gate. Release metadata and publication
remain a separate PR and exact-candidate process.

## Non-goals

No unrelated cleanup, upstream sync, updater, website, official tag or publication
in this integration PR. No new feature behavior beyond the five source PRs.

## Current context

User requested publishing the next release after reporting the previous player
tests and the mini-player follow-up work fine. These reports apply to the debug
builds supplied in this conversation; they do not certify the combined signed
release candidate or every device/backend scenario.

Master base: 7bfdbc694e0581e292bb7211860395fd9e5dc43b (published 1.5.1/code16).
Integrated heads: gesture stack #75/#76/#77 at
1a1a606fc0023826fced568e83e1219c8427775a, and #80/#82 at
54415a5ebf0a6cfa4e841802aa7185faa96bf1e2. Their merge ad1b1dcc9d28d8ee11e971c15d865ea662d73500
was clean, including PlayerFragment and the player/QA docs. Feature commits remain
in the history so the source PR attribution and evidence are preserved.

Master's ruleset requires an up-to-date `verify` check, PRs and resolved review
threads, with no bypass actors. Use one pure player integration PR rather than
mixing feature commits with the version/release-notes change or weakening CI.

## Files likely involved

- Existing player/gesture/mini-player changes from PRs #75-#77, #80 and #82
- This integration plan
- Next PR only: version metadata, release notes, bundled/Fastlane changelog,
  roadmap/agent map and release QA record

## Risks

Risk level: high for the combined player surface. The automatic merge can be
textually clean while gesture resets, mini-player geometry and PiP interact.
Use combined Gradle checks and actual emulator playback/transition checks.

## Human approval

Required before implementation: already provided by the maintainer's request to
push the new release after the debug tests passed. Exact signed-candidate QA and
both release review lanes are still required before tagging/publication.

## Implementation steps

1. Merge the two reviewed source tips locally without changing their code.
2. Run combined debug/release assembly, tests and lint; repository contract tests.
3. Check combined live/VoD, minimize/restore, PiP, chat swipe and zoom/pan/reset on
   the emulator; keep physical upgrade/candidate QA explicitly pending.
4. Push a pure integration PR, let its required up-to-date verify check pass and
   merge it through normal protection. Retain source PR links and dispositions.
5. Prepare a separate 1.5.2/code17 release metadata PR, validate, merge through CI
   and freeze the resulting master SHA.
6. Dispatch the signed-RC workflow at that SHA, verify the exact bundle and obtain
   GPT-6.1-sol/high and the maintainer-approved Freebuff/DeepSeek independent review
   (substituting for unavailable GLM for this release), plus exact-APK device approval.
7. Only then create the authorized annotated v1.5.2 tag and promote the exact bytes
   with the established publication tool. Never weaken protections or substitute
   a reviewer silently.

## Verification

- [x] assembleDebug
- [x] test
- [x] lintDebug
- [x] assembleRelease (unsigned local)
- [x] Repository contract tests and diff review
- [x] Combined emulator playback and transitions
- [ ] Required protected-master CI

Human QA required: live/VoD playback, switching, minimize/restore, close/reopen,
PiP/background, speed/quality, gestures and floating chat; candidate upgrade from
official 1.5.1, retained data, offline cold start and updater/install handover.

Human QA completed: general maintainer acceptance of the supplied #71 and #81
debug builds. Combined gesture/candidate and exact signed upgrade QA pending.

## Progress log

- 2026-10-31: User accepted the mini-player fix and requested the next release.
- 2026-10-31: Fetched current branches; source heads merge cleanly. Isolated the
  pure integration branch from release metadata. Original branches unchanged.
- 2026-10-31: Required OpenCode provider is unavailable in T3: its CLI version
  health check fails. Asked the maintainer to repair/enable it while preparation
  continues. No CLI/provider/model fallback or review substitution attempted.
- 2026-10-31: Maintainer explicitly approved latest-available DeepSeek through
  Freebuff as the independent review substitute for this release and requested
  a prompt to run manually. Prepared a tracked-source export pinned to ad1b1dcc9
  with the full diff and honest evidence status. Actual model/version/settings
  must come from Freebuff metadata; no model self-identification. Final signed-RC
  metadata/artifact review remains a later gate. Primary lane stays Sol/high.
- 2026-10-31: Repository contracts passed with native Git Bash on PATH (79 passed,
  0 failed, 2 POSIX-only cases skipped on Windows; all native Windows cases ran).
  Initial checksum fixture failure came from Windows resolving bash to the Store/
  WSL launcher. Corrected the documented environment, not source/tests. Updater
  static contracts and verifier Bash syntax also pass. Combined Gradle continues.
- 2026-10-31: Combined gates pass at source head ad1b1dcc9: 1,250 tests in 140
  suites, no failures/errors/skips; debug and unsigned release assembly pass;
  lint 0 errors / 332 existing warnings. Full Gradle run 10m42s. No code changes
  were needed for the integration. Native decoded VoD video checked with chat
  swipes hiding/showing the sidebar, extended zoom past Fill, one-finger pan,
  minimize clearing magnification and keeping a 645x363 mini-player, maximize,
  and system PiP entry/return. Framework UI Automator injected the pinch after
  this emulator console rejected event commands; an initial legacy runner
  dependency failure was resolved by including the device's own test-base jar.
  Those host helpers remain outside source. Emulator audio was disabled; no
  physical-device or audible-overlap certification. Test preference and original
  rotation settings restored; app stopped. Physical combined/signed QA pending.

## Decisions

- Preserve the separate source PRs and merge their tips through an integration
  PR with full combined CI. Keep version/release notes in the following PR.
- The prior debug acceptance does not approve an as-yet-unbuilt signed APK.
  Stop publication for exact-candidate approval and missing review evidence.

## Final PR summary draft

Combine the five approved player PRs, preserving source history and behavior.
Validation: combined Gradle/contract checks and emulator transitions. No version,
official tag or release publication in this PR; exact candidate QA remains next.
