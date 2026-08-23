# ThystTV current state

Status:  REVIEW
Agent:   zcode

Focus:   Validate round-8 popup fixes on device: Quality menu must open without
         the first-press jump (pre-reveal positioning + frozen anchor) and stay
         put while the quality label/viewer count refresh; portrait sheet text
         contrast (section labels, codec sublabels) in light/dark themes.
Next:    Install a fresh debug APK, run the round-8 QA list in
         .agent/plans/active/2026-08-22-popup-jump-portrait-readability-round8.md
         plus the standard player lifecycle regression list.
Pointer: codex/player-ux-tablet-live-discovery
As-of:   2026-08-22 · d5f379b5 + rounds 5-8 popup & stats follow-ups (uncommitted)

Notes:   Round 8 binds popup content before showing, positions the container
         before the host is revealed (no first-frame top-left flash), and caches
         the first valid trigger rect so control-bar reflows cannot drag an open
         popup. Secondary panel text contrast raised for portrait sheets over
         video. `assembleDebug`, all unit tests, and `lintDebug` pass (0 errors).
         If portrait readability still fails QA, get a screenshot before changing
         anything — the complaint may be about size/layout, not contrast.
