import assert from "node:assert/strict";
import fs from "node:fs";
import test from "node:test";

const buildGradle = fs.readFileSync("app/build.gradle.kts", "utf8");
const workflow = fs.readFileSync(".github/workflows/release.yml", "utf8");

test("release signing never falls back to the debug key", () => {
  const releaseBlock = buildGradle.match(/release\s*\{[\s\S]*?\n\s{8}\}/)?.[0] ?? "";
  assert.doesNotMatch(releaseBlock, /getByName\("debug"\)/);
  assert.match(buildGradle, /releaseKeystore\.isFile/);
  for (const name of ["KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD"]) {
    assert.match(buildGradle, new RegExp(name));
  }
});

test("every action is pinned to a full commit SHA", () => {
  const uses = [...workflow.matchAll(/^\s*uses:\s*([^\s#]+).*$/gm)].map((m) => m[1]);
  assert.ok(uses.length >= 4);
  for (const action of uses) assert.match(action, /^[^@]+@[0-9a-f]{40}$/);
});

test("workflow exposes the build-once promotion state machine", () => {
  assert.match(workflow, /expected_rc_sha:/);
  assert.match(workflow, /build_signed_rc:/);
  assert.match(workflow, /promote_release:/);
});

test("default and job permissions are least privilege", () => {
  assert.match(workflow, /contents:\s*read/);
  assert.match(workflow, /actions:\s*read/);
  assert.match(workflow, /contents:\s*write/);
});

test("keystore handling is guarded and cleaned up", () => {
  assert.match(workflow, /release-keystore\.jks/);
  assert.match(workflow, /if:\s*always\(\)/);
  // Test and lint must run before the keystore is decoded.
  const keystoreDecode = workflow.indexOf("base64 --decode > app/release-keystore.jks");
  const unitTest = workflow.search(/gradlew(\.bat)? test /);
  const lint = workflow.search(/gradlew(\.bat)?[^\n]*lintDebug/);
  assert.ok(keystoreDecode > -1);
  assert.ok(unitTest > -1 && unitTest < keystoreDecode, "unit tests must precede the keystore decode");
  assert.ok(lint > -1 && lint < keystoreDecode, "lint must precede the keystore decode");
});

test("RC binds run ID and manifest digest through tag metadata", () => {
  assert.match(workflow, /RC-Workflow-Run/);
  assert.match(workflow, /RC-Manifest-SHA256/);
});

test("promotion publishes verified bytes only", () => {
  assert.match(workflow, /--verify-tag/);
  const promotion = workflow.split(/^\s{2}promote_release:\s*$/m)[1] ?? "";
  assert.ok(promotion.length > 0, "promote_release job not found");
  assert.doesNotMatch(promotion, /assembleRelease|KEYSTORE_BASE64|KEYSTORE_PASSWORD|KEY_ALIAS|KEY_PASSWORD/);
  assert.match(promotion, /Protect release tags/);
  assert.match(promotion, /Authorize release tag creation/);
  assert.match(promotion, /release-metadata\.mjs policy/);
  assert.match(promotion, /github\.actor[^\n]*tzii|tzii[^\n]*github\.actor/);
  assert.match(promotion, /release-bundle\/rc-manifest\.json/);
  assert.match(promotion, /178386212/);
});

test("promotion reads the official signing certificate and attaches required assets", () => {
  assert.match(workflow, /official-signing-certificate\.sha256/);
  const promotion = workflow.split(/^\s{2}promote_release:\s*$/m)[1] ?? "";
  assert.match(promotion, /download-artifact/);
  assert.match(promotion, /run-id:/);
  assert.match(promotion, /github-token:/);
  assert.match(promotion, /ThystTV-\$\{VERSION_NAME\}\.apk"/);
  assert.match(promotion, /ThystTV-\$\{VERSION_NAME\}\.apk\.sha256"/);
  assert.match(promotion, /rc-manifest\.json"/);
});

test("artifacts are never overwritten", () => {
  assert.match(workflow, /overwrite:\s*false/);
  assert.doesNotMatch(workflow, /overwrite:\s*true/);
});

test("release notes cannot fall back to generated placeholder text", () => {
  assert.doesNotMatch(workflow, /Release notes were not found/);
  assert.match(workflow, /--notes-file "docs\/release-notes\/\$\{VERSION_NAME\}\.md"/);
});
