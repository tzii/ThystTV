# README and site feature/media refresh

## Goal

Bring the public feature inventory up to 1.6 and add current native screenshots
and a short recording alongside clearly labelled older media.

## Non-goals

No redesign, new hosting, release publication, account screenshots or app changes.

## Current context

README has 1.6 release intro but mostly 1.3 images; the static GitHub Pages site
has older feature copy. Preserve its four tabs, accessibility, reduced motion,
light/dark themes, fonts, download verification and Xtra credit.

## Files likely involved

README.md, docs/index.html, styles.css, site.test.js, images/features-1.6 and AGENT_MAP.

## Risks

Risk level: low. Avoid implying pending fixes are published; show native captures
with exact source and device provenance. No personal data or generated UI.

## Human approval

Required before implementation: no. User explicitly requested this refresh.

## Implementation steps

1. Capture current anonymous settings/rewind screens and a short native recording.
2. Update inventory for 1.4–1.6 features and document meaningful limitations.
3. Add a responsive gallery while retaining the existing layout and older demos.
4. Run site tests, validate media and links, inspect actual image pixels and diff.

## Verification

- [x] node --test docs/site.test.js (4 tests plus static assertions)
- [x] Native screenshots inspected; MP4 fully decoded with ffmpeg and sampled frames inspected; source and SHA-256 recorded
- [x] git diff --check and local media/link checks

Human QA required: phone/desktop browsers, light/dark, reduced motion and video controls.
Human QA completed: recorded below as performed. No fresh browser rendering claimed.

## Progress log

- 2026-11-15: Capturing native anonymous 1.6 debug UI from sign-in branch 5b60e79ad;
  its launcher-return change has no visible effect on these settings screens.

## Decisions

- Retain GitHub Pages and current editorial design. No generated screenshots.
- Current feature inventory refers to 1.6 functionality; pending Settings/Cast/
  reconnect PRs are deliberately excluded from public release claims.

## Final PR summary draft

Summary: feature inventory and native 1.6 media refresh.
Tests: site suite, local resources, actual PNG/MP4 validation.
Human QA: browser matrix remains.
Risks: older footage stays explicitly labelled; no authenticated/Cast coverage implied.

- 2026-11-15: Added three unedited native screenshots and a 9.2-second silent H.264
  recording (15fps, faststart). Preserved raw recording privately; checked actual
  pixels and decoded every final frame without errors. Updated 1.4–1.6 inventory,
  current gallery and media provenance. Existing accessibility/static checks pass.
- Browser layout/playback QA remains unperformed; existing four tabs and JS are unchanged.
