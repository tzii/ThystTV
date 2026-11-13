# Clip title and length, TV support and a direct button

## Goal

Let viewers set a clip's title and length, create clips on Android TV, and optionally
start a clip from a player button.

## Non-goals

An in-app clip editor (Edit still opens Twitch's), Create Clip From VOD, or the official
three-minute timeline: neither is available to ordinary viewers through the public API.

## Current context

Branch `feat/79-create-clips`, stacked on `feat/safer-sign-in`, which it needs to compile.

## Files likely involved

`ui/player/ClipCreation.kt`, `PlayerViewModel`, `PlayerFragment`, `PlayerMorePopupBinder`,
`repository/HelixRepository.kt`, clip dialog and player layouts, button preferences.

## Risks

Risk level: medium. No playback changes. TV grants are stored separately and may belong
to another account. Device-token refresh couldn't be verified without a real grant.

## Human approval

Requested directly by the maintainer, who performs all real clip creation.

## Implementation steps

1. `ClipRequest` limits and the Helix `title` and `duration` parameters.
2. Options dialog with retained choices and title rejection handling.
3. TV device grant through `LoginActivity`, with refresh.
4. Optional player button, default off.

## Verification

- [x] Focused tests: `ClipCreationTest`, `ClipResultDialogTest`, `ClipCodecsTest`
- [x] assembleDebug, test and lintDebug in the combined QA build
- [ ] Device QA: docs/MANUAL_QA.md > Create clip. No real clip was created in testing.

Human QA completed: none yet.

## Progress log

- 2026-11-03: Implemented; awaiting the maintainer's device QA.

## Decisions

`PlayerViewModel`'s constructor is unchanged because six tests build it positionally; the
grant is passed to `createClip` as a function.

## Final PR summary draft

Clips gain a title and a 5 to 60 second length, Android TV support through a device code,
and an optional player button.
