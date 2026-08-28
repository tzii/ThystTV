---
name: diagnosing-bugs
description: Diagnose ThystTV bugs and regressions by building a tight reproduction of the reported behavior before proposing a fix.
---

# Diagnosing bugs

1. Build the fastest red-capable reproduction for the exact symptom: unit test, contract test, focused Gradle test, static fixture, or a device/manual recipe when Android runtime behavior is essential.
2. Run it. If the symptom needs a real Twitch session, device, or account that is unavailable, say so and keep that evidence pending.
3. Minimize the reproduction, then rank falsifiable causes and test one variable at a time.
4. Add a regression test at the real seam when possible. For lifecycle/player bugs, a shallow helper test is not a substitute for the actual behavior.
5. Apply the smallest fix, rerun the original reproduction, then run the relevant `AGENTS.md` checks.
6. Remove temporary logging and list any Required manual QA. Never mark manual rows Completed unless a real human/device check actually occurred.

Report symptom, root cause, fix, automated evidence, and remaining manual evidence separately.
