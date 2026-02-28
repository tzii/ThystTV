---
name: systematic-debugging
description: A structured methodology for debugging software issues enforcing root cause analysis.
license: MIT
compatibility: opencode
metadata:
  process: systematic
  outcome: stability
---

## What I do
- Enforce a 4-phase process: **Observe** (collect data/logs), **Hypothesize** (theories), **Experiment** (test theories), and **Verify** (ensure fix works and no regressions).
- Prevent "guess-and-check" coding by requiring evidence before proposing changes.
- Analyze stack traces and logs to identify the exact point of failure.

## When to use me
- When facing a crash or unexpected app behavior.
- When dealing with race conditions or complex state-related bugs.
- When the cause of a bug is not immediately obvious from the first glance.
- Before committing a fix, to ensure it addresses the root cause rather than just a symptom.
