---
name: blast-radius
description: Check what a ThystTV change could break outside its direct call sites, especially player lifecycle, downloads, updater, persistence, release tooling, and upstream ports.
disable-model-invocation: true
---

# Blast radius

Pin the exact diff. Look beyond symbol references.

Follow lifecycle order, Activity/Fragment state, service/background behavior, persisted preferences and downloads, network/API shapes, updater/install flow, APK metadata/signing assumptions, release scripts, layouts/resources, and manual upstream ports.

Identify the safety assumptions the change depends on. Prove each with the cheapest real check available. A focused test or build is stronger than a code-reading argument; a real device reproduction is strongest when Android behavior is the risk.

Return confirmed risks, investigated-and-cleared cases, unproven assumptions, and the cheapest pre-merge check for the most likely regression. Do not turn unavailable device evidence into a pass.
