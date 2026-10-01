# Compact ThystTV website redesign

## Goal

Create a distinctive, playful website that introduces ThystTV, demonstrates its real features with restrained motion, and makes the latest Android APK easy to find. Keep the desktop experience compact and mobile content purposeful.

## Non-goals

No Android app, player, release, publishing, upstream, README, or existing artwork changes. No new website framework, external service, tracking, or simulated claims about playback.

## Current context

The existing GitHub Pages site uses `docs/index.html`, `docs/styles.css`, and `docs/script.js`. Its copy references the older 1.2 release. Real pre-1.3 device screenshots/video and native 1.3 layout previews are available. The current 1.3.1 candidate is not advertised as published. Unrelated untracked files already exist in the checkout and must remain untouched.

## Files likely involved

- `docs/index.html`
- `docs/styles.css`
- `docs/script.js`
- `docs/site.test.js`
- `docs/TESTING.md`
- `docs/MANUAL_QA.md`
- `docs/VISUAL_IDENTITY.md`
- `docs/fonts/` (self-hosted, licensed typography)

## Risks

- Small screens, enlarged text, keyboard interaction, motion sensitivity, unavailable storage, screenshot loading, and video/dialog focus.
- Existing semantic, distribution, credit, and screenshot provenance requirements must remain covered by the updated site checks.

Risk level: medium.

## Human approval

Required before implementation: no.

Reason: user explicitly requested this reversible website redesign. No publishing or app behavior changes are involved.

## Implementation steps

1. Inspect existing site, real assets, product claims, visual guidance, and checks.
2. Build a dark/cyan editorial hero and one keyboard-accessible interactive feature showcase. Keep all essential facts and the download accessible without JavaScript.
3. Add tactile microinteractions, controllable motion, a native video dialog, responsive layouts, and retained theme preference.
4. Update the site smoke checks and website QA guidance. Review the actual desktop/mobile pixels and interactions.
5. Record verification and remaining human QA, then archive this plan.

## Verification

Automated checks:

- [x] `node --test docs/site.test.js` (static contracts and four preference tests)
- [x] `node --check docs/script.js`
- [x] `git diff --check`
- [x] Browser checks: all four features at 320, 390, 768, 1024 and 1440px; no horizontal overflow at normal text; both themes, persisted motion, keyboard arrows/Home/End, dialog Escape/focus restoration, and loaded assets.
- [x] Isolated browser fixtures: 200% text, storage denial with functioning controls, and no-JavaScript content fallback. Fixed narrow enlarged-text wrapping and a Stats preview overlap.
- [x] Reduced-motion pre-paint behavior covered by executed preference tests and CSS/controller review. Actual OS/browser reduced-motion settings remain physical-browser QA.
- Android Gradle checks are not applicable: only website files/docs change.

Human QA required:

- [ ] Final visual acceptance on a physical phone and desktop, including touch, video playback, and installation from GitHub Releases.

Human QA completed:

- None claimed.

## Progress log

- 2026-10-01: Read project guidance and frontend design skills; inspected real screenshot pixels and existing static site. Created scoped plan.
- 2026-10-01: Implemented the compact static site with real media, optional animation, accessible feature tabs, native video dialog, full-size control/Stats links, and self-hosted licensed Outfit. Updated website QA and smoke checks.
- 2026-10-01: Completed local website verification and diff review. User accepted the design and authorized making it the latest GitHub website. Publication is a follow-up through the repository's required PR/verify gate and the existing master/docs Pages source; live deployment verification will be reported in the chat.
- 2026-10-01: Repository Node contract suite passed: 79 passing tests, no failures, two expected POSIX-only skips on Windows. Native browser video playback reached readyState 4, advanced the playhead and reported no error. Narrow 200% text now wraps without horizontal overflow.

## Decisions

- Decision: retain the static GitHub Pages architecture.
  Reason: it works with the existing repository and avoids redundant dependencies or hosting changes.
- Decision: use selectable feature previews instead of a long feature-card page.
  Reason: the user explicitly requested no unnecessary scrolling and an enjoyable feature showcase.
- Decision: reuse original public imagery and label native sample previews/pre-1.3 footage.
  Reason: preserve fidelity and avoid invented app interfaces or release claims.

## Final PR summary draft

Summary: compact interactive website redesign with real app media and accessible, optional motion.
Tests: static site contracts, four preference tests, JavaScript syntax, whitespace check, desktop/mobile browser feature/theme/keyboard/dialog/asset checks, and isolated large-text/no-JavaScript/storage-denied checks passed. Remote required CI runs before merge.
Human QA: physical-device visual acceptance/video/install check pending.
Risks: website only; no app/release changes.
