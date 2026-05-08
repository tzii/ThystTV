# ThystTV Visual Polish Skill

Use this skill for README, screenshots, banners, website/landing page, icon/background, social preview, and visual presentation work.

## Read First

- `AGENTS.md`
- `docs/AGENT_MAP.md`
- `docs/VISUAL_IDENTITY.md`
- `README.md`
- relevant website/docs-site files
- relevant image asset directories

## Visual Direction

ThystTV should look:

- dark
- polished
- Android-native
- purple/violet with cyan highlights
- viewer-first
- open-source/GitHub-friendly
- modern but not generic SaaS
- not Twitch-branded
- not gamer-cringe

## Screenshot Rules

- Prefer real screenshots.
- Use clean device frames where helpful.
- Keep feature popups readable.
- Hide private account info.
- Do not invent impossible UI.
- Avoid ugly black bars in showcased player states.
- Do not use Twitch logos/branding.

## Good Feature Callouts

- playback speed
- quality popup
- floating chat
- minimized player
- stats
- gestures
- large-screen behavior

## Implementation Rules

- Do not modify Android player code as part of visual docs work unless explicitly requested.
- Do not mix website/README polish with player lifecycle fixes.
- Keep README and site consistent but not duplicated.
- If generated assets are added, document their source or purpose.
- Do not claim screenshots are real unless they are real.

## Verification

For docs/images only:

- inspect rendered Markdown/site if possible
- check image paths
- check file sizes are reasonable
- check screenshots do not expose private info

For Android resources/icon changes:

```bash
./gradlew assembleDebug
./gradlew lintDebug
```

## Final Output

Return:

- changed assets/files
- before/after intent
- render checks performed
- privacy check status
- human visual QA required
- follow-up visual tasks
