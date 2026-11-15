# Settings: Spoilers, Live and search

## Goal

Group spoiler controls in one screen, live rewind controls in Player → Live,
and make section names searchable with correct wide-screen selection.

## Non-goals

No preference key/default changes, player behavior changes or release work.

## Current context

Based on 1.6.0 c461b3264. Spoiler Shield lived in UI; spoiler-free VoDs lived
in Player. Search omitted category titles. Wide selection needed a redraw.

## Files likely involved

- SettingsActivity, SettingsPanes, SettingsSearch and SettingsSearchAdapter
- root/player/ui/spoiler preference XML, settings navigation, strings
- SettingsStructureTest, SettingsSearchTest and SettingsPanesTest

## Risks

Moved rows and navigation must preserve stored values and phone/TV reachability.
Risk level: medium.

## Human approval

Required before implementation: no. User authorized all follow-up work; reversible UI change.

## Implementation steps

1. Move rows while retaining keys/defaults; register Spoilers navigation.
2. Index category names and show category context; redraw wide selection.
3. Test resources, navigation and search; inspect phone/wide UI.

## Verification

Automated checks:

- [x] Initial assembleDebug and focused settings tests passed (prior turn).
- [x] assembleDebug test lintDebug full gate (1,459 tests; no failures/errors/skips; lint 0 errors, 342 warnings)

Human QA required:

- [ ] Existing spoiler values/rules remain; manager add/remove, reveal and VoD hiding.
- [ ] Phone/wide/TV navigation, search, Back and correct selected section.
- [ ] Live buffer defaults and saved values unchanged.

Human QA completed: recorded below as performed.

## Progress log

- 2026-11-15: Recovered implementation from interrupted turn; focused gate exit 0.
- 2026-11-15: Started full build/test/lint with installed JDK 21 and Android SDK.

## Decisions

- Match titles, summaries and category names; screen names remain breadcrumbs.
- Keep all persisted keys and defaults, so no migration is required.

## Final PR summary draft

Summary: dedicated Spoilers screen, Live category, category-aware search and wide selection redraw.
Tests: full gate and native settings tests.
Human QA: phone/wide/TV matrix above.
Risks: moved settings discovery and navigation.

- 2026-11-15: Phone emulator: Spoilers rows render; searching spoiler finds all
  four controls with category breadcrumbs. Wide window: selecting Hide titles
  from search opens Spoilers and marks its row; switching sections works.
  Screenshots in docs/images/settings-followup. Physical TV/rule editing remains QA.
- Initial lint found the extracted inflater missing its pre-existing RestrictedApi
  annotation. Moved the annotation with the unchanged AndroidX inflation code;
  rerunning the gate. No lint baseline or severity changed.

- 2026-11-15: Final full gate passed; reviewed diff and captured native phone/wide screenshots. Physical QA stays listed.
