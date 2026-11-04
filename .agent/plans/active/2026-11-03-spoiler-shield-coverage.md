# Spoiler Shield: VoDs, clips, expiry, settings and player titles

## Goal

Finish #78: protect VoD and clip lists, bookmarks and downloads; let rules expire; add
channels and categories from Settings; hide the player and media-session title.

## Non-goals

Share intents, chat, and hiding a VoD's position (Spoiler-free VoDs covers that).

## Current context

Branch `feat/78-spoiler-shield`, with the player title in `feat/78-spoiler-player-title`
above it. The maintainer asked for this before testing everything on a device.

## Files likely involved

`ui/common/SpoilerShield.kt`, `ui/settings/SpoilerShieldSettings.kt`, list adapters and
layouts, item menus, `PlayerFragment`, `Media3Fragment`, `ExoPlayerFragment`, tests.

## Risks

Risk level: medium. Recycled cards must never flash protected content. The player
change is limited to title text and media metadata.

## Human approval

Requested directly by the maintainer. Publication is outside this work.

## Implementation steps

1. Rules with an optional expiry, read through one matcher.
2. Cards for VoDs, clips, bookmarks and downloads.
3. Settings search and add flow.
4. Player title and media-session title.

## Verification

- [x] Focused tests: `SpoilerShieldTest`, `SpoilerShieldCardsTest`
- [x] assembleDebug, test and lintDebug in the combined QA build
- [ ] Device QA: docs/MANUAL_QA.md > Spoiler Shield, plus live, VoD, stream switching,
  minimize/restore, close/reopen, PiP/background, speed/quality, gestures, floating chat

Human QA completed: none yet.

## Progress log

- 2026-11-03: Implemented; awaiting the maintainer's device QA.

## Decisions

An open list applies expiry at its next bind rather than running a timer per card.

## Final PR summary draft

Spoiler Shield now covers VoD and clip lists, bookmarks, downloads and player titles,
supports expiring rules, and can add channels or categories from Settings.
