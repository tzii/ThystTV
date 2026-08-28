---
name: grilling
description: Stress-test a player UX, architecture choice, release change, or risky plan before implementation. User-invoked only.
disable-model-invocation: true
---

# Grilling

Read the relevant ThystTV docs and code first. Facts available in the repository are not questions for the user.

Build the design as dependent decisions. Ask the currently unblocked decisions in one round, with a recommendation and the tradeoff for each. Recompute after the user's answers until behavior, non-goals, compatibility expectations, manual-QA needs, and acceptance criteria are explicit.

Do not implement a grilled plan until the user asks to proceed. For player/UI work, include phone versus wide-layout behavior and gesture conflicts when they are real design choices rather than silently assuming them.
