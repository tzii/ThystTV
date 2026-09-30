# Roadmap

## Current release target: 1.3.1

- Improve visibility of brightness/volume gesture feedback on large video surfaces.
- Adapt compatible upstream correctness and localization changes with explicit
  regression coverage and a commit-by-commit ledger.
- Keep experimental Twitch sync isolated on its existing branch.
- Add D-pad navigation and visible focus for the existing Android TV interface
  (issue #24), checked with automated view tests and a TV emulator.
- Validate player, compact/wide/resized layouts and accepted API changes on devices.

Version 1.3.1/code 13 is being prepared from this update. Publication remains
pending branch checks, signed-candidate verification, device QA and release review.
Physical TV/OEM validation and a dedicated television layout remain outside this
small navigation update. See `release-notes/1.3.1.md` for the candidate scope.

## Previous 1.2 priorities (historical)

### P0
- show current speed in the player speed button
- make VoD seek scrubbing significantly more responsive
- fix minimized-player black bars / ugly surface background behavior
- refresh the adaptive icon background so the mark does not blend into the background
- add polished screenshots to the README and site
- create stronger repo visual branding / social preview

### P1
- verify large-screen layouts more rigorously
- improve split-screen / resized-window behavior
- strengthen release process and repo health files
- clean up leftover branches and docs

## Guiding priorities
1. stability first
2. player UX second
3. visual polish third
4. new features after confidence is restored
