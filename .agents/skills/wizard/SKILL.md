---
name: wizard
description: Create a human-run checklist or script for setup, signing, dashboard, device, or release steps that the agent cannot perform itself. User-invoked only.
disable-model-invocation: true
---

# Wizard

Use this only for genuinely human-only steps.

1. Read the repository procedure first, especially `docs/RELEASE_PROCESS.md`, `docs/APK_VERIFICATION.md`, and `docs/MANUAL_QA.md`.
2. List the ordered stages, what each stage produces, and whether any value is secret.
3. For third-party dashboards, verify current instructions from authoritative docs. Never invent a button, field, URL, or menu path.
4. Generate a small PowerShell, shell, or Markdown runbook suited to the user's environment. Secret input must stay hidden and must not be echoed into logs.
5. Put confirmation gates before irreversible actions such as tagging, publishing, replacing credentials, or installing an APK over user data.
6. Statically verify commands and variable names. Do not run a human-interactive release wizard on the user's behalf unless explicitly asked for the action.

A wizard guides the person through what only they can do. It must not duplicate steps the agent can safely perform directly.
