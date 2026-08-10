# Release Process

ThystTV publishes releases through a build-once, exact-byte promotion pipeline. A signed
release candidate (RC) is built and independently approved exactly once; the GitHub Release
then promotes those exact bytes without rebuilding or re-signing anything.

## Branch gate

Before any release work is merged:

```powershell
.\gradlew.bat test --no-daemon --console=plain
.\gradlew.bat lintDebug assembleDebug assembleRelease --no-daemon --console=plain
node --test docs/site.test.js
node --test .github/workflows/release.test.mjs scripts/release/*.test.mjs
```

Local `assembleRelease` must stay unsigned unless `app/release-keystore.jks` and every
credential (`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) are present; Gradle never
falls back to the debug key. Complete the branch-level manual smoke suite and record the
evidence in the release PR.

## Merge and freeze the RC SHA

Merge the release PR through the repository's normal review policy, without tagging during
the merge, then freeze the exact post-merge commit:

```powershell
git fetch origin master
$rcSha = git rev-parse origin/master
$rcSha
```

Record `$rcSha` out of tree in the merged PR/review record. Evidence added after this freeze
belongs in merged-PR comments/attachments or workflow artifacts, never in a new source commit.

## Signed verification-only workflow dispatch

Dispatch the release workflow against the frozen SHA:

```powershell
gh workflow run release.yml --repo tzii/ThystTV --ref master -f expected_rc_sha=$rcSha
```

`build_signed_rc` asserts the dispatch targets `master` at exactly `$rcSha`, runs unit tests
and lint before decoding any key, decodes `app/release-keystore.jks` with `umask 077` and an
exit cleanup, builds the signed APK, verifies package/version/code/certificate against the
pinned official certificate, and uploads the APK, APK checksum, manifest, and manifest
checksum as artifact `{rc_sha}-{run_id}` with `overwrite: false`.

## Independent APK and manifest verification

Require event `workflow_dispatch`, conclusion `success`, and `head_sha == $rcSha`. Download
the `{rc_sha}-{run_id}` bundle and independently re-run the APK package/version/code/
certificate/checksum/manifest verification. Then smoke-test the exact signed RC APK,
including the logged-in cold-start offline Saved/Bookmarks/Downloads scenario, the
network-restoration retry, and updater/install behavior. Manual approval applies to these
exact bytes, not to a local rebuild.

## Mandatory GPT-5.6-sol/high adversarial review

Provide the exact base/head SHAs, full diff, workflow, release notes, upstream SHAs,
automated results, signed-RC run ID, manifest, checksum, certificate result, and manual
evidence. Require correctness, security, signing, lifecycle, compatibility, and regression
attacks. Preserve the raw attributed transcript outside the source tree. If GPT-5.6-sol/high
is unavailable, stop for user direction.

## Best-effort verified GLM 5.2 review

Use a local subagent or external crew harness only after confirming the actual model identity
is GLM 5.2. Give it an isolated snapshot and the same evidence without the first review's
conclusions. Preserve raw output outside the source tree. If unavailable, record the failed
check and use one clearly named independent external fallback reviewer.

## Finding disposition and RC invalidation

For each finding record reviewer, severity, verification, disposition, commit, and retest
evidence in merged-PR comments/attachments or workflow artifacts. Any code, docs, metadata,
or workflow change invalidates the RC: it requires a narrow follow-up PR, a new frozen master
SHA, a new signed RC bundle, the full smoke suite, and both adversarial lanes again.

## Release tag creation authorization and update/deletion protection

Before tagging, verify both rulesets exist exactly once and validate completely:

- `Protect release tags` targets exactly `refs/tags/v*` with no exclusions, is `active`,
  contains exactly the `deletion` and `update` rules, and has no bypass actors. While it
  remains active, nobody can update or delete `v*` tags directly.
- `Authorize release tag creation` targets exactly `refs/tags/v*` with no exclusions, is
  `active`, contains only the `creation` rule, and has exactly one always-bypass actor:
  GitHub user `tzii`, numeric ID `178386212`, type `User`. Only that exact user may create
  `v*` tags.

Record both ruleset IDs and full configurations in the out-of-tree release evidence before
tagging, and re-run every invariant against their post-publication configurations.
Administrators can still edit the rulesets themselves even though direct tag mutation is
blocked while the rules remain active; never weaken, duplicate, or silently re-target a
ruleset to work around a failed validation — stop for deliberate policy correction instead.

## Annotated tag fields

Create an annotated tag whose message binds exactly one approved RC run and manifest:

```text
RC-Workflow-Run: 123456789
RC-Manifest-SHA256: 64-lowercase-hex-characters
```

The numeric/digest examples describe format only; maintainers must copy both values from the
approved RC run and compare them with the values approved during review before tagging. The
peeled tag SHA must equal the frozen RC SHA. Lightweight tags, duplicate tag fields, or a
peeled SHA mismatch stop the release.

## Exact-byte publication

Pushing the tag runs `promote_release`, which verifies `github.actor == 'tzii'`, requires an
annotated tag object, parses exactly one run ID and manifest digest from the tag message,
validates the RC workflow run (exact run ID, `workflow_dispatch`, `success`, release workflow
path, `head_sha` equal to the peeled SHA), validates both release-tag rulesets against user
ID `178386212`, downloads artifact `{peeled_sha}-{run_id}`, re-verifies the manifest digest,
APK checksum, package/version/code/certificate, and the full promotion binding — then
publishes the downloaded APK, APK checksum, and manifest as three required release assets
with `gh release create --verify-tag --notes-file docs/release-notes/{version}.md`.
Publication never runs `assembleRelease` and never touches keystore secrets.

## Post-publication checks and immutable-tag rollback policy

Verify the release page, the required APK/checksum/manifest assets, updater discovery, the
install/upgrade path, canonical website download links, and the certificate fingerprint
against [`APK_VERIFICATION.md`](APK_VERIFICATION.md). Fetch both rulesets again by their
recorded IDs and re-run every invariant against their post-publication configurations.

`v*` tags are immutable while the protection rulesets remain active: never move, retarget,
or recreate a published tag, and never overwrite release assets. If a blocker appears after
publication, mark the affected version, prepare a new `versionCode`/`versionName`, and repeat
the entire RC/review pipeline with a new tag.

## Publication failure cases

Publication stops on any of: missing or expired run artifacts, non-dispatch runs,
unsuccessful conclusions, wrong head SHA, lightweight tags, duplicate or missing tag fields,
version mismatch, wrong package/certificate/checksum, a missing, duplicate, inactive, or
malformed release-tag ruleset, a wrong creation authority, any protection bypass, a missing
required release asset, or any attempt to rebuild the APK during promotion.
